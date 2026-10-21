package com.wtracker.field.controllers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wtracker.field.configs.UiConfig
import com.wtracker.field.configs.VisitConfig
import com.wtracker.field.configs.WooCommerceConfig
import com.wtracker.field.services.auth.SessionStore
import com.wtracker.field.services.camera.EvidenceDraftStore
import com.wtracker.field.services.clients.ClientService
import com.wtracker.field.services.location.LocationProvider
import com.wtracker.field.services.models.FieldClient
import com.wtracker.field.services.models.UserSession
import com.wtracker.field.services.woocommerce.OrderConfirmation
import com.wtracker.field.services.woocommerce.OrderConfirmationStore
import com.wtracker.field.services.woocommerce.OrderEvidenceGenerator
import com.wtracker.field.services.woocommerce.WooCommerceService
import com.wtracker.field.services.woocommerce.models.OrderStep
import com.wtracker.field.services.woocommerce.models.WooCheckoutForm
import com.wtracker.field.services.woocommerce.models.WooCreatedOrder
import com.wtracker.field.services.woocommerce.models.WooOrderDraft
import com.wtracker.field.services.woocommerce.models.WooOrderItem
import com.wtracker.field.services.woocommerce.models.WooProduct
import com.wtracker.field.utils.AppResult
import com.wtracker.field.validations.OrderValidator
import com.wtracker.field.validations.ValidationResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class OrderCreationUiState(
    val isLoadingClient: Boolean = true,
    val isLoadingProducts: Boolean = true,
    val isUpdatingLocation: Boolean = false,
    val client: FieldClient? = null,
    val vendorSession: UserSession? = null,
    val isVendor: Boolean = false,
    val products: List<WooProduct> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val isFilterCartOnly: Boolean = false,
    val orderStep: OrderStep = OrderStep.CATALOG,
    val checkoutForm: WooCheckoutForm = WooCheckoutForm(),
    val draft: WooOrderDraft = WooOrderDraft(),
    val inputQuantities: Map<Long, Int> = emptyMap(),
    val isSubmitting: Boolean = false,
    val createdOrder: WooCreatedOrder? = null,
    val isScheduledClient: Boolean = false,
    val isNewClient: Boolean = false,
    val errorMessage: String? = null,
    val validationError: String? = null,
    val successToast: String? = null,
) {
    val isClientCodeEditable: Boolean
        get() = if (isNewClient) false else (!isScheduledClient || client?.clientCode.isNullOrBlank())

    val isNewClientEditable: Boolean
        get() = if (isNewClient) false else !isScheduledClient

    val availableCategories: List<String>
        get() = products
            .flatMap { it.categories }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(
                compareBy<String> { category ->
                    if (category.firstOrNull()?.isDigit() == true || category.startsWith("@")) 1 else 0
                }.thenBy { it.lowercase() }
            )

    val filteredProducts: List<WooProduct>
        get() {
            val query = searchQuery.trim()
            val cartProductIds = draft.items.map { it.product.id }.toSet()

            return products.filter { product ->
                if (isFilterCartOnly && product.id !in cartProductIds) {
                    return@filter false
                }

                val matchesCategory = selectedCategory == null ||
                    product.categoryName.equals(selectedCategory, ignoreCase = true) ||
                    product.categories.any { it.equals(selectedCategory, ignoreCase = true) }

                val matchesSearch = query.isBlank() ||
                    product.name.contains(query, ignoreCase = true) ||
                    (product.sku?.contains(query, ignoreCase = true) == true) ||
                    (product.categoryName?.contains(query, ignoreCase = true) == true) ||
                    product.categories.any { it.contains(query, ignoreCase = true) }

                matchesCategory && matchesSearch
            }
        }

    val canProceedToCheckout: Boolean
        get() = isVendor && !draft.isEmpty && validationError == null

    val isFormComplete: Boolean
        get() = OrderValidator.isCheckoutFormComplete(checkoutForm)

    val canSubmitOrder: Boolean
        get() = isVendor && !draft.isEmpty && !isSubmitting && isFormComplete

    val totalItemsCount: Int get() = draft.totalItemsCount
    val totalAmount: Double get() = draft.total

    fun getInputQuantity(productId: Long): Int = inputQuantities[productId] ?: 1

    fun getCartQuantity(productId: Long): Int =
        draft.items.firstOrNull { it.product.id == productId }?.quantity ?: 0
}

sealed interface OrderCreationEvent {
    data class OrderSuccess(val order: WooCreatedOrder) : OrderCreationEvent
}

private data class OrderInternalState(
    val products: List<WooProduct> = emptyList(),
    val isLoadingProducts: Boolean = true,
    val isUpdatingLocation: Boolean = false,
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val isFilterCartOnly: Boolean = false,
    val orderStep: OrderStep = OrderStep.CATALOG,
    val checkoutForm: WooCheckoutForm = WooCheckoutForm(),
    val isCheckoutFormInitialized: Boolean = false,
    val draft: WooOrderDraft = WooOrderDraft(),
    val inputQuantities: Map<Long, Int> = emptyMap(),
    val isSubmitting: Boolean = false,
    val createdOrder: WooCreatedOrder? = null,
    val errorMessage: String? = null,
    val validationError: String? = null,
    val successToast: String? = null,
)

class OrderCreationViewModel(
    private val clientId: String,
    private val clientService: ClientService,
    private val sessionStore: SessionStore,
    private val wooCommerceService: WooCommerceService,
    private val evidenceDraftStore: EvidenceDraftStore,
    private val orderConfirmationStore: OrderConfirmationStore,
    private val orderEvidenceGenerator: OrderEvidenceGenerator,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    val isNewClient: Boolean = clientId == com.wtracker.field.routes.AppDestinations.NEW_CLIENT_ID ||
        clientId.isBlank() || clientId.equals("new", ignoreCase = true)

    private val internalState = MutableStateFlow(OrderInternalState())
    private val eventChannel = Channel<OrderCreationEvent>(Channel.BUFFERED)
    val events: Flow<OrderCreationEvent> = eventChannel.receiveAsFlow()

    init {
        loadCatalog(forceRefresh = false)
        if (isNewClient) {
            fetchInitialLocationForNewClient()
        }
    }

    private fun fetchInitialLocationForNewClient() {
        viewModelScope.launch {
            when (val locResult = locationProvider.getFreshLocation()) {
                is AppResult.Success -> {
                    val loc = locResult.data
                    val latStr = String.format(Locale.US, "%.6f", loc.coordinate.latitude)
                    val lngStr = String.format(Locale.US, "%.6f", loc.coordinate.longitude)
                    internalState.update { state ->
                        state.copy(
                            checkoutForm = state.checkoutForm.copy(
                                latitud = state.checkoutForm.latitud.ifBlank { latStr },
                                longitud = state.checkoutForm.longitud.ifBlank { lngStr },
                            )
                        )
                    }
                }
                is AppResult.Failure -> {
                    // Si falla el fix inicial, el usuario puede actualizarlo con el botón GPS
                }
            }
        }
    }

    val uiState: StateFlow<OrderCreationUiState> = combine(
        if (isNewClient) kotlinx.coroutines.flow.flowOf(null) else clientService.observeFieldClient(clientId),
        sessionStore.sessionFlow,
        internalState,
    ) { client, session, internal ->
        val isVendor = session?.role.equals(WooCommerceConfig.REQUIRED_VENDOR_ROLE, ignoreCase = true)

        // Prellenar checkoutForm de manera reactiva la primera vez que se cargan cliente y sesión
        val effectiveForm = if (!internal.isCheckoutFormInitialized && session != null && (client != null || isNewClient)) {
            val prefill = if (isNewClient) {
                buildNewClientCheckoutForm(session, internal.checkoutForm)
            } else {
                buildInitialCheckoutForm(client!!, session, internal.checkoutForm)
            }
            internalState.update { it.copy(checkoutForm = prefill, isCheckoutFormInitialized = true) }
            prefill
        } else {
            internal.checkoutForm
        }

        OrderCreationUiState(
            isLoadingClient = !isNewClient && client == null,
            isLoadingProducts = internal.isLoadingProducts,
            isUpdatingLocation = internal.isUpdatingLocation,
            client = client,
            vendorSession = session,
            isVendor = isVendor,
            isScheduledClient = !isNewClient && client != null,
            isNewClient = isNewClient,
            products = internal.products,
            searchQuery = internal.searchQuery,
            selectedCategory = internal.selectedCategory,
            isFilterCartOnly = internal.isFilterCartOnly,
            orderStep = internal.orderStep,
            checkoutForm = effectiveForm,
            draft = internal.draft,
            inputQuantities = internal.inputQuantities,
            isSubmitting = internal.isSubmitting,
            createdOrder = internal.createdOrder,
            errorMessage = internal.errorMessage,
            validationError = internal.validationError,
            successToast = internal.successToast,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(UiConfig.STATE_SUBSCRIPTION_TIMEOUT_MS), OrderCreationUiState())

    fun loadCatalog(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            internalState.update { it.copy(isLoadingProducts = true, errorMessage = null) }
            when (val result = wooCommerceService.getProducts(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    internalState.update { it.copy(products = result.data, isLoadingProducts = false) }
                }
                is AppResult.Failure -> {
                    internalState.update { it.copy(isLoadingProducts = false, errorMessage = result.error.message) }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        internalState.update { it.copy(searchQuery = query) }
    }

    fun onCategorySelected(category: String?) {
        internalState.update { state ->
            val nextCategory = if (state.selectedCategory.equals(category, ignoreCase = true)) null else category
            state.copy(selectedCategory = nextCategory, isFilterCartOnly = false)
        }
    }

    fun onToggleCartFilter() {
        internalState.update { state ->
            val nextFilter = !state.isFilterCartOnly
            state.copy(
                isFilterCartOnly = nextFilter,
                selectedCategory = if (nextFilter) null else state.selectedCategory,
            )
        }
    }

    fun onIncrementCartQuantity(product: WooProduct) {
        internalState.update { state ->
            val existingItem = state.draft.items.firstOrNull { it.product.id == product.id }
            val currentInCart = existingItem?.quantity ?: 0
            val newTotalInCart = currentInCart + 1

            val stock = product.stockQuantity
            if (stock != null && newTotalInCart > stock) {
                return@update state.copy(
                    validationError = "Solo hay $stock unidades disponibles de '${product.name}'."
                )
            }

            val updatedItems = if (existingItem != null) {
                state.draft.items.map { item ->
                    if (item.product.id == product.id) item.copy(quantity = newTotalInCart) else item
                }
            } else {
                state.draft.items + WooOrderItem(product = product, quantity = 1)
            }

            state.copy(
                draft = state.draft.copy(items = updatedItems),
                validationError = null,
            )
        }
    }

    fun onDecrementCartQuantity(product: WooProduct) {
        internalState.update { state ->
            val existingItem = state.draft.items.firstOrNull { it.product.id == product.id } ?: return@update state
            val newQuantity = existingItem.quantity - 1

            val updatedItems = if (newQuantity <= 0) {
                state.draft.items.filterNot { it.product.id == product.id }
            } else {
                state.draft.items.map { item ->
                    if (item.product.id == product.id) item.copy(quantity = newQuantity) else item
                }
            }

            val nextFilterCart = if (updatedItems.isEmpty()) false else state.isFilterCartOnly

            state.copy(
                draft = state.draft.copy(items = updatedItems),
                isFilterCartOnly = nextFilterCart,
                validationError = null,
            )
        }
    }

    fun onSetCartQuantity(product: WooProduct, quantity: Int) {
        internalState.update { state ->
            val safeQuantity = quantity.coerceAtLeast(0)
            if (safeQuantity == 0) {
                val updatedItems = state.draft.items.filterNot { it.product.id == product.id }
                val nextFilterCart = if (updatedItems.isEmpty()) false else state.isFilterCartOnly
                return@update state.copy(
                    draft = state.draft.copy(items = updatedItems),
                    isFilterCartOnly = nextFilterCart,
                    validationError = null,
                )
            }

            val stock = product.stockQuantity
            if (stock != null && safeQuantity > stock) {
                return@update state.copy(
                    validationError = "Solo hay $stock unidades disponibles de '${product.name}'."
                )
            }

            val existingItem = state.draft.items.firstOrNull { it.product.id == product.id }
            val updatedItems = if (existingItem != null) {
                state.draft.items.map { item ->
                    if (item.product.id == product.id) item.copy(quantity = safeQuantity) else item
                }
            } else {
                state.draft.items + WooOrderItem(product = product, quantity = safeQuantity)
            }

            state.copy(
                draft = state.draft.copy(items = updatedItems),
                validationError = null,
            )
        }
    }

    fun onRemoveFromCart(product: WooProduct) {
        internalState.update { state ->
            val updatedItems = state.draft.items.filterNot { it.product.id == product.id }
            val nextFilterCart = if (updatedItems.isEmpty()) false else state.isFilterCartOnly
            state.copy(
                draft = state.draft.copy(items = updatedItems),
                isFilterCartOnly = nextFilterCart,
                validationError = null,
            )
        }
    }

    fun onProceedToCheckout() {
        val currentState = uiState.value
        val draftValidation = OrderValidator.validateDraft(currentState.draft)
        if (draftValidation is ValidationResult.Invalid) {
            internalState.update { it.copy(validationError = draftValidation.message) }
            return
        }

        val client = currentState.client
        val session = currentState.vendorSession
        internalState.update { state ->
            val form = if (session != null) {
                if (isNewClient) {
                    buildNewClientCheckoutForm(session, state.checkoutForm)
                } else if (client != null) {
                    buildInitialCheckoutForm(client, session, state.checkoutForm)
                } else {
                    state.checkoutForm
                }
            } else {
                state.checkoutForm
            }
            state.copy(
                orderStep = OrderStep.CHECKOUT,
                checkoutForm = form,
                validationError = null,
            )
        }
    }

    fun onBackToCatalog() {
        internalState.update { it.copy(orderStep = OrderStep.CATALOG, validationError = null) }
    }

    fun onCheckoutFormChanged(form: WooCheckoutForm) {
        val sanitizedForm = if (isNewClient) {
            form.copy(clienteNuevo = "Si", codigoCliente = "")
        } else if (uiState.value.isScheduledClient) {
            form.copy(clienteNuevo = "No")
        } else {
            form
        }
        internalState.update { it.copy(checkoutForm = sanitizedForm, validationError = null) }
    }

    fun onUpdateClientLocation() {
        viewModelScope.launch {
            internalState.update { it.copy(isUpdatingLocation = true, errorMessage = null) }
            when (val freshLocationResult = locationProvider.getFreshLocation()) {
                is AppResult.Success -> {
                    val freshLoc = freshLocationResult.data
                    val latStr = String.format(Locale.US, "%.6f", freshLoc.coordinate.latitude)
                    val lngStr = String.format(Locale.US, "%.6f", freshLoc.coordinate.longitude)

                    internalState.update { state ->
                        state.copy(
                            isUpdatingLocation = false,
                            checkoutForm = state.checkoutForm.copy(
                                latitud = latStr,
                                longitud = lngStr,
                            ),
                            successToast = "Ubicación GPS actualizada: $latStr, $lngStr",
                        )
                    }

                    if (!isNewClient) {
                        clientService.updateClientCoordinates(
                            clientId = clientId,
                            lat = freshLoc.coordinate.latitude,
                            lng = freshLoc.coordinate.longitude,
                        )
                    }
                }
                is AppResult.Failure -> {
                    internalState.update {
                        it.copy(isUpdatingLocation = false, errorMessage = freshLocationResult.error.message)
                    }
                }
            }
        }
    }

    fun onSubmitOrder() {
        val currentState = uiState.value
        val client = currentState.client
        val session = currentState.vendorSession
        val checkoutForm = currentState.checkoutForm

        if ((client == null && !isNewClient) || session == null) {
            internalState.update { it.copy(errorMessage = "Sesión o cliente no disponible para registrar el pedido.") }
            return
        }

        val draftValidation = OrderValidator.validateDraft(currentState.draft)
        if (draftValidation is ValidationResult.Invalid) {
            internalState.update { it.copy(validationError = draftValidation.message) }
            return
        }

        val checkoutValidation = OrderValidator.validateCheckout(checkoutForm)
        if (checkoutValidation is ValidationResult.Invalid) {
            internalState.update { it.copy(validationError = checkoutValidation.message) }
            return
        }

        val roleValidation = OrderValidator.validateVendorRole(session.role)
        if (roleValidation is ValidationResult.Invalid) {
            internalState.update { it.copy(errorMessage = roleValidation.message) }
            return
        }

        viewModelScope.launch {
            internalState.update { it.copy(isSubmitting = true, errorMessage = null, validationError = null) }

            val parsedLat = checkoutForm.latitud.toDoubleOrNull()
            val parsedLng = checkoutForm.longitud.toDoubleOrNull()
            val updatedBusinessName = checkoutForm.mercado.trim().takeIf { it.isNotBlank() }
            val fullPhoneNumber = checkoutForm.telefonoCompleto.ifBlank { checkoutForm.telefono }

            if (!isNewClient) {
                clientService.updateClientDetails(
                    clientId = clientId,
                    businessName = updatedBusinessName,
                    contactName = checkoutForm.nombreCliente.trim().takeIf { it.isNotBlank() },
                    phone = fullPhoneNumber.trim().takeIf { it.isNotBlank() },
                    address = checkoutForm.domicilio.trim().takeIf { it.isNotBlank() },
                    deliveryAddress = checkoutForm.direccionEntrega.trim().takeIf { it.isNotBlank() },
                    stallNumber = checkoutForm.nroPuesto.trim().takeIf { it.isNotBlank() },
                    clientCode = checkoutForm.codigoCliente.trim().takeIf { it.isNotBlank() },
                    lat = parsedLat,
                    lng = parsedLng,
                )
            }

            val updatedClient = client?.copy(
                businessName = updatedBusinessName ?: client.businessName,
                contactName = checkoutForm.nombreCliente.trim().takeIf { it.isNotBlank() } ?: client.contactName,
                phone = fullPhoneNumber.trim().takeIf { it.isNotBlank() } ?: client.phone,
                address = checkoutForm.domicilio.trim().takeIf { it.isNotBlank() } ?: client.address,
                deliveryAddress = checkoutForm.direccionEntrega.trim().takeIf { it.isNotBlank() } ?: client.deliveryAddress,
                stallNumber = checkoutForm.nroPuesto.trim().takeIf { it.isNotBlank() } ?: client.stallNumber,
                clientCode = checkoutForm.codigoCliente.trim().takeIf { it.isNotBlank() } ?: client.clientCode,
                coordinate = if (parsedLat != null && parsedLng != null) {
                    com.wtracker.field.services.models.GeoCoordinate(parsedLat, parsedLng)
                } else client.coordinate,
            ) ?: com.wtracker.field.services.models.FieldClient(
                id = "new_${System.currentTimeMillis()}",
                businessName = updatedBusinessName ?: checkoutForm.nombreCliente.trim().ifBlank { "Cliente Nuevo" },
                contactName = checkoutForm.nombreCliente.trim().takeIf { it.isNotBlank() },
                phone = fullPhoneNumber.trim().takeIf { it.isNotBlank() },
                address = checkoutForm.domicilio.trim().takeIf { it.isNotBlank() },
                deliveryAddress = checkoutForm.direccionEntrega.trim().takeIf { it.isNotBlank() },
                stallNumber = checkoutForm.nroPuesto.trim().takeIf { it.isNotBlank() },
                clientCode = checkoutForm.codigoCliente.trim().takeIf { it.isNotBlank() },
                coordinate = if (parsedLat != null && parsedLng != null) {
                    com.wtracker.field.services.models.GeoCoordinate(parsedLat, parsedLng)
                } else null,
                currentStatus = com.wtracker.field.services.models.VisitStatus.PENDING,
                statusUpdatedAtMillis = System.currentTimeMillis(),
                hasPendingSync = false,
            )

            when (val result = wooCommerceService.submitOrder(currentState.draft, checkoutForm, updatedClient, session)) {
                is AppResult.Success -> {
                    val order = result.data
                    val itemsSummary = currentState.draft.items.joinToString(", ") { "${it.quantity}x ${it.product.name}" }
                    val notesText = buildString {
                        append("Pedido #${order.orderNumber} en WooCommerce. ")
                        append("Origen: App móvil. ")
                        append("Total: ${order.currency} ${order.total}. ")
                        append("Detalle: $itemsSummary")
                    }.let { text ->
                        if (text.length > VisitConfig.NOTES_MAX_LENGTH) {
                            text.take(VisitConfig.NOTES_MAX_LENGTH - 3) + "..."
                        } else text
                    }

                    try {
                        val evidence = orderEvidenceGenerator.generateEvidence(
                            client = updatedClient,
                            vendorSession = session,
                            order = order,
                            draft = currentState.draft,
                            checkoutForm = checkoutForm,
                        )
                        evidenceDraftStore.putDraft(clientId, evidence)
                        orderConfirmationStore.putConfirmation(
                            OrderConfirmation(
                                clientId = clientId,
                                order = order,
                                draft = currentState.draft,
                                notesText = notesText,
                                evidence = evidence,
                            )
                        )
                    } catch (_: Exception) {
                        // Resiliente ante cualquier fallo de renderizado gráfico
                    }

                    internalState.update {
                        it.copy(
                            isSubmitting = false,
                            createdOrder = order,
                            orderStep = OrderStep.CATALOG,
                            draft = WooOrderDraft(), // Limpiar carrito
                            inputQuantities = emptyMap(),
                            isFilterCartOnly = false,
                        )
                    }
                    eventChannel.send(OrderCreationEvent.OrderSuccess(order))
                }
                is AppResult.Failure -> {
                    internalState.update {
                        it.copy(isSubmitting = false, errorMessage = result.error.message)
                    }
                }
            }
        }
    }

    fun onDismissOrderSuccess() {
        internalState.update { it.copy(createdOrder = null) }
    }

    fun onDismissToast() {
        internalState.update { it.copy(successToast = null) }
    }

    private fun buildInitialCheckoutForm(
        client: FieldClient,
        session: UserSession,
        existing: WooCheckoutForm,
    ): WooCheckoutForm {
        val contactOrBusiness = client.contactName?.takeIf { it.isNotBlank() } ?: client.businessName
        val clientLat = client.coordinate?.latitude?.let { String.format(Locale.US, "%.6f", it) }.orEmpty()
        val clientLng = client.coordinate?.longitude?.let { String.format(Locale.US, "%.6f", it) }.orEmpty()
        val clientCode = client.clientCode?.takeIf { it.isNotBlank() }.orEmpty()
        val deliveryAddress = client.deliveryAddress?.takeIf { it.isNotBlank() }.orEmpty()
        val referentialAddress = client.address?.takeIf { it.isNotBlank() }.orEmpty()
        val stallNumber = client.stallNumber?.takeIf { it.isNotBlank() }.orEmpty()

        val (initialCountry, initialPhone) = com.wtracker.field.ui.components.CountryCodes.splitPhone(client.phone)

        return existing.copy(
            clienteNuevo = "No",
            codigoCliente = existing.codigoCliente.ifBlank { clientCode },
            nombreCliente = existing.nombreCliente.ifBlank { contactOrBusiness },
            mercado = existing.mercado.ifBlank { client.businessName },
            direccionEntrega = existing.direccionEntrega.ifBlank { deliveryAddress },
            domicilio = existing.domicilio.ifBlank { referentialAddress },
            codigoPais = existing.codigoPais.ifBlank { initialCountry.dialCode },
            telefono = existing.telefono.ifBlank { initialPhone },
            nroPuesto = existing.nroPuesto.ifBlank { stallNumber },
            nroPedido = existing.nroPedido,
            canalVerde = existing.canalVerde.ifBlank { "No" },
            nombreVendedor = session.fullName,
            pais = "Bolivia",
            latitud = existing.latitud.ifBlank { clientLat },
            longitud = existing.longitud.ifBlank { clientLng },
            saldoAnterior = existing.saldoAnterior.ifBlank { "" },
        )
    }

    private fun buildNewClientCheckoutForm(
        session: UserSession,
        existing: WooCheckoutForm,
    ): WooCheckoutForm {
        return existing.copy(
            clienteNuevo = "Si",
            codigoCliente = "",
            nombreCliente = existing.nombreCliente,
            mercado = existing.mercado,
            direccionEntrega = existing.direccionEntrega,
            domicilio = existing.domicilio,
            codigoPais = existing.codigoPais.ifBlank { "+591" },
            telefono = existing.telefono,
            nroPuesto = existing.nroPuesto,
            nroPedido = existing.nroPedido,
            canalVerde = existing.canalVerde.ifBlank { "No" },
            tipoVenta = existing.tipoVenta.ifBlank { "Al contado" },
            nombreVendedor = session.fullName,
            pais = "Bolivia",
            latitud = existing.latitud,
            longitud = existing.longitud,
            saldoAnterior = existing.saldoAnterior.ifBlank { "" },
        )
    }
}
