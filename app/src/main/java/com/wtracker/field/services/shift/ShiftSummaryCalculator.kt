package com.wtracker.field.services.shift

import com.wtracker.field.services.local.LocationPingEntity
import com.wtracker.field.services.local.ShiftEntity
import com.wtracker.field.services.local.StopEventEntity
import com.wtracker.field.services.local.SyncStates
import com.wtracker.field.services.local.VisitRecordEntity
import com.wtracker.field.services.local.ClientEntity
import com.wtracker.field.services.models.GeoCoordinate
import com.wtracker.field.services.models.ShiftSummary
import com.wtracker.field.services.models.StopEvent
import com.wtracker.field.services.models.StopEventType
import com.wtracker.field.services.models.VisitStatus
import com.wtracker.field.utils.GeoUtils

/** Cálculo puro de métricas de jornada a partir de los registros locales. */
object ShiftSummaryCalculator {

    fun calculate(
        shift: ShiftEntity,
        pings: List<LocationPingEntity>,
        stops: List<StopEventEntity>,
        visits: List<VisitRecordEntity>,
        clients: List<ClientEntity>,
        geofenceRadiusMeters: Int,
    ): ShiftSummary {
        val trustedPath = pings.filterNot { it.isMockLocation }.map { GeoCoordinate(it.latitude, it.longitude) }
        val visitsInShift = visits.filter { it.recordedAtMillis >= shift.startedAtMillis }
        val clientsById = clients.associateBy { it.id }

        val outOfRangeCount = visitsInShift.count { visit ->
            if (visit.status == VisitStatus.EN_ROUTE.apiValue) return@count false
            val client = clientsById[visit.clientId]
            if (client?.latitude != null && client.longitude != null) {
                val distance = GeoUtils.distanceMeters(
                    GeoCoordinate(visit.latitude, visit.longitude),
                    GeoCoordinate(client.latitude, client.longitude)
                )
                distance > geofenceRadiusMeters
            } else false
        }

        val sortedVisits = visitsInShift.sortedBy { it.recordedAtMillis }
        var currentStreak = 0
        var maxStreak = 0
        for (visit in sortedVisits) {
            if (visit.status == VisitStatus.VISITED.apiValue) {
                currentStreak++
                if (currentStreak > maxStreak) {
                    maxStreak = currentStreak
                }
            } else if (visit.status == VisitStatus.FAILED.apiValue) {
                currentStreak = 0
            }
        }

        return ShiftSummary(
            shiftId = shift.id,
            startedAtMillis = shift.startedAtMillis,
            endedAtMillis = shift.endedAtMillis,
            distanceMeters = GeoUtils.totalPathMeters(trustedPath),
            locationPingCount = pings.size,
            lastPingAtMillis = pings.lastOrNull()?.recordedAtMillis,
            visitedCount = visitsInShift.count { it.status == VisitStatus.VISITED.apiValue },
            failedCount = visitsInShift.count { it.status == VisitStatus.FAILED.apiValue },
            enRouteCount = visitsInShift.count { it.status == VisitStatus.EN_ROUTE.apiValue },
            pendingSyncCount = visitsInShift.count {
                it.syncState == SyncStates.PENDING_VISIT || it.syncState == SyncStates.PENDING_EVIDENCE
            },
            outOfRangeEvidenceCount = outOfRangeCount,
            totalTasksCount = clients.size,
            maxSuccessStreak = maxStreak,
            currentSuccessStreak = currentStreak,
            stops = stops.map { it.toStopEvent() },
        )
    }

    private fun StopEventEntity.toStopEvent() = StopEvent(
        id = id,
        type = StopEventType.entries.firstOrNull { it.name == type } ?: StopEventType.STOP,
        coordinate = GeoCoordinate(latitude, longitude),
        startedAtMillis = startedAtMillis,
        endedAtMillis = endedAtMillis,
    )
}
