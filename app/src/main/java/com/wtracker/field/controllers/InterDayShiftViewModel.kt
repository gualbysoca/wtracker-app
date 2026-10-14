package com.wtracker.field.controllers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wtracker.field.services.route.RoutePlanService
import com.wtracker.field.utils.TimeProvider
import com.wtracker.field.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class InterDayShiftViewModel(
    private val routePlanService: RoutePlanService,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _showModal = MutableStateFlow(false)
    val showModal: StateFlow<Boolean> = _showModal.asStateFlow()

    fun checkShiftStatus() {
        viewModelScope.launch {
            if (routePlanService.hasActiveRoute()) {
                val shift = routePlanService.observeActiveRoute().first()
                if (shift != null) {
                    val now = timeProvider.nowMillis()
                    val startOfDay = TimeUtils.startOfDayMillis(now, timeProvider.zone())
                    val shiftStartedAt = shift.startedAtMillis
                    val isDifferentDay = shiftStartedAt < startOfDay
                    val isMoreThan12Hours = now - shiftStartedAt > 12 * 60 * 60 * 1000L

                    if (isDifferentDay || isMoreThan12Hours) {
                        _showModal.value = true
                    }
                }
            }
        }
    }

    fun finishShift() {
        viewModelScope.launch {
            routePlanService.finishRoute()
            _showModal.value = false
        }
    }

    fun dismissModal() {
        _showModal.value = false
    }
}
