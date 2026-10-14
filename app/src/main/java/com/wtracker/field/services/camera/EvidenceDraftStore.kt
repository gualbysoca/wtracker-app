package com.wtracker.field.services.camera

import com.wtracker.field.services.models.CapturedEvidence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Borradores de evidencia por cliente, compartidos entre la pantalla de cámara y la de
 * registro de visita. El mapa se reemplaza (nunca se muta) en cada cambio.
 */
class EvidenceDraftStore(private val fileStore: EvidenceFileStore) {

    private val drafts = MutableStateFlow<Map<String, CapturedEvidence>>(emptyMap())

    fun observeDraft(clientId: String): Flow<CapturedEvidence?> =
        drafts.map { it[clientId] }.distinctUntilChanged()

    /** Guarda el borrador; si había uno previo para ese cliente, elimina su archivo huérfano. */
    fun putDraft(clientId: String, evidence: CapturedEvidence) {
        val previousDraft = drafts.value[clientId]
        drafts.update { current -> current + (clientId to evidence) }
        if (previousDraft != null && previousDraft.filePath != evidence.filePath) {
            fileStore.delete(previousDraft.filePath)
        }
    }

    /** Descarta el borrador y su archivo (el usuario canceló). */
    fun discardDraft(clientId: String) {
        val draft = drafts.value[clientId] ?: return
        drafts.update { current -> current - clientId }
        fileStore.delete(draft.filePath)
    }

    /** Libera el borrador SIN borrar el archivo: la propiedad pasa a la cola de sincronización. */
    fun releaseDraft(clientId: String) {
        drafts.update { current -> current - clientId }
    }
}
