package com.wtracker.field.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wtracker.field.configs.MapConfig
import com.wtracker.field.services.models.GeoCoordinate
import com.wtracker.field.services.models.RouteStop
import com.wtracker.field.services.models.VisitStatus
import com.wtracker.field.ui.theme.WTheme
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow

/** Datos ya resueltos para pintar un marcador (colores calculados desde los tokens). */
internal data class MapMarkerStyle(val fillArgb: Int, val textArgb: Int, val sizePx: Int, val textSizePx: Float)

/**
 * Instancia del mapa con su estado asociado. Debe vivir FUERA de los items de un LazyColumn:
 * si se crea dentro, cada vez que el item sale del viewport se destruye y al volver se
 * reconstruye (costoso en el hilo principal), lo que provoca ANR al hacer scroll repetido.
 */
class RouteMapState internal constructor(internal val mapView: MapView) {
    internal val viewport = MapViewportState()
    internal val badgeCache = mutableMapOf<String, BitmapDrawable>()
}

@Composable
fun rememberRouteMapState(isLightMode: Boolean): RouteMapState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { RouteMapState(createMapView(context, isLightMode)) }

    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> state.mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> state.mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            state.mapView.onPause()
            state.mapView.onDetach()
        }
    }
    return state
}

/**
 * Wrapper del mapa: es el ÚNICO archivo de UI que conoce osmdroid. Cambiar a Google Maps
 * u otro proveedor sólo requiere reimplementar este composable.
 */
@Composable
fun RouteMap(
    stops: List<RouteStop>,
    userLocation: GeoCoordinate?,
    modifier: Modifier = Modifier,
    isLightMode: Boolean = false,
    mapState: RouteMapState = rememberRouteMapState(isLightMode),
) {
    val colors = WTheme.colors
    val density = LocalDensity.current
    val shape = RoundedCornerShape(WTheme.radius.lg)

    val markerSizePx = with(density) { WTheme.sizes.orderBadge.roundToPx() }
    val markerTextSizePx = with(density) { WTheme.typography.label.fontSize.toPx() }
    val routeLineWidthPx = with(density) { WTheme.sizes.borderThick.toPx() * 2 }
    val routeLineArgb = colors.accent.toArgb()
    val userMarkerStyle = remember(colors, markerSizePx, markerTextSizePx) {
        MapMarkerStyle(colors.brandGradientEnd.toArgb(), colors.accentContent.toArgb(), markerSizePx, markerTextSizePx)
    }
    val toneArgbByStatus = VisitStatus.entries.associateWith { it.toneColor().toArgb() }
    val statusStyles = remember(stops, colors, markerSizePx, markerTextSizePx) {
        stops.associate { stop ->
            stop.client.id to MapMarkerStyle(toneArgbByStatus.getValue(stop.client.currentStatus), colors.background.toArgb(), markerSizePx, markerTextSizePx)
        }
    }
    val viewportState = mapState.viewport

    AndroidView(
        factory = { mapState.mapView },
        modifier = modifier
            .clip(shape)
            .border(WTheme.sizes.borderThin, colors.border, shape),
        update = { view ->
            if (viewportState.lastIsLightMode != isLightMode) {
                view.overlayManager.tilesOverlay.setColorFilter(if (isLightMode) null else TilesOverlay.INVERT_COLORS)
                viewportState.lastIsLightMode = isLightMode
                view.invalidate()
            }

            val stopsChanged = stops !== viewportState.lastStops && stops != viewportState.lastStops
            val locationChanged = userLocation != viewportState.lastUserLocation
            val stylesChanged = statusStyles !== viewportState.lastStatusStyles

            if (stopsChanged || locationChanged || stylesChanged) {
                renderRoute(view, stops, userLocation, statusStyles, userMarkerStyle, routeLineArgb, routeLineWidthPx, mapState.badgeCache)
                viewportState.lastStops = stops
                viewportState.lastUserLocation = userLocation
                viewportState.lastStatusStyles = statusStyles
                fitViewportOnce(view, stops, userLocation, viewportState)
            }
        },
    )
}

internal class MapViewportState {
    var hasFittedToContent: Boolean = false
    var lastStops: List<RouteStop>? = null
    var lastUserLocation: GeoCoordinate? = null
    var lastStatusStyles: Map<String, MapMarkerStyle>? = null
    var lastIsLightMode: Boolean? = null
}

private fun createMapView(context: Context, isLightMode: Boolean): MapView = MapView(context).apply {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(true)
    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
    overlayManager.tilesOverlay.setColorFilter(if (isLightMode) null else TilesOverlay.INVERT_COLORS)
    controller.setZoom(MapConfig.DEFAULT_ZOOM)
    controller.setCenter(GeoPoint(MapConfig.DEFAULT_CENTER.latitude, MapConfig.DEFAULT_CENTER.longitude))
    // Evita que el LazyColumn padre robe los gestos de arrastre/zoom del mapa.
    setOnTouchListener { view, event ->
        val isGestureActive = event.action != MotionEvent.ACTION_UP && event.action != MotionEvent.ACTION_CANCEL
        view.parent?.requestDisallowInterceptTouchEvent(isGestureActive)
        false
    }
}

private fun renderRoute(
    mapView: MapView,
    stops: List<RouteStop>,
    userLocation: GeoCoordinate?,
    statusStyles: Map<String, MapMarkerStyle>,
    userMarkerStyle: MapMarkerStyle,
    routeLineArgb: Int,
    routeLineWidthPx: Float,
    badgeCache: MutableMap<String, BitmapDrawable>,
) {
    mapView.overlays.clear()
    val openStopPoints = stops
        .filterNot { it.client.currentStatus.isFinal }
        .mapNotNull { it.client.coordinate?.toGeoPoint() }

    val routePath = listOfNotNull(userLocation?.toGeoPoint()) + openStopPoints
    if (routePath.size > 1) {
        mapView.overlays.add(
            Polyline(mapView).apply {
                setPoints(routePath)
                outlinePaint.color = routeLineArgb
                outlinePaint.strokeWidth = routeLineWidthPx
                outlinePaint.strokeCap = Paint.Cap.ROUND
            },
        )
    }

    stops.forEach { stop ->
        val coordinate = stop.client.coordinate ?: return@forEach
        val style = statusStyles[stop.client.id] ?: return@forEach
        mapView.overlays.add(
            Marker(mapView).apply {
                position = coordinate.toGeoPoint()
                title = stop.client.businessName
                snippet = stop.client.address
                icon = buildBadgeDrawable(mapView.context, stop.order.toString(), style, badgeCache)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            },
        )
    }

    userLocation?.let { location ->
        mapView.overlays.add(
            Marker(mapView).apply {
                position = location.toGeoPoint()
                icon = buildIconBadgeDrawable(mapView.context, com.wtracker.field.R.drawable.ic_person, userMarkerStyle, badgeCache)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null as MarkerInfoWindow?)
            },
        )
    }
    mapView.invalidate()
}

private fun fitViewportOnce(
    mapView: MapView,
    stops: List<RouteStop>,
    userLocation: GeoCoordinate?,
    viewportState: MapViewportState,
) {
    if (viewportState.hasFittedToContent) return
    val points = stops.mapNotNull { it.client.coordinate?.toGeoPoint() } + listOfNotNull(userLocation?.toGeoPoint())
    if (points.isEmpty()) return

    viewportState.hasFittedToContent = true
    mapView.post {
        if (points.size == 1) {
            mapView.controller.setCenter(points.first())
            return@post
        }
        mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points), false, MapConfig.FIT_PADDING_PX, MapConfig.MAX_FIT_ZOOM, null)
    }
}

private fun buildBadgeDrawable(
    context: Context,
    label: String,
    style: MapMarkerStyle,
    cache: MutableMap<String, BitmapDrawable>,
): BitmapDrawable {
    val cacheKey = "$label-${style.fillArgb}-${style.textArgb}-${style.sizePx}-${style.textSizePx}"
    return cache.getOrPut(cacheKey) {
        val bitmap = Bitmap.createBitmap(style.sizePx, style.sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = style.sizePx / 2f
        canvas.drawCircle(radius, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.fillArgb })

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.textArgb
            textSize = style.textSizePx
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val textBaseline = radius - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, radius, textBaseline, textPaint)
        BitmapDrawable(context.resources, bitmap)
    }
}

private fun buildIconBadgeDrawable(
    context: Context,
    @DrawableRes iconRes: Int,
    style: MapMarkerStyle,
    cache: MutableMap<String, BitmapDrawable>,
): BitmapDrawable {
    val cacheKey = "icon-$iconRes-${style.fillArgb}-${style.textArgb}-${style.sizePx}"
    return cache.getOrPut(cacheKey) {
        val bitmap = Bitmap.createBitmap(style.sizePx, style.sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = style.sizePx / 2f
        canvas.drawCircle(radius, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.fillArgb })

        val drawable = androidx.core.content.ContextCompat.getDrawable(context, iconRes)?.mutate()
        if (drawable != null) {
            val padding = (style.sizePx * 0.2f).toInt()
            drawable.setBounds(padding, padding, style.sizePx - padding, style.sizePx - padding)
            androidx.core.graphics.drawable.DrawableCompat.setTint(drawable, style.textArgb)
            drawable.draw(canvas)
        }
        
        BitmapDrawable(context.resources, bitmap)
    }
}

private fun GeoCoordinate.toGeoPoint() = GeoPoint(latitude, longitude)
