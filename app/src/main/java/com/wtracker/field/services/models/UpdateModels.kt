package com.wtracker.field.services.models

/** Versión publicada de la app, ya resuelta a una URL descargable. */
data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String?,
)
