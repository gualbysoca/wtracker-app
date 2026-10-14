package com.wtracker.field.services.clients

import com.wtracker.field.services.api.dto.MobileClientDto
import com.wtracker.field.services.local.ClientEntity
import com.wtracker.field.services.local.SyncStates
import com.wtracker.field.services.local.VisitRecordEntity
import com.wtracker.field.services.models.FieldClient
import com.wtracker.field.services.models.GeoCoordinate
import com.wtracker.field.services.models.VisitStatus
import com.wtracker.field.utils.TimeUtils

internal fun MobileClientDto.toEntity(downloadedAtMillis: Long) = ClientEntity(
    id = id,
    businessName = businessName,
    contactName = contactName,
    phone = phone,
    address = address,
    latitude = lat,
    longitude = lng,
    serverLastVisitStatus = lastVisitStatus,
    serverLastVisitAtMillis = TimeUtils.parseIsoMillis(lastVisitAt),
    downloadedAtMillis = downloadedAtMillis,
)

/**
 * El estado del día se deriva SÓLO de las marcaciones locales del usuario actual:
 * `last_visit_status` del servidor es global (cualquier trabajador) y no representa
 * el avance personal de esta hoja de ruta.
 */
internal fun ClientEntity.toFieldClient(latestTodayRecord: VisitRecordEntity?) = FieldClient(
    id = id,
    businessName = businessName,
    contactName = contactName,
    phone = phone,
    address = address,
    coordinate = toCoordinateOrNull(latitude, longitude),
    currentStatus = VisitStatus.fromApiValue(latestTodayRecord?.status),
    statusUpdatedAtMillis = latestTodayRecord?.recordedAtMillis,
    hasPendingSync = latestTodayRecord != null &&
        latestTodayRecord.syncState != SyncStates.SYNCED &&
        latestTodayRecord.syncState != SyncStates.REJECTED,
)

private fun toCoordinateOrNull(latitude: Double?, longitude: Double?): GeoCoordinate? {
    if (latitude == null || longitude == null) return null
    return GeoCoordinate(latitude, longitude)
}
