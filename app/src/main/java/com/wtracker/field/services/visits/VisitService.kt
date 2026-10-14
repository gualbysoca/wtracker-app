package com.wtracker.field.services.visits

import com.wtracker.field.configs.EvidenceConfig
import com.wtracker.field.configs.LocalTransactionRunner
import com.wtracker.field.configs.VisitConfig
import com.wtracker.field.services.auth.SessionStore
import com.wtracker.field.services.local.EvidenceDao
import com.wtracker.field.services.local.EvidenceEntity
import com.wtracker.field.services.local.SyncStates
import com.wtracker.field.services.local.VisitRecordDao
import com.wtracker.field.services.local.VisitRecordEntity
import com.wtracker.field.services.models.CapturedEvidence
import com.wtracker.field.services.models.CapturedLocation
import com.wtracker.field.services.models.VisitMarkRequest
import com.wtracker.field.services.models.VisitStatus
import com.wtracker.field.services.sync.SyncScheduler
import com.wtracker.field.utils.AppError
import com.wtracker.field.utils.AppResult
import com.wtracker.field.utils.TimeProvider
import com.wtracker.field.validations.ValidationResult
import com.wtracker.field.validations.VisitMarkValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/**
 * Registra marcaciones SIEMPRE en local primero (Room) y delega el envío al worker de
 * sincronización. Así una visita nunca se pierde por falta de cobertura.
 */
class VisitService(
    private val visitRecordDao: VisitRecordDao,
    private val evidenceDao: EvidenceDao,
    private val transactionRunner: LocalTransactionRunner,
    private val sessionStore: SessionStore,
    private val syncScheduler: SyncScheduler,
    private val timeProvider: TimeProvider,
) {

    suspend fun registerVisitMark(request: VisitMarkRequest): AppResult<Unit> {
        val session = sessionStore.ensureLoaded()
            ?: return AppResult.Failure(AppError.Unauthorized("Tu sesión expiró. Inicia sesión nuevamente."))

        val validation = VisitMarkValidator.validate(request, timeProvider.nowMillis())
        if (validation is ValidationResult.Invalid) return AppResult.Failure(AppError.Validation(validation.message))

        val visitRecord = buildVisitRecord(request, session.userId)
        val evidenceRecord = request.evidence?.let { buildEvidenceRecord(it, visitRecord.localId) }

        transactionRunner.runInTransaction {
            visitRecordDao.insert(visitRecord)
            evidenceRecord?.let { evidenceDao.insert(it) }
        }
        syncScheduler.requestImmediateSync()
        return AppResult.Success(Unit)
    }

    /**
     * Cierre de ruta: los clientes no visitados se registran como "fallido" generados por el sistema.
     * No pasan por [VisitMarkValidator] porque no hubo presencia en el lugar (no hay foto posible) y
     * el Backoffice acepta "fallido" sin evidencia. La ubicación es la del trabajador al cerrar.
     */
    suspend fun registerRouteClosureFailures(clientIds: Collection<String>, location: CapturedLocation): AppResult<Int> {
        val session = sessionStore.ensureLoaded()
            ?: return AppResult.Failure(AppError.Unauthorized("Tu sesión expiró. Inicia sesión nuevamente."))
        if (clientIds.isEmpty()) return AppResult.Success(0)

        val recordedAtMillis = timeProvider.nowMillis()
        val failureRecords = clientIds.map { clientId ->
            buildRouteClosureFailure(clientId, session.userId, location, recordedAtMillis)
        }
        transactionRunner.runInTransaction { failureRecords.forEach { visitRecordDao.insert(it) } }
        syncScheduler.requestImmediateSync()
        return AppResult.Success(failureRecords.size)
    }

    fun observePendingSyncCount(): Flow<Int> = sessionStore.sessionFlow.flatMapLatest { session ->
        if (session == null) flowOf(0) else visitRecordDao.observePendingCount(session.userId)
    }

    private fun buildRouteClosureFailure(
        clientId: String,
        userId: String,
        location: CapturedLocation,
        recordedAtMillis: Long,
    ) = VisitRecordEntity(
        localId = UUID.randomUUID().toString(),
        clientId = clientId,
        userId = userId,
        status = VisitStatus.FAILED.apiValue,
        latitude = location.coordinate.latitude,
        longitude = location.coordinate.longitude,
        accuracyMeters = location.accuracyMeters,
        notes = VisitConfig.ROUTE_CLOSURE_FAILURE_NOTE,
        recordedAtMillis = recordedAtMillis,
        remoteVisitId = null,
        syncState = SyncStates.PENDING_VISIT,
        syncAttempts = 0,
        lastSyncError = null,
    )

    private fun buildVisitRecord(request: VisitMarkRequest, userId: String) = VisitRecordEntity(
        localId = UUID.randomUUID().toString(),
        clientId = request.clientId,
        userId = userId,
        status = request.status.apiValue,
        latitude = request.location.coordinate.latitude,
        longitude = request.location.coordinate.longitude,
        accuracyMeters = request.location.accuracyMeters,
        notes = request.notes?.trim()?.takeIf { it.isNotEmpty() },
        // Hora del evento real (captura de la foto si existe), no la hora del envío.
        recordedAtMillis = request.evidence?.capturedAtMillis ?: request.location.capturedAtMillis,
        remoteVisitId = null,
        syncState = SyncStates.PENDING_VISIT,
        syncAttempts = 0,
        lastSyncError = null,
    )

    private fun buildEvidenceRecord(evidence: CapturedEvidence, visitLocalId: String) = EvidenceEntity(
        localId = UUID.randomUUID().toString(),
        visitLocalId = visitLocalId,
        filePath = evidence.filePath,
        sha256 = evidence.sha256,
        mimeType = evidence.mimeType,
        evidenceType = EvidenceConfig.TYPE_PHOTO,
        latitude = evidence.location.coordinate.latitude,
        longitude = evidence.location.coordinate.longitude,
        accuracyMeters = evidence.location.accuracyMeters,
        isMockLocation = evidence.location.isMockLocation,
        fixTimeMillis = evidence.location.fixTimeMillis,
        capturedAtMillis = evidence.capturedAtMillis,
        remoteEvidenceId = null,
        uploadedAtMillis = null,
    )
}
