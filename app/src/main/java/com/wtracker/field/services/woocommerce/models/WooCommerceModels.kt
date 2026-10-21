package com.wtracker.field.services.woocommerce.models

data class WooProduct(
    val id: Long,
    val name: String,
    val price: Double,
    val formattedPrice: String,
    val sku: String?,
    val stockQuantity: Int?,
    val inStock: Boolean,
    val categoryName: String?,
    val categories: List<String> = emptyList(),
    val imageUrl: String?,
)

data class WooOrderItem(
    val product: WooProduct,
    val quantity: Int,
) {
    val lineTotal: Double get() = product.price * quantity
}

data class WooOrderDraft(
    val items: List<WooOrderItem> = emptyList(),
    val notes: String = "",
) {
    val total: Double get() = items.sumOf { it.lineTotal }
    val totalItemsCount: Int get() = items.sumOf { it.quantity }
    val isEmpty: Boolean get() = items.isEmpty()
}

data class WooCreatedOrder(
    val id: Long,
    val orderNumber: String,
    val status: String,
    val total: String,
    val currency: String,
    val createdAt: String,
)

enum class OrderStep {
    CATALOG,
    CHECKOUT,
}

data class WooCheckoutForm(
    val clienteNuevo: String = "No",
    val codigoCliente: String = "",
    val nombreCliente: String = "",
    val domicilio: String = "",
    val codigoPais: String = "+591",
    val telefono: String = "",
    val pais: String = "Bolivia",
    val direccionEntrega: String = "",
    val mercado: String = "",
    val nroPuesto: String = "",
    val nroPedido: String = "",
    val canalVerde: String = "No",
    val tipoVenta: String = "Al contado",
    val fechaEntrega: String = "",
    val horaEntrega: String = "",
    val nombreVendedor: String = "",
    val latitud: String = "",
    val longitud: String = "",
    val saldoAnterior: String = "",
) {
    val telefonoCompleto: String
        get() = if (telefono.isBlank()) "" else "$codigoPais ${telefono.trim()}".trim()
}

