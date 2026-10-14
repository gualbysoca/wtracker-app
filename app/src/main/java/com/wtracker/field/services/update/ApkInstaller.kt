package com.wtracker.field.services.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.wtracker.field.configs.UpdateConfig
import com.wtracker.field.utils.AppError
import com.wtracker.field.utils.AppResult
import java.io.File

/** Wrapper del instalador de paquetes de Android: lanza el APK descargado y gestiona el permiso de instalación. */
class ApkInstaller(context: Context) {

    private val appContext = context.applicationContext

    fun canInstallPackages(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    /** Lleva al usuario al ajuste "Instalar apps desconocidas" de WTracker. */
    fun openInstallPermissionSettings(): AppResult<Unit> {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(intent)
    }

    fun install(apkFile: File): AppResult<Unit> {
        val apkUri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, UpdateConfig.APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(intent)
    }

    private fun launch(intent: Intent): AppResult<Unit> = try {
        appContext.startActivity(intent)
        AppResult.Success(Unit)
    } catch (error: ActivityNotFoundException) {
        AppResult.Failure(AppError.Unknown("No se encontró el instalador de paquetes del dispositivo."))
    }
}
