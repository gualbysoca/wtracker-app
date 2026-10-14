package com.wtracker.field.services.update

import com.wtracker.field.configs.UpdateConfig
import com.wtracker.field.services.api.dto.ReleaseManifestDto
import com.wtracker.field.services.models.AppRelease
import com.wtracker.field.utils.AppError
import com.wtracker.field.utils.AppResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Detecta y descarga nuevas versiones publicadas en `releases/` del repositorio.
 * Una versión es "nueva" sólo si su `versionCode` supera al instalado.
 */
class UpdateService(
    private val httpClient: UpdateHttpClient,
    private val json: Json,
    private val updatesDir: File,
    private val installedVersionCode: Int,
) {

    /** Success(null) = la app ya está al día o aún no hay ninguna versión publicada. */
    suspend fun findAvailableUpdate(): AppResult<AppRelease?> {
        return try {
            val manifestText = httpClient.fetchText(UpdateConfig.RELEASES_BASE_URL + UpdateConfig.MANIFEST_FILE_NAME)
            val manifest = json.decodeFromString<ReleaseManifestDto>(manifestText)
            if (manifest.versionCode <= installedVersionCode) return AppResult.Success(null)
            AppResult.Success(manifest.toRelease())
        } catch (error: HttpStatusException) {
            if (error.statusCode == HTTP_NOT_FOUND) return AppResult.Success(null)
            AppResult.Failure(AppError.Server("No se pudo consultar las versiones disponibles.", error.statusCode))
        } catch (error: IOException) {
            AppResult.Failure(AppError.Network())
        } catch (error: SerializationException) {
            AppResult.Failure(AppError.Unknown("El archivo de versiones publicado es inválido."))
        }
    }

    /** Descarga el APK, verifica su SHA-256 y devuelve el archivo listo para instalar. */
    suspend fun downloadRelease(release: AppRelease, onProgressPercent: (Int) -> Unit): AppResult<File> {
        val apkFile = File(updatesDir, apkFileNameOf(release))
        val partialFile = File(updatesDir, apkFile.name + PARTIAL_SUFFIX)

        return try {
            prepareCleanUpdatesDir()
            httpClient.downloadTo(release.apkUrl, partialFile, onProgressPercent)
            if (!partialFile.sha256().equals(release.sha256, ignoreCase = true)) {
                partialFile.delete()
                return AppResult.Failure(AppError.Validation("La descarga está corrupta. Inténtalo nuevamente."))
            }
            if (!partialFile.renameTo(apkFile)) {
                return AppResult.Failure(AppError.Storage("No se pudo guardar la actualización en el dispositivo."))
            }
            AppResult.Success(apkFile)
        } catch (error: HttpStatusException) {
            AppResult.Failure(AppError.Server("No se encontró el archivo de la nueva versión.", error.statusCode))
        } catch (error: IOException) {
            partialFile.delete()
            AppResult.Failure(AppError.Network("No se pudo descargar la actualización. Revisa tu conexión y el espacio disponible."))
        }
    }

    private fun prepareCleanUpdatesDir() {
        updatesDir.mkdirs()
        updatesDir.listFiles()?.forEach { it.delete() }
    }

    private fun apkFileNameOf(release: AppRelease) = "wtracker-${release.versionName}.apk"

    private fun ReleaseManifestDto.toRelease() = AppRelease(
        versionCode = versionCode,
        versionName = versionName,
        apkUrl = UpdateConfig.RELEASES_BASE_URL + apkFileName,
        sha256 = sha256,
        notes = notes?.takeIf { it.isNotBlank() },
    )

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(HASH_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val PARTIAL_SUFFIX = ".part"
        const val HASH_BUFFER_BYTES = 16 * 1024
    }
}
