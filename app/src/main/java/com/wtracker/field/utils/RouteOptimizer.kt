package com.wtracker.field.utils

import com.wtracker.field.services.models.FieldClient
import com.wtracker.field.services.models.GeoCoordinate
import com.wtracker.field.services.models.RouteStop
import com.wtracker.field.services.models.VisitStatus

/**
 * Construye la hoja de ruta del día:
 *  1. Clientes "en ruta" primero (es el destino actual del trabajador).
 *  2. Pendientes con coordenadas ordenados por vecino más cercano desde la posición actual.
 *  3. Pendientes sin coordenadas (alfabético) — no se pueden optimizar.
 *  4. Completados (visitado/fallido) al final, el más reciente primero.
 *
 * Vecino más cercano es O(n²) — suficiente para el volumen diario de un trabajador (< 500 clientes).
 */
object RouteOptimizer {

    fun buildRoute(clients: List<FieldClient>, origin: GeoCoordinate?): List<RouteStop> {
        val (completed, open) = clients.partition { it.currentStatus.isFinal }
        val (enRoute, pending) = open.partition { it.currentStatus == VisitStatus.EN_ROUTE }
        val (locatable, unlocatable) = pending.partition { it.coordinate != null }

        val orderedClients = enRoute.sortedBy { it.businessName } +
            orderByNearestNeighbor(locatable, origin) +
            unlocatable.sortedBy { it.businessName } +
            completed.sortedByDescending { it.statusUpdatedAtMillis ?: 0L }

        return orderedClients.mapIndexed { index, client ->
            RouteStop(
                order = index + 1,
                client = client,
                distanceFromUserMeters = distanceOrNull(origin, client.coordinate),
            )
        }
    }

    private fun orderByNearestNeighbor(clients: List<FieldClient>, origin: GeoCoordinate?): List<FieldClient> {
        if (clients.isEmpty()) return emptyList()

        val remaining = clients.sortedBy { it.businessName }.toMutableList()
        val ordered = ArrayList<FieldClient>(clients.size)
        var cursor: GeoCoordinate = origin ?: requireNotNull(remaining.first().coordinate)

        while (remaining.isNotEmpty()) {
            val nearest = remaining.minBy { GeoUtils.distanceMeters(cursor, requireNotNull(it.coordinate)) }
            ordered += nearest
            remaining -= nearest
            cursor = requireNotNull(nearest.coordinate)
        }
        return ordered.toList()
    }

    private fun distanceOrNull(origin: GeoCoordinate?, target: GeoCoordinate?): Double? {
        if (origin == null || target == null) return null
        return GeoUtils.distanceMeters(origin, target)
    }
}
