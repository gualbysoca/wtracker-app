package com.wtracker.field.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wtracker.field.R
import com.wtracker.field.configs.AppContainer
import com.wtracker.field.controllers.ControllerFactories
import com.wtracker.field.controllers.OrderCreationEvent
import com.wtracker.field.controllers.OrderCreationUiState
import com.wtracker.field.controllers.OrderCreationViewModel
import com.wtracker.field.services.woocommerce.models.OrderStep
import com.wtracker.field.services.woocommerce.models.WooCheckoutForm
import com.wtracker.field.services.woocommerce.models.WooProduct
import com.wtracker.field.ui.components.EmptyState
import com.wtracker.field.ui.components.ErrorState
import com.wtracker.field.ui.components.InlineMessage
import com.wtracker.field.ui.components.LoadingState
import com.wtracker.field.ui.components.MessageTone
import com.wtracker.field.ui.components.WButton
import com.wtracker.field.ui.components.WButtonVariant
import com.wtracker.field.ui.components.WDialog
import com.wtracker.field.ui.components.WTextField
import com.wtracker.field.ui.components.WTopBar
import com.wtracker.field.ui.theme.WTheme
import java.util.Locale

data class OrderCreationActions(
    val onBack: () -> Unit,
    val onSearchChanged: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onCategorySelected: (String?) -> Unit,
    val onToggleCartFilter: () -> Unit,
    val onIncrementQuantity: (WooProduct) -> Unit,
    val onDecrementQuantity: (WooProduct) -> Unit,
    val onSetQuantity: (WooProduct, Int) -> Unit,
    val onRetryLoad: () -> Unit,
    val onRefreshCatalog: () -> Unit,
    val onProceedToCheckout: () -> Unit,
    val onBackToCatalog: () -> Unit,
    val onCheckoutFormChanged: (WooCheckoutForm) -> Unit,
    val onSubmitOrder: () -> Unit,
    val onDismissSuccess: () -> Unit,
    val onDismissToast: () -> Unit,
    val onUpdateLocation: () -> Unit,
)

@Composable
fun OrderCreationRoute(
    container: AppContainer,
    clientId: String,
    onBack: () -> Unit,
) {
    val viewModel: OrderCreationViewModel = viewModel(factory = ControllerFactories.orderCreation(container, clientId))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OrderCreationEvent.OrderSuccess -> {
                    // Modal de confirmación gestionado en uiState.createdOrder
                }
            }
        }
    }

    OrderCreationScreen(
        uiState = uiState,
        actions = OrderCreationActions(
            onBack = onBack,
            onSearchChanged = viewModel::onSearchQueryChanged,
            onClearSearch = { viewModel.onSearchQueryChanged("") },
            onCategorySelected = viewModel::onCategorySelected,
            onToggleCartFilter = viewModel::onToggleCartFilter,
            onIncrementQuantity = viewModel::onIncrementCartQuantity,
            onDecrementQuantity = viewModel::onDecrementCartQuantity,
            onSetQuantity = viewModel::onSetCartQuantity,
            onRetryLoad = { viewModel.loadCatalog(forceRefresh = true) },
            onRefreshCatalog = { viewModel.loadCatalog(forceRefresh = true) },
            onProceedToCheckout = viewModel::onProceedToCheckout,
            onBackToCatalog = viewModel::onBackToCatalog,
            onCheckoutFormChanged = viewModel::onCheckoutFormChanged,
            onSubmitOrder = viewModel::onSubmitOrder,
            onDismissSuccess = {
                viewModel.onDismissOrderSuccess()
                onBack()
            },
            onDismissToast = viewModel::onDismissToast,
            onUpdateLocation = viewModel::onUpdateClientLocation,
        ),
    )
}

@Composable
fun OrderCreationScreen(
    uiState: OrderCreationUiState,
    actions: OrderCreationActions,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successToast) {
        val toast = uiState.successToast
        if (toast != null) {
            snackbarHostState.showSnackbar(toast)
            actions.onDismissToast()
        }
    }

    Scaffold(
        containerColor = WTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            WTopBar(
                title = if (uiState.orderStep == OrderStep.CHECKOUT) {
                    stringResource(R.string.order_checkout_title)
                } else {
                    stringResource(R.string.order_title)
                },
                subtitle = if (uiState.orderStep == OrderStep.CHECKOUT) {
                    stringResource(R.string.order_checkout_subtitle)
                } else {
                    val client = uiState.client
                    if (client != null) {
                        val contact = client.contactName?.takeIf { it.isNotBlank() } ?: client.businessName
                        val code = client.clientCode?.takeIf { it.isNotBlank() }
                        if (code != null) "$contact - $code" else contact
                    } else if (uiState.isNewClient) {
                        "Cliente Nuevo"
                    } else {
                        null
                    }
                },
                onBack = if (uiState.orderStep == OrderStep.CHECKOUT) {
                    actions.onBackToCatalog
                } else {
                    actions.onBack
                },
                actions = {
                    if (uiState.orderStep == OrderStep.CATALOG) {
                        IconButton(
                            onClick = actions.onRefreshCatalog,
                            enabled = !uiState.isLoadingProducts,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.order_refresh_catalog),
                                tint = WTheme.colors.textPrimary,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (uiState.isVendor && uiState.orderStep == OrderStep.CATALOG && !uiState.isLoadingProducts && uiState.products.isNotEmpty()) {
                OrderCartBottomBar(uiState = uiState, actions = actions)
            }
        },
    ) { innerPadding ->
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val bottomPadding = maxOf(innerPadding.calculateBottomPadding(), imeBottom)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                    bottom = bottomPadding,
                ),
        ) {
            when {
                !uiState.isVendor -> {
                    VendorRestrictedView()
                }
                uiState.orderStep == OrderStep.CHECKOUT -> {
                    OrderCheckoutView(
                        uiState = uiState,
                        actions = actions,
                    )
                }
                uiState.isLoadingProducts -> {
                    LoadingState(message = stringResource(R.string.order_loading_catalog))
                }
                uiState.errorMessage != null && uiState.products.isEmpty() -> {
                    ErrorState(
                        message = uiState.errorMessage,
                        onRetry = actions.onRetryLoad,
                    )
                }
                uiState.products.isEmpty() -> {
                    EmptyState(
                        title = stringResource(R.string.order_empty_catalog_title),
                        message = stringResource(R.string.order_empty_catalog_message),
                    )
                }
                else -> {
                    OrderCatalogTableView(uiState = uiState, actions = actions)
                }
            }
        }
    }

    uiState.createdOrder?.let { createdOrder ->
        WDialog(
            title = stringResource(R.string.order_success_title),
            icon = Icons.Outlined.CheckCircle,
            tone = WTheme.colors.statusVisited,
            confirmText = stringResource(R.string.order_accept_action),
            dismissText = "",
            confirmVariant = WButtonVariant.SUCCESS,
            onConfirm = actions.onDismissSuccess,
            onDismiss = actions.onDismissSuccess,
        ) {
            Text(
                text = stringResource(R.string.order_success_message, createdOrder.orderNumber),
                style = WTheme.typography.body,
                color = WTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(WTheme.spacing.xs))
            Text(
                text = "${stringResource(R.string.order_total_label)} ${createdOrder.currency} ${createdOrder.total}",
                style = WTheme.typography.bodyStrong,
                color = WTheme.colors.accent,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(WTheme.spacing.xs))
            Text(
                text = "Se guardó el comprobante como evidencia y se actualizaron las notas de la tarea.",
                style = WTheme.typography.caption,
                color = WTheme.colors.statusVisited,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun VendorRestrictedView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WTheme.spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(WTheme.sizes.iconXl)
                .background(WTheme.colors.danger.copy(alpha = WTheme.alpha.subtle), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = WTheme.colors.danger,
                modifier = Modifier.size(WTheme.sizes.iconLg),
            )
        }
        Spacer(Modifier.height(WTheme.spacing.lg))
        Text(
            text = stringResource(R.string.order_vendor_only_hint),
            style = WTheme.typography.body,
            color = WTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OrderCatalogTableView(
    uiState: OrderCreationUiState,
    actions: OrderCreationActions,
) {
    val colors = WTheme.colors
    val spacing = WTheme.spacing

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Buscador exacto con icono integrado en el extremo derecho
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
        ) {
            Surface(
                shape = RoundedCornerShape(WTheme.radius.sm),
                color = colors.surface,
                border = BorderStroke(WTheme.sizes.borderThin, colors.border),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = uiState.searchQuery,
                        onValueChange = actions.onSearchChanged,
                        singleLine = true,
                        textStyle = WTheme.typography.body.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        decorationBox = { innerTextField ->
                            if (uiState.searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.order_search_placeholder),
                                    style = WTheme.typography.body,
                                    color = colors.textMuted,
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.weight(1f).padding(start = spacing.xs),
                    )

                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = actions.onClearSearch, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(WTheme.sizes.iconSm),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(colors.surfaceElevated, RoundedCornerShape(WTheme.radius.sm))
                            .border(BorderStroke(WTheme.sizes.borderThin, colors.border), RoundedCornerShape(WTheme.radius.sm)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(WTheme.sizes.iconSm),
                        )
                    }
                }
            }
        }

        // 2. Filtro horizontal de categorías + Botón dinámico "Carrito" al lado de "Todos"
        val hasCategories = uiState.availableCategories.isNotEmpty()
        val hasCartItems = uiState.draft.items.isNotEmpty()

        if (hasCategories || hasCartItems) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.sm),
                contentPadding = PaddingValues(horizontal = spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                // Botón "Todos"
                item {
                    CategoryFilterChip(
                        name = stringResource(R.string.order_category_all),
                        isSelected = uiState.selectedCategory == null && !uiState.isFilterCartOnly,
                        onClick = { actions.onCategorySelected(null) },
                    )
                }

                // Botón dinámico "Carrito" (se muestra al lado de "Todos" si hay por lo menos un producto seleccionado)
                if (hasCartItems) {
                    item {
                        CategoryFilterChip(
                            name = "${stringResource(R.string.order_filter_cart)} (${uiState.totalItemsCount})",
                            isSelected = uiState.isFilterCartOnly,
                            onClick = actions.onToggleCartFilter,
                        )
                    }
                }

                items(uiState.availableCategories) { category ->
                    CategoryFilterChip(
                        name = category,
                        isSelected = !uiState.isFilterCartOnly && uiState.selectedCategory.equals(category, ignoreCase = true),
                        onClick = { actions.onCategorySelected(category) },
                    )
                }
            }
        }

        // 3. Alertas de validación/error
        AnimatedVisibility(
            visible = uiState.validationError != null || uiState.errorMessage != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Box(modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs)) {
                uiState.validationError?.let { InlineMessage(message = it, tone = MessageTone.WARNING) }
                uiState.errorMessage?.let { InlineMessage(message = it, tone = MessageTone.ERROR) }
            }
        }

        // 4. Encabezado de la tabla: "Productos" y "Cant." con divisor vertical
        Surface(
            color = colors.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.order_header_products),
                        style = WTheme.typography.bodyStrong.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )

                    Text(
                        text = stringResource(R.string.order_header_quantity),
                        style = WTheme.typography.bodyStrong.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(64.dp),
                    )

                    Spacer(Modifier.width(spacing.md))

                    // Espacio correspondiente a la columna de botones (+ / -)
                    Box(modifier = Modifier.width(92.dp))
                }
                HorizontalDivider(thickness = WTheme.sizes.borderThin, color = colors.border)
            }
        }

        val filteredProducts = uiState.filteredProducts
        if (filteredProducts.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.order_empty_search_title),
                message = stringResource(R.string.order_empty_search_message),
                modifier = Modifier.weight(1f),
            )
        } else {
            // 5. Lista tabular de productos
            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(filteredProducts, key = { _, product -> product.id }) { _, product ->
                    val inCartQty = uiState.getCartQuantity(product.id)

                    ProductRowItem(
                        product = product,
                        inCartQuantity = inCartQty,
                        onQuantityChanged = { newQty -> actions.onSetQuantity(product, newQty) },
                        onIncrement = { actions.onIncrementQuantity(product) },
                        onDecrement = { actions.onDecrementQuantity(product) },
                    )
                    HorizontalDivider(thickness = WTheme.sizes.borderThin, color = colors.border)
                }
            }
        }
    }
}

@Composable
private fun ProductRowItem(
    product: WooProduct,
    inCartQuantity: Int,
    onQuantityChanged: (Int) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val colors = WTheme.colors
    val spacing = WTheme.spacing
    var isEditingQuantity by remember { mutableStateOf(false) }

    val titleColor = if (colors.background == Color.White || colors.background == Color(0xFFF9FAFC)) {
        Color(0xFFB94A3E)
    } else {
        colors.accent
    }

    val greenButtonColor = Color(0xFF388E3C)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Columna Izquierda: Información del producto (Nombre y Precio)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.md, end = spacing.sm),
        ) {
            Text(
                text = product.name,
                style = WTheme.typography.subtitle.copy(fontWeight = FontWeight.Bold),
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(spacing.xxs))
            Text(
                text = String.format(Locale.US, "Bs.%.2f", product.price).replace('.', ','),
                style = WTheme.typography.body,
                color = colors.textPrimary,
            )
        }

        // Columna Central: Cuadro de Cantidad agregada al carrito (Cant.)
        val hasQuantity = inCartQuantity > 0
        Box(
            modifier = Modifier
                .width(54.dp)
                .height(38.dp)
                .background(
                    if (hasQuantity) colors.accent.copy(alpha = WTheme.alpha.subtle) else colors.background,
                    RoundedCornerShape(WTheme.radius.sm)
                )
                .border(
                    BorderStroke(
                        WTheme.sizes.borderThin,
                        if (hasQuantity) colors.accent else colors.border
                    ),
                    RoundedCornerShape(WTheme.radius.sm)
                )
                .clickable { isEditingQuantity = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = inCartQuantity.toString(),
                style = if (hasQuantity) WTheme.typography.bodyStrong.copy(fontWeight = FontWeight.Bold) else WTheme.typography.body,
                color = if (hasQuantity) colors.accent else colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }

        // Divisor vertical sutil
        Box(
            modifier = Modifier
                .padding(horizontal = spacing.xs)
                .width(1.dp)
                .height(44.dp)
                .background(colors.border),
        )

        // Columna Derecha: Botón (-) habilitado si ya fue agregado, y botón (+) verde para sumar
        Row(
            modifier = Modifier.padding(end = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            if (hasQuantity) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(colors.danger.copy(alpha = WTheme.alpha.subtle), RoundedCornerShape(WTheme.radius.sm))
                        .border(BorderStroke(WTheme.sizes.borderThin, colors.danger.copy(alpha = 0.5f)), RoundedCornerShape(WTheme.radius.sm))
                        .clickable(onClick = onDecrement),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = "Quitar uno del pedido",
                        tint = colors.danger,
                        modifier = Modifier.size(WTheme.sizes.iconSm),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(greenButtonColor, RoundedCornerShape(WTheme.radius.sm))
                    .clickable(enabled = product.inStock, onClick = onIncrement),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.action_take_order),
                    tint = Color.White,
                    modifier = Modifier.size(WTheme.sizes.iconSm),
                )
            }
        }
    }

    if (isEditingQuantity) {
        QuantityPickerDialog(
            currentQuantity = inCartQuantity,
            productName = product.name,
            onDismiss = { isEditingQuantity = false },
            onConfirm = { newQty ->
                onQuantityChanged(newQty)
                isEditingQuantity = false
            },
        )
    }
}

@Composable
private fun QuantityPickerDialog(
    currentQuantity: Int,
    productName: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var tempQuantity by remember { mutableStateOf(if (currentQuantity > 0) currentQuantity.toString() else "1") }

    WDialog(
        title = "Definir cantidad",
        icon = Icons.Outlined.ShoppingCart,
        tone = WTheme.colors.accent,
        confirmText = "Aceptar",
        confirmVariant = WButtonVariant.PRIMARY,
        onConfirm = {
            val parsed = tempQuantity.toIntOrNull()?.coerceAtLeast(0) ?: 0
            onConfirm(parsed)
        },
        onDismiss = onDismiss,
    ) {
        Text(
            text = productName,
            style = WTheme.typography.bodyStrong,
            color = WTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(WTheme.spacing.md))
        WTextField(
            value = tempQuantity,
            onValueChange = { input ->
                if (input.all { it.isDigit() } && input.length <= 4) {
                    tempQuantity = input
                }
            },
            label = "Cantidad (0 para quitar)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                val parsed = tempQuantity.toIntOrNull()?.coerceAtLeast(0) ?: 0
                onConfirm(parsed)
            }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CategoryFilterChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = WTheme.colors
    val spacing = WTheme.spacing

    val backgroundColor = if (isSelected) colors.accent else colors.surfaceElevated
    val contentColor = if (isSelected) colors.accentContent else colors.textSecondary
    val borderColor = if (isSelected) colors.accent else colors.border

    Surface(
        shape = RoundedCornerShape(WTheme.radius.sm),
        color = backgroundColor,
        border = BorderStroke(WTheme.sizes.borderThin, borderColor),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = name,
                style = WTheme.typography.caption.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                ),
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun OrderCartBottomBar(
    uiState: OrderCreationUiState,
    actions: OrderCreationActions,
) {
    val colors = WTheme.colors
    val spacing = WTheme.spacing

    Surface(
        shape = RoundedCornerShape(topStart = WTheme.radius.xl, topEnd = WTheme.radius.xl),
        color = colors.surface,
        border = BorderStroke(WTheme.sizes.borderThin, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.order_items_count, uiState.totalItemsCount),
                    style = WTheme.typography.caption,
                    color = colors.textSecondary,
                )
                Text(
                    text = String.format(Locale.US, "Bs. %.2f", uiState.totalAmount).replace('.', ','),
                    style = WTheme.typography.title,
                    color = colors.accent,
                )
            }

            WButton(
                text = stringResource(R.string.order_checkout_action),
                onClick = actions.onProceedToCheckout,
                isLoading = false,
                enabled = uiState.canProceedToCheckout,
                leadingIcon = Icons.Outlined.ShoppingCart,
                modifier = Modifier.width(180.dp),
            )
        }
    }
}
