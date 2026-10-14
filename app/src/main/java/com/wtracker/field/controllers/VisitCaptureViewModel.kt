package com.wtracker.field.controllers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wtracker.field.configs.UiConfig
import com.wtracker.field.services.camera.EvidenceDraftStore
import com.wtracker.field.configs.AppPreferencesStore
import com.wtracker.field.services.models.CapturedEvidence
import com.wtracker.field.services.models.CapturedLocation
import com.wtracker.field.services.models.FieldClient
import com.wtracker.field.services.models.VisitMarkRequest
import com.wtracker.field.services.models.VisitStatus
import com.wtracker.field.services.visits.VisitService
import com.wtracker.field.services.clients.ClientService
import com.wtracker.field.services.location.LocationProvider
import com.wtracker.field.services.models.BusinessSettings
import com.wtracker.field.utils.AppResult
import com.wtracker.field.utils.GeoUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VisitCaptureUiState(
    val isLoading: Boolean = true,
    val client: FieldClient? = null,
    val selectedStatus: VisitStatus? = null,
    val evidence: CapturedEvidence? = null,
    val notes: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val showOutOfRangeWarning: Boolean = false,
    val hasOtherEnRoute: Boolean = false,
    val businessSettings: BusinessSettings = BusinessSettings(),
) {
    val requiresEvidence: Boolean get() = selectedStatus?.requiresEvidence == true
    val hasChanges: Boolean
        get() = selectedStatus != client?.currentStatus?.takeIf { it in VisitStatus.markableStatuses } ||
                evidence != null ||
                notes.isNotEmpty()
    val canSubmit: Boolean
        get() = hasChanges && selectedStatus != null && (!requiresEvidence || evidence != null) && !isSubmitting &&
                !(selectedStatus == VisitStatus.EN_ROUTE && hasOtherEnRoute)
    val validationError: String?
        get() = if (selectedStatus == VisitStatus.EN_ROUTE && hasOtherEnRoute) {
            "Ya tienes una tarea en curso. Finalízala antes de iniciar otra."
        } else {
            errorMessage
        }
    val evidenceIsOutOfRange: Boolean
        get() {
            if (evidence == null || client?.coordinate == null) return false
            val distance = GeoUtils.distanceMeters(evidence.location.coordinate, client.coordinate)
            return distance > businessSettings.geofenceRadiusMeters
        }
}

sealed interface VisitCaptureEvent {
    data object Completed : VisitCaptureEvent
}

private data class VisitFormState(
    val selectedStatus: VisitStatus? = null,
    val notes: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val isSubmitted: Boolean = false,
    val showOutOfRangeWarning: Boolean = false,
)

class VisitCaptureViewModel(
    private val clientId: String,
    private val visitService: VisitService,
    private val locationProvider: LocationProvider,
    private val evidenceDraftStore: EvidenceDraftStore,
    clientService: ClientService,
    private val appPreferencesStore: AppPreferencesStore,
) : ViewModel() {

    private val formState = MutableStateFlow(VisitFormState())
    private val eventChannel = Channel<VisitCaptureEvent>(Channel.BUFFERED)
    val events: Flow<VisitCaptureEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<VisitCaptureUiState> = combine(
        clientService.observeFieldClient(clientId),
        evidenceDraftStore.observeDraft(clientId),
        formState,
        clientService.observeFieldClients(),
        appPreferencesStore.businessSettingsFlow,
    ) { client, evidence, form, allClients, settings ->
        val hasOtherEnRoute = allClients.any { it.currentStatus == VisitStatus.EN_ROUTE && it.id != clientId }
        VisitCaptureUiState(
            isLoading = false,
            client = client,
            selectedStatus = form.selectedStatus ?: client?.currentStatus?.takeIf { it in VisitStatus.markableStatuses },
            evidence = evidence,
            notes = form.notes,
            isSubmitting = form.isSubmitting,
            errorMessage = form.errorMessage,
            showOutOfRangeWarning = form.showOutOfRangeWarning,
            hasOtherEnRoute = hasOtherEnRoute,
            businessSettings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(UiConfig.STATE_SUBSCRIPTION_TIMEOUT_MS), VisitCaptureUiState())

    fun onStatusSelected(status: VisitStatus) {
        formState.update { it.copy(selectedStatus = status, errorMessage = null) }
    }

    fun onNotesChanged(notes: String) {
        formState.update { it.copy(notes = notes) }
    }

    fun onDiscardEvidence() {
        evidenceDraftStore.discardDraft(clientId)
    }

    fun onSubmit() {
        val state = uiState.value
        val status = state.selectedStatus
        if (!state.canSubmit || status == null) return

        if (state.evidenceIsOutOfRange) {
            formState.update { it.copy(showOutOfRangeWarning = true) }
            return
        }
        executeSubmit(state, status)
    }

    fun onConfirmSubmit() {
        formState.update { it.copy(showOutOfRangeWarning = false) }
        val state = uiState.value
        val status = state.selectedStatus
        if (!state.canSubmit || status == null) return
        executeSubmit(state, status)
    }

    fun onDismissWarning() {
        formState.update { it.copy(showOutOfRangeWarning = false) }
    }

    private fun executeSubmit(state: VisitCaptureUiState, status: VisitStatus) {
        formState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            val locationResult = resolveVisitLocation(state.evidence)
            if (locationResult is AppResult.Failure) {
                formState.update { it.copy(isSubmitting = false, errorMessage = locationResult.error.message) }
                return@launch
            }

            val request = VisitMarkRequest(
                clientId = clientId,
                status = status,
                location = (locationResult as AppResult.Success).data,
                evidence = state.evidence.takeIf { status.requiresEvidence },
                notes = state.notes,
            )
            when (val result = visitService.registerVisitMark(request)) {
                is AppResult.Success -> {
                    // La foto pasa a ser propiedad de la cola de sincronización.
                    evidenceDraftStore.releaseDraft(clientId)
                    formState.update { it.copy(isSubmitting = false, isSubmitted = true) }
                    eventChannel.send(VisitCaptureEvent.Completed)
                }
                is AppResult.Failure -> formState.update {
                    it.copy(isSubmitting = false, errorMessage = result.error.message)
                }
            }
        }
    }

    /** Con foto: se usan las coordenadas selladas en ella (coherencia foto ↔ visita). Sin foto: lectura nueva. */
    private suspend fun resolveVisitLocation(evidence: CapturedEvidence?): AppResult<CapturedLocation> {
        if (evidence != null) return AppResult.Success(evidence.location)
        return locationProvider.getFreshLocation()
    }

    override fun onCleared() {
        // Si el usuario abandona sin registrar, la foto no debe quedar huérfana en el equipo.
        if (!formState.value.isSubmitted) evidenceDraftStore.discardDraft(clientId)
        super.onCleared()
    }
}
