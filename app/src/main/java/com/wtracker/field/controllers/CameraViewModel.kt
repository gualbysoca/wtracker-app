package com.wtracker.field.controllers

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wtracker.field.configs.AppPreferencesStore
import com.wtracker.field.configs.LocationConfig
import com.wtracker.field.configs.UiConfig
import com.wtracker.field.services.camera.EvidenceDraftStore
import com.wtracker.field.services.camera.EvidenceFileStore
import com.wtracker.field.services.camera.EvidenceStamper
import com.wtracker.field.services.camera.InstantCameraController
import com.wtracker.field.services.clients.ClientService
import com.wtracker.field.services.location.LocationProvider
import com.wtracker.field.services.models.CapturedEvidence
import com.wtracker.field.services.models.CapturedLocation
import com.wtracker.field.utils.AppResult
import com.wtracker.field.utils.GeoUtils
import com.wtracker.field.utils.TimeProvider
import com.wtracker.field.validations.CapturedLocationValidator
import com.wtracker.field.validations.ValidationResult
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface CameraPhase {
    data object Preview : CameraPhase
    data object Capturing : CameraPhase
    data class Review(val evidence: CapturedEvidence, val rawFilePath: String) : CameraPhase
    data class Failure(val message: String) : CameraPhase
}

data class CameraUiState(
    val clientName: String = "",
    val phase: CameraPhase = CameraPhase.Preview,
    val liveLocation: CapturedLocation? = null,
)

sealed interface CameraEvent {
    data object EvidenceConfirmed : CameraEvent
}

/**
 * Orquesta la captura instantánea: el GPS se precalienta durante la vista previa y se lee
 * EN PARALELO al disparo, para que la coordenada sellada corresponda al instante de la foto.
 * La foto sólo se adjunta a la visita cuando el usuario la confirma (sin edición posible).
 */
class CameraViewModel(
    private val clientId: String,
    private val cameraController: InstantCameraController,
    private val evidenceStamper: EvidenceStamper,
    private val evidenceDraftStore: EvidenceDraftStore,
    private val evidenceFileStore: EvidenceFileStore,
    private val locationProvider: LocationProvider,
    private val timeProvider: TimeProvider,
    private val clientService: ClientService,
    private val appPreferencesStore: AppPreferencesStore,
) : ViewModel() {

    private val phase = MutableStateFlow<CameraPhase>(CameraPhase.Preview)
    private val liveLocation = MutableStateFlow<CapturedLocation?>(null)
    private val eventChannel = Channel<CameraEvent>(Channel.BUFFERED)
    private var isEvidenceConfirmed = false

    val events: Flow<CameraEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<CameraUiState> = combine(
        clientService.observeFieldClient(clientId).map { it?.businessName.orEmpty() },
        phase,
        liveLocation,
    ) { clientName, currentPhase, location -> CameraUiState(clientName, currentPhase, location) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(UiConfig.STATE_SUBSCRIPTION_TIMEOUT_MS), CameraUiState())

    init {
        warmUpGps()
    }

    fun onPreviewSurfaceReady(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        viewModelScope.launch {
            val bindResult = cameraController.bindToLifecycle(lifecycleOwner, surfaceProvider)
            if (bindResult is AppResult.Failure) phase.value = CameraPhase.Failure(bindResult.error.message)
        }
    }

    fun onShutterPressed() {
        if (phase.value != CameraPhase.Preview) return
        phase.value = CameraPhase.Capturing

        viewModelScope.launch {
            val shutterAtMillis = timeProvider.nowMillis()
            val locationDeferred = async { resolveCaptureLocation() }
            val captureResult = cameraController.takePicture(evidenceFileStore.newRawCaptureFile())
            val locationResult = locationDeferred.await()
            phase.value = buildEvidence(captureResult, locationResult, shutterAtMillis)
        }
    }

    fun onRetake() {
        discardUnconfirmedEvidence()
        phase.value = CameraPhase.Preview
    }

    fun onConfirmEvidence() {
        val reviewPhase = phase.value as? CameraPhase.Review ?: return
        isEvidenceConfirmed = true
        evidenceDraftStore.putDraft(clientId, reviewPhase.evidence)
        evidenceFileStore.delete(reviewPhase.rawFilePath)
        viewModelScope.launch { eventChannel.send(CameraEvent.EvidenceConfirmed) }
    }

    private suspend fun buildEvidence(
        captureResult: AppResult<File>,
        locationResult: AppResult<CapturedLocation>,
        shutterAtMillis: Long,
    ): CameraPhase {
        if (captureResult is AppResult.Failure) return CameraPhase.Failure(captureResult.error.message)
        val rawFile = (captureResult as AppResult.Success).data

        if (locationResult is AppResult.Failure) {
            evidenceFileStore.delete(rawFile)
            return CameraPhase.Failure(locationResult.error.message)
        }
        val location = (locationResult as AppResult.Success).data

        val locationValidation = CapturedLocationValidator.validate(location)
        if (locationValidation is ValidationResult.Invalid) {
            evidenceFileStore.delete(rawFile)
            return CameraPhase.Failure(locationValidation.message)
        }

        val client = clientService.observeFieldClient(clientId).firstOrNull()
        val isOutOfRange = if (client?.coordinate != null) {
            val radius = appPreferencesStore.businessSettingsFlow.first().geofenceRadiusMeters
            GeoUtils.distanceMeters(location.coordinate, client.coordinate) > radius
        } else false

        return when (val stampResult = evidenceStamper.stamp(rawFile, location, shutterAtMillis, uiState.value.clientName, isOutOfRange)) {
            is AppResult.Success -> CameraPhase.Review(stampResult.data, rawFile.absolutePath)
            is AppResult.Failure -> {
                evidenceFileStore.delete(rawFile)
                CameraPhase.Failure(stampResult.error.message)
            }
        }
    }

    /** Reutiliza la lectura en vivo si es reciente y precisa; si no, solicita una nueva. */
    private suspend fun resolveCaptureLocation(): AppResult<CapturedLocation> {
        val cachedLocation = liveLocation.value ?: return locationProvider.getFreshLocation()
        val isFresh = timeProvider.nowMillis() - cachedLocation.capturedAtMillis <= LocationConfig.FRESH_FIX_MAX_AGE_MS
        val isAccurate = cachedLocation.accuracyMeters <= LocationConfig.MAX_ACCEPTABLE_ACCURACY_METERS
        if (isFresh && isAccurate) return AppResult.Success(cachedLocation)
        return locationProvider.getFreshLocation()
    }

    private fun warmUpGps() {
        viewModelScope.launch {
            locationProvider.observeLocationUpdates(LocationConfig.CAMERA_WARMUP_INTERVAL_MS)
                .catch { error -> phase.value = CameraPhase.Failure(error.message ?: GPS_UNAVAILABLE_MESSAGE) }
                .collect { location -> liveLocation.value = location }
        }
    }

    private fun discardUnconfirmedEvidence() {
        val reviewPhase = phase.value as? CameraPhase.Review ?: return
        evidenceFileStore.delete(reviewPhase.rawFilePath)
        if (!isEvidenceConfirmed) evidenceFileStore.delete(reviewPhase.evidence.filePath)
    }

    override fun onCleared() {
        discardUnconfirmedEvidence()
        cameraController.release()
        super.onCleared()
    }

    private companion object {
        const val GPS_UNAVAILABLE_MESSAGE = "No se pudo acceder al GPS."
    }
}
