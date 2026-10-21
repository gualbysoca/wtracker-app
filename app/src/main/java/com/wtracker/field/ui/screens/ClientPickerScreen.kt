package com.wtracker.field.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wtracker.field.R
import com.wtracker.field.configs.AppContainer
import com.wtracker.field.controllers.ClientPickerEvent
import com.wtracker.field.controllers.ClientPickerMode
import com.wtracker.field.controllers.ClientPickerUiState
import com.wtracker.field.controllers.ClientPickerViewModel
import com.wtracker.field.controllers.ControllerFactories
import com.wtracker.field.ui.components.EmptyState
import com.wtracker.field.ui.components.ErrorState
import com.wtracker.field.ui.components.LoadingState
import com.wtracker.field.ui.components.SelectableClientCard
import com.wtracker.field.ui.components.StartRouteDialog
import com.wtracker.field.ui.components.WTextField
import com.wtracker.field.ui.components.WButton
import com.wtracker.field.ui.components.WTopBar
import com.wtracker.field.ui.theme.WTheme
import java.time.ZoneId

data class ClientPickerActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onClientToggled: (clientId: String) -> Unit,
    val onSearchToggled: () -> Unit,
    val onSearchQueryChanged: (String) -> Unit,
    val onPrimaryAction: () -> Unit,
    val onStartConfirmed: () -> Unit,
    val onStartDismissed: () -> Unit,
    val onTransientMessageShown: () -> Unit,
    val onTakeOrderNewClient: () -> Unit,
)

@Composable
fun ClientPickerRoute(
    container: AppContainer,
    mode: ClientPickerMode,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    onTakeOrderNewClient: () -> Unit = {},
) {
    val viewModel: ClientPickerViewModel = viewModel(factory = ControllerFactories.clientPicker(container, mode))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ClientPickerEvent.Completed -> onCompleted()
            }
        }
    }

    ClientPickerScreen(
        uiState = uiState,
        actions = ClientPickerActions(
            onBack = onBack,
            onRefresh = viewModel::refreshClients,
            onClientToggled = viewModel::onClientToggled,
            onSearchToggled = viewModel::onSearchToggled,
            onSearchQueryChanged = viewModel::onSearchQueryChanged,
            onPrimaryAction = viewModel::onPrimaryActionClicked,
            onStartConfirmed = viewModel::onStartRouteConfirmed,
            onStartDismissed = viewModel::onStartConfirmationDismissed,
            onTransientMessageShown = viewModel::onTransientMessageShown,
            onTakeOrderNewClient = onTakeOrderNewClient,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientPickerScreen(uiState: ClientPickerUiState, actions: ClientPickerActions) {
    val snackbarHostState = remember { SnackbarHostState() }
    val zone = remember { ZoneId.systemDefault() }

    LaunchedEffect(uiState.transientMessage) {
        val message = uiState.transientMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        actions.onTransientMessageShown()
    }

    Scaffold(
        containerColor = WTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { ClientPickerTopBar(uiState = uiState, actions = actions) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            ClientPickerBody(uiState = uiState, actions = actions)
        }
    }

    uiState.startConfirmation?.let { confirmation ->
        StartRouteDialog(
            startAtMillis = confirmation.startAtMillis,
            clientCount = confirmation.clientCount,
            zone = zone,
            isSubmitting = uiState.isSubmitting,
            onConfirm = actions.onStartConfirmed,
            onDismiss = actions.onStartDismissed,
        )
    }
}

@Composable
private fun ClientPickerTopBar(uiState: ClientPickerUiState, actions: ClientPickerActions) {
    val isStartMode = uiState.mode == ClientPickerMode.START_ROUTE
    val subtitle = if (uiState.selectedCount > 0) {
        pluralStringResource(R.plurals.picker_selected_count, uiState.selectedCount, uiState.selectedCount)
    } else {
        pluralStringResource(R.plurals.picker_available_count, uiState.availableClientCount, uiState.availableClientCount)
    }

    Column {
        WTopBar(
            title = stringResource(if (isStartMode) R.string.picker_title_start else R.string.picker_title_add),
            subtitle = subtitle,
            onBack = actions.onBack,
            actions = {
                if (uiState.isVendor) {
                    IconButton(onClick = actions.onTakeOrderNewClient) {
                        Icon(
                            imageVector = Icons.Outlined.PersonAddAlt,
                            contentDescription = "Tomar pedido a cliente nuevo",
                            tint = WTheme.colors.accent,
                        )
                    }
                }
                IconButton(onClick = actions.onSearchToggled) {
                    Icon(
                        imageVector = if (uiState.isSearchVisible) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                        contentDescription = stringResource(if (uiState.isSearchVisible) R.string.action_close_search else R.string.action_search),
                    )
                }
                PickerPrimaryAction(isStartMode = isStartMode, isEnabled = uiState.canSubmit, onClick = actions.onPrimaryAction)
            },
        )
        AnimatedVisibility(
            visible = uiState.isSearchVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ClientSearchField(query = uiState.searchQuery, onQueryChanged = actions.onSearchQueryChanged)
        }
    }
}

/** Acción principal de la cabecera: "Iniciar" (abre el modal de confirmación) o "Agregar" en caliente. */
@Composable
private fun PickerPrimaryAction(isStartMode: Boolean, isEnabled: Boolean, onClick: () -> Unit) {
    val colors = WTheme.colors
    FilledIconButton(
        onClick = onClick,
        enabled = isEnabled,
        modifier = Modifier.padding(end = WTheme.spacing.sm),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = colors.accent,
            contentColor = colors.accentContent,
            disabledContainerColor = colors.surfaceElevated,
            disabledContentColor = colors.textMuted,
        ),
    ) {
        Icon(
            imageVector = if (isStartMode) Icons.Outlined.PlayArrow else Icons.Outlined.Check,
            contentDescription = stringResource(if (isStartMode) R.string.picker_action_start else R.string.picker_action_add),
        )
    }
}

@Composable
private fun ClientSearchField(query: String, onQueryChanged: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    WTextField(
        value = query,
        onValueChange = onQueryChanged,
        label = stringResource(R.string.picker_search_label),
        leadingIcon = Icons.Outlined.Search,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        trailingContent = if (query.isEmpty()) null else {
            {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = stringResource(R.string.action_clear_search))
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WTheme.spacing.lg, vertical = WTheme.spacing.sm)
            .focusRequester(focusRequester),
    )
}

@Composable
private fun ClientPickerBody(uiState: ClientPickerUiState, actions: ClientPickerActions) {
    val spacing = WTheme.spacing
    // Siempre una lista desplazable para que "pull to refresh" funcione también en los estados vacíos.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        pickerContent(uiState = uiState, actions = actions)
    }
}

private fun LazyListScope.pickerContent(uiState: ClientPickerUiState, actions: ClientPickerActions) {
    if (uiState.isInitialLoading) {
        item(key = "loading") { PickerStateContainer { LoadingState(message = stringResource(R.string.picker_loading)) } }
        return
    }
    if (uiState.availableClientCount == 0 && uiState.loadErrorMessage != null) {
        item(key = "error") { PickerStateContainer { ErrorState(message = uiState.loadErrorMessage, onRetry = actions.onRefresh) } }
        return
    }
    if (uiState.availableClientCount == 0) {
        item(key = "empty") {
            val messageRes = if (uiState.mode == ClientPickerMode.ADD_TO_ROUTE) R.string.picker_add_empty_message else R.string.picker_empty_message
            PickerStateContainer {
                EmptyState(title = stringResource(R.string.picker_empty_title), message = stringResource(messageRes))
            }
        }
        return
    }
    if (uiState.clients.isEmpty()) {
        item(key = "no_results") {
            PickerStateContainer {
                EmptyState(
                    title = stringResource(R.string.picker_no_results_title),
                    message = stringResource(R.string.picker_no_results_message, uiState.searchQuery),
                    icon = Icons.Outlined.SearchOff,
                    action = if (uiState.isVendor) {
                        {
                            WButton(
                                text = "Tomar pedido a cliente nuevo",
                                onClick = actions.onTakeOrderNewClient,
                                leadingIcon = Icons.Outlined.PersonAddAlt,
                            )
                        }
                    } else null,
                )
            }
        }
        return
    }

    items(items = uiState.clients, key = { it.client.id }) { selectable ->
        SelectableClientCard(
            client = selectable.client,
            isSelected = selectable.isSelected,
            enabled = !uiState.isSubmitting,
            onToggle = { actions.onClientToggled(selectable.client.id) },
        )
    }
}

@Composable
private fun PickerStateContainer(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = WTheme.spacing.xxl)) { content() }
}
