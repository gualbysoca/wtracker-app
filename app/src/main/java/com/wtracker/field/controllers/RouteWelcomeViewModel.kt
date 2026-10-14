package com.wtracker.field.controllers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wtracker.field.configs.UiConfig
import com.wtracker.field.configs.AppPreferencesStore
import com.wtracker.field.services.auth.AuthService
import com.wtracker.field.services.auth.SessionStore
import com.wtracker.field.services.models.ShiftSummary
import com.wtracker.field.services.shift.ShiftService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RouteWelcomeUiState(
    val userName: String = "",
    val isLoggingOut: Boolean = false,
    val recentCompletedSummary: ShiftSummary? = null,
)

/** Pantalla de entrada cuando el trabajador aún no inició su ruta del día. */
class RouteWelcomeViewModel(
    private val authService: AuthService,
    sessionStore: SessionStore,
    shiftService: ShiftService,
    private val appPreferencesStore: AppPreferencesStore,
) : ViewModel() {

    private val isLoggingOut = MutableStateFlow(false)

    val uiState: StateFlow<RouteWelcomeUiState> = combine(
        sessionStore.sessionFlow,
        isLoggingOut,
        shiftService.observeTodaySummary(),
        appPreferencesStore.lastSeenSummaryIdFlow
    ) { session, loggingOut, summary, lastSeenId ->
        val showSummary = summary != null && !summary.isActive && summary.shiftId != lastSeenId
        RouteWelcomeUiState(
            userName = session?.fullName.orEmpty(),
            isLoggingOut = loggingOut,
            recentCompletedSummary = if (showSummary) summary else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(UiConfig.STATE_SUBSCRIPTION_TIMEOUT_MS), RouteWelcomeUiState())

    fun onDismissSummary(summaryId: String) {
        viewModelScope.launch {
            appPreferencesStore.setLastSeenSummaryId(summaryId)
        }
    }

    fun onLogout() {
        if (isLoggingOut.value) return
        isLoggingOut.value = true
        viewModelScope.launch {
            try {
                authService.logout()
            } finally {
                isLoggingOut.value = false
            }
        }
    }
}
