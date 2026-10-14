package com.wtracker.field.services.models

/**
 * Estados de visita. `apiValue` coincide con el ENUM `visit_status` del Backoffice.
 * PENDING es sólo local: el cliente aún no tiene marcación hoy.
 */
enum class VisitStatus(val apiValue: String, val requiresEvidence: Boolean) {
    PENDING(apiValue = "pendiente", requiresEvidence = false),
    EN_ROUTE(apiValue = "en_ruta", requiresEvidence = false),
    VISITED(apiValue = "visitado", requiresEvidence = true),
    FAILED(apiValue = "fallido", requiresEvidence = true);

    val isFinal: Boolean get() = this == VISITED || this == FAILED

    companion object {
        val markableStatuses: List<VisitStatus> = listOf(EN_ROUTE, VISITED, FAILED)

        fun fromApiValue(value: String?): VisitStatus = entries.firstOrNull { it.apiValue == value } ?: PENDING
    }
}

data class GeoCoordinate(val latitude: Double, val longitude: Double)

/** Lectura GPS con los datos necesarios para validar su autenticidad. */
data class CapturedLocation(
    val coordinate: GeoCoordinate,
    val accuracyMeters: Float,
    val isMockLocation: Boolean,
    val fixTimeMillis: Long,
    val capturedAtMillis: Long,
)

/** Foto ya sellada (marca visual + EXIF) y con su huella SHA-256 calculada. */
data class CapturedEvidence(
    val filePath: String,
    val sha256: String,
    val mimeType: String,
    val location: CapturedLocation,
    val capturedAtMillis: Long,
)

data class UserSession(
    val userId: String,
    val fullName: String,
    val email: String,
    val role: String,
    val accessToken: String,
    val refreshToken: String,
)

data class FieldClient(
    val id: String,
    val businessName: String,
    val contactName: String?,
    val phone: String?,
    val address: String?,
    val coordinate: GeoCoordinate?,
    val currentStatus: VisitStatus,
    val statusUpdatedAtMillis: Long?,
    val hasPendingSync: Boolean,
)

data class RouteStop(
    val order: Int,
    val client: FieldClient,
    val distanceFromUserMeters: Double?,
)

data class VisitMarkRequest(
    val clientId: String,
    val status: VisitStatus,
    val location: CapturedLocation,
    val evidence: CapturedEvidence?,
    val notes: String?,
)
