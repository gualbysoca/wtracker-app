package com.wtracker.field.configs

import android.Manifest
import android.os.Build

/**
 * Permisos de la ruta. Se comparten entre la bienvenida (donde se inicia la ruta y por tanto el
 * servicio GPS en primer plano) y la hoja de ruta.
 */
object RoutePermissions {
    val requested: List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        // La notificación del servicio de jornada es visible sólo si se concede en Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val required: List<String> = listOf(Manifest.permission.ACCESS_FINE_LOCATION)
}
