package com.god.ApplicationManager.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.security.RemoteFeedFetcher
import java.util.concurrent.TimeUnit

/**
 * Refreshes enabled remote threat-intel feeds once a day (network required).
 * A failed refresh keeps the last good cache; feed status is visible in the
 * security screen's Sources tab.
 */
class ThreatIntelRefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val appContext = applicationContext
            val dao = AppDatabase.getInstance(appContext).securityDao()
            val feeds = dao.enabledFeeds()
            if (feeds.isEmpty()) {
                Log.i(TAG, "No enabled threat feeds; nothing to refresh")
                return Result.success()
            }
            var failures = 0
            for (feed in feeds) {
                val result = RemoteFeedFetcher.fetch(appContext, feed)
                Log.i(TAG, "Feed ${feed.name}: ${result.status} (${result.entryCount} entries)")
                if (!result.ok) failures++
            }
            if (failures == feeds.size) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Threat feed refresh failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ThreatIntelRefresh"
        private const val WORK_NAME = "threat-intel-refresh"

        @JvmStatic
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ThreatIntelRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** One-shot refresh used by the "refresh now" button; call from a background thread. */
        @JvmStatic
        fun refreshNow(context: Context) {
            val appContext = context.applicationContext
            val dao = AppDatabase.getInstance(appContext).securityDao()
            for (feed in dao.enabledFeeds()) {
                RemoteFeedFetcher.fetch(appContext, feed)
            }
        }
    }
}
