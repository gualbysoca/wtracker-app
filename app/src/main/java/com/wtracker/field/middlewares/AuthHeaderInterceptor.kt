package com.wtracker.field.middlewares

import com.wtracker.field.services.api.ApiPaths
import com.wtracker.field.services.auth.SessionStore
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal const val AUTHORIZATION_HEADER = "Authorization"
internal const val BEARER_PREFIX = "Bearer "

internal fun Request.withBearerToken(accessToken: String): Request =
    newBuilder().header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$accessToken").build()

internal fun Request.bearerTokenOrNull(): String? =
    header(AUTHORIZATION_HEADER)?.removePrefix(BEARER_PREFIX)

/** Equivalente móvil de `authenticate` del Backoffice: adjunta el JWT a cada petición protegida. */
class AuthHeaderInterceptor(private val sessionStore: SessionStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.endsWith(ApiPaths.LOGIN)) return chain.proceed(request)

        val accessToken = sessionStore.peekSessionBlocking()?.accessToken
            ?: return chain.proceed(request)

        return chain.proceed(request.withBearerToken(accessToken))
    }
}
