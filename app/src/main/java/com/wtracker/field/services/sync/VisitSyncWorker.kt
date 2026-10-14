package com.wtracker.field.services.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wtracker.field.WTrackerApplication

/** Worker delgado: toda la lógica vive en [VisitSyncService] para poder probarla aislada. */
class VisitSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as WTrackerApplication).container
        
        val locationOutcome = container.locationSyncService.syncPendingLocations()
        val visitOutcome = container.visitSyncService.syncPendingVisits()

        if (locationOutcome == SyncOutcome.NO_SESSION || visitOutcome == SyncOutcome.NO_SESSION) {
            return Result.success()
        }

        return if (locationOutcome == SyncOutcome.RETRY_LATER || visitOutcome == SyncOutcome.RETRY_LATER) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
