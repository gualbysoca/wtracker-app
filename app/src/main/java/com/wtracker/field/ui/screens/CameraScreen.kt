package com.wtracker.field.ui.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wtracker.field.R
import com.wtracker.field.configs.AppContainer
import com.wtracker.field.controllers.CameraEvent
import com.wtracker.field.controllers.CameraPhase
import com.wtracker.field.controllers.CameraUiState
import com.wtracker.field.controllers.CameraViewModel
import com.wtracker.field.controllers.ControllerFactories
import com.wtracker.field.services.models.CapturedEvidence
import com.wtracker.field.ui.components.CameraViewport
import com.wtracker.field.ui.components.EmptyState
import com.wtracker.field.ui.components.EvidenceMetadataLines
import com.wtracker.field.ui.components.GpsAccuracyPill
import com.wtracker.field.ui.components.LocalEvidenceImage
import com.wtracker.field.ui.components.PermissionGate
import com.wtracker.field.ui.components.ShutterButton
import com.wtracker.field.ui.components.WButton
import com.wtracker.field.ui.components.WButtonVariant
import com.wtracker.field.ui.theme.WTheme
import java.time.ZoneId

data class CameraActions(
    val onBack: () -> Unit,
    val onSurfaceReady: (LifecycleOwner, Preview.SurfaceProvider) -> Unit,
    val onShutterPressed: () -> Unit,
    val onRetake: () -> Unit,
    val onConfirm: () -> Unit,
)

private val cameraPermissions: List<String> = listOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)

@Composable
fun CameraRoute(container: AppContainer, clientId: String, onClose: () -> Unit) {
    PermissionGate(
        requestedPermissions = cameraPermissions,
        requiredPermissions = cameraPermissions,
        rationaleTitle = stringResource(R.string.camera_permission_title),
        rationaleMessage = stringResource(R.string.camera_permission_message),
        icon = Icons.Outlined.PhotoCamera,
    ) {
        // El ViewModel se crea sólo con permisos concedidos: el precalentamiento del GPS los necesita.
        val viewModel: CameraViewModel = viewModel(factory = ControllerFactories.camera(container, clientId))
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    CameraEvent.EvidenceConfirmed -> onClose()
                }
            }
        }

        CameraScreen(
            uiState = uiState,
            actions = CameraActions(
                onBack = onClose,
                onSurfaceReady = viewModel::onPreviewSurfaceReady,
                onShutterPressed = viewModel::onShutterPressed,
                onRetake = viewModel::onRetake,
                onConfirm = viewModel::onConfirmEvidence,
            ),
        )
    }
}

/** Cámara instantánea a pantalla completa: sin galería, sin edición, sin filtros. */
@Composable
fun CameraScreen(uiState: CameraUiState, actions: CameraActions) {
    val phase = uiState.phase
    Box(modifier = Modifier.fillMaxSize().background(WTheme.colors.cameraBackground)) {
        // Fuera de Failure el visor permanece montado; al reintentar se recrea y vuelve a enlazar CameraX.
        if (phase !is CameraPhase.Failure) {
            CameraViewport(onSurfaceReady = actions.onSurfaceReady, modifier = Modifier.fillMaxSize())
        }
        when (phase) {
            CameraPhase.Preview, CameraPhase.Capturing -> CaptureOverlay(uiState = uiState, actions = actions)
            is CameraPhase.Review -> ReviewOverlay(evidence = phase.evidence, rawFilePath = phase.rawFilePath, actions = actions)
            is CameraPhase.Failure -> FailureOverlay(message = phase.message, actions = actions)
        }
    }
}

@Composable
private fun CaptureOverlay(uiState: CameraUiState, actions: CameraActions) {
    val isCapturing = uiState.phase == CameraPhase.Capturing
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        CameraTopBar(title = uiState.clientName, onBack = actions.onBack) { GpsAccuracyPill(location = uiState.liveLocation) }
        Box(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(WTheme.colors.cameraOverlay)
                .padding(WTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WTheme.spacing.md),
        ) {
            CameraCaption(text = stringResource(if (isCapturing) R.string.camera_processing else R.string.camera_hint))
            ShutterButton(onClick = actions.onShutterPressed, isCapturing = isCapturing)
        }
    }
}

@Composable
private fun ReviewOverlay(evidence: CapturedEvidence, rawFilePath: String, actions: CameraActions) {
    val zone = remember { ZoneId.systemDefault() }
    val spacing = WTheme.spacing
    Column(modifier = Modifier.fillMaxSize().background(WTheme.colors.cameraBackground).safeDrawingPadding()) {
        CameraTopBar(title = stringResource(R.string.camera_review_title), onBack = actions.onRetake)
        LocalEvidenceImage(
            filePath = rawFilePath,
            contentDescription = stringResource(R.string.evidence_image_description),
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Column(
            modifier = Modifier.fillMaxWidth().background(WTheme.colors.cameraOverlay).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            EvidenceMetadataLines(evidence = evidence, zone = zone)
            CameraCaption(text = stringResource(R.string.camera_review_hint))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                WButton(
                    text = stringResource(R.string.evidence_retake),
                    onClick = actions.onRetake,
                    variant = WButtonVariant.SECONDARY,
                    leadingIcon = Icons.Outlined.Replay,
                    modifier = Modifier.weight(1f),
                )
                WButton(
                    text = stringResource(R.string.camera_confirm),
                    onClick = actions.onConfirm,
                    leadingIcon = Icons.Outlined.CheckCircle,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FailureOverlay(message: String, actions: CameraActions) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        CameraTopBar(title = stringResource(R.string.camera_title), onBack = actions.onBack)
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            EmptyState(
                title = stringResource(R.string.camera_failure_title),
                message = message,
                icon = Icons.Outlined.PhotoCamera,
                action = { WButton(text = stringResource(R.string.action_retry), onClick = actions.onRetake, leadingIcon = Icons.Outlined.Replay) },
            )
        }
    }
}

@Composable
private fun CameraTopBar(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WTheme.colors.cameraOverlay)
            .padding(horizontal = WTheme.spacing.sm, vertical = WTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = WTheme.colors.cameraContent,
            )
        }
        Text(
            text = title,
            style = WTheme.typography.subtitle,
            color = WTheme.colors.cameraContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun CameraCaption(text: String) {
    Text(
        text = text,
        style = WTheme.typography.caption,
        color = WTheme.colors.cameraContent,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
