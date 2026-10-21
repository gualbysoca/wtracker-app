package com.wtracker.field.services.clients

import com.wtracker.field.configs.AppPreferencesStore
import com.wtracker.field.services.api.WTrackerApi
import com.wtracker.field.services.api.dto.UpdateClientRequestDto
import com.wtracker.field.services.api.requireData
import com.wtracker.field.services.auth.SessionStore
import com.wtracker.field.services.local.ClientDao
import com.wtracker.field.services.local.VisitRecordDao
import com.wtracker.field.services.models.FieldClient
import com.wtracker.field.utils.ApiErrorMapper
import com.wtracker.field.utils.AppResult
import com.wtracker.field.utils.TimeProvider
import com.wtracker.field.utils.TimeUtils
import com.wtracker.field.utils.map
import com.wtracker.field.utils.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.wtracker.field.services.shift.ShiftService
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Repositorio offline-first del directorio de clientes: la UI siempre lee de Room. */
class ClientService(
    private val api: WTrackerApi,
    private val clientDao: ClientDao,
    private val visitRecordDao: VisitRecordDao,
    private val sessionStore: SessionStore,
    private val shiftService: ShiftService,
    private val errorMapper: ApiErrorMapper,
    private val timeProvider: TimeProvider,
    private val appPreferencesStore: AppPreferencesStore,
) {

    fun observeFieldClients(): Flow<List<FieldClient>> = sessionStore.sessionFlow.flatMapLatest { session ->
        if (session == null) return@flatMapLatest flowOf(emptyList())
        shiftService.observeActiveShift().flatMapLatest { shift ->
            val latestRecordsFlow = if (shift != null) {
                visitRecordDao.observeLatestPerClientSince(session.userId, shift.startedAtMillis)
            } else {
                flowOf(emptyList())
            }
            combine(
                clientDao.observeAll(),
                latestRecordsFlow,
            ) { clients, latestRecords ->
                val latestByClientId = latestRecords.associateBy { it.clientId }
                clients.map { client -> client.toFieldClient(latestByClientId[client.id]) }
            }
        }
    }

    fun observeFieldClient(clientId: String): Flow<FieldClient?> =
        observeFieldClients().map { clients -> clients.firstOrNull { it.id == clientId } }

    /**
     * Actualiza los datos del cliente tanto en la base de datos local (Room)
     * como en el backend a través de PATCH /mobile/clients/:id.
     */
    suspend fun updateClientDetails(
        clientId: String,
        businessName: String? = null,
        contactName: String?,
        phone: String?,
        address: String?,
        deliveryAddress: String?,
        stallNumber: String?,
        clientCode: String?,
        lat: Double?,
        lng: Double?,
    ): AppResult<Unit> {
        clientDao.updateClientDetails(
            clientId = clientId,
            businessName = businessName,
            contactName = contactName,
            phone = phone,
            address = address,
            deliveryAddress = deliveryAddress,
            stallNumber = stallNumber,
            clientCode = clientCode,
            lat = lat,
            lng = lng,
        )

        return try {
            safeApiCall(errorMapper) {
                api.updateClient(
                    clientId = clientId,
                    body = UpdateClientRequestDto(
                        businessName = businessName,
                        mercado = businessName,
                        contactName = contactName,
                        phone = phone,
                        address = address,
                        deliveryAddress = deliveryAddress,
                        stallNumber = stallNumber,
                        clientCode = clientCode,
                        lat = lat,
                        lng = lng,
                    ),
                )
            }.map { Unit }
        } catch (_: Exception) {
            AppResult.Success(Unit)
        }
    }

    /**
     * Actualiza las coordenadas del cliente en la base de datos local y
     * sincroniza de forma segura con el backend.
     */
    suspend fun updateClientCoordinates(clientId: String, lat: Double, lng: Double): AppResult<Unit> {
        clientDao.updateCoordinates(clientId, lat, lng)
        return try {
            safeApiCall(errorMapper) {
                api.updateClient(
                    clientId = clientId,
                    body = UpdateClientRequestDto(lat = lat, lng = lng),
                )
            }.map { Unit }
        } catch (_: Exception) {
            AppResult.Success(Unit)
        }
    }

    /**
     * `GET /mobile/sync`: reemplaza la caché de clientes y refresca el radio global de geovalla.
     * Si el servidor no envía `geofence_radius`, se conserva el último valor guardado.
     */
    suspend fun downloadClients(): AppResult<Int> {
        val downloadResult = safeApiCall(errorMapper) { api.getSync().requireData() }
        return downloadResult.map { sync ->
            val downloadedAtMillis = timeProvider.nowMillis()
            clientDao.replaceAll(sync.clients.map { it.toEntity(downloadedAtMillis) })
            parseGeofenceRadiusMeters(sync.geofenceRadius)?.let { appPreferencesStore.setGeofenceRadiusMeters(it) }
            sync.clients.size
        }
    }

    private fun parseGeofenceRadiusMeters(raw: JsonElement?): Int? {
        val primitive = raw as? JsonPrimitive ?: return null
        val meters = primitive.content.toDoubleOrNull()?.toInt() ?: return null
        return meters.takeIf { it > 0 }
    }

    private fun startOfTodayMillis(): Long =
        TimeUtils.startOfDayMillis(timeProvider.nowMillis(), timeProvider.zone())
}
