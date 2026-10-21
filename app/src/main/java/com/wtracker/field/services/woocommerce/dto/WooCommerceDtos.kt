package com.wtracker.field.services.woocommerce.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WooImageDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("src") val src: String? = null,
    @SerialName("name") val name: String? = null,
)

@Serializable
data class WooCategoryDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("slug") val slug: String? = null,
)

@Serializable
data class WooProductDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("slug") val slug: String? = null,
    @SerialName("permalink") val permalink: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("short_description") val shortDescription: String? = null,
    @SerialName("sku") val sku: String? = null,
    @SerialName("price") val price: String? = null,
    @SerialName("regular_price") val regularPrice: String? = null,
    @SerialName("sale_price") val salePrice: String? = null,
    @SerialName("manage_stock") val manageStock: Boolean? = null,
    @SerialName("stock_quantity") val stockQuantity: Int? = null,
    @SerialName("stock_status") val stockStatus: String? = null,
    @SerialName("images") val images: List<WooImageDto> = emptyList(),
    @SerialName("categories") val categories: List<WooCategoryDto> = emptyList(),
)

@Serializable
data class WooBillingDto(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("company") val company: String? = null,
    @SerialName("address_1") val address1: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("country") val country: String? = null,
)

@Serializable
data class WooShippingDto(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("company") val company: String? = null,
    @SerialName("address_1") val address1: String? = null,
    @SerialName("country") val country: String? = null,
)

@Serializable
data class WooLineItemRequestDto(
    @SerialName("product_id") val productId: Long,
    @SerialName("quantity") val quantity: Int,
)

@Serializable
data class WooMetaDataDto(
    @SerialName("key") val key: String,
    @SerialName("value") val value: String,
)

@Serializable
data class WooCustomerDto(
    @SerialName("id") val id: Long,
    @SerialName("email") val email: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("role") val role: String? = null,
)

@Serializable
data class WooCreateCustomerRequestDto(
    @SerialName("email") val email: String,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("username") val username: String? = null,
)

@Serializable
data class WooOrderRequestDto(
    @SerialName("customer_id") val customerId: Long? = null,
    @SerialName("payment_method") val paymentMethod: String = "cod",
    @SerialName("payment_method_title") val paymentMethodTitle: String = "Pago contra entrega",
    @SerialName("set_paid") val setPaid: Boolean = false,
    @SerialName("status") val status: String = "pending",
    @SerialName("created_via") val createdVia: String = "App móvil",
    @SerialName("billing") val billing: WooBillingDto? = null,
    @SerialName("shipping") val shipping: WooShippingDto? = null,
    @SerialName("line_items") val lineItems: List<WooLineItemRequestDto>,
    @SerialName("customer_note") val customerNote: String? = null,
    @SerialName("meta_data") val metaData: List<WooMetaDataDto> = emptyList(),
)

@Serializable
data class WooOrderResponseDto(
    @SerialName("id") val id: Long,
    @SerialName("number") val number: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("total") val total: String? = null,
    @SerialName("currency") val currency: String? = null,
    @SerialName("date_created") val dateCreated: String? = null,
)
