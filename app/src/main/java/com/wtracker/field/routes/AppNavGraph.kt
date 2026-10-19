package com.wtracker.field.routes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wtracker.field.R
import com.wtracker.field.configs.AppContainer
import com.wtracker.field.controllers.AppStartState
import com.wtracker.field.controllers.AppStartViewModel
import com.wtracker.field.controllers.ClientPickerMode
import com.wtracker.field.controllers.ControllerFactories
import com.wtracker.field.controllers.InterDayShiftViewModel
import com.wtracker.field.ui.components.BrandMark
import com.wtracker.field.ui.components.WDialog
import com.wtracker.field.ui.components.WButtonVariant
import com.wtracker.field.ui.theme.WTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import com.wtracker.field.ui.components.GradientBackground
import com.wtracker.field.ui.components.LoadingState
import com.wtracker.field.ui.screens.CameraRoute
import com.wtracker.field.ui.screens.ClientPickerRoute
import com.wtracker.field.ui.screens.LoginRoute
import com.wtracker.field.ui.screens.RouteSheetRoute
import com.wtracker.field.ui.screens.RouteWelcomeRoute
import com.wtracker.field.ui.screens.ShiftSummaryRoute
import com.wtracker.field.ui.screens.VisitCaptureRoute

/** Rutas tipadas: único lugar donde se definen patrones y argumentos de navegación. */
object AppDestinations {
    const val LOGIN = "login"
    const val ROUTE_WELCOME = "route_welcome"
    const val ROUTE_SHEET = "route_sheet"
    const val SHIFT_SUMMARY = "shift_summary"
    const val ARG_CLIENT_ID = "clientId"
    const val ARG_PICKER_MODE = "mode"
    const val VISIT_PATTERN = "visit/{$ARG_CLIENT_ID}"
    const val CAMERA_PATTERN = "camera/{$ARG_CLIENT_ID}"
    const val CLIENT_PICKER_PATTERN = "client_picker/{$ARG_PICKER_MODE}"

    fun visit(clientId: String): String = "visit/$clientId"
    fun camera(clientId: String): String = "camera/$clientId"
    fun clientPicker(mode: ClientPickerMode): String = "client_picker/${mode.routeValue}"
}

private val clientIdArguments = listOf(navArgument(AppDestinations.ARG_CLIENT_ID) { type = NavType.StringType })
private val pickerModeArguments = listOf(navArgument(AppDestinations.ARG_PICKER_MODE) { type = NavType.StringType })

/**
 * Raíz de navegación. La sesión y la ruta activa (no las pantallas) deciden el destino raíz:
 *  - sin sesión → login (un logout o refresh token expirado limpia toda la pila),
 *  - con sesión y sin ruta → bienvenida,
 *  - con ruta activa → hoja de ruta (al iniciar/finalizar la ruta la pila se reinicia sola).
 */
@Composable
fun AppNavGraph(container: AppContainer) {
    val appStartViewModel: AppStartViewModel = viewModel(factory = ControllerFactories.appStart(container))
    val appStartState by appStartViewModel.uiState.collectAsStateWithLifecycle()

    when (val state = appStartState) {
        AppStartState.Loading -> AppSplash()
        is AppStartState.Ready -> key(state.isLoggedIn) {
            SessionAwareNavHost(container = container, rootDestination = state.rootDestination())
        }
    }
}

private fun AppStartState.Ready.rootDestination(): String = when {
    !isLoggedIn -> AppDestinations.LOGIN
    hasActiveRoute -> AppDestinations.ROUTE_SHEET
    else -> AppDestinations.ROUTE_WELCOME
}

@Composable
private fun SessionAwareNavHost(container: AppContainer, rootDestination: String) {
    val interDayViewModel: InterDayShiftViewModel = viewModel(factory = ControllerFactories.interDayShift(container))
    val showInterDayModal by interDayViewModel.showModal.collectAsStateWithLifecycle()
    val isFinishingShift by interDayViewModel.isFinishing.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                interDayViewModel.checkShiftStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(rootDestination) {
        if (rootDestination == AppDestinations.ROUTE_SHEET) {
            interDayViewModel.checkShiftStatus()
        }
    }

    if (showInterDayModal) {
        InterDayShiftModal(
            isFinishing = isFinishingShift,
            onFinishShift = { interDayViewModel.finishShift() },
            onContinue = { interDayViewModel.dismissModal() }
        )
    }

    val navController = rememberNavController()
    // Se fija sólo en la primera composición: cambiar startDestination reconstruiría el grafo.
    val startDestination = remember { rootDestination }

    LaunchedEffect(rootDestination) {
        val currentRoute = navController.currentDestination?.route
        if (currentRoute != null && currentRoute != rootDestination) {
            navController.resetTo(rootDestination)
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppDestinations.LOGIN) { LoginRoute(container = container) }

        composable(AppDestinations.ROUTE_WELCOME) {
            RouteWelcomeRoute(
                container = container,
                onAddClients = { navController.navigate(AppDestinations.clientPicker(ClientPickerMode.START_ROUTE)) },
            )
        }

        composable(AppDestinations.CLIENT_PICKER_PATTERN, arguments = pickerModeArguments) { entry ->
            val mode = ClientPickerMode.fromRouteValue(entry.arguments?.getString(AppDestinations.ARG_PICKER_MODE))
            ClientPickerRoute(
                container = container,
                mode = mode,
                onBack = { navController.popBackStack() },
                onCompleted = {
                    // Al iniciar ruta, el cambio de destino raíz ya lleva a la hoja de ruta; sólo "agregar" regresa.
                    if (mode == ClientPickerMode.ADD_TO_ROUTE) navController.popBackStack()
                },
            )
        }

        composable(AppDestinations.ROUTE_SHEET) {
            RouteSheetRoute(
                container = container,
                onOpenClient = { clientId -> navController.navigate(AppDestinations.visit(clientId)) },
                onOpenSummary = { navController.navigate(AppDestinations.SHIFT_SUMMARY) },
                onAddClients = { navController.navigate(AppDestinations.clientPicker(ClientPickerMode.ADD_TO_ROUTE)) },
            )
        }

        composable(AppDestinations.VISIT_PATTERN, arguments = clientIdArguments) { entry ->
            val clientId = entry.arguments?.getString(AppDestinations.ARG_CLIENT_ID).orEmpty()
            VisitCaptureRoute(
                container = container,
                clientId = clientId,
                onOpenCamera = { navController.navigate(AppDestinations.camera(clientId)) },
                onFinished = { navController.popBackStack() },
            )
        }

        composable(AppDestinations.CAMERA_PATTERN, arguments = clientIdArguments) { entry ->
            val clientId = entry.arguments?.getString(AppDestinations.ARG_CLIENT_ID).orEmpty()
            CameraRoute(container = container, clientId = clientId, onClose = { navController.popBackStack() })
        }

        composable(AppDestinations.SHIFT_SUMMARY) {
            ShiftSummaryRoute(container = container, onBack = { navController.popBackStack() })
        }
    }
}

/** Navega al destino raíz eliminando toda la pila previa (evita volver a pantallas sin sesión). */
private fun NavHostController.resetTo(destination: String) {
    if (currentDestination?.route == destination) return
    navigate(destination) {
        popUpTo(graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
private fun AppSplash() {
    GradientBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) { BrandMark() }
            LoadingState(message = stringResource(R.string.app_start_loading), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun InterDayShiftModal(isFinishing: Boolean, onFinishShift: () -> Unit, onContinue: () -> Unit) {
    WDialog(
        title = "Turno pendiente detectado",
        icon = Icons.Outlined.Flag,
        tone = WTheme.colors.warning,
        confirmText = "Finalizar Jornada (Recomendado)",
        confirmVariant = WButtonVariant.PRIMARY,
        dismissText = "Continuar Ruta",
        isConfirmLoading = isFinishing,
        onConfirm = onFinishShift,
        onDismiss = onContinue,
    ) {
        Text(
            text = "Tienes una jornada que inició en un día anterior y no fue cerrada. ¿Deseas finalizarla ahora o continuar con tu ruta actual?",
            style = WTheme.typography.body,
            color = WTheme.colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
