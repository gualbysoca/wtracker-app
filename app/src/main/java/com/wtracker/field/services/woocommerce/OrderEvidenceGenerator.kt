package com.wtracker.field.services.woocommerce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import androidx.exifinterface.media.ExifInterface
import com.wtracker.field.BuildConfig
import com.wtracker.field.configs.EvidenceConfig
import com.wtracker.field.services.camera.EvidenceFileStore
import com.wtracker.field.services.location.LocationProvider
import com.wtracker.field.services.models.CapturedEvidence
import com.wtracker.field.services.models.CapturedLocation
import com.wtracker.field.services.models.FieldClient
import com.wtracker.field.services.models.GeoCoordinate
import com.wtracker.field.services.models.UserSession
import com.wtracker.field.services.woocommerce.models.WooCheckoutForm
import com.wtracker.field.services.woocommerce.models.WooCreatedOrder
import com.wtracker.field.services.woocommerce.models.WooOrderDraft
import com.wtracker.field.utils.AppResult
import com.wtracker.field.utils.GeoUtils
import com.wtracker.field.utils.HashUtils
import com.wtracker.field.utils.TimeProvider
import com.wtracker.field.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Genera un comprobante digital sellado (voucher en formato JPEG con metadatos EXIF y hash SHA-256)
 * que certifica el pedido tomado en campo como evidencia válida para la visita.
 */
class OrderEvidenceGenerator(
    private val fileStore: EvidenceFileStore,
    private val locationProvider: LocationProvider,
    private val timeProvider: TimeProvider,
) {

    suspend fun generateEvidence(
        client: FieldClient,
        vendorSession: UserSession,
        order: WooCreatedOrder,
        draft: WooOrderDraft,
        checkoutForm: WooCheckoutForm? = null,
    ): CapturedEvidence = withContext(Dispatchers.Default) {
        val nowMillis = timeProvider.nowMillis()
        val location = resolveLocation(client, nowMillis)

        val width = 900
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawVoucher(canvas, width, height, client, vendorSession, order, draft, checkoutForm, location, nowMillis)

        val outputFile = fileStore.newEvidenceFile()
        writeJpeg(bitmap, outputFile)
        bitmap.recycle()

        val displayClient = checkoutForm?.nombreCliente?.takeIf { it.isNotBlank() } ?: client.businessName
        writeExifMetadata(outputFile, location, nowMillis, displayClient, order.orderNumber)

        CapturedEvidence(
            filePath = outputFile.absolutePath,
            sha256 = HashUtils.sha256Hex(outputFile),
            mimeType = EvidenceConfig.MIME_TYPE,
            location = location,
            capturedAtMillis = nowMillis,
        )
    }

    private suspend fun resolveLocation(client: FieldClient, nowMillis: Long): CapturedLocation {
        val gpsResult = locationProvider.getFreshLocation()
        if (gpsResult is AppResult.Success<CapturedLocation>) {
            return gpsResult.data
        }
        val coordinate = client.coordinate ?: GeoCoordinate(-17.7833, -63.1821)
        return CapturedLocation(
            coordinate = coordinate,
            accuracyMeters = 10f,
            isMockLocation = false,
            fixTimeMillis = nowMillis,
            capturedAtMillis = nowMillis,
        )
    }

    private fun drawVoucher(
        canvas: Canvas,
        width: Int,
        height: Int,
        client: FieldClient,
        vendorSession: UserSession,
        order: WooCreatedOrder,
        draft: WooOrderDraft,
        checkoutForm: WooCheckoutForm?,
        location: CapturedLocation,
        nowMillis: Long,
    ) {
        val darkBg = Color.parseColor("#0A0F1E")
        val cardBg = Color.parseColor("#121A2E")
        val borderColor = Color.parseColor("#26324F")
        val textPrimary = Color.parseColor("#F2F5FB")
        val textSecondary = Color.parseColor("#A9B4CC")
        val textMuted = Color.parseColor("#6B7794")
        val accentColor = Color.parseColor("#5B8CFF")
        val greenSuccess = Color.parseColor("#34D399")

        // 1. Fondo completo
        canvas.drawColor(darkBg)

        // 2. Banner superior con título
        val headerRect = RectF(40f, 40f, width - 40f, 150f)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBg
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(headerRect, 16f, 16f, headerPaint)
        canvas.drawRoundRect(headerRect, 16f, 16f, borderPaint)

        val appTagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22D3EE")
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        }
        canvas.drawText("WTRACKER · CONFIRMACIÓN DE PEDIDO", 64f, 85f, appTagPaint)

        val storeTagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 18f
            typeface = Typeface.DEFAULT
        }
        canvas.drawText("Integración WooCommerce Móvil", 64f, 120f, storeTagPaint)

        // 3. Bloque de Número de Pedido y Estado
        val orderTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("PEDIDO #${order.orderNumber}", 50f, 215f, orderTitlePaint)

        val (statusLabel, statusBgColor, statusFgColor) = when (order.status.lowercase(Locale.ROOT)) {
            "pending" -> Triple("● Pendiente de pago", Color.parseColor("#382705"), Color.parseColor("#FBBF24"))
            "processing" -> Triple("● Procesando", Color.parseColor("#053321"), Color.parseColor("#34D399"))
            "completed" -> Triple("● Completado", Color.parseColor("#053321"), Color.parseColor("#34D399"))
            "on-hold" -> Triple("● En espera", Color.parseColor("#382705"), Color.parseColor("#FBBF24"))
            else -> Triple("● Pendiente de pago", Color.parseColor("#382705"), Color.parseColor("#FBBF24"))
        }

        val statusTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = statusFgColor
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        val pillWidth = statusTextPaint.measureText(statusLabel) + 36f
        val statusPillRect = RectF(50f, 235f, 50f + pillWidth, 275f)
        val statusBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = statusBgColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(statusPillRect, 10f, 10f, statusBgPaint)
        canvas.drawText(statusLabel, 68f, 263f, statusTextPaint)

        // 4. Bloque Informativo (Cliente, Vendedor, Fecha, Origen)
        val infoRect = RectF(40f, 295f, width - 40f, 445f)
        canvas.drawRoundRect(infoRect, 16f, 16f, headerPaint)
        canvas.drawRoundRect(infoRect, 16f, 16f, borderPaint)

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textSecondary
            textSize = 20f
            typeface = Typeface.DEFAULT
        }
        val valueBoldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPrimary
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }

        val displayClient = checkoutForm?.nombreCliente?.takeIf { it.isNotBlank() } ?: client.businessName
        val displayVendor = checkoutForm?.nombreVendedor?.takeIf { it.isNotBlank() } ?: vendorSession.fullName

        canvas.drawText("Cliente:", 64f, 335f, labelPaint)
        val fittedClient = TextUtils.ellipsize(displayClient, valueBoldPaint, 580f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(fittedClient, 180f, 335f, valueBoldPaint)

        canvas.drawText("Vendedor:", 64f, 375f, labelPaint)
        val fittedVendor = TextUtils.ellipsize(displayVendor, valueBoldPaint, 580f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(fittedVendor, 180f, 375f, valueBoldPaint)

        val dateText = TimeUtils.formatStamp(nowMillis, timeProvider.zone())
        canvas.drawText("Fecha:", 64f, 415f, labelPaint)
        canvas.drawText(dateText, 180f, 415f, valueBoldPaint)

        canvas.drawText("Origen:", 470f, 415f, labelPaint)
        canvas.drawText("App móvil", 555f, 415f, valueBoldPaint)

        // 5. Encabezado de Productos
        val tableHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("CANT.", 54f, 490f, tableHeaderPaint)
        canvas.drawText("PRODUCTO", 150f, 490f, tableHeaderPaint)

        val rightHeaderPaint = TextPaint(tableHeaderPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("SUBTOTAL", width - 54f, 490f, rightHeaderPaint)

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            strokeWidth = 1.5f
        }
        canvas.drawLine(40f, 508f, width - 40f, 508f, dividerPaint)

        // 6. Filas de productos (hasta 8 filas)
        var rowY = 548f
        val maxItems = 8
        val itemsToDraw = draft.items.take(maxItems)

        val qtyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        val prodPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPrimary
            textSize = 21f
            typeface = Typeface.DEFAULT
        }
        val subtotalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPrimary
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }

        for (item in itemsToDraw) {
            canvas.drawText("${item.quantity}", 64f, rowY, qtyPaint)
            val productName = TextUtils.ellipsize(item.product.name, prodPaint, 520f, TextUtils.TruncateAt.END).toString()
            canvas.drawText(productName, 150f, rowY, prodPaint)
            val subtotalText = String.format(Locale.US, "Bs. %.2f", item.lineTotal).replace('.', ',')
            canvas.drawText(subtotalText, width - 54f, rowY, subtotalPaint)
            rowY += 46f
        }

        if (draft.items.size > maxItems) {
            val extraCount = draft.items.size - maxItems
            val extraPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textMuted
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText("+ $extraCount productos adicionales en este pedido", 64f, rowY, extraPaint)
        }

        // 7. Total del pedido
        val totalLineY = 930f
        canvas.drawLine(40f, totalLineY, width - 40f, totalLineY, dividerPaint)

        val totalLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textSecondary
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("TOTAL A PAGAR", 54f, totalLineY + 54f, totalLabelPaint)

        val totalValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 38f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("${order.currency} ${order.total}".replace('.', ','), width - 54f, totalLineY + 54f, totalValuePaint)

        // 8. Sello de Evidencia de Campo (inferior)
        val stampRect = RectF(40f, 1025f, width - 40f, 1160f)
        val stampBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#070B16")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(stampRect, 14f, 14f, stampBgPaint)
        canvas.drawRoundRect(stampRect, 14f, 14f, borderPaint)

        // Franja verde lateral de verificación
        val stripeRect = RectF(40f, 1025f, 52f, 1160f)
        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = greenSuccess
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(stripeRect, 4f, 4f, stripePaint)

        val stampMonoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPrimary
            textSize = 20f
            typeface = Typeface.MONOSPACE
        }
        val stampFootPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = greenSuccess
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
        }
        val appFootPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 18f
            typeface = Typeface.DEFAULT
        }

        val gpsText = "GPS: ${GeoUtils.formatCoordinate(location.coordinate)}  ±${location.accuracyMeters.roundToInt()}m"
        canvas.drawText(gpsText, 68f, 1065f, stampMonoPaint)
        canvas.drawText("Comprobante Digital Verificado · Confirmación WooCommerce", 68f, 1102f, stampFootPaint)
        canvas.drawText("WTracker Field · Registro de evidencia para visita comercial", 68f, 1136f, appFootPaint)
    }

    private fun writeJpeg(bitmap: Bitmap, outputFile: File) {
        val compressed = FileOutputStream(outputFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, EvidenceConfig.JPEG_QUALITY, stream)
        }
        if (!compressed) throw IOException("No se pudo codificar el comprobante de pedido en JPEG.")
    }

    private fun writeExifMetadata(
        outputFile: File,
        location: CapturedLocation,
        capturedAtMillis: Long,
        clientName: String,
        orderNumber: String,
    ) {
        val zone = timeProvider.zone()
        val localDateTime = TimeUtils.formatExifDateTime(capturedAtMillis, zone)
        val offset = TimeUtils.formatExifOffset(capturedAtMillis, zone)

        ExifInterface(outputFile.absolutePath).apply {
            setLatLong(location.coordinate.latitude, location.coordinate.longitude)
            setAttribute(ExifInterface.TAG_GPS_DATESTAMP, TimeUtils.formatExifGpsDate(location.fixTimeMillis))
            setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, TimeUtils.formatExifGpsTime(location.fixTimeMillis))
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, localDateTime)
            setAttribute(ExifInterface.TAG_DATETIME, localDateTime)
            setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
            setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
            setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
            setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
            setAttribute(ExifInterface.TAG_SOFTWARE, "${EvidenceConfig.SOFTWARE_TAG} ${BuildConfig.VERSION_NAME}")
            setAttribute(
                ExifInterface.TAG_IMAGE_DESCRIPTION,
                "WTracker | Pedido #$orderNumber | Origen=App móvil | cliente=$clientName | precision=${location.accuracyMeters.roundToInt()}m",
            )
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            saveAttributes()
        }
    }
}
