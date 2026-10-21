package com.wtracker.field.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AddBusiness
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit
import com.wtracker.field.R
import com.wtracker.field.configs.AppContainer
import com.wtracker.field.configs.RoutePermissions
import com.wtracker.field.controllers.ControllerFactories
import com.wtracker.field.controllers.RouteSheetUiState
import com.wtracker.field.controllers.RouteSheetViewModel
import com.wtracker.field.controllers.UpdateUiState
import com.wtracker.field.controllers.UpdateViewModel
import com.wtracker.field.ui.components.UpdateBanner
import com.wtracker.field.utils.GamificationEngine
import com.wtracker.field.ui.components.NewClientOrderCard
import com.wtracker.field.ui.components.ClientRouteCard
import com.wtracker.field.ui.components.DiagnosticBanner
import com.wtracker.field.ui.components.EmptyState
import com.wtracker.field.ui.components.ErrorState
import com.wtracker.field.ui.components.LoadingState
import com.wtracker.field.ui.components.PermissionGate
import com.wtracker.field.ui.components.RouteClosureDialog
import com.wtracker.field.ui.components.RouteMap
import com.wtracker.field.ui.components.RouteMapState
import com.wtracker.field.ui.components.rememberRouteMapState
import com.wtracker.field.ui.components.RouteProgressHeader
import com.wtracker.field.ui.components.SectionHeader
import com.wtracker.field.ui.components.ShiftControlCard
import com.wtracker.field.ui.components.WButton
import com.wtracker.field.ui.components.WButtonVariant
import com.wtracker.field.ui.components.WTopBar
import com.wtracker.field.ui.components.isPositive
import com.wtracker.field.ui.theme.WTheme
import com.wtracker.field.utils.TimeUtils
import java.time.ZoneId

/** Callbacks agrupados para mantener la firma de la pantalla legible e inmutable. */
data class RouteSheetActions(
    val onRefresh: () -> Unit,
    val onLogoutRequested: () -> Unit,
    val onOpenSummary: () -> Unit,
    val onOpenClient: (clientId: String) -> Unit,
    val onAddClients: () -> Unit,
    val onFinishRouteRequested: () -> Unit,
    val onRemoveClientFromRoute: (clientId: String) -> Unit,
    val onRouteClosureConfirmed: () -> Unit,
    val onRouteClosureDismissed: () -> Unit,
    val onRouteCompletedModalDismissed: () -> Unit,
    val onTransientMessageShown: () -> Unit,
    val onToggleTheme: () -> Unit,
    val onAcceptNextClientSuggestion: () -> Unit,
    val onDismissNextClientSuggestion: () -> Unit,
    val onBadgesAcknowledged: () -> Unit,
    val onUpdateClicked: () -> Unit,
    val onTakeOrderNewClient: () -> Unit,
)

@Composable
fun RouteSheetRoute(
    container: AppContainer,
    onOpenClient: (String) -> Unit,
    onOpenSummary: () -> Unit,
    onAddClients: () -> Unit,
    onTakeOrderNewClient: () -> Unit,
) {
    val viewModel: RouteSheetViewModel = viewModel(factory = ControllerFactories.routeSheet(container))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateViewModel: UpdateViewModel = viewModel(factory = ControllerFactories.update(container))
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val isLightMode by container.appPreferencesStore.isLightModeFlow.collectAsStateWithLifecycle(initialValue = false)
    val coroutineScope = rememberCoroutineScope()
    val onToggleTheme = {
        coroutineScope.launch {
            container.appPreferencesStore.setLightMode(!isLightMode)
        }
        Unit
    }

    PermissionGate(
        requestedPermissions = RoutePermissions.requested,
        requiredPermissions = RoutePermissions.required,
        rationaleTitle = stringResource(R.string.route_permission_title),
        rationaleMessage = stringResource(R.string.route_permission_message),
        icon = Icons.Outlined.LocationOn,
    ) {
        LaunchedEffect(Unit) { viewModel.onLocationPermissionGranted() }
        RouteSheetScreen(
            uiState = uiState,
            updateState = updateState,
            actions = RouteSheetActions(
                onRefresh = viewModel::refreshClients,
                onLogoutRequested = viewModel::onLogoutRequested,
                onOpenSummary = onOpenSummary,
                onOpenClient = onOpenClient,
                onAddClients = onAddClients,
                onFinishRouteRequested = viewModel::onFinishRouteRequested,
                onRemoveClientFromRoute = viewModel::onRemoveClientFromRoute,
                onRouteClosureConfirmed = viewModel::onRouteClosureConfirmed,
                onRouteClosureDismissed = viewModel::onRouteClosureDismissed,
                onRouteCompletedModalDismissed = viewModel::onRouteCompletedModalDismissed,
                onTransientMessageShown = viewModel::onTransientMessageShown,
                onToggleTheme = onToggleTheme,
                onAcceptNextClientSuggestion = viewModel::onAcceptNextClientSuggestion,
                onDismissNextClientSuggestion = viewModel::onDismissNextClientSuggestion,
                onBadgesAcknowledged = viewModel::onBadgesAcknowledged,
                onUpdateClicked = updateViewModel::onUpdateClicked,
                onTakeOrderNewClient = onTakeOrderNewClient,
            ),
            isLightMode = isLightMode,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSheetScreen(uiState: RouteSheetUiState, updateState: UpdateUiState, isLightMode: Boolean, actions: RouteSheetActions) {
    val snackbarHostState = remember { SnackbarHostState() }
    val zone = remember { ZoneId.systemDefault() }
    var showFullScreenMap by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.transientMessage) {
        val message = uiState.transientMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        actions.onTransientMessageShown()
    }

    LaunchedEffect(uiState.suggestedNextClient) {
        val client = uiState.suggestedNextClient
        if (client == null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            return@LaunchedEffect
        }
        val title = uiState.suggestionTitle ?: "Siguiente cliente: ${client.businessName}"
        val result = snackbarHostState.showSnackbar(
            message = "$title\nIr a: ${client.businessName}",
            actionLabel = "¡Ir ahora!",
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            actions.onAcceptNextClientSuggestion()
        } else {
            actions.onDismissNextClientSuggestion()
        }
    }

    Scaffold(
        containerColor = WTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            WTopBar(
                title = uiState.userName.ifBlank { stringResource(R.string.route_title) },
                subtitle = uiState.nowMillis.takeIf { it > 0L }?.let { TimeUtils.formatLongDate(it, zone) },
                actions = { RouteSheetTopActions(isVendor = uiState.isVendor, isBusy = uiState.isShiftActionInProgress, isLightMode = isLightMode, actions = actions) },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            RouteSheetBody(
                uiState = uiState,
                updateState = updateState,
                actions = actions,
                isLightMode = isLightMode,
                onOpenFullScreenMap = { showFullScreenMap = true },
            )
        }
    }

    uiState.routeClosureDialogReason?.let { reason ->
        RouteClosureDialog(
            reason = reason,
            pendingVisitCount = uiState.pendingVisitCount,
            isSubmitting = uiState.isShiftActionInProgress,
            onConfirm = actions.onRouteClosureConfirmed,
            onDismiss = actions.onRouteClosureDismissed,
        )
    }

    if (uiState.showRouteCompletedModal) {
        RouteCompletedModal(
            onDismiss = actions.onRouteCompletedModalDismissed,
            onOpenSummary = {
                actions.onRouteCompletedModalDismissed()
                actions.onOpenSummary()
            },
        )
    }

    if (uiState.earnedBadges.isNotEmpty()) {
        Dialog(onDismissRequest = actions.onBadgesAcknowledged) {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(WTheme.radius.md)).background(WTheme.colors.surface)) {
                Column(modifier = Modifier.padding(WTheme.spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("¡Nuevos Logros!", style = WTheme.typography.title, color = WTheme.colors.textPrimary, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(WTheme.spacing.md))
                    uiState.earnedBadges.forEach { badge ->
                        Text(GamificationEngine.getBadgeLabel(badge), style = WTheme.typography.subtitle, color = WTheme.colors.accent, textAlign = TextAlign.Center)
                        Text(GamificationEngine.getBadgeDescription(badge, uiState.businessSettings), style = WTheme.typography.body, color = WTheme.colors.textSecondary, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(WTheme.spacing.sm))
                    }
                    val acknowledgeLabel = if (uiState.earnedBadges.any { !it.isPositive() }) "Entendido" else "¡Genial!"
                    WButton(text = acknowledgeLabel, onClick = actions.onBadgesAcknowledged, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        val hasNegative = uiState.earnedBadges.any { !it.isPositive() }
        
        if (hasNegative) {
            val drawable = androidx.core.content.ContextCompat.getDrawable(LocalContext.current, R.drawable.ic_lightning)
            val lightningShape = drawable?.let { nl.dionsegijn.konfetti.core.models.Shape.DrawableShape(it, tint = true) } ?: nl.dionsegijn.konfetti.core.models.Shape.Square
            val stormParty = Party(
                speed = 10f,
                maxSpeed = 50f,
                damping = 0.9f,
                spread = 360,
                colors = listOf(0xFFB0BEC5.toInt(), 0xFF78909C.toInt(), 0xFFFFEB3B.toInt()),
                shapes = listOf(lightningShape),
                emitter = Emitter(duration = 200, TimeUnit.MILLISECONDS).max(150),
                position = Position.Relative(0.5, 0.0)
            )
            KonfettiView(
                modifier = Modifier.fillMaxSize(),
                parties = listOf(stormParty)
            )
        } else {
            KonfettiView(
                modifier = Modifier.fillMaxSize(),
                parties = listOf(
                    Party(
                        speed = 0f,
                        maxSpeed = 30f,
                        damping = 0.9f,
                        spread = 360,
                        colors = listOf(0xFFFCE18A.toInt(), 0xFFFF726D.toInt(), 0xFFF4306D.toInt(), 0xFFB48DEF.toInt()),
                        emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
                        position = Position.Relative(0.5, 0.3)
                    )
                )
            )
        }
    }

    if (showFullScreenMap) {
        Dialog(
            onDismissRequest = { showFullScreenMap = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(WTheme.colors.background)) {
                RouteMap(
                    stops = uiState.stops,
                    userLocation = uiState.userLocation,
                    modifier = Modifier.fillMaxSize(),
                    isLightMode = isLightMode,
                )
                IconButton(
                    onClick = { showFullScreenMap = false },
                    modifier = Modifier
                        .padding(WTheme.spacing.lg)
                        .align(Alignment.TopEnd)
                        .background(WTheme.colors.surfaceElevated.copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.map_fullscreen_close),
                        tint = WTheme.colors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteSheetTopActions(isVendor: Boolean, isBusy: Boolean, isLightMode: Boolean, actions: RouteSheetActions) {
    if (isVendor) {
        IconButton(onClick = actions.onTakeOrderNewClient) {
            Icon(
                imageVector = Icons.Outlined.PersonAddAlt,
                contentDescription = "Tomar pedido a cliente nuevo",
                tint = WTheme.colors.accent,
            )
        }
    }
    IconButton(onClick = actions.onToggleTheme) {
        Icon(
            imageVector = if (isLightMode) Icons.Outlined.DarkMode else Icons.Outlined.LightMode, 
            contentDescription = if (isLightMode) "Modo oscuro" else "Modo claro"
        )
    }
    IconButton(onClick = actions.onOpenSummary) {
        Icon(imageVector = Icons.Outlined.Insights, contentDescription = stringResource(R.string.action_summary))
    }
    IconButton(onClick = actions.onLogoutRequested, enabled = !isBusy) {
        Icon(imageVector = Icons.AutoMirrored.Outlined.Logout, contentDescription = stringResource(R.string.action_logout))
    }
}

@Composable
private fun RouteSheetBody(
    uiState: RouteSheetUiState,
    updateState: UpdateUiState,
    actions: RouteSheetActions,
    isLightMode: Boolean,
    onOpenFullScreenMap: () -> Unit,
) {
    val spacing = WTheme.spacing
    val mapState = rememberRouteMapState(isLightMode)
    // El contenido siempre es una lista desplazable para que el gesto "pull to refresh" funcione en todos los estados.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (updateState.isUpdateAvailable) {
            item(key = "update_banner") { UpdateBanner(state = updateState, onUpdateClick = actions.onUpdateClicked) }
        }
        item(key = "banner") { 
            DiagnosticBanner(
                isOnline = uiState.isOnline, 
                isGpsEnabled = uiState.isGpsEnabled,
                pendingSyncCount = uiState.pendingSyncCount
            ) 
        }
        if (uiState.stuckTaskHours != null) {
            item(key = "stuck_task_banner") {
                StuckTaskBanner(hours = uiState.stuckTaskHours!!)
            }
        }
        item(key = "shift") {
            ShiftControlCard(activeShiftStartedAtMillis = uiState.activeShiftStartedAtMillis, nowMillis = uiState.nowMillis)
        }
        if (uiState.isVendor) {
            item(key = "new_client_order_card") {
                NewClientOrderCard(onClick = actions.onTakeOrderNewClient)
            }
        }
        routeContent(uiState = uiState, actions = actions, isLightMode = isLightMode, mapState = mapState, onOpenFullScreenMap = onOpenFullScreenMap)
        item(key = "finish_route") {
            FinishRouteButton(
                isBusy = uiState.isShiftActionInProgress,
                pendingCount = uiState.pendingVisitCount,
                onClick = actions.onFinishRouteRequested
            )
        }
    }
}

private fun LazyListScope.routeContent(uiState: RouteSheetUiState, actions: RouteSheetActions, isLightMode: Boolean, mapState: RouteMapState, onOpenFullScreenMap: () -> Unit) {
    if (uiState.isInitialLoading) {
        item(key = "loading") { RouteStateContainer { LoadingState(message = stringResource(R.string.route_loading)) } }
        return
    }
    if (uiState.stops.isEmpty() && uiState.loadErrorMessage != null) {
        item(key = "error") { RouteStateContainer { ErrorState(message = uiState.loadErrorMessage, onRetry = actions.onRefresh) } }
        return
    }
    if (uiState.stops.isEmpty()) {
        item(key = "empty") {
            RouteStateContainer {
                EmptyState(
                    title = stringResource(R.string.route_empty_title),
                    message = stringResource(R.string.route_empty_message),
                    action = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(WTheme.spacing.sm),
                        ) {
                            WButton(
                                text = stringResource(R.string.route_empty_action),
                                onClick = actions.onAddClients,
                                leadingIcon = Icons.Outlined.AddBusiness,
                            )
                            if (uiState.isVendor) {
                                WButton(
                                    text = "Tomar pedido a cliente nuevo",
                                    onClick = actions.onTakeOrderNewClient,
                                    variant = WButtonVariant.SECONDARY,
                                    leadingIcon = Icons.Outlined.PersonAddAlt,
                                )
                            }
                        }
                    },
                )
            }
        }
        return
    }

    item(key = "progress") { RouteProgressHeader(completedCount = uiState.completedCount, totalCount = uiState.stops.size) }
    item(key = "map") {
        Box(modifier = Modifier.fillMaxWidth().height(WTheme.sizes.mapHeight)) {
            RouteMap(
                stops = uiState.stops,
                userLocation = uiState.userLocation,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(WTheme.radius.lg)),
                isLightMode = isLightMode,
                mapState = mapState,
            )
            IconButton(
                onClick = onOpenFullScreenMap,
                modifier = Modifier
                    .padding(WTheme.spacing.sm)
                    .align(Alignment.TopEnd)
                    .background(WTheme.colors.surfaceElevated.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Fullscreen,
                    contentDescription = stringResource(R.string.map_fullscreen_open),
                    tint = WTheme.colors.textPrimary
                )
            }
        }
    }
    item(key = "section") {
        SectionHeader(
            title = stringResource(R.string.route_section_clients),
            countBadge = uiState.stops.size,
            action = { AddClientsAction(onClick = actions.onAddClients) },
        )
    }
    items(
        items = uiState.stops, 
        key = { "client_${it.client.id}" },
        contentType = { "ClientRouteCard" }
    ) { stop ->
        ClientRouteCard(
            stop = stop,
            onClick = { actions.onOpenClient(stop.client.id) },
            onRemove = { actions.onRemoveClientFromRoute(stop.client.id) },
        )
    }
}

/** Agrega clientes "en caliente" a la ruta del día sin interrumpir la jornada. */
@Composable
private fun AddClientsAction(onClick: () -> Unit) {
    WButton(
        text = stringResource(R.string.route_add_clients_action),
        onClick = onClick,
        leadingIcon = Icons.Outlined.AddBusiness,
    )
}

@Composable
private fun FinishRouteButton(isBusy: Boolean, pendingCount: Int, onClick: () -> Unit) {
    val variant = if (pendingCount == 0) WButtonVariant.SUCCESS else WButtonVariant.DANGER
    WButton(
        text = stringResource(R.string.route_finish_action),
        onClick = onClick,
        variant = variant,
        leadingIcon = Icons.Outlined.Flag,
        enabled = !isBusy,
        modifier = Modifier.fillMaxWidth().padding(top = WTheme.spacing.sm, bottom = WTheme.spacing.lg),
    )
}

@Composable
private fun RouteCompletedModal(
    onDismiss: () -> Unit,
    onOpenSummary: () -> Unit,
) {
    com.wtracker.field.ui.components.WDialog(
        title = "¡Ruta Completada!",
        icon = Icons.Outlined.CheckCircle,
        tone = WTheme.colors.statusVisited,
        confirmText = "Ver Resumen de Ruta",
        confirmVariant = WButtonVariant.PRIMARY,
        onConfirm = onOpenSummary,
        onDismiss = onDismiss,
        dismissText = "Cerrar",
    ) {
        androidx.compose.material3.Text(
            text = "Has completado todas las visitas programadas para hoy.",
            style = WTheme.typography.body,
            color = WTheme.colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun RouteStateContainer(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = WTheme.spacing.xxl)) { content() }
}

@Composable
private fun StuckTaskBanner(hours: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WTheme.radius.md))
            .background(WTheme.colors.danger.copy(alpha = 0.1f))
            .padding(WTheme.spacing.md)
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WTheme.spacing.sm)
        ) {
            Icon(
                imageVector = Icons.Outlined.Flag,
                contentDescription = null,
                tint = WTheme.colors.danger
            )
            androidx.compose.material3.Text(
                text = "⚠️ Tienes una tarea En Ruta desde hace $hours horas. Por favor, finalízala o repórtala como fallida para poder continuar.",
                style = WTheme.typography.bodyStrong,
                color = WTheme.colors.danger,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
