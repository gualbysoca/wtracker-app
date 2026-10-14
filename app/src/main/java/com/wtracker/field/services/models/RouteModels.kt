package com.wtracker.field.services.models

/** Ruta del día: clientes elegidos por el trabajador, atada a la jornada activa. */
data class DailyRoute(
    val shiftId: String,
    val startedAtMillis: Long,
    val clientIds: Set<String>,
)

/** Resultado de cerrar la ruta: cuántas visitas pendientes se marcaron automáticamente como fallidas. */
data class RouteClosure(val autoFailedVisitCount: Int)

/** Motivo del cierre de ruta. En ambos casos las visitas no completadas se marcan como fallidas. */
enum class RouteClosureReason { FINISH_ROUTE, LOGOUT }
