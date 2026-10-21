package com.wtracker.field.services.woocommerce

import com.wtracker.field.services.models.CapturedEvidence
import com.wtracker.field.services.woocommerce.models.WooCreatedOrder
import com.wtracker.field.services.woocommerce.models.WooOrderDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Representa la confirmación de un pedido exitoso tomado para un cliente en campo.
 */
data class OrderConfirmation(
    val clientId: String,
    val order: WooCreatedOrder,
    val draft: WooOrderDraft,
    val notesText: String,
    val evidence: CapturedEvidence,
)

/**
 * Almacén en memoria de confirmaciones de pedido pendientes de asentar en la marcación de visita.
 * Permite comunicar el resultado de [OrderCreationViewModel] a [VisitCaptureViewModel]
 * sin acoplamiento directo entre pantallas (SoC).
 */
class OrderConfirmationStore {

    private val confirmations = MutableStateFlow<Map<String, OrderConfirmation>>(emptyMap())

    fun observeConfirmation(clientId: String): Flow<OrderConfirmation?> =
        confirmations.map { it[clientId] }.distinctUntilChanged()

    fun getConfirmation(clientId: String): OrderConfirmation? =
        confirmations.value[clientId]

    fun putConfirmation(confirmation: OrderConfirmation) {
        confirmations.update { it + (confirmation.clientId to confirmation) }
    }

    fun clearConfirmation(clientId: String) {
        confirmations.update { it - clientId }
    }
}
