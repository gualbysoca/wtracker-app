package com.wtracker.field.controllers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wtracker.field.configs.UiConfig
import com.wtracker.field.services.clients.ClientService
import com.wtracker.field.services.models.FieldClient
import com.wtracker.field.services.route.RoutePlanService
import com.wtracker.field.utils.AppResult
import com.wtracker.field.utils.TextSearch
import com.wtracker.field.utils.TimeProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import com.wtracker.field.configs.WooCommerceConfig
import com.wtracker.field.services.auth.SessionStore
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Un mismo selector sirve para armar la ruta inicial y para agregar clientes "en caliente". */
enum class ClientPickerMode(val routeValue: String) {
    START_ROUTE("start"),
    ADD_TO_ROUTE("add");

    companion object {
        fun fromRouteValue(value: String?): ClientPickerMode = entries.firstOrNull { it.routeValue == value } ?: START_ROUTE
    }
}

data class SelectableClient(val client: FieldClient, val isSelected: Boolean)

/** Datos del modal "¿Listo para comenzar tu ruta de hoy?" congelados al abrirlo. */
data class RouteStartConfirmation(val startAtMillis: Long, val clientCount: Int)

data class ClientPickerUiState(
    val mode: ClientPickerMode = ClientPickerMode.START_ROUTE,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadErrorMessage: String? = null,
    val clients: List<SelectableClient> = emptyList(),
    val availableClientCount: Int = 0,
    val selectedCount: Int = 0,
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val startConfirmation: RouteStartConfirmation? = null,
    val isSubmitting: Boolean = false,
    val transientMessage: String? = null,
    val isVendor: Boolean = false,
) {
    val canSubmit: Boolean get() = selectedCount > 0 && !isSubmitting
}

sealed interface ClientPickerEvent {
    data object Completed : ClientPickerEvent
}

private data class ClientPickerLocalState(
    val selectedClientIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasCompletedFirstLoad: Boolean = false,
    val loadErrorMessage: String? = null,
    val startConfirmation: RouteStartConfirmation? = null,
    val isSubmitting: Boolean = false,
    val transientMessage: String? = null,
)

class ClientPickerViewModel(
    private val mode: ClientPickerMode,
    private val clientService: ClientService,
    private val routePlanService: RoutePlanService,
    private val timeProvider: TimeProvider,
    sessionStore: SessionStore,
) : ViewModel() {

    private val localState = MutableStateFlow(ClientPickerLocalState())
    private val eventChannel = Channel<ClientPickerEvent>(Channel.BUFFERED)
    val events: Flow<ClientPickerEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<ClientPickerUiState> = combine(
        clientService.observeFieldClients(),
        routePlanService.observeActiveRoute(),
        sessionStore.sessionFlow,
        localState,
    ) { clients, activeRoute, session, local ->
        // Al agregar en caliente, los clientes que ya están en la ruta no se vuelven a ofrecer.
        val clientIdsAlreadyInRoute = if (mode == ClientPickerMode.ADD_TO_ROUTE) activeRoute?.clientIds.orEmpty() else emptySet()
        val availableClients = clients.filterNot { it.id in clientIdsAlreadyInRoute }
        val visibleClients = availableClients.filter { client ->
            TextSearch.matches(local.searchQuery, client.businessName, client.address, client.contactName)
        }
        val isVendor = session?.role.equals(WooCommerceConfig.REQUIRED_VENDOR_ROLE, ignoreCase = true)

        ClientPickerUiState(
            mode = mode,
            isInitialLoading = clients.isEmpty() && !local.hasCompletedFirstLoad,
            isRefreshing = local.isRefreshing,
            loadErrorMessage = local.loadErrorMessage,
            clients = visibleClients.map { SelectableClient(it, isSelected = it.id in local.selectedClientIds) },
            availableClientCount = availableClients.size,
            selectedCount = local.selectedClientIds.size,
            searchQuery = local.searchQuery,
            isSearchVisible = local.isSearchVisible,
            startConfirmation = local.startConfirmation,
            isSubmitting = local.isSubmitting,
            transientMessage = local.transientMessage,
            isVendor = isVendor,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(UiConfig.STATE_SUBSCRIPTION_TIMEOUT_MS),
        ClientPickerUiState(mode = mode),
    )

    init {
        refreshClients()
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

    fun onClientToggled(clientId: String) {
        if (localState.value.isSubmitting) return
        localState.update { state ->
            val updatedSelection = if (clientId in state.selectedClientIds) {
                state.selectedClientIds - clientId
            } else {
                state.selectedClientIds + clientId
            }
            state.copy(selectedClientIds = updatedSelection)
        }
    }

    /** Ocultar el buscador también limpia el filtro para no esconder clientes sin que el usuario lo note. */
    fun onSearchToggled() {
        localState.update { state ->
            if (state.isSearchVisible) state.copy(isSearchVisible = false, searchQuery = "") else state.copy(isSearchVisible = true)
        }
    }

    fun onSearchQueryChanged(query: String) {
        localState.update { it.copy(searchQuery = query) }
    }

    /** Ícono de la cabecera: al iniciar ruta pide confirmación; al agregar en caliente aplica directo. */
    fun onPrimaryActionClicked() {
        val state = localState.value
        if (state.selectedClientIds.isEmpty() || state.isSubmitting) return

        when (mode) {
            ClientPickerMode.START_ROUTE -> localState.update {
                it.copy(startConfirmation = RouteStartConfirmation(timeProvider.nowMillis(), state.selectedClientIds.size))
            }
            ClientPickerMode.ADD_TO_ROUTE -> submit { routePlanService.addClientsToRoute(state.selectedClientIds) }
        }
    }

    fun onStartConfirmationDismissed() {
        if (localState.value.isSubmitting) return
        localState.update { it.copy(startConfirmation = null) }
    }

    fun onStartRouteConfirmed() {
        val selectedClientIds = localState.value.selectedClientIds
        submit { routePlanService.startRoute(selectedClientIds) }
    }

    fun onTransientMessageShown() {
        localState.update { it.copy(transientMessage = null) }
    }

    private fun submit(action: suspend () -> AppResult<*>) {
        if (localState.value.isSubmitting) return
        localState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            when (val result = action()) {
                // Se mantiene isSubmitting hasta salir de la pantalla para evitar dobles envíos.
                is AppResult.Success -> eventChannel.send(ClientPickerEvent.Completed)
                is AppResult.Failure -> localState.update {
                    it.copy(isSubmitting = false, startConfirmation = null, transientMessage = result.error.message)
                }
            }
        }
    }
}
