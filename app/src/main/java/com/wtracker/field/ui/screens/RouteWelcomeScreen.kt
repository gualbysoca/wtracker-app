package com.wtracker.field.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.AddBusiness
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wtracker.field.R
import com.wtracker.field.configs.AppContainer
import com.wtracker.field.configs.RoutePermissions
import com.wtracker.field.controllers.ControllerFactories
import com.wtracker.field.controllers.RouteWelcomeUiState
import com.wtracker.field.controllers.RouteWelcomeViewModel
import com.wtracker.field.controllers.UpdateUiState
import com.wtracker.field.controllers.UpdateViewModel
import com.wtracker.field.ui.components.UpdateBanner
import com.wtracker.field.services.models.ShiftSummary
import com.wtracker.field.ui.components.BrandMark
import com.wtracker.field.ui.components.GradientBackground
import com.wtracker.field.ui.components.PermissionGate
import com.wtracker.field.ui.components.WButton
import com.wtracker.field.ui.components.WButtonVariant
import com.wtracker.field.ui.theme.WTheme
import com.wtracker.field.utils.GeoUtils
import com.wtracker.field.utils.ScreenshotUtils
import com.wtracker.field.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Primera pantalla tras el login cuando no hay ruta activa. Se exige el permiso de ubicación aquí
 * porque iniciar la ruta arranca el servicio GPS en primer plano.
 */
@Composable
fun RouteWelcomeRoute(container: AppContainer, onAddClients: () -> Unit) {
    val viewModel: RouteWelcomeViewModel = viewModel(factory = ControllerFactories.routeWelcome(container))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateViewModel: UpdateViewModel = viewModel(factory = ControllerFactories.update(container))
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()

    PermissionGate(
        requestedPermissions = RoutePermissions.requested,
        requiredPermissions = RoutePermissions.required,
        rationaleTitle = stringResource(R.string.route_permission_title),
        rationaleMessage = stringResource(R.string.route_permission_message),
        icon = Icons.Outlined.LocationOn,
    ) {
        RouteWelcomeScreen(
            uiState = uiState,
            updateState = updateState,
            onUpdateClicked = updateViewModel::onUpdateClicked,
            onAddClients = onAddClients,
            onLogout = viewModel::onLogout,
            onDismissSummary = viewModel::onDismissSummary
        )
    }
}

@Composable
fun RouteWelcomeScreen(
    uiState: RouteWelcomeUiState,
    updateState: UpdateUiState,
    onUpdateClicked: () -> Unit,
    onAddClients: () -> Unit,
    onLogout: () -> Unit,
    onDismissSummary: (String) -> Unit
) {
    val spacing = WTheme.spacing

    GradientBackground {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .widthIn(max = WTheme.sizes.maxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = spacing.xl, vertical = spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            BrandMark()
            WelcomeHeader(userName = uiState.userName)
            if (updateState.isUpdateAvailable) {
                UpdateBanner(state = updateState, onUpdateClick = onUpdateClicked)
            }
            Spacer(Modifier.height(spacing.sm))
            WButton(
                text = stringResource(R.string.welcome_add_clients),
                onClick = onAddClients,
                leadingIcon = Icons.Outlined.AddBusiness,
                enabled = !uiState.isLoggingOut,
                modifier = Modifier.fillMaxWidth(),
            )
            WButton(
                text = stringResource(R.string.action_logout),
                onClick = onLogout,
                variant = WButtonVariant.GHOST,
                leadingIcon = Icons.AutoMirrored.Outlined.Logout,
                isLoading = uiState.isLoggingOut,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    uiState.recentCompletedSummary?.let { summary ->
        RouteSummaryModal(
            summary = summary,
            onDismiss = { onDismissSummary(summary.shiftId) }
        )
    }
}

@Composable
private fun WelcomeHeader(userName: String) {
    val title = if (userName.isBlank()) {
        stringResource(R.string.welcome_title_anonymous)
    } else {
        stringResource(R.string.welcome_title, userName)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(WTheme.spacing.sm)) {
        Text(
            text = title,
            style = WTheme.typography.display,
            color = WTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.welcome_message),
            style = WTheme.typography.body,
            color = WTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RouteSummaryModal(
    summary: ShiftSummary,
    onDismiss: () -> Unit,
) {
    var isCapturing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        val context = LocalContext.current
        val dialogView = LocalView.current
        Surface(
            modifier = Modifier.widthIn(max = WTheme.sizes.maxContentWidth).fillMaxWidth(),
            shape = RoundedCornerShape(WTheme.radius.xl),
            color = WTheme.colors.surface,
            border = androidx.compose.foundation.BorderStroke(WTheme.sizes.borderThin, WTheme.colors.border),
        ) {
            Column(
                modifier = Modifier.padding(WTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(WTheme.spacing.lg),
            ) {
                Surface(
                    shape = CircleShape,
                    color = WTheme.colors.surfaceElevated,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.Flag, contentDescription = null, tint = WTheme.colors.accent, modifier = Modifier.size(32.dp))
                    }
                }

                Text(
                    text = stringResource(R.string.summary_title),
                    style = WTheme.typography.title,
                    color = WTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                val zone = remember { ZoneId.systemDefault() }
                val nowMillis = System.currentTimeMillis()
                val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(zone)
                val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
                val startInstant = java.time.Instant.ofEpochMilli(summary.startedAtMillis)
                val endInstant = summary.endedAtMillis?.let { java.time.Instant.ofEpochMilli(it) } ?: java.time.Instant.ofEpochMilli(nowMillis)

                Surface(
                    shape = RoundedCornerShape(WTheme.radius.lg),
                    color = WTheme.colors.surfaceElevated,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                ) {
                    Column(
                        modifier = Modifier.padding(WTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(WTheme.spacing.xs)
                    ) {
                        SummaryModalRow("Fecha", dateFmt.format(startInstant), Icons.Outlined.Event)
                        SummaryModalRow("Hora de inicio", timeFmt.format(startInstant), Icons.Outlined.Timer)
                        SummaryModalRow("Hora de finalización", timeFmt.format(endInstant), Icons.Outlined.Timer)
                        SummaryModalRow(stringResource(R.string.summary_metric_total_tasks), summary.totalTasksCount.toString(), Icons.Outlined.FormatListNumbered)
                        SummaryModalRow(stringResource(R.string.summary_metric_visited), summary.visitedCount.toString(), Icons.Outlined.CheckCircle)
                        SummaryModalRow(stringResource(R.string.summary_metric_failed), summary.failedCount.toString(), Icons.Outlined.HighlightOff)
                        SummaryModalRow("Evidencias observadas", summary.outOfRangeEvidenceCount.toString(), ImageVector.vectorResource(R.drawable.ic_camera_warning))
                        SummaryModalRow("Distancia recorrida", GeoUtils.formatDistance(summary.distanceMeters), Icons.Outlined.Route)
                        SummaryModalRow("Tiempo en ruta", TimeUtils.formatDuration(summary.elapsedMillis(nowMillis)), Icons.Outlined.Timer)
                        SummaryModalRow("Tiempo de inactividad", TimeUtils.formatDuration(summary.totalStoppedMillis(nowMillis)), Icons.Outlined.PauseCircle)
                    }
                }
                
                if (!isCapturing) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WTheme.spacing.xs)) {
                        WButton(
                            text = "Descargar",
                            onClick = { 
                                coroutineScope.launch {
                                    isCapturing = true
                                    delay(100)
                                    ScreenshotUtils.captureAndDownload(context, dialogView)
                                    isCapturing = false
                                }
                            },
                            variant = WButtonVariant.PRIMARY,
                            leadingIcon = Icons.Outlined.Download,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        WButton(
                            text = "Compartir",
                            onClick = { 
                                coroutineScope.launch {
                                    isCapturing = true
                                    delay(100)
                                    ScreenshotUtils.captureAndShare(context, dialogView)
                                    isCapturing = false
                                }
                            },
                            variant = WButtonVariant.SECONDARY,
                            leadingIcon = Icons.Outlined.Share,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        WButton(
                            text = "Cerrar",
                            onClick = onDismiss,
                            variant = WButtonVariant.GHOST,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryModalRow(label: String, value: String, icon: ImageVector) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = WTheme.spacing.xs), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WTheme.spacing.md)) {
            Surface(
                shape = CircleShape,
                color = WTheme.colors.surface,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = WTheme.colors.accent, modifier = Modifier.size(16.dp))
                }
            }
            Text(text = label, style = WTheme.typography.body, color = WTheme.colors.textSecondary)
        }
        Text(text = value, style = WTheme.typography.bodyStrong, color = WTheme.colors.textPrimary)
    }
}
