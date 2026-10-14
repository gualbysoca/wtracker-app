package com.wtracker.field.services.route

import com.wtracker.field.configs.AppPreferencesStore
import com.wtracker.field.configs.LocalTransactionRunner
import com.wtracker.field.services.api.WTrackerApi
import com.wtracker.field.services.api.dto.ShiftRequestDto
import com.wtracker.field.services.clients.ClientService
import com.wtracker.field.services.local.PlannedVisitDao
import com.wtracker.field.services.local.PlannedVisitEntity
import com.wtracker.field.services.location.LocationProvider
import com.wtracker.field.services.models.BusinessSettings
import com.wtracker.field.services.models.DailyRoute
import com.wtracker.field.services.models.FieldClient
import com.wtracker.field.services.models.RouteClosure
import com.wtracker.field.services.models.VisitStatus
import com.wtracker.field.services.shift.ShiftService
import com.wtracker.field.services.visits.VisitService
import com.wtracker.field.utils.ApiErrorMapper
import com.wtracker.field.utils.AppError
import com.wtracker.field.utils.AppResult
import com.wtracker.field.utils.TimeProvider
import com.wtracker.field.utils.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Ciclo de vida de la ruta del día. La ruta está atada a la jornada:
 *  - Iniciar ruta  → abre la jornada + guarda los clientes elegidos + arranca el rastreo GPS.
 *  - Agregar       → suma clientes a la jornada activa ("en caliente").
 *  - Finalizar     → marca como fallidos los clientes no completados y cierra la jornada.
 */
class RoutePlanService(
    private val api: WTrackerApi,
    private val errorMapper: ApiErrorMapper,
    private val plannedVisitDao: PlannedVisitDao,
    private val shiftService: ShiftService,
    private val clientService: ClientService,
    private val visitService: VisitService,
    private val locationProvider: LocationProvider,
    private val transactionRunner: LocalTransactionRunner,
    private val timeProvider: TimeProvider,
    private val appPreferencesStore: AppPreferencesStore,
) {

    fun observeHasActiveRoute(): Flow<Boolean> =
        shiftService.observeActiveShift().map { it != null }.distinctUntilChanged()

    suspend fun hasActiveRoute(): Boolean = shiftService.findActiveShift() != null

    fun observeActiveRoute(): Flow<DailyRoute?> = shiftService.observeActiveShift().flatMapLatest { shift ->
        if (shift == null) return@flatMapLatest flowOf(null)
        plannedVisitDao.observeClientIds(shift.id).map { clientIds ->
            DailyRoute(shiftId = shift.id, startedAtMillis = shift.startedAtMillis, clientIds = clientIds.toSet())
        }
    }

    /** Clientes de la ruta activa con su estado del día. Vacío si no hay ruta. */
    fun observeRouteClients(): Flow<List<FieldClient>> =
        combine(clientService.observeFieldClients(), observeActiveRoute()) { clients, route ->
            if (route == null) return@combine emptyList()
            clients.filter { it.id in route.clientIds }
        }

    suspend fun startRoute(clientIds: Set<String>): AppResult<DailyRoute> {
        if (clientIds.isEmpty()) {
            return AppResult.Failure(AppError.Validation("Selecciona al menos un cliente para iniciar tu ruta."))
        }

        val apiResult = safeApiCall(errorMapper) {
            val response = api.updateShiftStatus(ShiftRequestDto(isOnShift = true))
            if (!response.success) {
                throw Exception(response.message ?: "No se pudo iniciar la ruta en el servidor.")
            }
            val settingsResponse = try {
                api.getSettings()
            } catch (e: Exception) {
                null
            }
            if (settingsResponse?.success == true && settingsResponse.data != null) {
                val dto = settingsResponse.data
                
                fun kotlinx.serialization.json.JsonElement?.safeInt(): Int? {
                    if (this == null) return null
                    return try {
                        val primitive = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
                        primitive.content.toIntOrNull()
                    } catch (e: Exception) {
                        null
                    }
                }

                // El radio de geovalla proviene únicamente de /mobile/sync; aquí se conserva el guardado.
                val currentRadius = appPreferencesStore.businessSettingsFlow.first().geofenceRadiusMeters
                val settings = BusinessSettings(
                    geofenceRadiusMeters = currentRadius,
                    gamificationEnabled = dto.gamificationEnabled,
                    gamificationSniper = dto.gamificationSniper.safeInt() ?: 10,
                    gamificationOnFire = dto.gamificationOnFire.safeInt() ?: 20,
                    gamificationBlackCloud = dto.gamificationBlackCloud.safeInt() ?: 5,
                    gamificationTurtle = dto.gamificationTurtle.safeInt() ?: 60,
                )
                appPreferencesStore.setBusinessSettings(settings)
            }
            response
        }
        if (apiResult is AppResult.Failure) {
            return apiResult
        }

        // Jornada y clientes se guardan juntos: la hoja de ruta nunca debe verse con una ruta a medias.
        val openShiftResult = transactionRunner.runInTransaction {
            val shiftResult = shiftService.openShift()
            if (shiftResult is AppResult.Success) {
                plannedVisitDao.insertAll(buildPlannedVisits(shiftResult.data.id, clientIds))
            }
            shiftResult
        }
        if (openShiftResult is AppResult.Failure) return openShiftResult

        val shift = (openShiftResult as AppResult.Success).data
        shiftService.startTracking()
        return AppResult.Success(DailyRoute(shift.id, shift.startedAtMillis, clientIds))
    }

    suspend fun addClientsToRoute(clientIds: Set<String>): AppResult<Int> {
        if (clientIds.isEmpty()) {
            return AppResult.Failure(AppError.Validation("Selecciona al menos un cliente para agregarlo a tu ruta."))
        }
        val activeShift = shiftService.findActiveShift()
            ?: return AppResult.Failure(AppError.Validation("No tienes una ruta activa. Inicia una ruta primero."))

        plannedVisitDao.insertAll(buildPlannedVisits(activeShift.id, clientIds))
        return AppResult.Success(clientIds.size)
    }

    suspend fun finishRoute(): AppResult<RouteClosure> {
        val activeShift = shiftService.findActiveShift()
            ?: return AppResult.Failure(AppError.Validation("No tienes una ruta activa para finalizar."))

        try {
            safeApiCall(errorMapper) {
                api.updateShiftStatus(ShiftRequestDto(isOnShift = false))
            }
        } catch (e: Exception) {
            // Ignoramos errores de red en el cierre para no bloquear al usuario si pierde señal al final del día.
            // Si el backend requiere que esto nunca falle, podríamos cambiarlo para retornar un error.
            // Según los requerimientos, lo principal es registrarlo localmente y hacer la llamada (best effort o hard fail).
            // Lo dejamos pasar para permitir finalizar localmente (el backend lo cerrará en medianoche de todos modos o mediante sync).
        }

        val pendingClientIds = findPendingClientIds(activeShift.id)
        if (pendingClientIds.isNotEmpty()) {
            val failureResult = registerPendingAsFailed(pendingClientIds)
            if (failureResult is AppResult.Failure) return failureResult
        }

        shiftService.endActiveShift()
        return AppResult.Success(RouteClosure(autoFailedVisitCount = pendingClientIds.size))
    }

    /**
     * Saca un cliente de la ruta activa. Es una operación 100% local: sólo se permite mientras el cliente
     * no tenga ninguna marcación (en ruta, visitado o fallido), por lo que el backend nunca se entera.
     */
    suspend fun removeClientFromRoute(clientId: String): AppResult<Unit> {
        val activeShift = shiftService.findActiveShift()
            ?: return AppResult.Failure(AppError.Validation("No tienes una ruta activa."))

        val client = clientService.observeFieldClients().first().firstOrNull { it.id == clientId }
        if (client != null && client.currentStatus != VisitStatus.PENDING) {
            return AppResult.Failure(AppError.Validation("Sólo puedes quitar tareas que aún no han sido iniciadas."))
        }

        plannedVisitDao.deleteClient(activeShift.id, clientId)
        return AppResult.Success(Unit)
    }

    private suspend fun registerPendingAsFailed(pendingClientIds: Set<String>): AppResult<Int> {
        val locationResult = locationProvider.getFreshLocation()
        if (locationResult is AppResult.Failure) {
            return AppResult.Failure(
                AppError.Location("No pudimos obtener tu ubicación para cerrar la ruta. Intenta nuevamente en un lugar abierto."),
            )
        }
        val location = (locationResult as AppResult.Success).data
        return visitService.registerRouteClosureFailures(pendingClientIds, location)
    }

    /** Clientes de la ruta sin estado final hoy. Ignora clientes que ya no existen en el directorio. */
    private suspend fun findPendingClientIds(shiftId: String): Set<String> {
        val plannedClientIds = plannedVisitDao.findClientIds(shiftId).toSet()
        if (plannedClientIds.isEmpty()) return emptySet()

        return clientService.observeFieldClients().first()
            .filter { it.id in plannedClientIds && !it.currentStatus.isFinal }
            .map { it.id }
            .toSet()
    }

    private fun buildPlannedVisits(shiftId: String, clientIds: Set<String>): List<PlannedVisitEntity> {
        val addedAtMillis = timeProvider.nowMillis()
        return clientIds.map { clientId -> PlannedVisitEntity(shiftId = shiftId, clientId = clientId, addedAtMillis = addedAtMillis) }
    }
}
