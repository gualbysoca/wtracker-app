package com.wtracker.field.configs

import com.wtracker.field.BuildConfig
import com.wtracker.field.middlewares.AUTHORIZATION_HEADER
import com.wtracker.field.middlewares.AuthHeaderInterceptor
import com.wtracker.field.middlewares.TokenRefreshAuthenticator
import com.wtracker.field.services.api.AuthRefreshApi
import com.wtracker.field.services.api.WTrackerApi
import com.wtracker.field.services.auth.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Wrapper de construcción de clientes HTTP: único punto que conoce OkHttp/Retrofit. */
class NetworkClientFactory(
    private val json: Json,
    private val sessionStore: SessionStore,
) {

    private val converterFactory = json.asConverterFactory(NetworkConfig.JSON_MEDIA_TYPE.toMediaType())

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // BASIC evita volcar binarios de fotos en logcat; el JWT se redacta siempre.
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        redactHeader(AUTHORIZATION_HEADER)
    }

    private val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(NetworkConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("ngrok-skip-browser-warning", "true")
                    .header("Bypass-Tunnel-Reminder", "true")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val authenticatedClient: OkHttpClient by lazy {
        val refreshApi = buildRetrofit(baseClient).create(AuthRefreshApi::class.java)
        baseClient.newBuilder()
            .addInterceptor(AuthHeaderInterceptor(sessionStore))
            .authenticator(TokenRefreshAuthenticator(sessionStore, refreshApi))
            .build()
    }

    fun createApi(): WTrackerApi = buildRetrofit(authenticatedClient).create(WTrackerApi::class.java)

    private fun buildRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(NetworkConfig.apiBaseUrl)
        .client(client)
        .addConverterFactory(converterFactory)
        .build()
}
