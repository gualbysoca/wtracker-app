package com.wtracker.field.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wtracker.field.R
import com.wtracker.field.configs.AppContainer
import com.wtracker.field.controllers.ControllerFactories
import com.wtracker.field.controllers.ShiftSummaryUiState
import com.wtracker.field.controllers.ShiftSummaryViewModel
import com.wtracker.field.services.models.ShiftSummary
import com.wtracker.field.ui.components.EmptyState
import com.wtracker.field.ui.components.LoadingState
import com.wtracker.field.ui.components.MetricTile
import com.wtracker.field.ui.components.SectionHeader
import com.wtracker.field.ui.components.StopEventRow
import com.wtracker.field.ui.components.WTopBar
import com.wtracker.field.ui.components.getIcon
import com.wtracker.field.ui.components.isPositive
import com.wtracker.field.ui.theme.WTheme
import com.wtracker.field.utils.GeoUtils
import com.wtracker.field.utils.ScreenshotUtils
import com.wtracker.field.utils.TimeUtils
import com.wtracker.field.utils.GamificationEngine
import com.wtracker.field.utils.GamificationBadge
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@Composable
fun ShiftSummaryRoute(container: AppContainer, onBack: () -> Unit) {
    val viewModel: ShiftSummaryViewModel = viewModel(factory = ControllerFactories.shiftSummary(container))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShiftSummaryScreen(uiState = uiState, onBack = onBack)
}

@Composable
fun ShiftSummaryScreen(uiState: ShiftSummaryUiState, onBack: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val context = LocalContext.current
    val view = LocalView.current
    val summary = uiState.summary
    val allTasksCompleted = summary?.let { it.visitedCount + it.failedCount >= it.totalTasksCount } ?: false
    var isCapturing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = WTheme.colors.background,
        topBar = {
            if (!isCapturing) {
                WTopBar(
                    title = stringResource(R.string.summary_title),
                    subtitle = summary?.let { shiftRangeLabel(it, zone) },
                    onBack = onBack,
                    actions = {
                        if (summary != null) {
                            IconButton(
                                onClick = { 
                                    coroutineScope.launch {
                                        isCapturing = true
                                        kotlinx.coroutines.delay(100)
                                        ScreenshotUtils.captureAndDownload(context, view)
                                        isCapturing = false
                                    }
                                },
                                enabled = allTasksCompleted
                            ) {
                                Icon(imageVector = Icons.Outlined.Download, contentDescription = "Descargar", tint = if (allTasksCompleted) WTheme.colors.textPrimary else WTheme.colors.textMuted)
                            }
                            IconButton(
                                onClick = { 
                                    coroutineScope.launch {
                                        isCapturing = true
                                        kotlinx.coroutines.delay(100)
                                        ScreenshotUtils.captureAndShare(context, view)
                                        isCapturing = false
                                    }
                                },
                                enabled = allTasksCompleted
                            ) {
                                Icon(imageVector = Icons.Outlined.Share, contentDescription = "Compartir", tint = if (allTasksCompleted) WTheme.colors.textPrimary else WTheme.colors.textMuted)
                            }
                        }
                    }
                )
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        if (uiState.isLoading) {
            LoadingState(message = stringResource(R.string.summary_loading), modifier = contentModifier)
            return@Scaffold
        }
        if (summary == null) {
            EmptyState(
                title = stringResource(R.string.summary_empty_title),
                message = stringResource(R.string.summary_empty_message),
                icon = Icons.Outlined.EventBusy,
                modifier = contentModifier,
            )
            return@Scaffold
        }
        
        val badges = remember(summary, uiState.nowMillis, uiState.businessSettings) {
            GamificationEngine.evaluateSummary(summary, uiState.nowMillis, uiState.businessSettings)
        }

        ShiftSummaryContent(summary = summary, uiState = uiState, zone = zone, badges = badges, modifier = contentModifier)
    }
}

@Composable
private fun shiftRangeLabel(summary: ShiftSummary, zone: ZoneId): String {
    val startLabel = TimeUtils.formatClock(summary.startedAtMillis, zone)
    val endedAt = summary.endedAtMillis ?: return stringResource(R.string.summary_status_active, startLabel)
    return stringResource(R.string.summary_status_closed, startLabel, TimeUtils.formatClock(endedAt, zone))
}

@Composable
private fun ShiftSummaryContent(summary: ShiftSummary, uiState: ShiftSummaryUiState, zone: ZoneId, badges: List<GamificationBadge>, modifier: Modifier) {
    val spacing = WTheme.spacing
    val metrics = buildMetrics(summary, uiState.nowMillis)
    var selectedBadge by remember { mutableStateOf<Pair<GamificationBadge, ImageVector>?>(null) }
    
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        metricRows(metrics)
        
        if (badges.isNotEmpty()) {
            item(key = "badges_header") {
                SectionHeader(title = "Logros y Medallas")
            }
            item(key = "badges_list") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(WTheme.spacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    badges.forEach { badge ->
                        val badgeIcon = badge.getIcon()
                        androidx.compose.material3.AssistChip(
                            onClick = { selectedBadge = badge to badgeIcon },
                            label = { Text(GamificationEngine.getBadgeLabel(badge)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = badgeIcon,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item(key = "stops_header") {
            val totalInactivityTime = TimeUtils.formatDuration(summary.totalStoppedMillis(uiState.nowMillis))
            SectionHeader(
                title = stringResource(R.string.summary_section_stops),
                trailing = totalInactivityTime
            )
        }
        if (summary.stops.isEmpty()) {
            item(key = "stops_empty") {
                Text(
                    text = stringResource(R.string.summary_stops_empty),
                    style = WTheme.typography.body,
                    color = WTheme.colors.textMuted,
                )
            }
            return@LazyColumn
        }
        items(items = summary.stops, key = { it.id }) { stopEvent ->
            StopEventRow(stopEvent = stopEvent, nowMillis = uiState.nowMillis, zone = zone)
        }
    }

    selectedBadge?.let { (badge, icon) ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { selectedBadge = null }) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(WTheme.radius.lg),
                color = WTheme.colors.surface,
                border = androidx.compose.foundation.BorderStroke(WTheme.sizes.borderThin, WTheme.colors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(WTheme.spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(WTheme.spacing.lg)
                ) {
                    val tone = WTheme.colors.accent
                    Box(
                        modifier = Modifier
                            .size(WTheme.sizes.iconXl)
                            .background(tone.copy(alpha = WTheme.alpha.subtle), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = tone, modifier = Modifier.size(WTheme.sizes.iconLg))
                    }
                    Text(text = GamificationEngine.getBadgeLabel(badge), style = WTheme.typography.title, color = WTheme.colors.textPrimary)
                    Text(text = GamificationEngine.getBadgeDescription(badge, uiState.businessSettings), style = WTheme.typography.body, color = WTheme.colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    com.wtracker.field.ui.components.WButton(text = stringResource(R.string.action_back), onClick = { selectedBadge = null }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private data class SummaryMetric(val labelRes: Int, val tooltipRes: Int, val value: String, val icon: ImageVector, val tone: Color)

@Composable
private fun buildMetrics(summary: ShiftSummary, nowMillis: Long): List<SummaryMetric> {
    val colors = WTheme.colors
    val totalTasks = summary.totalTasksCount
    return listOf(
        SummaryMetric(R.string.summary_metric_distance, R.string.summary_tooltip_distance, GeoUtils.formatDistance(summary.distanceMeters), Icons.Outlined.Route, colors.brandGradientEnd),
        SummaryMetric(R.string.summary_metric_visited, R.string.summary_tooltip_visited, summary.visitedCount.toString(), Icons.Outlined.CheckCircle, colors.statusVisited),
        SummaryMetric(R.string.summary_metric_failed, R.string.summary_tooltip_failed, summary.failedCount.toString(), Icons.Outlined.HighlightOff, colors.statusFailed),
        SummaryMetric(R.string.summary_metric_total_tasks, R.string.summary_tooltip_total_tasks, totalTasks.toString(), Icons.Outlined.FormatListNumbered, colors.accent),
        SummaryMetric(R.string.summary_metric_elapsed, R.string.summary_tooltip_elapsed, TimeUtils.formatDuration(summary.elapsedMillis(nowMillis)), Icons.Outlined.Timer, colors.accent),
        SummaryMetric(R.string.summary_metric_out_of_range_evidence, R.string.summary_tooltip_out_of_range, summary.outOfRangeEvidenceCount.toString(), ImageVector.vectorResource(R.drawable.ic_camera_warning), colors.warning),
        SummaryMetric(R.string.summary_metric_stopped, R.string.summary_tooltip_stopped, TimeUtils.formatDuration(summary.totalStoppedMillis(nowMillis)), Icons.Outlined.PauseCircle, colors.danger),
    )
}

/** Cuadrícula dentro de la lista perezosa (evita anidar contenedores desplazables). */
private fun LazyListScope.metricRows(metrics: List<SummaryMetric>) {
    metrics.chunked(METRICS_PER_ROW).forEachIndexed { rowIndex, rowMetrics ->
        item(key = "metrics_$rowIndex") {
            Row(horizontalArrangement = Arrangement.spacedBy(WTheme.spacing.md)) {
                rowMetrics.forEach { metric ->
                    MetricTile(
                        label = stringResource(metric.labelRes),
                        value = metric.value,
                        icon = metric.icon,
                        tone = metric.tone,
                        tooltipText = stringResource(metric.tooltipRes),
                        modifier = Modifier.weight(1f),
                    )
                }
                // Rellenar espacios vacíos en la última fila para mantener el ancho normalizado
                repeat(METRICS_PER_ROW - rowMetrics.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private const val METRICS_PER_ROW = 2
