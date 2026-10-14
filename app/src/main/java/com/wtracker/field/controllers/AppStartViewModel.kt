package com.wtracker.field.controllers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wtracker.field.services.auth.SessionStore
import com.wtracker.field.services.route.RoutePlanService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

sealed interface AppStartState {
    data object Loading : AppStartState
    data class Ready(val isLoggedIn: Boolean, val hasActiveRoute: Boolean) : AppStartState
}

/**
 * Decide la ruta raíz y reacciona en toda la app a: logout / expiración de sesión, inicio de ruta
 * (bienvenida → hoja de ruta) y fin de ruta (hoja de ruta → bienvenida).
 */
class AppStartViewModel(sessionStore: SessionStore, routePlanService: RoutePlanService) : ViewModel() {

    val uiState: StateFlow<AppStartState> = flow {
        sessionStore.ensureLoaded()
        emitAll(
            combine(sessionStore.sessionFlow, routePlanService.observeHasActiveRoute()) { session, hasActiveRoute ->
                val isLoggedIn = session != null
                AppStartState.Ready(isLoggedIn = isLoggedIn, hasActiveRoute = isLoggedIn && hasActiveRoute)
            },
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppStartState.Loading)
}
