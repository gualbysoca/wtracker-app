package com.wtracker.field.services.woocommerce

import com.wtracker.field.configs.WooCommerceConfig
import com.wtracker.field.services.models.FieldClient
import com.wtracker.field.services.models.UserSession
import com.wtracker.field.services.woocommerce.dto.WooBillingDto
import com.wtracker.field.services.woocommerce.dto.WooCreateCustomerRequestDto
import com.wtracker.field.services.woocommerce.dto.WooLineItemRequestDto
import com.wtracker.field.services.woocommerce.dto.WooMetaDataDto
import com.wtracker.field.services.woocommerce.dto.WooOrderRequestDto
import com.wtracker.field.services.woocommerce.dto.WooProductDto
import com.wtracker.field.services.woocommerce.dto.WooShippingDto
import com.wtracker.field.services.woocommerce.models.WooCheckoutForm
import com.wtracker.field.services.woocommerce.models.WooCreatedOrder
import com.wtracker.field.services.woocommerce.models.WooOrderDraft
import com.wtracker.field.services.woocommerce.models.WooProduct
import com.wtracker.field.utils.AppError
import com.wtracker.field.utils.AppResult
import com.wtracker.field.utils.flatMap
import com.wtracker.field.utils.map
import com.wtracker.field.validations.OrderValidator
import com.wtracker.field.validations.ValidationResult
import java.util.Locale

interface WooCommerceService {
    suspend fun getProducts(
        forceRefresh: Boolean = false,
        searchQuery: String? = null,
    ): AppResult<List<WooProduct>>

    suspend fun submitOrder(
        draft: WooOrderDraft,
        checkoutForm: WooCheckoutForm,
        client: FieldClient,
        vendorSession: UserSession,
    ): AppResult<WooCreatedOrder>
}

class DefaultWooCommerceService(
    private val client: WooCommerceClient,
) : WooCommerceService {

    companion object {
        const val CATALOG_CACHE_TTL_MS = 60 * 60 * 1000L // 1 hora de caché en memoria
    }

    private var cachedProducts: List<WooProduct>? = null
    private var lastCatalogFetchTimeMillis: Long = 0L

    override suspend fun getProducts(
        forceRefresh: Boolean,
        searchQuery: String?,
    ): AppResult<List<WooProduct>> {
        val now = System.currentTimeMillis()
        val isCacheValid = cachedProducts != null && (now - lastCatalogFetchTimeMillis < CATALOG_CACHE_TTL_MS)

        if (!forceRefresh && searchQuery.isNullOrBlank() && isCacheValid) {
            return AppResult.Success(cachedProducts!!)
        }

        val result = client.fetchProducts(searchQuery)
        return result.map { dtoList ->
            val domainProducts = dtoList.map { it.toDomain() }
            if (searchQuery.isNullOrBlank()) {
                cachedProducts = domainProducts
                lastCatalogFetchTimeMillis = now
            }
            domainProducts
        }
    }

    override suspend fun submitOrder(
        draft: WooOrderDraft,
        checkoutForm: WooCheckoutForm,
        client: FieldClient,
        vendorSession: UserSession,
    ): AppResult<WooCreatedOrder> {
        // 1. Validar rol del vendedor (Early return)
        val roleValidation = OrderValidator.validateVendorRole(vendorSession.role)
        if (roleValidation is ValidationResult.Invalid) {
            return AppResult.Failure(AppError.Forbidden(roleValidation.message))
        }

        // 2. Validar borrador del pedido (Early return)
        val draftValidation = OrderValidator.validateDraft(draft)
        if (draftValidation is ValidationResult.Invalid) {
            return AppResult.Failure(AppError.Validation(draftValidation.message))
        }

        // 3. Validar formulario de checkout (Early return)
        val checkoutValidation = OrderValidator.validateCheckout(checkoutForm)
        if (checkoutValidation is ValidationResult.Invalid) {
            return AppResult.Failure(AppError.Validation(checkoutValidation.message))
        }

        // 4. Mapeo Automático por Correo Electrónico (Opción B)
        val customerId = resolveCustomerIdByEmail(vendorSession)

        // 5. Mapear datos a DTO de WooCommerce con customerId resuelto
        val requestDto = buildOrderRequestDto(draft, checkoutForm, client, vendorSession, customerId)

        // 6. Enviar a WooCommerce mediante el wrapper
        val result = this.client.postOrder(requestDto)
        return result.map { response ->
            WooCreatedOrder(
                id = response.id,
                orderNumber = response.number ?: response.id.toString(),
                status = response.status.orEmpty(),
                total = response.total.orEmpty(),
                currency = response.currency ?: WooCommerceConfig.DEFAULT_CURRENCY_SYMBOL,
                createdAt = response.dateCreated.orEmpty(),
            )
        }
    }

    private suspend fun resolveCustomerIdByEmail(vendorSession: UserSession): Long? {
        val email = vendorSession.email.trim()
        if (email.isBlank()) return null

        // Paso A: Buscar si ya existe un cliente/usuario con este email
        when (val lookupResult = client.findCustomerByEmail(email)) {
            is AppResult.Success -> {
                val customer = lookupResult.data
                if (customer != null) {
                    return customer.id
                }
            }
            is AppResult.Failure -> {
                // Si la búsqueda falla por red, continuamos al intento de creación o fallback
            }
        }

        // Paso B: Si no existe, registrarlo en WooCommerce para vincular su cuenta
        val createRequest = WooCreateCustomerRequestDto(
            email = email,
            firstName = vendorSession.fullName,
            username = email,
        )
        return when (val createResult = client.createCustomer(createRequest)) {
            is AppResult.Success -> createResult.data.id
            is AppResult.Failure -> null
        }
    }

    private fun buildOrderRequestDto(
        draft: WooOrderDraft,
        checkoutForm: WooCheckoutForm,
        client: FieldClient,
        vendorSession: UserSession,
        customerId: Long?,
    ): WooOrderRequestDto {
        val lineItems = draft.items.map { item ->
            WooLineItemRequestDto(
                productId = item.product.id,
                quantity = item.quantity,
            )
        }

        val vendorFullName = checkoutForm.nombreVendedor.ifBlank { vendorSession.fullName.trim() }
        val nameParts = vendorFullName.split(" ", limit = 2)
        val vendorFirstName = nameParts.firstOrNull()?.takeIf { it.isNotBlank() } ?: vendorFullName
        val vendorLastName = if (nameParts.size > 1) nameParts[1].trim() else ""

        val fullPhone = checkoutForm.telefonoCompleto.ifBlank { checkoutForm.telefono }
        val billing = WooBillingDto(
            firstName = vendorFirstName,
            lastName = vendorLastName,
            company = checkoutForm.nombreCliente.ifBlank { client.businessName },
            address1 = checkoutForm.direccionEntrega.ifBlank { checkoutForm.domicilio.ifBlank { client.address.orEmpty() } },
            phone = fullPhone.ifBlank { client.phone.orEmpty() },
            email = vendorSession.email.takeIf { it.isNotBlank() },
            country = "BO",
        )

        val shipping = WooShippingDto(
            firstName = checkoutForm.nombreCliente.ifBlank { client.businessName },
            lastName = "",
            company = checkoutForm.nombreCliente.ifBlank { client.businessName },
            address1 = checkoutForm.direccionEntrega.ifBlank { checkoutForm.domicilio.ifBlank { client.address.orEmpty() } },
            country = "BO",
        )

        val metaData = listOf(
            WooMetaDataDto(key = "billing_cliente_nuevo", value = checkoutForm.clienteNuevo),
            WooMetaDataDto(key = "billing_codigo", value = checkoutForm.codigoCliente.ifBlank { "0" }),
            WooMetaDataDto(key = "billing_nombre_cliente", value = checkoutForm.nombreCliente),
            WooMetaDataDto(key = "billing_domicilio", value = checkoutForm.domicilio),
            WooMetaDataDto(key = "billing_telefono", value = fullPhone),
            WooMetaDataDto(key = "billing_country", value = "BO"),
            WooMetaDataDto(key = "billing_direccion_entrega", value = checkoutForm.direccionEntrega),
            WooMetaDataDto(key = "billing_mercado", value = checkoutForm.mercado),
            WooMetaDataDto(key = "billing_nro_puesto", value = checkoutForm.nroPuesto),
            WooMetaDataDto(key = "billing_nro_pedido", value = checkoutForm.nroPedido),
            WooMetaDataDto(key = "billing_factura", value = checkoutForm.canalVerde),
            WooMetaDataDto(key = "billing_tipo_venta", value = checkoutForm.tipoVenta),
            WooMetaDataDto(key = "billing_fecha_entrega", value = checkoutForm.fechaEntrega),
            WooMetaDataDto(key = "billing_hora_entrega", value = checkoutForm.horaEntrega),
            WooMetaDataDto(key = "billing_first_name", value = vendorFirstName),
            WooMetaDataDto(key = "billing_latitud", value = checkoutForm.latitud),
            WooMetaDataDto(key = "billing_longitud", value = checkoutForm.longitud),
            WooMetaDataDto(key = "billing_saldo_anterior", value = checkoutForm.saldoAnterior),

            WooMetaDataDto(key = "_billing_cliente_nuevo", value = checkoutForm.clienteNuevo),
            WooMetaDataDto(key = "_billing_codigo", value = checkoutForm.codigoCliente.ifBlank { "0" }),
            WooMetaDataDto(key = "_billing_nombre_cliente", value = checkoutForm.nombreCliente),
            WooMetaDataDto(key = "_billing_domicilio", value = checkoutForm.domicilio),
            WooMetaDataDto(key = "_billing_telefono", value = fullPhone),
            WooMetaDataDto(key = "_billing_country", value = "BO"),
            WooMetaDataDto(key = "_billing_direccion_entrega", value = checkoutForm.direccionEntrega),
            WooMetaDataDto(key = "_billing_mercado", value = checkoutForm.mercado),
            WooMetaDataDto(key = "_billing_nro_puesto", value = checkoutForm.nroPuesto),
            WooMetaDataDto(key = "_billing_nro_pedido", value = checkoutForm.nroPedido),
            WooMetaDataDto(key = "_billing_factura", value = checkoutForm.canalVerde),
            WooMetaDataDto(key = "_billing_tipo_venta", value = checkoutForm.tipoVenta),
            WooMetaDataDto(key = "_billing_fecha_entrega", value = checkoutForm.fechaEntrega),
            WooMetaDataDto(key = "_billing_hora_entrega", value = checkoutForm.horaEntrega),
            WooMetaDataDto(key = "_billing_first_name", value = vendorFirstName),
            WooMetaDataDto(key = "_billing_latitud", value = checkoutForm.latitud),
            WooMetaDataDto(key = "_billing_longitud", value = checkoutForm.longitud),
            WooMetaDataDto(key = "_billing_saldo_anterior", value = checkoutForm.saldoAnterior),
            WooMetaDataDto(key = "latitud", value = checkoutForm.latitud),
            WooMetaDataDto(key = "longitud", value = checkoutForm.longitud),

            WooMetaDataDto(key = "_wtracker_vendor_id", value = vendorSession.userId),
            WooMetaDataDto(key = "_wtracker_vendor_name", value = vendorSession.fullName),
            WooMetaDataDto(key = "_wtracker_vendor_email", value = vendorSession.email),
            WooMetaDataDto(key = "_wtracker_client_id", value = client.id),
            WooMetaDataDto(key = "_wtracker_client_name", value = client.businessName),
            WooMetaDataDto(key = "_wtracker_source", value = "WTracker Field App"),
        )

        val customerNote = buildString {
            if (draft.notes.isNotBlank()) {
                append(draft.notes)
                append(" | ")
            }
            append("Pedido tomado en campo por vendedor: ${vendorSession.fullName}. Nro pedido: ${checkoutForm.nroPedido}")
        }

        return WooOrderRequestDto(
            customerId = customerId,
            paymentMethod = WooCommerceConfig.DEFAULT_PAYMENT_METHOD,
            paymentMethodTitle = WooCommerceConfig.DEFAULT_PAYMENT_METHOD_TITLE,
            setPaid = false,
            status = WooCommerceConfig.DEFAULT_ORDER_STATUS,
            createdVia = WooCommerceConfig.DEFAULT_CREATED_VIA,
            billing = billing,
            shipping = shipping,
            lineItems = lineItems,
            customerNote = customerNote,
            metaData = metaData,
        )
    }

    private fun WooProductDto.toDomain(): WooProduct {
        val parsedPrice = price?.toDoubleOrNull() ?: regularPrice?.toDoubleOrNull() ?: 0.0
        val symbol = WooCommerceConfig.DEFAULT_CURRENCY_SYMBOL
        val formattedPrice = String.format(Locale.US, "%s %.2f", symbol, parsedPrice)

        val categoryNames = categories.mapNotNull {
            it.name?.replace("&amp;", "&")?.replace("&#038;", "&")?.trim()
        }

        return WooProduct(
            id = id,
            name = name,
            price = parsedPrice,
            formattedPrice = formattedPrice,
            sku = sku?.takeIf { it.isNotBlank() },
            stockQuantity = stockQuantity,
            inStock = stockStatus == "instock" || (stockQuantity != null && stockQuantity > 0) || stockStatus == null,
            categoryName = categoryNames.firstOrNull(),
            categories = categoryNames,
            imageUrl = images.firstOrNull()?.src,
        )
    }
}
