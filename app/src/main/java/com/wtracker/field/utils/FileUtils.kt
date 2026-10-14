package com.wtracker.field.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.security.MessageDigest

object HashUtils {

    private const val HASH_ALGORITHM = "SHA-256"
    private const val BUFFER_SIZE_BYTES = 8 * 1024

    /** Huella SHA-256 del archivo final: permite al servidor detectar cualquier alteración posterior. */
    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

object BitmapUtils {

    fun calculateInSampleSize(sourceWidth: Int, sourceHeight: Int, maxEdgePx: Int): Int {
        var sampleSize = 1
        while (maxOf(sourceWidth, sourceHeight) / (sampleSize * 2) >= maxEdgePx) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /** Decodifica un JPEG submuestreado para no cargar imágenes de 12MP completas en memoria. */
    fun decodeSampled(file: File, maxEdgePx: Int, mutable: Boolean = false): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx)
            inMutable = mutable
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
        return applyExifRotation(file.absolutePath, bitmap)
    }

    private fun applyExifRotation(filePath: String, bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(filePath)
        val degrees = exif.rotationDegrees.toFloat()
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        
        val result = if (bitmap.isMutable && !rotated.isMutable) {
            rotated.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            rotated
        }
        
        if (result !== rotated) rotated.recycle()
        bitmap.recycle()
        return result
    }
}
