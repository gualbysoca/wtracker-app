package com.wtracker.field.services.location

import com.wtracker.field.services.models.CapturedLocation
import com.wtracker.field.utils.AppResult
import kotlinx.coroutines.flow.Flow

/** Abstracción del proveedor GPS: la app no depende directamente de Google Play Services. */
interface LocationProvider {

    /** Lectura nueva de alta precisión (no reutiliza caché antigua). */
    suspend fun getFreshLocation(): AppResult<CapturedLocation>

    /** Flujo continuo de lecturas; falla con SecurityException si se revoca el permiso. */
    fun observeLocationUpdates(intervalMillis: Long): Flow<CapturedLocation>

    fun hasPreciseLocationPermission(): Boolean
}
