package com.wtracker.field.controllers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wtracker.field.configs.AppPreferencesStore
import com.wtracker.field.configs.UiConfig
import com.wtracker.field.services.models.BusinessSettings
import com.wtracker.field.services.models.ShiftSummary
import com.wtracker.field.services.shift.ShiftService
import com.wtracker.field.utils.TimeProvider
import com.wtracker.field.utils.tickerFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ShiftSummaryUiState(
    val isLoading: Boolean = true,
    val summary: ShiftSummary? = null,
    val nowMillis: Long = 0L,
    val businessSettings: BusinessSettings = BusinessSettings(),
)

class ShiftSummaryViewModel(
    shiftService: ShiftService,
    timeProvider: TimeProvider,
    appPreferencesStore: AppPreferencesStore,
) : ViewModel() {

    val uiState: StateFlow<ShiftSummaryUiState> = combine(
        shiftService.observeTodaySummary(),
        tickerFlow(UiConfig.CLOCK_TICK_MS, timeProvider),
        appPreferencesStore.businessSettingsFlow,
    ) { summary, nowMillis, settings -> ShiftSummaryUiState(isLoading = false, summary = summary, nowMillis = nowMillis, businessSettings = settings) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(UiConfig.STATE_SUBSCRIPTION_TIMEOUT_MS), ShiftSummaryUiState())
}
