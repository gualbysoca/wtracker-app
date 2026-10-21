package com.wtracker.field.configs

import android.content.Context
import com.wtracker.field.services.auth.AuthService
import com.wtracker.field.services.auth.SessionExitService
import com.wtracker.field.services.auth.SessionStore
import com.wtracker.field.services.camera.EvidenceDraftStore
import com.wtracker.field.services.camera.EvidenceFileStore
import com.wtracker.field.services.camera.EvidenceStamper
import com.wtracker.field.services.camera.InstantCameraController
import com.wtracker.field.services.clients.ClientService
import com.wtracker.field.services.connectivity.ConnectivityObserver
import com.wtracker.field.services.location.FusedLocationProvider
import com.wtracker.field.services.location.GpsObserver
import com.wtracker.field.services.location.LocationProvider
import com.wtracker.field.services.route.RoutePlanService
import com.wtracker.field.services.shift.ShiftService
import com.wtracker.field.services.sync.SyncScheduler
import com.wtracker.field.services.sync.VisitSyncService
import com.wtracker.field.services.visits.VisitService
import com.wtracker.field.utils.ApiErrorMapper
import com.wtracker.field.utils.SystemTimeProvider
import com.wtracker.field.utils.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.wtracker.field.BuildConfig
import com.wtracker.field.services.update.ApkInstaller
import com.wtracker.field.services.update.UpdateHttpClient
import com.wtracker.field.services.update.UpdateService
import com.wtracker.field.services.woocommerce.DefaultWooCommerceService
import com.wtracker.field.services.woocommerce.RetrofitWooCommerceClient
import com.wtracker.field.services.woocommerce.WooCommerceService
import java.io.File

/**
 * Inyección de dependencias manual (composition root). Se evita Hilt/Dagger en v1 para
 * reducir tiempo de build y complejidad; migrar es directo porque todo entra por constructor.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val timeProvider: TimeProvider = SystemTimeProvider()

    private val json = Json {
        ignoreUnknownKeys = true
        // Omite nulls al serializar: Zod `.optional()` del Backoffice rechaza `null` explícito.
        explicitNulls = false
        coerceInputValues = true
    }
    private val errorMapper = ApiErrorMapper(json)

    private val database by lazy { LocalDatabase.build(appContext) }
    private val transactionRunner by lazy { LocalTransactionRunner(database) }

    val sessionStore by lazy { SessionStore(appContext) }
    val appPreferencesStore by lazy { AppPreferencesStore(appContext) }
    private val api by lazy { NetworkClientFactory(json, sessionStore).createApi() }

    val syncScheduler by lazy { SyncScheduler(appContext) }
    val connectivityObserver by lazy { ConnectivityObserver(appContext) }
    val gpsObserver by lazy { GpsObserver(appContext) }
    val locationProvider: LocationProvider by lazy { FusedLocationProvider(appContext, timeProvider) }

    val evidenceFileStore by lazy { EvidenceFileStore(appContext) }
    val evidenceDraftStore by lazy { EvidenceDraftStore(evidenceFileStore) }
    val evidenceStamper by lazy { EvidenceStamper(evidenceFileStore, timeProvider) }

    val authService by lazy { AuthService(api, sessionStore, syncScheduler, errorMapper) }

    val wooCommerceService: WooCommerceService by lazy {
        val client = RetrofitWooCommerceClient.create(json = json)
        DefaultWooCommerceService(client)
    }

    val orderConfirmationStore by lazy { com.wtracker.field.services.woocommerce.OrderConfirmationStore() }

    val orderEvidenceGenerator by lazy {
        com.wtracker.field.services.woocommerce.OrderEvidenceGenerator(
            fileStore = evidenceFileStore,
            locationProvider = locationProvider,
            timeProvider = timeProvider,
        )
    }

    val clientService by lazy {
        ClientService(
            api, database.clientDao(), database.visitRecordDao(), sessionStore, shiftService,
            errorMapper, timeProvider, appPreferencesStore,
        )
    }

    val visitService by lazy {
        VisitService(
            database.visitRecordDao(),
            database.evidenceDao(),
            transactionRunner,
            sessionStore,
            syncScheduler,
            timeProvider,
        )
    }

    val visitSyncService by lazy {
        VisitSyncService(
            api,
            json,
            database.visitRecordDao(),
            database.evidenceDao(),
            evidenceFileStore,
            sessionStore,
            errorMapper,
            timeProvider,
        )
    }

    val locationSyncService by lazy {
        com.wtracker.field.services.sync.LocationSyncService(
            api,
            database.locationPingDao(),
            sessionStore,
            errorMapper,
        )
    }

    val shiftService by lazy {
        ShiftService(
            appContext,
            database.shiftDao(),
            database.clientDao(),
            database.locationPingDao(),
            database.stopEventDao(),
            database.visitRecordDao(),
            database.plannedVisitDao(),
            sessionStore,
            timeProvider,
            appPreferencesStore,
        )
    }

    val routePlanService by lazy {
        RoutePlanService(
            api,
            errorMapper,
            database.plannedVisitDao(),
            shiftService,
            clientService,
            visitService,
            locationProvider,
            transactionRunner,
            timeProvider,
            appPreferencesStore,
        )
    }

    val sessionExitService by lazy { SessionExitService(routePlanService, syncScheduler, authService) }

    val updateService by lazy {
        UpdateService(
            httpClient = UpdateHttpClient(),
            json = json,
            updatesDir = File(appContext.cacheDir, UpdateConfig.UPDATES_CACHE_DIR),
            installedVersionCode = BuildConfig.VERSION_CODE,
        )
    }
    val apkInstaller by lazy { ApkInstaller(appContext) }

    /** Una instancia por pantalla de cámara: su ciclo de vida lo gobierna el ViewModel. */
    fun createCameraController(): InstantCameraController = InstantCameraController(appContext)

    /** Al arrancar el proceso, reanuda el envío de pendientes si hay una sesión guardada. */
    fun resumeBackgroundSync() {
        applicationScope.launch {
            if (sessionStore.ensureLoaded() == null) return@launch
            syncScheduler.requestImmediateSync()
            syncScheduler.schedulePeriodicSync()
        }
    }
}
