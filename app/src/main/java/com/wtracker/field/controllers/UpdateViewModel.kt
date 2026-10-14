package com.wtracker.field.controllers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wtracker.field.services.models.AppRelease
import com.wtracker.field.services.update.ApkInstaller
import com.wtracker.field.services.update.UpdateService
import com.wtracker.field.utils.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(
    val availableRelease: AppRelease? = null,
    val isDownloading: Boolean = false,
    val downloadProgressPercent: Int = 0,
    val errorMessage: String? = null,
) {
    val isUpdateAvailable: Boolean get() = availableRelease != null
}

class UpdateViewModel(
    private val updateService: UpdateService,
    private val apkInstaller: ApkInstaller,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = mutableUiState.asStateFlow()

    init {
        checkForUpdate()
    }

    /**
     * Un fallo al consultar (por ejemplo sin señal) no se muestra: el banner simplemente no aparece
     * y se vuelve a consultar al abrir la pantalla. Los fallos de descarga e instalación sí se informan.
     */
    fun checkForUpdate() {
        viewModelScope.launch {
            val result = updateService.findAvailableUpdate()
            if (result is AppResult.Success) mutableUiState.update { it.copy(availableRelease = result.data) }
        }
    }

    fun onUpdateClicked() {
        val state = mutableUiState.value
        val release = state.availableRelease ?: return
        if (state.isDownloading) return

        if (!apkInstaller.canInstallPackages()) {
            requestInstallPermission()
            return
        }
        downloadAndInstall(release)
    }

    private fun requestInstallPermission() {
        val openResult = apkInstaller.openInstallPermissionSettings()
        val message = (openResult as? AppResult.Failure)?.error?.message ?: INSTALL_PERMISSION_MESSAGE
        mutableUiState.update { it.copy(errorMessage = message) }
    }

    private fun downloadAndInstall(release: AppRelease) {
        mutableUiState.update { it.copy(isDownloading = true, downloadProgressPercent = 0, errorMessage = null) }

        viewModelScope.launch {
            val downloadResult = updateService.downloadRelease(release) { percent ->
                mutableUiState.update { it.copy(downloadProgressPercent = percent) }
            }
            val failureMessage = when (downloadResult) {
                is AppResult.Failure -> downloadResult.error.message
                is AppResult.Success -> (apkInstaller.install(downloadResult.data) as? AppResult.Failure)?.error?.message
            }
            mutableUiState.update { it.copy(isDownloading = false, errorMessage = failureMessage) }
        }
    }

    private companion object {
        const val INSTALL_PERMISSION_MESSAGE =
            "Activa «Permitir de esta fuente» para WTracker y vuelve a pulsar Actualizar."
    }
}
