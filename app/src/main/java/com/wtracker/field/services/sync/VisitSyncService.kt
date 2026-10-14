package com.wtracker.field.services.sync

import android.os.Build
import com.wtracker.field.BuildConfig
import com.wtracker.field.configs.NetworkConfig
import com.wtracker.field.configs.SyncConfig
import com.wtracker.field.services.api.ApiPaths
import com.wtracker.field.services.api.WTrackerApi
import com.wtracker.field.services.api.dto.CreateVisitRequestDto
import com.wtracker.field.services.api.dto.EvidenceMetadataDto
import com.wtracker.field.services.api.requireData
import com.wtracker.field.services.auth.SessionStore
import com.wtracker.field.services.camera.EvidenceFileStore
import com.wtracker.field.services.local.EvidenceDao
import com.wtracker.field.services.local.EvidenceEntity
import com.wtracker.field.services.local.SyncStates
import com.wtracker.field.services.local.VisitRecordDao
import com.wtracker.field.services.local.VisitRecordEntity
import com.wtracker.field.utils.ApiErrorMapper
import com.wtracker.field.utils.AppError
import com.wtracker.field.utils.AppResult
import com.wtracker.field.utils.TimeProvider
import com.wtracker.field.utils.TimeUtils
import com.wtracker.field.utils.safeApiCall
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

enum class SyncOutcome { COMPLETED, RETRY_LATER, NO_SESSION }

private enum class RecordOutcome { DONE, RETRY_LATER }

/**
 * Vacía la cola local respetando el orden cronológico (un "en_ruta" siempre llega antes
 * que el "visitado" del mismo cliente). Cada registro avanza en dos fases idempotentes:
 *   PENDING_VISIT    → POST /mobile/visits                 (guarda remoteVisitId)
 *   PENDING_EVIDENCE → POST /mobile/visits/:id/evidence    (multipart, campo "file")
 * Si una fase falla por red, se detiene todo y se reintenta luego sin repetir fases ya hechas.
 */
class VisitSyncService(
    private val api: WTrackerApi,
    private val json: Json,
    private val visitRecordDao: VisitRecordDao,
    private val evidenceDao: EvidenceDao,
    private val evidenceFileStore: EvidenceFileStore,
    private val sessionStore: SessionStore,
    private val errorMapper: ApiErrorMapper,
    private val timeProvider: TimeProvider,
) {

    private val textMediaType = NetworkConfig.TEXT_MEDIA_TYPE.toMediaType()
    private val jsonMediaType = NetworkConfig.JSON_MEDIA_TYPE.toMediaType()

    suspend fun syncPendingVisits(): SyncOutcome {
        val session = sessionStore.ensureLoaded() ?: return SyncOutcome.NO_SESSION

        repeat(SyncConfig.MAX_PASSES_PER_RUN) {
            val pendingRecords = visitRecordDao.findPendingForUser(session.userId)
            if (pendingRecords.isEmpty()) return SyncOutcome.COMPLETED

            for (record in pendingRecords) {
                if (syncRecord(record) == RecordOutcome.RETRY_LATER) return SyncOutcome.RETRY_LATER
            }
        }
        return SyncOutcome.COMPLETED
    }

    private suspend fun syncRecord(record: VisitRecordEntity): RecordOutcome {
        val remoteVisitId = record.remoteVisitId ?: when (val visitResult = uploadVisit(record)) {
            is AppResult.Success -> visitResult.data
            is AppResult.Failure -> return handleFailure(record, visitResult.error)
        }

        for (evidence in evidenceDao.findPendingForVisit(record.localId)) {
            val evidenceResult = uploadEvidence(remoteVisitId, record, evidence)
            if (evidenceResult is AppResult.Failure) return handleFailure(record, evidenceResult.error)
        }

        visitRecordDao.markSynced(record.localId)
        return RecordOutcome.DONE
    }

    private suspend fun uploadVisit(record: VisitRecordEntity): AppResult<String> {
        val request = CreateVisitRequestDto(
            clientId = record.clientId,
            status = record.status,
            lat = record.latitude,
            lng = record.longitude,
            notes = record.notes,
            timestamp = TimeUtils.toIsoUtc(record.recordedAtMillis),
        )
        val result = safeApiCall(errorMapper) { api.createVisit(request).requireData().id }
        if (result is AppResult.Success) {
            visitRecordDao.markVisitUploaded(record.localId, result.data, SyncStates.PENDING_EVIDENCE)
        }
        return result
    }

    private suspend fun uploadEvidence(
        remoteVisitId: String,
        record: VisitRecordEntity,
        evidence: EvidenceEntity,
    ): AppResult<Unit> {
        val photoFile = File(evidence.filePath)
        if (!photoFile.exists()) {
            return AppResult.Failure(AppError.Storage("El archivo de evidencia ya no existe en el dispositivo."))
        }

        val result = safeApiCall(errorMapper) {
            api.uploadEvidence(
                visitId = remoteVisitId,
                file = buildFilePart(photoFile, evidence.mimeType),
                latitude = evidence.latitude.toString().asTextBody(),
                longitude = evidence.longitude.toString().asTextBody(),
                capturedAt = TimeUtils.toIsoUtc(evidence.capturedAtMillis).asTextBody(),
                type = evidence.evidenceType.asTextBody(),
                metadata = buildMetadataBody(record, evidence),
            ).requireData().id
        }
        if (result is AppResult.Failure) return result

        val remoteEvidenceId = (result as AppResult.Success).data
        evidenceDao.markUploaded(evidence.localId, remoteEvidenceId, timeProvider.nowMillis())
        // El servidor es ahora la fuente de verdad; liberar espacio y no dejar fotos en el equipo.
        evidenceFileStore.delete(photoFile)
        return AppResult.Success(Unit)
    }

    private suspend fun handleFailure(record: VisitRecordEntity, error: AppError): RecordOutcome {
        val isTransient = error.isRetryable || error is AppError.Unauthorized
        if (isTransient) {
            visitRecordDao.registerSyncFailure(record.localId, error.message)
            return RecordOutcome.RETRY_LATER
        }
        // Rechazo definitivo (400/403/404...): reintentar no cambiará el resultado.
        visitRecordDao.markRejected(record.localId, error.message)
        return RecordOutcome.DONE
    }

    private fun buildFilePart(photoFile: File, mimeType: String): MultipartBody.Part =
        MultipartBody.Part.createFormData(
            ApiPaths.EVIDENCE_FILE_PART,
            photoFile.name,
            photoFile.asRequestBody(mimeType.toMediaType()),
        )

    private fun buildMetadataBody(record: VisitRecordEntity, evidence: EvidenceEntity): RequestBody {
        val metadata = EvidenceMetadataDto(
            sha256 = evidence.sha256,
            accuracyMeters = evidence.accuracyMeters,
            isMockLocation = evidence.isMockLocation,
            gpsFixTime = TimeUtils.toIsoUtc(evidence.fixTimeMillis),
            deviceTime = TimeUtils.toIsoUtc(evidence.capturedAtMillis),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            appVersion = BuildConfig.VERSION_NAME,
            localVisitId = record.localId,
        )
        return json.encodeToString(EvidenceMetadataDto.serializer(), metadata).toRequestBody(jsonMediaType)
    }

    private fun String.asTextBody(): RequestBody = toRequestBody(textMediaType)
}
