package com.wtracker.field.services.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wtracker.field.configs.SyncConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Wrapper de WorkManager. La restricción CONNECTED hace que el sistema dispare el envío
 * automáticamente cuando vuelve la cobertura, incluso si la app está cerrada.
 */
class SyncScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun requestImmediateSync() {
        val request = OneTimeWorkRequestBuilder<VisitSyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, SyncConfig.BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE: una marcación nueva nunca se descarta aunque haya un envío en curso.
        workManager.enqueueUniqueWork(SyncConfig.UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Red de seguridad por si algún disparo inmediato se perdió (ej. el proceso fue terminado). */
    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<VisitSyncWorker>(SyncConfig.PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .build()
        workManager.enqueueUniquePeriodicWork(SyncConfig.PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(SyncConfig.PERIODIC_WORK_NAME)
    }

    /**
     * Espera, como máximo [timeoutMillis], a que termine el envío inmediato en curso. Se observa el
     * trabajo de WorkManager en vez de sincronizar directamente para no competir con el worker
     * (dos envíos simultáneos podrían duplicar visitas). Devuelve false si se agotó el tiempo.
     */
    suspend fun awaitImmediateSyncIdle(timeoutMillis: Long): Boolean {
        val finished = withTimeoutOrNull(timeoutMillis) {
            workManager.getWorkInfosForUniqueWorkFlow(SyncConfig.UNIQUE_WORK_NAME)
                .first { workInfos -> workInfos.all { it.state.isFinished } }
        }
        return finished != null
    }
}
