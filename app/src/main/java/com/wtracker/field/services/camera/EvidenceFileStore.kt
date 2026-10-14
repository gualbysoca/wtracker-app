package com.wtracker.field.services.camera

import android.content.Context
import com.wtracker.field.configs.EvidenceConfig
import java.io.File
import java.util.UUID

/**
 * Las fotos viven en `noBackupFilesDir` (privado, sin backup, invisible para la galería
 * y otras apps). No se expone ningún FileProvider: nadie externo puede reemplazar archivos.
 */
class EvidenceFileStore(context: Context) {

    private val rawCaptureDirectory = File(context.noBackupFilesDir, RAW_DIRECTORY)
    private val evidenceDirectory = File(context.noBackupFilesDir, EVIDENCE_DIRECTORY)

    fun newRawCaptureFile(): File = newFileIn(rawCaptureDirectory)

    fun newEvidenceFile(): File = newFileIn(evidenceDirectory)

    fun delete(file: File) {
        if (file.exists()) file.delete()
    }

    fun delete(filePath: String) = delete(File(filePath))

    private fun newFileIn(directory: File): File {
        if (!directory.exists()) directory.mkdirs()
        return File(directory, "${UUID.randomUUID()}${EvidenceConfig.FILE_EXTENSION}")
    }

    private companion object {
        const val RAW_DIRECTORY = "evidence_raw"
        const val EVIDENCE_DIRECTORY = "evidence"
    }
}
