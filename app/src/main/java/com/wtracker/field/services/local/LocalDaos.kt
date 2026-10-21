package com.wtracker.field.services.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

private const val PENDING_STATES = "('${SyncStates.PENDING_VISIT}', '${SyncStates.PENDING_EVIDENCE}')"

@Dao
abstract class ClientDao {

    @Query("SELECT * FROM clients ORDER BY businessName ASC")
    abstract fun observeAll(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :clientId LIMIT 1")
    abstract fun observeById(clientId: String): Flow<ClientEntity?>

    @Query("UPDATE clients SET latitude = :lat, longitude = :lng WHERE id = :clientId")
    abstract suspend fun updateCoordinates(clientId: String, lat: Double, lng: Double)

    @Query(
        """
        UPDATE clients SET 
            businessName = COALESCE(:businessName, businessName),
            contactName = COALESCE(:contactName, contactName),
            phone = COALESCE(:phone, phone),
            address = COALESCE(:address, address),
            deliveryAddress = COALESCE(:deliveryAddress, deliveryAddress),
            stallNumber = COALESCE(:stallNumber, stallNumber),
            clientCode = COALESCE(:clientCode, clientCode),
            latitude = COALESCE(:lat, latitude),
            longitude = COALESCE(:lng, longitude)
        WHERE id = :clientId
        """
    )
    abstract suspend fun updateClientDetails(
        clientId: String,
        businessName: String?,
        contactName: String?,
        phone: String?,
        address: String?,
        deliveryAddress: String?,
        stallNumber: String?,
        clientCode: String?,
        lat: Double?,
        lng: Double?,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(clients: List<ClientEntity>)

    @Query("DELETE FROM clients")
    protected abstract suspend fun deleteAll()

    /** Reemplaza el directorio local por el descargado (evita el límite de 999 parámetros de `NOT IN`). */
    @Transaction
    open suspend fun replaceAll(clients: List<ClientEntity>) {
        deleteAll()
        insertAll(clients)
    }
}

@Dao
interface VisitRecordDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: VisitRecordEntity)

    @Query(
        "SELECT * FROM visit_records WHERE userId = :userId AND syncState IN $PENDING_STATES " +
            "ORDER BY recordedAtMillis ASC",
    )
    suspend fun findPendingForUser(userId: String): List<VisitRecordEntity>

    @Query(
        "UPDATE visit_records SET remoteVisitId = :remoteVisitId, syncState = :syncState, " +
            "lastSyncError = NULL WHERE localId = :localId",
    )
    suspend fun markVisitUploaded(localId: String, remoteVisitId: String, syncState: String)

    @Query("UPDATE visit_records SET syncState = '${SyncStates.SYNCED}', lastSyncError = NULL WHERE localId = :localId")
    suspend fun markSynced(localId: String)

    @Query(
        "UPDATE visit_records SET syncAttempts = syncAttempts + 1, lastSyncError = :error " +
            "WHERE localId = :localId",
    )
    suspend fun registerSyncFailure(localId: String, error: String)

    @Query(
        "UPDATE visit_records SET syncState = '${SyncStates.REJECTED}', lastSyncError = :error " +
            "WHERE localId = :localId",
    )
    suspend fun markRejected(localId: String, error: String)

    @Query("SELECT COUNT(*) FROM visit_records WHERE userId = :userId AND syncState IN $PENDING_STATES")
    fun observePendingCount(userId: String): Flow<Int>

    /** Última marcación de cada cliente desde [sinceMillis] (hoy) para el usuario actual. */
    @Query(
        """
        SELECT v.* FROM visit_records v
        INNER JOIN (
            SELECT clientId, MAX(recordedAtMillis) AS latestAt
            FROM visit_records
            WHERE userId = :userId AND recordedAtMillis >= :sinceMillis
            GROUP BY clientId
        ) latest ON v.clientId = latest.clientId AND v.recordedAtMillis = latest.latestAt
        WHERE v.userId = :userId
        """,
    )
    fun observeLatestPerClientSince(userId: String, sinceMillis: Long): Flow<List<VisitRecordEntity>>

    @Query(
        """
        SELECT v.* FROM visit_records v
        INNER JOIN (
            SELECT clientId, MAX(recordedAtMillis) AS latestAt
            FROM visit_records
            WHERE userId = :userId AND recordedAtMillis >= :sinceMillis
            GROUP BY clientId
        ) latest ON v.clientId = latest.clientId AND v.recordedAtMillis = latest.latestAt
        WHERE v.userId = :userId
        """,
    )
    suspend fun getLatestPerClientSince(userId: String, sinceMillis: Long): List<VisitRecordEntity>

    @Query(
        "SELECT * FROM visit_records WHERE userId = :userId AND recordedAtMillis >= :sinceMillis " +
            "ORDER BY recordedAtMillis DESC",
    )
    fun observeSince(userId: String, sinceMillis: Long): Flow<List<VisitRecordEntity>>
}

@Dao
interface EvidenceDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(evidence: EvidenceEntity)

    @Query("SELECT * FROM evidences WHERE visitLocalId = :visitLocalId AND uploadedAtMillis IS NULL")
    suspend fun findPendingForVisit(visitLocalId: String): List<EvidenceEntity>

    @Query(
        "UPDATE evidences SET remoteEvidenceId = :remoteEvidenceId, uploadedAtMillis = :uploadedAtMillis " +
            "WHERE localId = :localId",
    )
    suspend fun markUploaded(localId: String, remoteEvidenceId: String, uploadedAtMillis: Long)
}

@Dao
interface ShiftDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(shift: ShiftEntity)

    @Query("SELECT * FROM shifts WHERE userId = :userId AND endedAtMillis IS NULL ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun findActive(userId: String): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE userId = :userId AND endedAtMillis IS NULL ORDER BY startedAtMillis DESC LIMIT 1")
    fun observeActive(userId: String): Flow<ShiftEntity?>

    @Query(
        "SELECT * FROM shifts WHERE userId = :userId AND startedAtMillis >= :sinceMillis " +
            "ORDER BY startedAtMillis DESC LIMIT 1",
    )
    fun observeLatestSince(userId: String, sinceMillis: Long): Flow<ShiftEntity?>

    @Query("UPDATE shifts SET endedAtMillis = :endedAtMillis WHERE id = :shiftId")
    suspend fun close(shiftId: String, endedAtMillis: Long)
}

@Dao
interface PlannedVisitDao {

    /** IGNORE: volver a agregar un cliente que ya está en la ruta no es un error. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(plannedVisits: List<PlannedVisitEntity>)

    @Query("SELECT clientId FROM planned_visits WHERE shiftId = :shiftId")
    fun observeClientIds(shiftId: String): Flow<List<String>>

    @Query("SELECT clientId FROM planned_visits WHERE shiftId = :shiftId")
    suspend fun findClientIds(shiftId: String): List<String>

    @Query("DELETE FROM planned_visits WHERE shiftId = :shiftId AND clientId = :clientId")
    suspend fun deleteClient(shiftId: String, clientId: String)
}

@Dao
interface LocationPingDao {

    @Insert
    suspend fun insert(ping: LocationPingEntity)

    @Query("SELECT * FROM location_pings WHERE shiftId = :shiftId ORDER BY recordedAtMillis ASC")
    fun observeForShift(shiftId: String): Flow<List<LocationPingEntity>>

    @Query("SELECT * FROM location_pings ORDER BY recordedAtMillis ASC LIMIT :limit")
    suspend fun getPendingBatch(limit: Int): List<LocationPingEntity>

    @Query("DELETE FROM location_pings WHERE id IN (:ids)")
    suspend fun deleteBatch(ids: List<Long>)
}

@Dao
interface StopEventDao {

    @Insert
    suspend fun insert(stopEvent: StopEventEntity): Long

    @Query("SELECT * FROM stop_events WHERE shiftId = :shiftId AND endedAtMillis IS NULL ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun findOpen(shiftId: String): StopEventEntity?

    @Query("UPDATE stop_events SET type = :type WHERE id = :stopEventId")
    suspend fun updateType(stopEventId: Long, type: String)

    @Query("UPDATE stop_events SET endedAtMillis = :endedAtMillis WHERE id = :stopEventId")
    suspend fun close(stopEventId: Long, endedAtMillis: Long)

    @Query("SELECT * FROM stop_events WHERE shiftId = :shiftId ORDER BY startedAtMillis DESC")
    fun observeForShift(shiftId: String): Flow<List<StopEventEntity>>
}
