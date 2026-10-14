package com.wtracker.field.services.auth

import com.wtracker.field.configs.SyncConfig
import com.wtracker.field.services.route.RoutePlanService
import com.wtracker.field.services.sync.SyncScheduler
import com.wtracker.field.utils.AppResult

/**
 * Cierre de sesión desde una ruta en curso. El orden es crítico:
 *  1. Finalizar la ruta (pendientes → "fallido") mientras aún hay sesión: los registros quedan
 *     asociados al usuario y en la cola local.
 *  2. Dar un margen acotado para que la cola se envíe con el JWT vigente.
 *  3. Borrar la sesión. Lo que no alcanzó a enviarse (ej. sin señal) NO se pierde: queda en el
 *     equipo y se envía cuando ese mismo usuario vuelva a iniciar sesión.
 */
class SessionExitService(
    private val routePlanService: RoutePlanService,
    private val syncScheduler: SyncScheduler,
    private val authService: AuthService,
) {

    suspend fun logoutClosingActiveRoute(): AppResult<Unit> {
        if (routePlanService.hasActiveRoute()) {
            val closureResult = routePlanService.finishRoute()
            // Si la ruta no pudo cerrarse (ej. sin GPS), no se cierra sesión: el usuario reintenta.
            if (closureResult is AppResult.Failure) return closureResult
        }

        syncScheduler.requestImmediateSync()
        syncScheduler.awaitImmediateSyncIdle(SyncConfig.LOGOUT_SYNC_WAIT_MS)
        authService.logout()
        return AppResult.Success(Unit)
    }
}
