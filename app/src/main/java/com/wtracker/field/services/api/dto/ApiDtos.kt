package com.wtracker.field.services.api.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Envoltorio estándar de `responseFormatter.sendSuccess` del Backoffice. */
@Serializable
data class ApiEnvelopeDto<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
)

/** Envoltorio estándar de `responseFormatter.sendError`. */
@Serializable
data class ApiErrorBodyDto(
    val success: Boolean = false,
    val message: String? = null,
    val errors: Map<String, List<String>>? = null,
)

// ─────────────── Auth ───────────────

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class LoginResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val email: String,
    val role: String,
)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class RefreshResponseDto(val accessToken: String)

@Serializable
data class LogoutRequestDto(val refreshToken: String)

@Serializable
data class ShiftRequestDto(@SerialName("is_on_shift") val isOnShift: Boolean)

// ─────────────── Clientes ───────────────

/**
 * `GET /mobile/clients`. PostgreSQL devuelve DECIMAL como string ("-17.7502"),
 * por eso lat/lng usan [FlexibleDoubleSerializer].
 */
@Serializable
data class MobileClientDto(
    val id: String,
    @SerialName("business_name") val businessName: String,
    @SerialName("contact_name") val contactName: String? = null,
    val phone: String? = null,
    val address: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val lat: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val lng: Double? = null,
    @SerialName("last_visit_status") val lastVisitStatus: String? = null,
    @SerialName("last_visit_at") val lastVisitAt: String? = null,
)

/**
 * `GET /mobile/sync`. `geofence_radius` es el radio global en metros; puede faltar en
 * servidores antiguos (en cuyo caso se conserva el último valor guardado).
 */
@Serializable
data class MobileSyncDto(
    val clients: List<MobileClientDto> = emptyList(),
    @SerialName("geofence_radius") val geofenceRadius: kotlinx.serialization.json.JsonElement? = null,
)

// ─────────────── Visitas / Evidencias ───────────────

/** Cuerpo validado por `createVisitSchema` (Zod) en el Backoffice. */
@Serializable
data class CreateVisitRequestDto(
    @SerialName("client_id") val clientId: String,
    val status: String,
    val lat: Double,
    val lng: Double,
    val notes: String? = null,
    val timestamp: String,
)

@Serializable
data class VisitResponseDto(
    val id: String,
    val status: String? = null,
)

@Serializable
data class EvidenceResponseDto(
    val id: String,
    @SerialName("visit_id") val visitId: String? = null,
    @SerialName("file_url") val fileUrl: String? = null,
)

/** Metadatos de autenticidad enviados junto a la foto (columna JSONB `evidence.metadata`). */
@Serializable
data class EvidenceMetadataDto(
    val sha256: String,
    @SerialName("accuracy_m") val accuracyMeters: Float,
    @SerialName("is_mock_location") val isMockLocation: Boolean,
    @SerialName("gps_fix_time") val gpsFixTime: String,
    @SerialName("device_time") val deviceTime: String,
    @SerialName("device_model") val deviceModel: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("local_visit_id") val localVisitId: String,
)

/** Acepta números JSON o strings numéricos; devuelve null ante valores vacíos o inválidos. */
object FlexibleDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.doubleOrNull ?: primitive.content.toDoubleOrNull()
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) {
            encoder.encodeNull()
            return
        }
        encoder.encodeDouble(value)
    }
}

// ─────────────── Tracking ───────────────

@Serializable
data class LocationPingDto(
    val lat: Double,
    val lng: Double,
    val timestamp: String,
    val accuracy: Float,
    @SerialName("is_mock_location") val isMockLocation: Boolean,
)

