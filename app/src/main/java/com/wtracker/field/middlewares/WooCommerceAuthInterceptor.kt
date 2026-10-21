package com.wtracker.field.middlewares

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor de autenticación para la REST API de WooCommerce (v3).
 * Inyecta el header Authorization con esquema HTTP Basic (Consumer Key y Consumer Secret).
 */
class WooCommerceAuthInterceptor(
    private val consumerKey: String,
    private val consumerSecret: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (consumerKey.isBlank() || consumerSecret.isBlank()) {
            return chain.proceed(originalRequest)
        }

        val credentials = Credentials.basic(consumerKey, consumerSecret)
        val authenticatedRequest = originalRequest.newBuilder()
            .header(AUTHORIZATION_HEADER, credentials)
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
