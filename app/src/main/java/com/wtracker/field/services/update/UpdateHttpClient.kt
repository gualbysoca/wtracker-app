package com.wtracker.field.services.update

import com.wtracker.field.configs.UpdateConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class HttpStatusException(val statusCode: Int) : IOException("HTTP $statusCode")

/** Wrapper de red para la autoactualización: único punto del módulo que conoce OkHttp. */
class UpdateHttpClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(UpdateConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(UpdateConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build(),
) {

    suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(buildRequest(url)).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            response.body?.string() ?: throw IOException("Respuesta vacía")
        }
    }

    /** Descarga a [target] informando el avance (0..100) sólo cuando cambia el porcentaje. */
    suspend fun downloadTo(url: String, target: File, onProgressPercent: (Int) -> Unit) = withContext(Dispatchers.IO) {
        client.newCall(buildRequest(url)).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val body = response.body ?: throw IOException("Respuesta vacía")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            var lastPercent = -1

            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes <= 0) continue
                        val percent = (downloadedBytes * 100 / totalBytes).toInt()
                        if (percent == lastPercent) continue
                        lastPercent = percent
                        onProgressPercent(percent)
                    }
                }
            }
        }
    }

    private fun buildRequest(url: String): Request =
        Request.Builder().url(url).header("Cache-Control", "no-cache").build()

    private companion object {
        const val DOWNLOAD_BUFFER_BYTES = 16 * 1024
    }
}
