package com.wtracker.field.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wtracker.field.R
import com.wtracker.field.controllers.OrderCreationUiState
import com.wtracker.field.services.woocommerce.models.WooCheckoutForm
import com.wtracker.field.ui.components.CountryCodePhoneField
import com.wtracker.field.ui.components.InlineMessage
import com.wtracker.field.ui.components.MessageTone
import com.wtracker.field.ui.components.WButton
import com.wtracker.field.ui.components.WButtonVariant
import com.wtracker.field.ui.components.WTextField
import com.wtracker.field.ui.theme.WTheme
import java.util.Calendar
import java.util.Locale

@Composable
fun OrderCheckoutView(
    uiState: OrderCreationUiState,
    actions: OrderCreationActions,
    modifier: Modifier = Modifier,
) {
    val colors = WTheme.colors
    val spacing = WTheme.spacing
    val form = uiState.checkoutForm
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    val codigoClienteRequester = remember { FocusRequester() }
    val nombreClienteRequester = remember { FocusRequester() }
    val telefonoRequester = remember { FocusRequester() }
    val direccionEntregaRequester = remember { FocusRequester() }
    val nroPedidoRequester = remember { FocusRequester() }

    fun focusFirstEmptyRequiredField(): Boolean {
        return when {
            uiState.isClientCodeEditable && !uiState.isNewClient && form.codigoCliente.isBlank() -> {
                try { codigoClienteRequester.requestFocus() } catch (_: Exception) {}
                true
            }
            form.nombreCliente.isBlank() -> {
                try { nombreClienteRequester.requestFocus() } catch (_: Exception) {}
                true
            }
            form.telefono.isBlank() -> {
                try { telefonoRequester.requestFocus() } catch (_: Exception) {}
                true
            }
            form.direccionEntrega.isBlank() -> {
                try { direccionEntregaRequester.requestFocus() } catch (_: Exception) {}
                true
            }
            form.nroPedido.isBlank() -> {
                try { nroPedidoRequester.requestFocus() } catch (_: Exception) {}
                true
            }
            else -> false
        }
    }

    LaunchedEffect(Unit) {
        delay(200)
        focusFirstEmptyRequiredField()
    }

    fun showDatePicker() {
        val picker = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                actions.onCheckoutFormChanged(form.copy(fechaEntrega = formatted))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        picker.show()
    }

    fun showTimePicker() {
        val picker = TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val formatted = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                actions.onCheckoutFormChanged(form.copy(horaEntrega = formatted))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        picker.show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            // Alertas de validación
            AnimatedVisibility(
                visible = uiState.validationError != null || uiState.errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    uiState.validationError?.let { InlineMessage(message = it, tone = MessageTone.WARNING) }
                    uiState.errorMessage?.let { InlineMessage(message = it, tone = MessageTone.ERROR) }
                }
            }

            // Sección 1: Datos del Cliente
            CheckoutCard(
                title = "1. Datos del Cliente",
                icon = Icons.Outlined.Person,
            ) {
                // Cliente nuevo (Select Si / No)
                SegmentedPicker(
                    label = "Cliente nuevo*",
                    options = listOf("Si", "No"),
                    selectedOption = form.clienteNuevo,
                    onOptionSelected = { actions.onCheckoutFormChanged(form.copy(clienteNuevo = it)) },
                    enabled = uiState.isNewClientEditable,
                )

                Spacer(Modifier.height(spacing.xs))

                // Código de Cliente* (number)
                WTextField(
                    value = form.codigoCliente,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) actions.onCheckoutFormChanged(form.copy(codigoCliente = input))
                    },
                    label = if (uiState.isNewClient) "Código de Cliente" else "Código de Cliente*",
                    focusRequester = codigoClienteRequester,
                    enabled = uiState.isClientCodeEditable,
                    leadingIcon = Icons.Outlined.Badge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

                // Cliente* (nombre)
                WTextField(
                    value = form.nombreCliente,
                    onValueChange = { actions.onCheckoutFormChanged(form.copy(nombreCliente = it)) },
                    label = "Cliente*",
                    focusRequester = nombreClienteRequester,
                    leadingIcon = Icons.Outlined.Person,
                    singleLine = true,
                )

                // Teléfono* con selector de código de país
                CountryCodePhoneField(
                    dialCode = form.codigoPais,
                    onDialCodeChange = { actions.onCheckoutFormChanged(form.copy(codigoPais = it)) },
                    phoneNumber = form.telefono,
                    onPhoneNumberChange = { actions.onCheckoutFormChanged(form.copy(telefono = it)) },
                    label = "Teléfono*",
                    focusRequester = telefonoRequester,
                )
            }

            // Sección 2: Dirección y Entrega
            CheckoutCard(
                title = "2. Dirección y Entrega",
                icon = Icons.Outlined.LocalShipping,
            ) {
                // Dirección de Entrega*
                WTextField(
                    value = form.direccionEntrega,
                    onValueChange = { actions.onCheckoutFormChanged(form.copy(direccionEntrega = it)) },
                    label = "Dirección de Entrega*",
                    focusRequester = direccionEntregaRequester,
                    leadingIcon = Icons.Outlined.Place,
                    singleLine = true,
                )

                // Dirección (Referencial) (opcional)
                WTextField(
                    value = form.domicilio,
                    onValueChange = { actions.onCheckoutFormChanged(form.copy(domicilio = it)) },
                    label = "Dirección (Referencial) (opcional)",
                    leadingIcon = Icons.Outlined.Place,
                    singleLine = true,
                )

                // Mercado (opcional)
                WTextField(
                    value = form.mercado,
                    onValueChange = { actions.onCheckoutFormChanged(form.copy(mercado = it)) },
                    label = "Mercado (opcional)",
                    leadingIcon = Icons.Outlined.Storefront,
                    singleLine = true,
                )

                // Puesto Nro. (opcional, number)
                WTextField(
                    value = form.nroPuesto,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) actions.onCheckoutFormChanged(form.copy(nroPuesto = input))
                    },
                    label = "Puesto Nro. (opcional)",
                    leadingIcon = Icons.Outlined.ReceiptLong,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

                // Coordenadas geográficas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    WTextField(
                        value = form.latitud,
                        onValueChange = { actions.onCheckoutFormChanged(form.copy(latitud = it)) },
                        label = "Latitud",
                        leadingIcon = Icons.Outlined.Place,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    WTextField(
                        value = form.longitud,
                        onValueChange = { actions.onCheckoutFormChanged(form.copy(longitud = it)) },
                        label = "Longitud",
                        leadingIcon = Icons.Outlined.Place,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                WButton(
                    text = if (uiState.isUpdatingLocation) "Obteniendo ubicación GPS..." else "Actualizar ubicación GPS",
                    leadingIcon = Icons.Outlined.MyLocation,
                    onClick = actions.onUpdateLocation,
                    isLoading = uiState.isUpdatingLocation,
                    variant = WButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )

                // País / Región (no editable)
                WTextField(
                    value = form.pais,
                    onValueChange = {},
                    label = "País / Región",
                    enabled = false,
                    leadingIcon = Icons.Outlined.Lock,
                    singleLine = true,
                )

                // Fecha y Hora de entrega (opcionales)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    // Datepicker selector
                    PickerField(
                        label = "Fecha entrega",
                        value = form.fechaEntrega.ifBlank { "Seleccionar" },
                        icon = Icons.Outlined.CalendarMonth,
                        hasValue = form.fechaEntrega.isNotBlank(),
                        onClick = { showDatePicker() },
                        onClear = { actions.onCheckoutFormChanged(form.copy(fechaEntrega = "")) },
                        modifier = Modifier.weight(1f),
                    )

                    // Hourpicker selector
                    PickerField(
                        label = "Hora entrega",
                        value = form.horaEntrega.ifBlank { "Seleccionar" },
                        icon = Icons.Outlined.AccessTime,
                        hasValue = form.horaEntrega.isNotBlank(),
                        onClick = { showTimePicker() },
                        onClear = { actions.onCheckoutFormChanged(form.copy(horaEntrega = "")) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Sección 3: Condiciones de Venta
            CheckoutCard(
                title = "3. Condiciones de Venta",
                icon = Icons.Outlined.Description,
            ) {
                // Nro. de Pedido* (number)
                WTextField(
                    value = form.nroPedido,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) actions.onCheckoutFormChanged(form.copy(nroPedido = input))
                    },
                    label = "Nro. de Pedido*",
                    focusRequester = nroPedidoRequester,
                    leadingIcon = Icons.Outlined.ReceiptLong,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

                // Canal verde* (Select Si / No)
                SegmentedPicker(
                    label = "Canal verde*",
                    options = listOf("Si", "No"),
                    selectedOption = form.canalVerde,
                    onOptionSelected = { actions.onCheckoutFormChanged(form.copy(canalVerde = it)) },
                )

                // Tipo de Venta* (Select Al contado / Al credito)
                SegmentedPicker(
                    label = "Tipo de Venta*",
                    options = listOf("Al contado", "Al credito"),
                    selectedOption = form.tipoVenta,
                    onOptionSelected = { actions.onCheckoutFormChanged(form.copy(tipoVenta = it)) },
                )

                // Saldo (Bs.) (opcional, number)
                WTextField(
                    value = form.saldoAnterior,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("""^\d*(\.\d*)?$"""))) {
                            actions.onCheckoutFormChanged(form.copy(saldoAnterior = input))
                        }
                    },
                    label = "Saldo (Bs.) (opcional)",
                    leadingIcon = Icons.Outlined.ReceiptLong,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )

                // Nombre del Vendedor* (deshabilitado para edición)
                WTextField(
                    value = form.nombreVendedor,
                    onValueChange = {},
                    label = "Nombre del Vendedor*",
                    enabled = false,
                    leadingIcon = Icons.Outlined.Lock,
                    singleLine = true,
                )
            }

            // Sección 4: Resumen de Productos Seleccionados
            CheckoutCard(
                title = "4. Resumen del Pedido",
                icon = Icons.Outlined.ShoppingCart,
            ) {
                uiState.draft.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.xxs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${item.quantity}x ${item.product.name}",
                            style = WTheme.typography.body,
                            color = colors.textPrimary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = String.format(Locale.US, "Bs. %.2f", item.lineTotal).replace('.', ','),
                            style = WTheme.typography.bodyStrong,
                            color = colors.textPrimary,
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = spacing.xs), color = colors.border)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "TOTAL A PAGAR",
                        style = WTheme.typography.bodyStrong.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                    )
                    Text(
                        text = String.format(Locale.US, "Bs. %.2f", uiState.totalAmount).replace('.', ','),
                        style = WTheme.typography.title.copy(fontWeight = FontWeight.Bold),
                        color = colors.accent,
                    )
                }
            }

            Spacer(Modifier.height(spacing.sm))
        }

        // Barra inferior fija con botones de acción
        Surface(
            shape = RoundedCornerShape(topStart = WTheme.radius.xl, topEnd = WTheme.radius.xl),
            color = colors.surface,
            border = BorderStroke(WTheme.sizes.borderThin, colors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                WButton(
                    text = stringResource(R.string.order_checkout_submit),
                    onClick = actions.onSubmitOrder,
                    onClickDisabled = { focusFirstEmptyRequiredField() },
                    isLoading = uiState.isSubmitting,
                    enabled = uiState.canSubmitOrder,
                    leadingIcon = Icons.Outlined.CheckCircle,
                    variant = WButtonVariant.SUCCESS,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CheckoutCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    val colors = WTheme.colors
    val spacing = WTheme.spacing

    Surface(
        shape = RoundedCornerShape(WTheme.radius.md),
        color = colors.surface,
        border = BorderStroke(WTheme.sizes.borderThin, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(WTheme.sizes.iconSm),
                )
                Text(
                    text = title,
                    style = WTheme.typography.subtitle.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )
            }
            HorizontalDivider(color = colors.border, thickness = WTheme.sizes.borderThin)
            content()
        }
    }
}

@Composable
private fun SegmentedPicker(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    enabled: Boolean = true,
) {
    val colors = WTheme.colors
    val spacing = WTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Text(
            text = label,
            style = WTheme.typography.caption,
            color = colors.textSecondary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceElevated, RoundedCornerShape(WTheme.radius.sm))
                .border(BorderStroke(WTheme.sizes.borderThin, colors.border), RoundedCornerShape(WTheme.radius.sm))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            options.forEach { option ->
                val isSelected = option.equals(selectedOption, ignoreCase = true)
                val bgColor = if (isSelected) {
                    if (enabled) colors.accent else colors.accent.copy(alpha = 0.45f)
                } else Color.Transparent
                val textColor = if (isSelected) {
                    if (enabled) colors.accentContent else colors.textPrimary.copy(alpha = 0.7f)
                } else colors.textMuted

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(bgColor, RoundedCornerShape(WTheme.radius.sm - 2.dp))
                        .then(
                            if (enabled) {
                                Modifier.clickable { onOptionSelected(option) }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = WTheme.typography.bodyStrong.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = textColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerField(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    hasValue: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WTheme.colors
    val spacing = WTheme.spacing

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = label,
            style = WTheme.typography.caption,
            color = colors.textSecondary,
        )
        Surface(
            shape = RoundedCornerShape(WTheme.radius.md),
            color = colors.surface,
            border = BorderStroke(WTheme.sizes.borderThin, if (hasValue) colors.accent else colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (hasValue) colors.accent else colors.textMuted,
                        modifier = Modifier.size(WTheme.sizes.iconSm),
                    )
                    Text(
                        text = value,
                        style = WTheme.typography.body,
                        color = if (hasValue) colors.textPrimary else colors.textMuted,
                        maxLines = 1,
                    )
                }

                if (hasValue) {
                    IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "Limpiar",
                            tint = colors.textMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
