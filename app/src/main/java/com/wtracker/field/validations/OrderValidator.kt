package com.wtracker.field.validations

import com.wtracker.field.configs.WooCommerceConfig
import com.wtracker.field.services.woocommerce.models.WooCheckoutForm
import com.wtracker.field.services.woocommerce.models.WooOrderDraft

object OrderValidator {

    fun validateVendorRole(role: String): ValidationResult {
        if (role.equals(WooCommerceConfig.REQUIRED_VENDOR_ROLE, ignoreCase = true)) {
            return ValidationResult.Valid
        }
        return ValidationResult.Invalid("La toma de pedidos está reservada exclusivamente para el rol 'vendedor'.")
    }

    fun validateDraft(draft: WooOrderDraft): ValidationResult {
        if (draft.isEmpty) {
            return ValidationResult.Invalid("El pedido debe contener al menos un producto.")
        }

        val hasInvalidQuantity = draft.items.any { it.quantity <= 0 }
        if (hasInvalidQuantity) {
            return ValidationResult.Invalid("Todos los productos del pedido deben tener una cantidad mayor a cero.")
        }

        for (item in draft.items) {
            val stock = item.product.stockQuantity
            if (stock != null && item.quantity > stock) {
                return ValidationResult.Invalid(
                    "El producto '${item.product.name}' solo tiene $stock unidades disponibles en inventario."
                )
            }
        }

        return ValidationResult.Valid
    }

    fun isCheckoutFormComplete(form: WooCheckoutForm): Boolean {
        if (form.clienteNuevo.isBlank()) return false
        val isNewClient = form.clienteNuevo.equals("Si", ignoreCase = true)
        if (!isNewClient && (form.codigoCliente.isBlank() || form.codigoCliente.toLongOrNull() == null)) return false
        if (isNewClient && form.codigoCliente.isNotBlank() && form.codigoCliente.toLongOrNull() == null) return false
        if (form.nombreCliente.isBlank()) return false
        if (form.telefono.isBlank()) return false
        if (form.direccionEntrega.isBlank()) return false
        if (form.nroPedido.isBlank() || form.nroPedido.toLongOrNull() == null) return false
        if (form.canalVerde.isBlank()) return false
        if (form.tipoVenta.isBlank()) return false
        if (form.nombreVendedor.isBlank()) return false
        if (form.nroPuesto.isNotBlank() && form.nroPuesto.toLongOrNull() == null) return false
        if (form.saldoAnterior.isNotBlank() && form.saldoAnterior.toDoubleOrNull() == null) return false
        return true
    }

    fun validateCheckout(form: WooCheckoutForm): ValidationResult {
        if (form.clienteNuevo.isBlank()) {
            return ValidationResult.Invalid("El campo Cliente nuevo es obligatorio.")
        }
        val isNewClient = form.clienteNuevo.equals("Si", ignoreCase = true)
        if (!isNewClient && form.codigoCliente.isBlank()) {
            return ValidationResult.Invalid("El Código de Cliente es obligatorio.")
        }
        if (form.codigoCliente.isNotBlank() && form.codigoCliente.toLongOrNull() == null) {
            return ValidationResult.Invalid("El Código de Cliente debe ser un número válido.")
        }
        if (form.nombreCliente.isBlank()) {
            return ValidationResult.Invalid("El nombre del Cliente es obligatorio.")
        }
        if (form.telefono.isBlank()) {
            return ValidationResult.Invalid("El Teléfono del cliente es obligatorio.")
        }
        if (form.direccionEntrega.isBlank()) {
            return ValidationResult.Invalid("La Dirección de Entrega es obligatoria.")
        }
        if (form.nroPedido.isBlank()) {
            return ValidationResult.Invalid("El Nro. de Pedido es obligatorio.")
        }
        if (form.nroPedido.toLongOrNull() == null) {
            return ValidationResult.Invalid("El Nro. de Pedido debe ser un número válido.")
        }
        if (form.canalVerde.isBlank()) {
            return ValidationResult.Invalid("El campo Canal verde es obligatorio.")
        }
        if (form.tipoVenta.isBlank()) {
            return ValidationResult.Invalid("El Tipo de Venta es obligatorio.")
        }
        if (form.nombreVendedor.isBlank()) {
            return ValidationResult.Invalid("El Nombre del Vendedor es obligatorio.")
        }
        if (form.nroPuesto.isNotBlank() && form.nroPuesto.toLongOrNull() == null) {
            return ValidationResult.Invalid("El Puesto Nro. debe ser numérico.")
        }
        if (form.saldoAnterior.isNotBlank() && form.saldoAnterior.toDoubleOrNull() == null) {
            return ValidationResult.Invalid("El Saldo debe ser un número válido.")
        }
        return ValidationResult.Valid
    }
}
