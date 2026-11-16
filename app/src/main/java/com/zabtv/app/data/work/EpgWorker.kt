package com.zabtv.app.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zabtv.app.TpApp
import com.zabtv.app.data.repo.EpgService
import java.util.concurrent.TimeUnit

/**
 * Refresco periódico de EPG (Task 13): cada 6 h con backoff exponencial, solo
 * con red y sin batería baja. Recorre las listas con epgUrl y delega en
 * EpgService (mismo pipeline con cap 120 MB + ventana + prune 5 días).
 */
class EpgWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as TpApp).container
        val service = EpgService(applicationContext, container.db, container.okHttp)
        return try {
            val lists = container.db.listDao().all()
            var failures = 0
            for (list in lists) {
                val epgUrl = list.epgUrl
                if (epgUrl.isNullOrBlank()) continue
                runCatching { service.refresh(epgUrl) }.onFailure { failures++ }
            }
            if (failures > 0 && failures == lists.count { !it.epgUrl.isNullOrBlank() }) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val NAME = "epg-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<EpgWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
