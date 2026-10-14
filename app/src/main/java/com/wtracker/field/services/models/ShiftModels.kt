package com.wtracker.field.services.models

enum class StopEventType(val label: String) {
    STOP(label = "Parada"),
    INACTIVITY(label = "Inactividad"),
}

data class StopEvent(
    val id: Long,
    val type: StopEventType,
    val coordinate: GeoCoordinate,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
) {
    val isOngoing: Boolean get() = endedAtMillis == null

    fun durationMillis(nowMillis: Long): Long = (endedAtMillis ?: nowMillis) - startedAtMillis
}

data class ShiftSummary(
    val shiftId: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val distanceMeters: Double,
    val locationPingCount: Int,
    val lastPingAtMillis: Long?,
    val visitedCount: Int,
    val failedCount: Int,
    val enRouteCount: Int,
    val pendingSyncCount: Int,
    val outOfRangeEvidenceCount: Int,
    val totalTasksCount: Int,
    val maxSuccessStreak: Int,
    val currentSuccessStreak: Int,
    val stops: List<StopEvent>,
) {
    val isActive: Boolean get() = endedAtMillis == null

    fun elapsedMillis(nowMillis: Long): Long = (endedAtMillis ?: nowMillis) - startedAtMillis

    fun totalStoppedMillis(nowMillis: Long): Long = stops.sumOf { it.durationMillis(nowMillis) }
}
