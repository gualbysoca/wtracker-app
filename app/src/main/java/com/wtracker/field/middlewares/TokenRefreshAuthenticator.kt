package com.wtracker.field.middlewares

import com.wtracker.field.services.api.AuthRefreshApi
import com.wtracker.field.services.api.dto.RefreshRequestDto
import com.wtracker.field.services.auth.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

/**
 * Renueva el access token (TTL de 15 min en el Backoffice) ante un 401 y reintenta la
 * petición original. Si el refresh token es rechazado, cierra la sesión; si sólo falló
 * la red, conserva la sesión para no expulsar al trabajador en zonas sin cobertura.
 */
class TokenRefreshAuthenticator(
    private val sessionStore: SessionStore,
    private val refreshApi: AuthRefreshApi,
) : Authenticator {

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val failedToken = response.request.bearerTokenOrNull() ?: return null
        if (countPriorResponses(response) >= MAX_AUTH_ATTEMPTS) return null

        synchronized(refreshLock) {
            val session = sessionStore.peekSessionBlocking() ?: return null

            // Otro hilo ya renovó el token mientras esperábamos el lock.
            if (session.accessToken != failedToken) {
                return response.request.withBearerToken(session.accessToken)
            }

            return when (val outcome = requestNewAccessToken(session.refreshToken)) {
                is RefreshOutcome.Refreshed -> {
                    runBlocking { sessionStore.updateAccessToken(outcome.accessToken) }
                    response.request.withBearerToken(outcome.accessToken)
                }
                RefreshOutcome.Rejected -> {
                    runBlocking { sessionStore.clearSession() }
                    null
                }
                RefreshOutcome.NetworkUnavailable -> null
            }
        }
    }

    private fun requestNewAccessToken(refreshToken: String): RefreshOutcome = try {
        val refreshResponse = refreshApi.refresh(RefreshRequestDto(refreshToken)).execute()
        val newAccessToken = refreshResponse.body()?.data?.accessToken
        when {
            refreshResponse.isSuccessful && newAccessToken != null -> RefreshOutcome.Refreshed(newAccessToken)
            refreshResponse.code() in HTTP_SERVER_ERROR_RANGE -> RefreshOutcome.NetworkUnavailable
            else -> RefreshOutcome.Rejected
        }
    } catch (ioException: IOException) {
        RefreshOutcome.NetworkUnavailable
    }

    private fun countPriorResponses(response: Response): Int =
        generateSequence(response.priorResponse) { it.priorResponse }.count() + 1

    private sealed interface RefreshOutcome {
        data class Refreshed(val accessToken: String) : RefreshOutcome
        data object Rejected : RefreshOutcome
        data object NetworkUnavailable : RefreshOutcome
    }

    private companion object {
        const val MAX_AUTH_ATTEMPTS = 2
        val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}
