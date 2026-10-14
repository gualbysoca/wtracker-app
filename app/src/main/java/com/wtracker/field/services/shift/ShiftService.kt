package com.wtracker.field.services.shift

import android.content.Context
import com.wtracker.field.configs.AppPreferencesStore
import com.wtracker.field.services.auth.SessionStore
import com.wtracker.field.services.local.LocationPingDao
import com.wtracker.field.services.local.LocationPingEntity
import com.wtracker.field.services.local.PlannedVisitDao
import com.wtracker.field.services.local.ShiftDao
import com.wtracker.field.services.local.ClientDao
import com.wtracker.field.services.local.ShiftEntity
import com.wtracker.field.services.local.StopEventDao
import com.wtracker.field.services.local.StopEventEntity
import com.wtracker.field.services.local.VisitRecordDao
import com.wtracker.field.services.location.ShiftTrackingService
import com.wtracker.field.services.location.StopTransition
import com.wtracker.field.services.models.CapturedLocation
import com.wtracker.field.services.models.ShiftSummary
import com.wtracker.field.services.models.StopEventType
import com.wtracker.field.utils.AppError
import com.wtracker.field.utils.AppResult
import com.wtracker.field.utils.TimeProvider
import com.wtracker.field.utils.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/** Control de jornada: inicio/fin, recorrido GPS, paradas y métricas del día. */
class ShiftService(
    context: Context,
    private val shiftDao: ShiftDao,
    private val clientDao: ClientDao,
    private val locationPingDao: LocationPingDao,
    private val stopEventDao: StopEventDao,
    private val visitRecordDao: VisitRecordDao,
    private val plannedVisitDao: PlannedVisitDao,
    private val sessionStore: SessionStore,
    private val timeProvider: TimeProvider,
    private val appPreferencesStore: AppPreferencesStore,
) {

    private val appContext = context.applicationContext

    fun observeActiveShift(): Flow<ShiftEntity?> = sessionStore.sessionFlow.flatMapLatest { session ->
        if (session == null) flowOf(null) else shiftDao.observeActive(session.userId)
    }

    suspend fun findActiveShift(): ShiftEntity? {
        val session = sessionStore.ensureLoaded() ?: return null
        return shiftDao.findActive(session.userId)
    }

    /**
     * Abre (o reutiliza) la jornada activa tocando sólo Room, por lo que puede ejecutarse dentro
     * de una transacción. El rastreo GPS se arranca aparte con [startTracking].
     */
    suspend fun openShift(): AppResult<ShiftEntity> {
        val session = sessionStore.ensureLoaded()
            ?: return AppResult.Failure(AppError.Unauthorized("Inicia sesión para comenzar la jornada."))

        val shift = shiftDao.findActive(session.userId) ?: ShiftEntity(
            id = UUID.randomUUID().toString(),
            userId = session.userId,
            startedAtMillis = timeProvider.nowMillis(),
            endedAtMillis = null,
        ).also { shiftDao.insert(it) }
        return AppResult.Success(shift)
    }

    fun startTracking() {
        ShiftTrackingService.start(appContext)
    }

    suspend fun endActiveShift() {
        ShiftTrackingService.stop(appContext)
        val activeShift = findActiveShift() ?: return
        val nowMillis = timeProvider.nowMillis()
        stopEventDao.findOpen(activeShift.id)?.let { stopEventDao.close(it.id, nowMillis) }
        shiftDao.close(activeShift.id, nowMillis)
    }

    suspend fun recordLocationPing(shiftId: String, location: CapturedLocation) {
        locationPingDao.insert(
            LocationPingEntity(
                shiftId = shiftId,
                latitude = location.coordinate.latitude,
                longitude = location.coordinate.longitude,
                accuracyMeters = location.accuracyMeters,
                isMockLocation = location.isMockLocation,
                recordedAtMillis = location.capturedAtMillis,
            ),
        )
    }

    suspend fun applyStopTransition(shiftId: String, transition: StopTransition) {
        when (transition) {
            is StopTransition.Started -> stopEventDao.insert(
                StopEventEntity(
                    shiftId = shiftId,
                    type = StopEventType.STOP.name,
                    latitude = transition.coordinate.latitude,
                    longitude = transition.coordinate.longitude,
                    startedAtMillis = transition.atMillis,
                    endedAtMillis = null,
                ),
            )
            is StopTransition.EscalatedToInactivity -> stopEventDao.findOpen(shiftId)?.let {
                stopEventDao.updateType(it.id, StopEventType.INACTIVITY.name)
            }
            is StopTransition.EscalatedToExtremeAbandonment -> stopEventDao.findOpen(shiftId)?.let {
                stopEventDao.updateType(it.id, StopEventType.INACTIVITY.name)
            }
            is StopTransition.Ended -> stopEventDao.findOpen(shiftId)?.let {
                stopEventDao.close(it.id, transition.atMillis)
            }
        }
    }

    /** Resumen de la jornada activa o, si ya terminó, de la última jornada de hoy. */
    fun observeTodaySummary(): Flow<ShiftSummary?> = sessionStore.sessionFlow.flatMapLatest { session ->
        if (session == null) return@flatMapLatest flowOf(null)
        val startOfToday = TimeUtils.startOfDayMillis(timeProvider.nowMillis(), timeProvider.zone())

        shiftDao.observeLatestSince(session.userId, startOfToday).flatMapLatest { shift ->
            if (shift == null) return@flatMapLatest flowOf(null)
            combine(
                locationPingDao.observeForShift(shift.id),
                stopEventDao.observeForShift(shift.id),
                visitRecordDao.observeSince(session.userId, shift.startedAtMillis),
                combine(clientDao.observeAll(), appPreferencesStore.businessSettingsFlow) { clients, settings ->
                    clients to settings.geofenceRadiusMeters
                },
                plannedVisitDao.observeClientIds(shift.id),
            ) { pings, stops, visits, (clients, geofenceRadiusMeters), plannedClientIds ->
                val clientsInRoute = clients.filter { plannedClientIds.contains(it.id) }
                ShiftSummaryCalculator.calculate(
                    shift, pings, stops, visits, clientsInRoute, geofenceRadiusMeters,
                )
            }
        }
    }
}
