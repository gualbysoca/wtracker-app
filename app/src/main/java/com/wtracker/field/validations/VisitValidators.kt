package com.wtracker.field.validations

import com.wtracker.field.configs.EvidenceConfig
import com.wtracker.field.configs.LocationConfig
import com.wtracker.field.configs.VisitConfig
import com.wtracker.field.services.models.CapturedEvidence
import com.wtracker.field.services.models.CapturedLocation
import com.wtracker.field.services.models.VisitMarkRequest
import com.wtracker.field.services.models.VisitStatus
import kotlin.math.roundToInt

/** Reglas anti-fraude sobre la lectura GPS usada para sellar evidencias y marcar visitas. */
object CapturedLocationValidator {

    fun validate(location: CapturedLocation): ValidationResult {
        if (location.isMockLocation) {
            return ValidationResult.Invalid(
                "Se detectó una ubicación simulada. Desactiva las apps de ubicación falsa para continuar.",
            )
        }
        if (location.accuracyMeters > LocationConfig.MAX_ACCEPTABLE_ACCURACY_METERS) {
            return ValidationResult.Invalid(
                "Precisión GPS insuficiente (±${location.accuracyMeters.roundToInt()} m). " +
                    "Ubícate en un lugar abierto e intenta nuevamente.",
            )
        }
        return ValidationResult.Valid
    }
}

/**
 * Reglas de negocio para registrar una marcación:
 *  - "visitado" y "fallido" exigen foto de respaldo capturada in-app.
 *  - La foto no puede ser más antigua que [EvidenceConfig.EVIDENCE_MAX_AGE_MS].
 *  - La ubicación debe ser real y suficientemente precisa.
 */
object VisitMarkValidator {

    fun validate(request: VisitMarkRequest, nowMillis: Long): ValidationResult {
        if (request.status !in VisitStatus.markableStatuses) {
            return ValidationResult.Invalid("Selecciona un estado válido para la visita.")
        }
        if (request.status.requiresEvidence && request.evidence == null) {
            return ValidationResult.Invalid("Debes adjuntar una foto de respaldo para marcar la visita como \"${request.status.apiValue}\".")
        }
        val evidenceValidation = request.evidence?.let { validateEvidenceFreshness(it, nowMillis) }
        if (evidenceValidation is ValidationResult.Invalid) return evidenceValidation

        if ((request.notes?.length ?: 0) > VisitConfig.NOTES_MAX_LENGTH) {
            return ValidationResult.Invalid("Las observaciones no pueden superar ${VisitConfig.NOTES_MAX_LENGTH} caracteres.")
        }
        return CapturedLocationValidator.validate(request.location)
    }

    private fun validateEvidenceFreshness(evidence: CapturedEvidence, nowMillis: Long): ValidationResult {
        if (nowMillis - evidence.capturedAtMillis <= EvidenceConfig.EVIDENCE_MAX_AGE_MS) return ValidationResult.Valid
        return ValidationResult.Invalid("La foto de respaldo expiró. Toma una nueva captura.")
    }
}
