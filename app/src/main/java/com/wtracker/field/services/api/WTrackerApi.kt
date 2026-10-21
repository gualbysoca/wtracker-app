package com.wtracker.field.services.api

import com.wtracker.field.services.api.dto.ApiEnvelopeDto
import com.wtracker.field.services.api.dto.CreateVisitRequestDto
import com.wtracker.field.services.api.dto.EvidenceResponseDto
import com.wtracker.field.services.api.dto.LoginRequestDto
import com.wtracker.field.services.api.dto.LoginResponseDto
import com.wtracker.field.services.api.dto.LogoutRequestDto
import com.wtracker.field.services.api.dto.MobileClientDto
import com.wtracker.field.services.api.dto.MobileSyncDto
import com.wtracker.field.services.api.dto.RefreshRequestDto
import com.wtracker.field.services.api.dto.RefreshResponseDto
import com.wtracker.field.services.api.dto.SettingsDto
import com.wtracker.field.services.api.dto.ShiftRequestDto
import com.wtracker.field.services.api.dto.UpdateClientRequestDto
import com.wtracker.field.services.api.dto.VisitResponseDto
import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/** Endpoints del Backoffice consumidos por la app de campo (rutas relativas a `/api/v1/`). */
interface WTrackerApi {

    @POST(ApiPaths.LOGIN)
    suspend fun login(@Body body: LoginRequestDto): ApiEnvelopeDto<LoginResponseDto>

    @POST(ApiPaths.LOGOUT)
    suspend fun logout(@Body body: LogoutRequestDto): ApiEnvelopeDto<JsonElement>

    @POST(ApiPaths.MOBILE_SHIFT)
    suspend fun updateShiftStatus(@Body body: ShiftRequestDto): ApiEnvelopeDto<JsonElement>

    @GET(ApiPaths.MOBILE_CLIENTS)
    suspend fun getClients(): ApiEnvelopeDto<List<MobileClientDto>>

    @GET(ApiPaths.MOBILE_SYNC)
    suspend fun getSync(): ApiEnvelopeDto<MobileSyncDto>

    @GET(ApiPaths.SETTINGS)
    suspend fun getSettings(): ApiEnvelopeDto<SettingsDto>

    @POST(ApiPaths.MOBILE_VISITS)
    suspend fun createVisit(@Body body: CreateVisitRequestDto): ApiEnvelopeDto<VisitResponseDto>

    @Multipart
    @POST(ApiPaths.MOBILE_VISIT_EVIDENCE)
    suspend fun uploadEvidence(
        @Path("visitId") visitId: String,
        @Part file: MultipartBody.Part,
        @Part("lat") latitude: RequestBody,
        @Part("lng") longitude: RequestBody,
        @Part("captured_at") capturedAt: RequestBody,
        @Part("type") type: RequestBody,
        @Part("metadata") metadata: RequestBody,
    ): ApiEnvelopeDto<EvidenceResponseDto>

    @POST(ApiPaths.MOBILE_LOCATIONS)
    suspend fun uploadLocations(@Body body: List<com.wtracker.field.services.api.dto.LocationPingDto>): ApiEnvelopeDto<JsonElement>

    @POST("mobile/clients/{id}/location")
    suspend fun updateClientLocation(
        @Path("id") clientId: String,
        @Body body: com.wtracker.field.services.api.dto.ClientLocationUpdateDto,
    ): ApiEnvelopeDto<JsonElement>

    @PATCH("mobile/clients/{id}")
    suspend fun updateClient(
        @Path("id") clientId: String,
        @Body body: UpdateClientRequestDto,
    ): ApiEnvelopeDto<JsonElement>
}

/**
 * API aislada para el refresh de token. Usa un cliente HTTP SIN authenticator para
 * evitar recursión infinita cuando el propio refresh devuelve 401.
 */
interface AuthRefreshApi {
    @POST(ApiPaths.REFRESH)
    fun refresh(@Body body: RefreshRequestDto): Call<ApiEnvelopeDto<RefreshResponseDto>>
}

object ApiPaths {
    const val LOGIN = "auth/mobile/login"
    const val REFRESH = "auth/refresh"
    const val LOGOUT = "auth/logout"
    const val MOBILE_CLIENTS = "mobile/clients"
    const val MOBILE_SYNC = "mobile/sync"
    const val SETTINGS = "settings"
    const val MOBILE_VISITS = "mobile/visits"
    const val MOBILE_VISIT_EVIDENCE = "mobile/visits/{visitId}/evidence"
    const val MOBILE_LOCATIONS = "mobile/locations"
    const val MOBILE_SHIFT = "mobile/shift"
    const val EVIDENCE_FILE_PART = "file"
}

/** Extrae `data` del envoltorio o falla con el mensaje del servidor. */
fun <T> ApiEnvelopeDto<T>.requireData(): T =
    data ?: throw IllegalStateException(message ?: "La respuesta del servidor no contiene datos.")
