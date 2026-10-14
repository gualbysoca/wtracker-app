package com.wtracker.field.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wtracker.field.R
import com.wtracker.field.configs.AppContainer
import com.wtracker.field.configs.VisitConfig
import com.wtracker.field.controllers.ControllerFactories
import com.wtracker.field.controllers.VisitCaptureEvent
import com.wtracker.field.controllers.VisitCaptureUiState
import com.wtracker.field.controllers.VisitCaptureViewModel
import com.wtracker.field.services.models.CapturedEvidence
import com.wtracker.field.services.models.VisitStatus
import com.wtracker.field.ui.components.ClientInfoCard
import com.wtracker.field.ui.components.EmptyState
import com.wtracker.field.ui.components.EvidencePreviewCard
import com.wtracker.field.ui.components.InlineMessage
import com.wtracker.field.ui.components.LoadingState
import com.wtracker.field.ui.components.MessageTone
import com.wtracker.field.ui.components.SectionHeader
import com.wtracker.field.ui.components.StatusSelector
import com.wtracker.field.ui.components.WButton
import com.wtracker.field.ui.components.WButtonVariant
import com.wtracker.field.ui.components.WTextField
import com.wtracker.field.ui.components.WTopBar
import com.wtracker.field.ui.theme.WTheme
import java.time.ZoneId

data class VisitCaptureActions(
    val onBack: () -> Unit,
    val onStatusSelected: (VisitStatus) -> Unit,
    val onNotesChanged: (String) -> Unit,
    val onOpenCamera: () -> Unit,
    val onDiscardEvidence: () -> Unit,
    val onSubmit: () -> Unit,
    val onConfirmSubmit: () -> Unit,
    val onDismissWarning: () -> Unit,
)

@Composable
fun VisitCaptureRoute(
    container: AppContainer,
    clientId: String,
    onOpenCamera: () -> Unit,
    onFinished: () -> Unit,
) {
    val viewModel: VisitCaptureViewModel = viewModel(factory = ControllerFactories.visitCapture(container, clientId))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                VisitCaptureEvent.Completed -> onFinished()
            }
        }
    }

    VisitCaptureScreen(
        uiState = uiState,
        actions = VisitCaptureActions(
            onBack = onFinished,
            onStatusSelected = viewModel::onStatusSelected,
            onNotesChanged = viewModel::onNotesChanged,
            onOpenCamera = onOpenCamera,
            onDiscardEvidence = viewModel::onDiscardEvidence,
            onSubmit = viewModel::onSubmit,
            onConfirmSubmit = viewModel::onConfirmSubmit,
            onDismissWarning = viewModel::onDismissWarning,
        ),
    )
}

@Composable
fun VisitCaptureScreen(uiState: VisitCaptureUiState, actions: VisitCaptureActions) {
    Scaffold(
        containerColor = WTheme.colors.background,
        topBar = {
            WTopBar(
                title = stringResource(R.string.visit_title),
                subtitle = uiState.client?.businessName,
                onBack = actions.onBack,
            )
        },
        bottomBar = { VisitSubmitBar(uiState = uiState, onSubmit = actions.onSubmit) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            VisitCaptureBody(uiState = uiState, actions = actions)
        }
    }

    if (uiState.showOutOfRangeWarning) {
        com.wtracker.field.ui.components.WDialog(
            title = stringResource(R.string.visit_section_evidence),
            icon = ImageVector.vectorResource(R.drawable.ic_camera_warning),
            tone = WTheme.colors.warning,
            confirmText = stringResource(R.string.visit_submit),
            confirmVariant = com.wtracker.field.ui.components.WButtonVariant.PRIMARY,
            onConfirm = actions.onConfirmSubmit,
            onDismiss = actions.onDismissWarning,
        ) {
            androidx.compose.material3.Text(
                text = stringResource(R.string.visit_evidence_out_of_range),
                style = WTheme.typography.body,
                color = WTheme.colors.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun VisitCaptureBody(uiState: VisitCaptureUiState, actions: VisitCaptureActions) {
    if (uiState.isLoading) {
        LoadingState(message = stringResource(R.string.visit_loading))
        return
    }
    val client = uiState.client
    if (client == null) {
        EmptyState(
            title = stringResource(R.string.visit_client_missing_title),
            message = stringResource(R.string.visit_client_missing_message),
        )
        return
    }

    val spacing = WTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        ClientInfoCard(client = client)

        SectionHeader(title = stringResource(R.string.visit_section_status))
        StatusSelector(
            selectedStatus = uiState.selectedStatus,
            onStatusSelected = actions.onStatusSelected,
            enabled = !uiState.isSubmitting,
        )

        AnimatedVisibility(
            visible = uiState.validationError != null || (uiState.selectedStatus != null && !uiState.selectedStatus.requiresEvidence),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (uiState.selectedStatus != null && !uiState.selectedStatus.requiresEvidence) {
                    InlineMessage(message = stringResource(R.string.visit_evidence_optional_hint), tone = MessageTone.INFO)
                }
                uiState.validationError?.let { InlineMessage(message = it, tone = MessageTone.ERROR) }
            }
        }

        EvidenceSection(uiState = uiState, actions = actions)

        SectionHeader(title = stringResource(R.string.visit_section_notes))
        WTextField(
            value = uiState.notes,
            onValueChange = actions.onNotesChanged,
            label = stringResource(R.string.visit_notes_label),
            singleLine = false,
            minLines = NOTES_MIN_LINES,
            maxLength = VisitConfig.NOTES_MAX_LENGTH,
            supportingText = stringResource(R.string.visit_notes_counter, uiState.notes.length, VisitConfig.NOTES_MAX_LENGTH),
            enabled = !uiState.isSubmitting,
        )

    }
}

/** La foto sólo se solicita (y se adjunta) para estados finales: visitado / fallido. */
@Composable
private fun EvidenceSection(uiState: VisitCaptureUiState, actions: VisitCaptureActions) {
    val selectedStatus = uiState.selectedStatus ?: return
    if (!selectedStatus.requiresEvidence) return

    Column(verticalArrangement = Arrangement.spacedBy(WTheme.spacing.md)) {
        SectionHeader(title = stringResource(R.string.visit_section_evidence))
        EvidenceSlot(
            uiState = uiState,
            isEnabled = !uiState.isSubmitting,
            onOpenCamera = actions.onOpenCamera,
            onDiscardEvidence = actions.onDiscardEvidence,
        )
    }
}

@Composable
private fun EvidenceSlot(
    uiState: VisitCaptureUiState,
    isEnabled: Boolean,
    onOpenCamera: () -> Unit,
    onDiscardEvidence: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val evidence = uiState.evidence
    if (evidence != null) {
        EvidencePreviewCard(evidence = evidence, zone = zone, onRetake = onOpenCamera, onRemove = onDiscardEvidence)
        if (uiState.evidenceIsOutOfRange) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(WTheme.spacing.sm))
            InlineMessage(message = stringResource(R.string.visit_evidence_out_of_range), tone = MessageTone.WARNING)
        }
        return
    }
    InlineMessage(message = stringResource(R.string.visit_evidence_required_hint), tone = MessageTone.WARNING)
    WButton(
        text = stringResource(R.string.visit_take_photo),
        onClick = onOpenCamera,
        leadingIcon = Icons.Outlined.PhotoCamera,
        variant = WButtonVariant.SECONDARY,
        enabled = isEnabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun VisitSubmitBar(uiState: VisitCaptureUiState, onSubmit: () -> Unit) {
    if (uiState.client == null) return
    val spacing = WTheme.spacing
    Surface(color = WTheme.colors.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            AnimatedVisibility(visible = uiState.selectedStatus == null) {
                InlineMessage(message = stringResource(R.string.visit_select_status_hint), tone = MessageTone.INFO)
            }
            WButton(
                text = stringResource(R.string.visit_submit),
                onClick = onSubmit,
                leadingIcon = Icons.Outlined.CheckCircle,
                isLoading = uiState.isSubmitting,
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val NOTES_MIN_LINES = 3
