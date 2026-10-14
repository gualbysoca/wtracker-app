package com.wtracker.field.configs

import com.wtracker.field.BuildConfig

object NetworkConfig {
    val apiBaseUrl: String = BuildConfig.API_BASE_URL
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 60L
    const val JSON_MEDIA_TYPE = "application/json"
    const val TEXT_MEDIA_TYPE = "text/plain"
}

object LocationConfig {
    /** Frecuencia de muestreo GPS durante la jornada (servicio en segundo plano). */
    const val TRACKING_INTERVAL_MS = 15_000L

    /** Frecuencia de muestreo mientras la cámara está abierta (precalienta el GPS). */
    const val CAMERA_WARMUP_INTERVAL_MS = 2_000L

    /** Antigüedad máxima aceptable de una lectura GPS para sellar una evidencia. */
    const val FRESH_FIX_MAX_AGE_MS = 15_000L
    const val FIX_TIMEOUT_MS = 20_000L

    /** Precisión mínima exigida para sellar evidencias o marcar visitas. */
    const val MAX_ACCEPTABLE_ACCURACY_METERS = 75f
    const val GOOD_ACCURACY_METERS = 20f

    /** Detección de paradas: permanecer dentro de este radio por STOP_AFTER_MS genera una parada. */
    const val STOP_RADIUS_METERS = 60.0
    const val STOP_AFTER_MS = 5 * 60_000L

    /** Una parada que excede este tiempo se reclasifica como inactividad. */
    const val INACTIVITY_AFTER_MS = 20 * 60_000L

    /** Abandono extremo: 4 horas sin movimiento. Gatilla auto-checkout. */
    const val EXTREME_ABANDONMENT_AFTER_MS = 4 * 60 * 60_000L
}

object EvidenceConfig {
    const val MAX_IMAGE_EDGE_PX = 1920
    const val PREVIEW_IMAGE_EDGE_PX = 1080
    const val JPEG_QUALITY = 85
    const val MIME_TYPE = "image/jpeg"
    const val FILE_EXTENSION = ".jpg"

    /** Valor del ENUM `evidence_type` del Backoffice. */
    const val TYPE_PHOTO = "foto"

    /** Una foto más antigua que esto no puede adjuntarse (evita reutilizar capturas viejas). */
    const val EVIDENCE_MAX_AGE_MS = 10 * 60_000L
    const val SOFTWARE_TAG = "WTracker Field"
}

/** Estilo del sello incrustado en la foto (fuera de Compose, por eso valores ARGB). */
object EvidenceStampConfig {
    val BAR_BACKGROUND_ARGB: Int = 0xD90A0F1E.toInt()
    val PRIMARY_TEXT_ARGB: Int = 0xFFF2F5FB.toInt()
    val SECONDARY_TEXT_ARGB: Int = 0xFFA9B4CC.toInt()
    val ACCENT_ARGB: Int = 0xFF34D399.toInt()
    val DANGER_ARGB: Int = 0xFFDC2626.toInt()
    const val PRIMARY_TEXT_RATIO = 0.036f
    const val SECONDARY_TEXT_RATIO = 0.028f
    const val PADDING_RATIO = 0.032f
    const val LINE_GAP_RATIO = 0.012f
    const val ACCENT_STRIPE_RATIO = 0.01f
    const val FOOTER_LABEL = "WTracker · Captura verificada en campo"
    const val OUT_OF_RANGE_FOOTER_LABEL = "WTracker · Fuera de rango de tolerancia"
}

object VisitConfig {
    /** Límite de `notes` en `createVisitSchema` del Backoffice. */
    const val NOTES_MAX_LENGTH = 500
    val FIELD_ROLES: Set<String> = setOf("reponedor", "vendedor", "cobrador", "repartidor")

    /** Observación de las visitas marcadas como fallidas por el sistema al cerrar la ruta. */
    const val ROUTE_CLOSURE_FAILURE_NOTE = "Marcada automáticamente como fallida al finalizar la ruta sin visitar al cliente."
}

object SyncConfig {
    const val UNIQUE_WORK_NAME = "visit-sync"
    const val PERIODIC_WORK_NAME = "visit-sync-periodic"
    const val PERIODIC_INTERVAL_MINUTES = 15L
    const val BACKOFF_DELAY_SECONDS = 30L
    const val MAX_PASSES_PER_RUN = 3

    /** Espera máxima al cerrar sesión para que la cola alcance a enviarse antes de borrar el JWT. */
    const val LOGOUT_SYNC_WAIT_MS = 8_000L
}

object TrackingNotificationConfig {
    const val CHANNEL_ID = "shift_tracking"
    const val HIGH_PRIORITY_CHANNEL_ID = "shift_tracking_urgent"
    const val NOTIFICATION_ID = 1001
    const val ABANDONMENT_NOTIFICATION_ID = 1002
}

object UiConfig {
    const val CLOCK_TICK_MS = 30_000L
    const val STATE_SUBSCRIPTION_TIMEOUT_MS = 5_000L
}

/** Autoactualización: la app no está en la tienda, así que lee sus versiones de la carpeta `releases/` del repo. */
object UpdateConfig {
    const val RELEASES_BASE_URL = "https://raw.githubusercontent.com/gualbysoca/wtracker-app/main/releases/"
    const val MANIFEST_FILE_NAME = "version.json"
    const val UPDATES_CACHE_DIR = "updates"
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 60L
}
