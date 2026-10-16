package com.wtracker.field.configs

import android.content.Context
import com.wtracker.field.BuildConfig
import com.wtracker.field.services.models.GeoCoordinate
import java.io.File

/** Configuración global del motor de mapas (osmdroid). Único punto acoplado a la librería. */
object MapConfig {

    private const val OSMDROID_DIRECTORY = "osmdroid"
    private const val TILE_CACHE_DIRECTORY = "tiles"

    const val DEFAULT_ZOOM = 13.0
    const val MAX_FIT_ZOOM = 17.0
    const val FIT_PADDING_PX = 96

    /** Máximo de paradas dibujadas en el mapa (las más cercanas en el orden de ruta). */
    const val MAX_RENDERED_STOPS = 150

    /** Zoom mínimo al ajustar el mapa a la ruta y tamaño de tesela del mapa base. */
    const val MIN_FIT_ZOOM = 3.0
    const val TILE_SIZE_PX = 256.0

    /** Límites válidos de Web Mercator. */
    const val MAX_LATITUDE = 85.0
    const val MAX_LONGITUDE = 180.0

    /** Centro por defecto (Santa Cruz de la Sierra, zona de los datos del Backoffice). */
    val DEFAULT_CENTER = GeoCoordinate(latitude = -17.7833, longitude = -63.1821)

    fun initialize(context: Context) {
        val osmConfiguration = org.osmdroid.config.Configuration.getInstance()
        val basePath = File(context.cacheDir, OSMDROID_DIRECTORY)
        osmConfiguration.userAgentValue = BuildConfig.APPLICATION_ID
        // Caché en almacenamiento privado: no requiere permisos de almacenamiento externo.
        osmConfiguration.osmdroidBasePath = basePath
        osmConfiguration.osmdroidTileCache = File(basePath, TILE_CACHE_DIRECTORY)
    }
}
