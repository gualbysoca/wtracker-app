package com.wtracker.field.services.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Estados de la cola de sincronización (strings constantes para usarlos en @Query). */
object SyncStates {
    const val PENDING_VISIT = "PENDING_VISIT"
    const val PENDING_EVIDENCE = "PENDING_EVIDENCE"
    const val SYNCED = "SYNCED"
    const val REJECTED = "REJECTED"
}

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String,
    val businessName: String,
    val contactName: String?,
    val phone: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val serverLastVisitStatus: String?,
    val serverLastVisitAtMillis: Long?,
    val downloadedAtMillis: Long,
)

/**
 * Cada marcación (en_ruta / visitado / fallido) es un registro inmutable en la cola.
 * `userId` evita que, si otro usuario inicia sesión en el mismo equipo, se envíen
 * visitas pendientes con un JWT ajeno.
 */
@Entity(
    tableName = "visit_records",
    indices = [Index("clientId"), Index("userId"), Index("syncState"), Index("recordedAtMillis")],
)
data class VisitRecordEntity(
    @PrimaryKey val localId: String,
    val clientId: String,
    val userId: String,
    val status: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val notes: String?,
    val recordedAtMillis: Long,
    val remoteVisitId: String?,
    val syncState: String,
    val syncAttempts: Int,
    val lastSyncError: String?,
)

@Entity(
    tableName = "evidences",
    foreignKeys = [
        ForeignKey(
            entity = VisitRecordEntity::class,
            parentColumns = ["localId"],
            childColumns = ["visitLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("visitLocalId")],
)
data class EvidenceEntity(
    @PrimaryKey val localId: String,
    val visitLocalId: String,
    val filePath: String,
    val sha256: String,
    val mimeType: String,
    val evidenceType: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val isMockLocation: Boolean,
    val fixTimeMillis: Long,
    val capturedAtMillis: Long,
    val remoteEvidenceId: String?,
    val uploadedAtMillis: Long?,
)

@Entity(tableName = "shifts", indices = [Index("userId")])
data class ShiftEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
)

/**
 * Cliente elegido por el trabajador para su ruta del día. La ruta vive atada a la jornada
 * (`shiftId`): iniciar la ruta abre la jornada y finalizarla la cierra.
 */
@Entity(tableName = "planned_visits", primaryKeys = ["shiftId", "clientId"])
data class PlannedVisitEntity(
    val shiftId: String,
    val clientId: String,
    val addedAtMillis: Long,
)

@Entity(tableName = "location_pings", indices = [Index("shiftId")])
data class LocationPingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val isMockLocation: Boolean,
    val recordedAtMillis: Long,
)

@Entity(tableName = "stop_events", indices = [Index("shiftId")])
data class StopEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
)
