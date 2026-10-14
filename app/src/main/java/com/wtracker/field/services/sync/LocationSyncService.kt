package com.wtracker.field.services.sync

import com.wtracker.field.services.api.WTrackerApi
import com.wtracker.field.services.api.dto.LocationPingDto
import com.wtracker.field.services.auth.SessionStore
import com.wtracker.field.services.local.LocationPingDao
import com.wtracker.field.utils.ApiErrorMapper
import com.wtracker.field.utils.AppResult
import com.wtracker.field.utils.TimeUtils
import com.wtracker.field.utils.safeApiCall

class LocationSyncService(
    private val api: WTrackerApi,
    private val locationPingDao: LocationPingDao,
    private val sessionStore: SessionStore,
    private val errorMapper: ApiErrorMapper,
) {
    suspend fun syncPendingLocations(): SyncOutcome {
        if (sessionStore.ensureLoaded() == null) return SyncOutcome.NO_SESSION

        var hasMorePings = true
        var isCompleted = true

        while (hasMorePings) {
            val batch = locationPingDao.getPendingBatch(BATCH_SIZE)
            if (batch.isEmpty()) {
                hasMorePings = false
                break
            }

            val payload = batch.map { ping ->
                LocationPingDto(
                    lat = ping.latitude,
                    lng = ping.longitude,
                    timestamp = TimeUtils.toIsoUtc(ping.recordedAtMillis),
                    accuracy = ping.accuracyMeters,
                    isMockLocation = ping.isMockLocation,
                )
            }

            val result = safeApiCall(errorMapper) {
                api.uploadLocations(payload)
            }

            if (result is AppResult.Success) {
                val idsToDelete = batch.map { it.id }
                locationPingDao.deleteBatch(idsToDelete)
            } else {
                val error = (result as AppResult.Failure).error
                if (error is com.wtracker.field.utils.AppError.Rejected) {
                    // Si el servidor rechaza permanentemente (ej. 400 Bad Request o 404),
                    // descartamos el lote para evitar un bucle infinito de reintentos.
                    val idsToDelete = batch.map { it.id }
                    locationPingDao.deleteBatch(idsToDelete)
                } else {
                    isCompleted = false
                    hasMorePings = false // Detenerse en errores transitorios y reintentar luego
                }
            }
        }

        return if (isCompleted) SyncOutcome.COMPLETED else SyncOutcome.RETRY_LATER
    }

    companion object {
        private const val BATCH_SIZE = 100
    }
}
