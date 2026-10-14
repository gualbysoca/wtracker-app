package com.wtracker.field.services.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.wtracker.field.configs.LocationConfig
import com.wtracker.field.services.models.CapturedLocation
import com.wtracker.field.services.models.GeoCoordinate
import com.wtracker.field.utils.AppError
import com.wtracker.field.utils.AppResult
import com.wtracker.field.utils.TimeProvider
import com.wtracker.field.utils.hasPermissions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Implementación sobre Fused Location Provider (Google Play Services). */
class FusedLocationProvider(
    context: Context,
    private val timeProvider: TimeProvider,
) : LocationProvider {

    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)

    override fun hasPreciseLocationPermission(): Boolean =
        appContext.hasPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION))

    @SuppressLint("MissingPermission")
    override suspend fun getFreshLocation(): AppResult<CapturedLocation> {
        if (!hasPreciseLocationPermission()) {
            return AppResult.Failure(AppError.Permission("Se requiere el permiso de ubicación precisa."))
        }

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(LocationConfig.FRESH_FIX_MAX_AGE_MS)
            .setDurationMillis(LocationConfig.FIX_TIMEOUT_MS)
            .build()
        val cancellationSource = CancellationTokenSource()

        return try {
            val location = fusedClient.getCurrentLocation(request, cancellationSource.token).await(cancellationSource)
                ?: return AppResult.Failure(
                    AppError.Location("No se pudo obtener tu ubicación. Verifica que el GPS esté activado."),
                )
            AppResult.Success(location.toCapturedLocation())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (securityException: SecurityException) {
            AppResult.Failure(AppError.Permission("El permiso de ubicación fue revocado."))
        } catch (exception: Exception) {
            AppResult.Failure(AppError.Location("Error al leer el GPS: ${exception.message.orEmpty()}"))
        }
    }

    @SuppressLint("MissingPermission")
    override fun observeLocationUpdates(intervalMillis: Long): Flow<CapturedLocation> = callbackFlow {
        if (!hasPreciseLocationPermission()) {
            close(SecurityException("Permiso de ubicación precisa no concedido"))
            awaitClose()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toCapturedLocation()) }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }

    private fun Location.toCapturedLocation() = CapturedLocation(
        coordinate = GeoCoordinate(latitude, longitude),
        accuracyMeters = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        isMockLocation = isMockReading(),
        fixTimeMillis = time,
        capturedAtMillis = timeProvider.nowMillis(),
    )

    private fun Location.isMockReading(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else @Suppress("DEPRECATION") isFromMockProvider
}
