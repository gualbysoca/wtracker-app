package com.wtracker.field.controllers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wtracker.field.configs.AppPreferencesStore
import com.wtracker.field.configs.UiConfig
import com.wtracker.field.services.auth.SessionExitService
import com.wtracker.field.services.auth.SessionStore
import com.wtracker.field.services.clients.ClientService
import com.wtracker.field.services.connectivity.ConnectivityObserver
import com.wtracker.field.services.location.LocationProvider
import com.wtracker.field.services.models.GeoCoordinate
import com.wtracker.field.services.models.RouteClosureReason
import com.wtracker.field.services.models.RouteStop
import com.wtracker.field.services.models.VisitMarkRequest
import com.wtracker.field.services.models.VisitStatus
import com.wtracker.field.services.models.FieldClient
import com.wtracker.field.services.models.BusinessSettings
import com.wtracker.field.services.route.RoutePlanService
import com.wtracker.field.services.shift.ShiftService
import com.wtracker.field.services.visits.VisitService
import com.wtracker.field.utils.AppResult
import com.wtracker.field.utils.GamificationBadge
import com.wtracker.field.utils.GamificationEngine
import com.wtracker.field.utils.RouteOptimizer
import com.wtracker.field.utils.TimeProvider
import com.wtracker.field.utils.tickerFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

data class RouteSheetUiState(
    val userName: String = "",
    val nowMillis: Long = 0L,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadErrorMessage: String? = null,
    val stops: List<RouteStop> = emptyList(),
    val userLocation: GeoCoordinate? = null,
    val isOnline: Boolean = true,
    val isGpsEnabled: Boolean = true,
    val pendingSyncCount: Int = 0,
    val activeShiftStartedAtMillis: Long? = null,
    val isShiftActionInProgress: Boolean = false,
    /** Motivo del modal de cierre de ruta visible (finalizar o cerrar sesión); null si no hay modal. */
    val routeClosureDialogReason: RouteClosureReason? = null,
    val transientMessage: String? = null,
    val showRouteCompletedModal: Boolean = false,
    val suggestedNextClient: FieldClient? = null,
    val suggestionTitle: String? = null,
    val earnedBadges: List<GamificationBadge> = emptyList(),
    val businessSettings: BusinessSettings = BusinessSettings(),
) {
    val completedCount: Int get() = stops.count { it.client.currentStatus.isFinal }
    val pendingVisitCount: Int get() = stops.size - completedCount
    val hasActiveShift: Boolean get() = activeShiftStartedAtMillis != null
    val visitedCount: Int get() = stops.count { it.client.currentStatus == com.wtracker.field.services.models.VisitStatus.VISITED }
    val failedCount: Int get() = stops.count { it.client.currentStatus == com.wtracker.field.services.models.VisitStatus.FAILED }
    val stuckTaskHours: Int? get() = stops.asSequence()
        .filter { it.client.currentStatus == com.wtracker.field.services.models.VisitStatus.EN_ROUTE && it.client.statusUpdatedAtMillis != null }
        .map { (nowMillis - it.client.statusUpdatedAtMillis!!) / (60 * 60 * 1000L) }
        .filter { it >= 2L }
        .maxOrNull()?.toInt()
}

private data class RouteSheetLocalState(
    val isRefreshing: Boolean = false,
    val hasCompletedFirstLoad: Boolean = false,
    val loadErrorMessage: String? = null,
    val userLocation: GeoCoordinate? = null,
    val isShiftActionInProgress: Boolean = false,
    val routeClosureDialogReason: RouteClosureReason? = null,
    val transientMessage: String? = null,
    val hasShownRouteCompletedModal: Boolean = false,
    val suggestedNextClient: FieldClient? = null,
    val suggestionTitle: String? = null,
    val earnedBadges: List<GamificationBadge> = emptyList(),
)

private data class RouteSheetStatusSnapshot(
    val isOnline: Boolean,
    val isGpsEnabled: Boolean,
    val pendingSyncCount: Int,
    val activeShiftStartedAtMillis: Long?,
    val nowMillis: Long,
)

class RouteSheetViewModel(
    private val clientService: ClientService,
    private val routePlanService: RoutePlanService,
    private val shiftService: ShiftService,
    private val sessionExitService: SessionExitService,
    private val locationProvider: LocationProvider,
    private val visitService: VisitService,
    connectivityObserver: ConnectivityObserver,
    gpsObserver: com.wtracker.field.services.location.GpsObserver,
    sessionStore: SessionStore,
    timeProvider: TimeProvider,
    private val appPreferencesStore: AppPreferencesStore,
) : ViewModel() {

    private val localState = MutableStateFlow(RouteSheetLocalState())

    private val statusSnapshot = combine(
        connectivityObserver.observeIsOnline(),
        gpsObserver.observeIsGpsEnabled(),
        visitService.observePendingSyncCount(),
        shiftService.observeActiveShift(),
        tickerFlow(UiConfig.CLOCK_TICK_MS, timeProvider),
    ) { isOnline, isGpsEnabled, pendingCount, activeShift, nowMillis ->
        RouteSheetStatusSnapshot(isOnline, isGpsEnabled, pendingCount, activeShift?.startedAtMillis, nowMillis)
    }

    private val stopsFlow = combine(
        routePlanService.observeRouteClients(),
        localState.map { it.userLocation }.distinctUntilChanged()
    ) { clients, location ->
        RouteOptimizer.buildRoute(clients, location)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)

    private val sessionAndSettingsFlow = combine(
        sessionStore.sessionFlow,
        appPreferencesStore.businessSettingsFlow
    ) { session, settings -> Pair(session, settings) }

    val uiState: StateFlow<RouteSheetUiState> = combine(
        stopsFlow,
        routePlanService.observeRouteClients(),
        localState,
        statusSnapshot,
        sessionAndSettingsFlow,
    ) { stops, routeClients, local, status, sessionAndSettings ->
        val (session, settings) = sessionAndSettings
        RouteSheetUiState(
            userName = session?.fullName.orEmpty(),
            nowMillis = status.nowMillis,
            isInitialLoading = routeClients.isEmpty() && !local.hasCompletedFirstLoad,
            isRefreshing = local.isRefreshing,
            loadErrorMessage = local.loadErrorMessage,
            stops = stops,
            userLocation = local.userLocation,
            isOnline = status.isOnline,
            isGpsEnabled = status.isGpsEnabled,
            pendingSyncCount = status.pendingSyncCount,
            activeShiftStartedAtMillis = status.activeShiftStartedAtMillis,
            isShiftActionInProgress = local.isShiftActionInProgress,
            routeClosureDialogReason = local.routeClosureDialogReason,
            transientMessage = local.transientMessage,
            showRouteCompletedModal = routeClients.isNotEmpty() && routeClients.all { it.currentStatus.isFinal } && !local.hasShownRouteCompletedModal,
            suggestedNextClient = local.suggestedNextClient,
            suggestionTitle = local.suggestionTitle,
            earnedBadges = local.earnedBadges,
            businessSettings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(UiConfig.STATE_SUBSCRIPTION_TIMEOUT_MS), RouteSheetUiState())

    init {
        refreshClients()
        viewModelScope.launch {
            var previousCompletedCount = -1
            routePlanService.observeRouteClients().collect { clients ->
                val currentCompletedCount = clients.count { it.currentStatus.isFinal }
                
                if (previousCompletedCount != -1 && currentCompletedCount > previousCompletedCount) {
                    viewModelScope.launch {
                        val settings = appPreferencesStore.businessSettingsFlow.first()
                        val status = GamificationEngine.evaluate(clients, timeProvider.nowMillis(), settings)
                        
                        val nextUp = RouteOptimizer.buildRoute(clients, localState.value.userLocation).firstOrNull { !it.client.currentStatus.isFinal }
                        val (suggestedNextClient, suggestionTitle) = if (nextUp != null && nextUp.client.currentStatus != VisitStatus.EN_ROUTE) {
                            Pair(nextUp.client, status.latestMessage)
                        } else {
                            Pair(null, null)
                        }

                        localState.update { 
                            it.copy(
                                suggestedNextClient = suggestedNextClient, 
                                suggestionTitle = suggestionTitle, 
                                earnedBadges = it.earnedBadges + status.badges
                            ) 
                        }
                    }
                }
                previousCompletedCount = currentCompletedCount
                
                if (clients.any { !it.currentStatus.isFinal }) {
                    localState.update { it.copy(hasShownRouteCompletedModal = false) }
                }
            }
        }

        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                shiftService.observeTodaySummary(),
                com.wtracker.field.utils.tickerFlow(com.wtracker.field.configs.UiConfig.CLOCK_TICK_MS, timeProvider),
                appPreferencesStore.businessSettingsFlow,
            ) { summary, nowMillis, settings ->
                if (summary == null) return@combine
                if (GamificationEngine.snailLevel(summary, nowMillis, settings) < 1) return@combine
                if (appPreferencesStore.snailNotifiedShiftIdFlow.first() == summary.shiftId) return@combine

                appPreferencesStore.setSnailNotifiedShiftId(summary.shiftId)
                localState.update { it.copy(earnedBadges = it.earnedBadges + GamificationBadge.SNAIL) }
            }.collect { }
        }
    }

    fun refreshClients() {
        if (localState.value.isRefreshing) return
        localState.update { it.copy(isRefreshing = true) }

        viewModelScope.launch {
            val result = clientService.downloadClients()
            localState.update { state ->
                when (result) {
                    is AppResult.Success -> state.copy(isRefreshing = false, hasCompletedFirstLoad = true, loadErrorMessage = null)
                    is AppResult.Failure -> state.copy(
                        isRefreshing = false,
                        hasCompletedFirstLoad = true,
                        loadErrorMessage = result.error.message,
                        transientMessage = result.error.message,
                    )
                }
            }
        }
    }

    /** La ubicación sólo se usa para ordenar la ruta; si falla, la lista queda alfabética. */
    fun onLocationPermissionGranted() {
        viewModelScope.launch {
            val locationResult = locationProvider.getFreshLocation()
            if (locationResult is AppResult.Success) {
                localState.update { it.copy(userLocation = locationResult.data.coordinate) }
            }
        }
    }

    fun onFinishRouteRequested() {
        localState.update { it.copy(routeClosureDialogReason = RouteClosureReason.FINISH_ROUTE) }
    }

    /** Quita una tarea aún no iniciada de la ruta; el servicio rechaza las que ya tienen marcación. */
    fun onRemoveClientFromRoute(clientId: String) {
        viewModelScope.launch {
            val result = routePlanService.removeClientFromRoute(clientId)
            if (result is AppResult.Failure) {
                localState.update { it.copy(transientMessage = result.error.message) }
            }
        }
    }

    /** Con visitas pendientes se exige la misma confirmación que al finalizar; sin pendientes, sale directo. */
    fun onLogoutRequested() {
        if (uiState.value.pendingVisitCount > 0) {
            localState.update { it.copy(routeClosureDialogReason = RouteClosureReason.LOGOUT) }
            return
        }
        runShiftAction { logoutClosingRoute() }
    }

    fun onRouteClosureDismissed() {
        if (localState.value.isShiftActionInProgress) return
        localState.update { it.copy(routeClosureDialogReason = null) }
    }

    fun onRouteCompletedModalDismissed() {
        localState.update { it.copy(hasShownRouteCompletedModal = true) }
    }

    /**
     * Ambos motivos marcan los pendientes como fallidos. Al no quedar ruta activa (o sesión),
     * AppStartViewModel lleva a la bienvenida o al login.
     */
    fun onRouteClosureConfirmed() {
        val reason = localState.value.routeClosureDialogReason ?: return
        
        // Al cerrar manualmente la ruta, silenciamos el modal de "Ruta Terminada" 
        // para que no salte de golpe al fallar automáticamente los clientes pendientes.
        localState.update { it.copy(hasShownRouteCompletedModal = true) }

        runShiftAction {
            val errorMessage = when (reason) {
                RouteClosureReason.FINISH_ROUTE -> finishRoute()
                RouteClosureReason.LOGOUT -> logoutClosingRoute()
            }
            localState.update { it.copy(routeClosureDialogReason = null) }
            errorMessage
        }
    }

    private suspend fun finishRoute(): String? = when (val result = routePlanService.finishRoute()) {
        is AppResult.Success -> null
        is AppResult.Failure -> result.error.message
    }

    private suspend fun logoutClosingRoute(): String? = when (val result = sessionExitService.logoutClosingActiveRoute()) {
        is AppResult.Success -> null
        is AppResult.Failure -> result.error.message
    }

    fun onTransientMessageShown() {
        localState.update { it.copy(transientMessage = null) }
    }

    private fun runShiftAction(action: suspend () -> String?) {
        if (localState.value.isShiftActionInProgress) return
        localState.update { it.copy(isShiftActionInProgress = true) }
        viewModelScope.launch {
            val errorMessage = action()
            localState.update { it.copy(isShiftActionInProgress = false, transientMessage = errorMessage) }
        }
    }

    fun onAcceptNextClientSuggestion() {
        val nextClient = localState.value.suggestedNextClient ?: return
        localState.update { it.copy(suggestedNextClient = null, suggestionTitle = null) }
        viewModelScope.launch {
            val locationResult = locationProvider.getFreshLocation()
            if (locationResult is AppResult.Success) {
                visitService.registerVisitMark(
                    VisitMarkRequest(
                        clientId = nextClient.id,
                        status = VisitStatus.EN_ROUTE,
                        location = locationResult.data,
                        evidence = null,
                        notes = null
                    )
                )
            }
        }
    }

    fun onDismissNextClientSuggestion() {
        localState.update { it.copy(suggestedNextClient = null, suggestionTitle = null) }
    }

    fun onBadgesAcknowledged() {
        localState.update { it.copy(earnedBadges = emptyList()) }
    }
}
