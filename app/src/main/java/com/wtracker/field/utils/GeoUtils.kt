package com.wtracker.field.utils

import com.wtracker.field.services.models.GeoCoordinate
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val METERS_PER_KILOMETER = 1_000.0
    private const val COORDINATE_DECIMALS = 6

    fun distanceMeters(from: GeoCoordinate, to: GeoCoordinate): Double {
        val deltaLatitude = Math.toRadians(to.latitude - from.latitude)
        val deltaLongitude = Math.toRadians(to.longitude - from.longitude)
        val haversine = sin(deltaLatitude / 2).pow(2) +
            cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) *
            sin(deltaLongitude / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(haversine))
    }

    fun totalPathMeters(path: List<GeoCoordinate>): Double =
        path.zipWithNext { from, to -> distanceMeters(from, to) }.sum()

    fun formatDistance(meters: Double): String {
        if (meters < METERS_PER_KILOMETER) return "${meters.toInt()} m"
        return String.format(Locale("es"), "%.1f km", meters / METERS_PER_KILOMETER)
    }

    fun formatCoordinate(coordinate: GeoCoordinate): String =
        String.format(
            Locale.US,
            "%.${COORDINATE_DECIMALS}f, %.${COORDINATE_DECIMALS}f",
            coordinate.latitude,
            coordinate.longitude,
        )
}
