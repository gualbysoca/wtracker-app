package com.wtracker.field.services.models

import kotlinx.serialization.Serializable

/** Radio global usado mientras el servidor no haya entregado ninguno. */
const val DEFAULT_GEOFENCE_RADIUS_METERS = 100

@Serializable
data class BusinessSettings(
    val geofenceRadiusMeters: Int = DEFAULT_GEOFENCE_RADIUS_METERS,
    val gamificationEnabled: Boolean = true,
    val gamificationSniper: Int = 10,
    val gamificationOnFire: Int = 20,
    val gamificationBlackCloud: Int = 5,
    val gamificationTurtle: Int = 60,
)
