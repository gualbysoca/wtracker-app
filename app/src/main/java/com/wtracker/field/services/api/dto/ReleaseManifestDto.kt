package com.wtracker.field.services.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Contenido de `releases/version.json`, generado por `scripts/publish-release.sh`. */
@Serializable
data class ReleaseManifestDto(
    @SerialName("versionCode") val versionCode: Int,
    @SerialName("versionName") val versionName: String,
    @SerialName("apk") val apkFileName: String,
    @SerialName("sha256") val sha256: String,
    @SerialName("notes") val notes: String? = null,
)
