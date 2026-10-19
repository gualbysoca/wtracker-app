package com.wtracker.field.services.location

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wtracker.field.WTrackerApplication
import com.wtracker.field.configs.LocationConfig
import com.wtracker.field.configs.TrackingNotificationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano (tipo "location") activo durante la jornada. Registra el
 * recorrido y detecta paradas/inactividad aunque la app esté en segundo plano.
 * Se usa foreground service en lugar de ACCESS_BACKGROUND_LOCATION: cumple el requisito
 * sin pasar por la revisión restrictiva de Google Play para ubicación en segundo plano.
 */
class ShiftTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var trackingJob: Job? = null
    private var autoCheckoutJob: Job? = null
    private val resetStateFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_AUTO_CHECKOUT) {
            cancelAutoCheckout()
            return START_STICKY
        }

        if (!promoteToForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (trackingJob?.isActive != true) {
            trackingJob = serviceScope.launch { runTracking() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground(): Boolean = try {
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            TrackingNotificationConfig.NOTIFICATION_ID,
            TrackingNotificationFactory.build(this, startedAtMillis = null),
            foregroundType,
        )
        true
    } catch (exception: Exception) {
        // Android 14+ lanza SecurityException si el permiso de ubicación no está concedido.
        Log.e(TAG, "No se pudo iniciar el servicio de jornada", exception)
        false
    }

    private suspend fun runTracking() {
        val container = (application as WTrackerApplication).container
        val shiftService = container.shiftService
        val activeShift = shiftService.findActiveShift()
        if (activeShift == null) {
            stopSelf()
            return
        }

        refreshNotification(activeShift.startedAtMillis)
        var detectorState: StopDetectorState = StopDetectorState.Idle
        val detectorConfig = StopDetectorConfig()

        serviceScope.launch {
            resetStateFlow.collect { detectorState = StopDetectorState.Idle }
        }

        var pingsSinceLastSync = 0
        var lastLocationSyncMillis = System.currentTimeMillis()

        try {
            container.locationProvider
                .observeLocationUpdates(LocationConfig.TRACKING_INTERVAL_MS)
                .collect { location ->
                    shiftService.recordLocationPing(activeShift.id, location)
                    pingsSinceLastSync++

                    val now = System.currentTimeMillis()
                    val shouldSync = pingsSinceLastSync >= LocationConfig.LOCATION_SYNC_BATCH_SIZE ||
                        (now - lastLocationSyncMillis >= LocationConfig.LOCATION_SYNC_MAX_INTERVAL_MS)

                    if (shouldSync) {
                        pingsSinceLastSync = 0
                        lastLocationSyncMillis = now
                        serviceScope.launch { container.locationSyncService.syncPendingLocations() }
                    }

                    val evaluation = StopDetector.evaluate(
                        detectorState,
                        LocationSample(location.coordinate, location.capturedAtMillis, location.accuracyMeters),
                        detectorConfig,
                    )
                    detectorState = evaluation.state
                    evaluation.transition?.let { transition ->
                        if (transition is StopTransition.EscalatedToExtremeAbandonment) {
                            startAutoCheckoutTimer()
                        } else {
                            shiftService.applyStopTransition(activeShift.id, transition)
                            pingsSinceLastSync = 0
                            lastLocationSyncMillis = now
                            serviceScope.launch { container.locationSyncService.syncPendingLocations() }
                        }
                    }
                }
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Permiso de ubicación revocado durante la jornada", securityException)
            stopSelf()
        }
    }

    private fun refreshNotification(startedAtMillis: Long) {
        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        notificationManager.notify(
            TrackingNotificationConfig.NOTIFICATION_ID,
            TrackingNotificationFactory.build(this, startedAtMillis),
        )
    }

    private fun startAutoCheckoutTimer() {
        if (autoCheckoutJob?.isActive == true) return

        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        notificationManager.notify(
            TrackingNotificationConfig.ABANDONMENT_NOTIFICATION_ID,
            TrackingNotificationFactory.buildAbandonmentNotification(this)
        )

        autoCheckoutJob = serviceScope.launch {
            delay(5 * 60_000L) // 5 minutos de gracia
            notificationManager.cancel(TrackingNotificationConfig.ABANDONMENT_NOTIFICATION_ID)
            val container = (application as WTrackerApplication).container
            container.routePlanService.finishRoute()
            stopSelf()
        }
    }

    private fun cancelAutoCheckout() {
        autoCheckoutJob?.cancel()
        autoCheckoutJob = null
        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        notificationManager.cancel(TrackingNotificationConfig.ABANDONMENT_NOTIFICATION_ID)
        resetStateFlow.tryEmit(Unit)
    }

    companion object {
        private const val TAG = "ShiftTrackingService"
        const val ACTION_CANCEL_AUTO_CHECKOUT = "com.wtracker.field.ACTION_CANCEL_AUTO_CHECKOUT"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ShiftTrackingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShiftTrackingService::class.java))
        }
    }
}
