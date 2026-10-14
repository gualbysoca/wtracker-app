package com.wtracker.field.services.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import androidx.exifinterface.media.ExifInterface
import com.wtracker.field.BuildConfig
import com.wtracker.field.configs.EvidenceConfig
import com.wtracker.field.configs.EvidenceStampConfig
import com.wtracker.field.services.models.CapturedEvidence
import com.wtracker.field.services.models.CapturedLocation
import com.wtracker.field.utils.AppError
import com.wtracker.field.utils.AppResult
import com.wtracker.field.utils.BitmapUtils
import com.wtracker.field.utils.GeoUtils
import com.wtracker.field.utils.HashUtils
import com.wtracker.field.utils.TimeProvider
import com.wtracker.field.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.min
import kotlin.math.roundToInt

private data class StampLines(
    val title: String,
    val coordinates: String,
    val timestamp: String,
    val footer: String,
)

/**
 * Sella la evidencia de forma inmutable en TRES capas:
 *  1. Marca visual "quemada" en los píxeles (GPS, precisión, fecha/hora, cliente).
 *  2. Metadatos EXIF estándar (GPS, DateTimeOriginal con zona horaria, dispositivo).
 *  3. Huella SHA-256 del archivo final, enviada al servidor para detectar alteraciones.
 * La foto cruda de CameraX se elimina siempre; sólo persiste la versión sellada.
 */
class EvidenceStamper(
    private val fileStore: EvidenceFileStore,
    private val timeProvider: TimeProvider,
) {

    suspend fun stamp(
        rawCapture: File,
        location: CapturedLocation,
        capturedAtMillis: Long,
        clientName: String,
        isOutOfRange: Boolean,
    ): AppResult<CapturedEvidence> = withContext(Dispatchers.Default) {
        try {
            val uprightBitmap = decodeUprightBitmap(rawCapture)
            drawStamp(uprightBitmap, buildStampLines(location, capturedAtMillis, clientName, isOutOfRange), isOutOfRange)

            val outputFile = fileStore.newEvidenceFile()
            writeJpeg(uprightBitmap, outputFile)
            uprightBitmap.recycle()
            writeExifMetadata(outputFile, location, capturedAtMillis, clientName)

            AppResult.Success(
                CapturedEvidence(
                    filePath = outputFile.absolutePath,
                    sha256 = HashUtils.sha256Hex(outputFile),
                    mimeType = EvidenceConfig.MIME_TYPE,
                    location = location,
                    capturedAtMillis = capturedAtMillis,
                ),
            )
        } catch (ioException: IOException) {
            AppResult.Failure(AppError.Storage("No se pudo procesar la foto: ${ioException.message.orEmpty()}"))
        } catch (outOfMemory: OutOfMemoryError) {
            AppResult.Failure(AppError.Storage("Memoria insuficiente para procesar la foto. Intenta nuevamente."))
        }
    }

    private fun decodeUprightBitmap(rawCapture: File): Bitmap {
        return BitmapUtils.decodeSampled(rawCapture, EvidenceConfig.MAX_IMAGE_EDGE_PX, mutable = true)
            ?: throw IOException("La captura no es una imagen válida")
    }

    private fun buildStampLines(location: CapturedLocation, capturedAtMillis: Long, clientName: String, isOutOfRange: Boolean) = StampLines(
        title = clientName,
        coordinates = "GPS ${GeoUtils.formatCoordinate(location.coordinate)}  ±${location.accuracyMeters.roundToInt()} m",
        timestamp = TimeUtils.formatStamp(capturedAtMillis, timeProvider.zone()),
        footer = if (isOutOfRange) EvidenceStampConfig.OUT_OF_RANGE_FOOTER_LABEL else EvidenceStampConfig.FOOTER_LABEL,
    )

    private fun drawStamp(bitmap: Bitmap, lines: StampLines, isOutOfRange: Boolean) {
        val canvas = Canvas(bitmap)
        val shortEdge = min(bitmap.width, bitmap.height).toFloat()
        val padding = shortEdge * EvidenceStampConfig.PADDING_RATIO
        val lineGap = shortEdge * EvidenceStampConfig.LINE_GAP_RATIO
        val accentWidth = shortEdge * EvidenceStampConfig.ACCENT_STRIPE_RATIO

        val titlePaint = textPaint(EvidenceStampConfig.PRIMARY_TEXT_ARGB, shortEdge * EvidenceStampConfig.PRIMARY_TEXT_RATIO, Typeface.DEFAULT_BOLD)
        val detailPaint = textPaint(EvidenceStampConfig.PRIMARY_TEXT_ARGB, shortEdge * EvidenceStampConfig.SECONDARY_TEXT_RATIO, Typeface.MONOSPACE)
        val footerPaint = textPaint(EvidenceStampConfig.SECONDARY_TEXT_ARGB, shortEdge * EvidenceStampConfig.SECONDARY_TEXT_RATIO, Typeface.DEFAULT)

        val titleHeight = -titlePaint.fontMetrics.ascent + titlePaint.fontMetrics.descent
        val detailHeight = -detailPaint.fontMetrics.ascent + detailPaint.fontMetrics.descent
        val footerHeight = -footerPaint.fontMetrics.ascent + footerPaint.fontMetrics.descent

        val contentHeight = titleHeight + detailHeight * 2 + footerHeight + lineGap * 3
        val barTop = bitmap.height - contentHeight - padding * 2

        val accentColor = if (isOutOfRange) EvidenceStampConfig.DANGER_ARGB else EvidenceStampConfig.ACCENT_ARGB

        canvas.drawRect(0f, barTop, bitmap.width.toFloat(), bitmap.height.toFloat(), fillPaint(EvidenceStampConfig.BAR_BACKGROUND_ARGB))
        canvas.drawRect(0f, barTop, accentWidth, bitmap.height.toFloat(), fillPaint(accentColor))

        val textStart = accentWidth + padding
        val availableWidth = bitmap.width - textStart - padding
        
        // Start baseline at the top of the bar + padding + the ascent (distance from baseline to top of text)
        var baseline = barTop + padding - titlePaint.fontMetrics.ascent

        listOf(
            lines.title to titlePaint,
            lines.coordinates to detailPaint,
            lines.timestamp to detailPaint,
            lines.footer to footerPaint,
        ).forEachIndexed { index, (text, paint) ->
            val fittedText = TextUtils.ellipsize(text, paint, availableWidth, TextUtils.TruncateAt.END).toString()
            canvas.drawText(fittedText, textStart, baseline, paint)
            // Advance baseline for the NEXT line
            if (index < 3) {
                // The next paint is known: index 0 -> detail, index 1 -> detail, index 2 -> footer
                val nextPaint = if (index == 2) footerPaint else detailPaint
                baseline += paint.fontMetrics.descent + lineGap - nextPaint.fontMetrics.ascent
            }
        }
    }

    private fun writeJpeg(bitmap: Bitmap, outputFile: File) {
        val compressed = FileOutputStream(outputFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, EvidenceConfig.JPEG_QUALITY, stream)
        }
        if (!compressed) throw IOException("No se pudo codificar la imagen")
    }

    private fun writeExifMetadata(outputFile: File, location: CapturedLocation, capturedAtMillis: Long, clientName: String) {
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
                "WTracker | cliente=$clientName | precision=${location.accuracyMeters.roundToInt()}m",
            )
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            saveAttributes()
        }
    }

    private fun textPaint(color: Int, size: Float, typeface: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.textSize = size
        this.typeface = typeface
    }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
}
