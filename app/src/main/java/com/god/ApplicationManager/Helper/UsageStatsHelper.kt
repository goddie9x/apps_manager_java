package com.god.ApplicationManager.Helper

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import java.util.concurrent.TimeUnit

/** Snapshot of one app's storage footprint (bytes). */
data class StorageInfo(val appBytes: Long, val dataBytes: Long, val cacheBytes: Long) {
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}

/** Snapshot of one app's recent usage. */
data class UsageInfo(val lastTimeUsed: Long, val totalForegroundMillis: Long, val launchCount: Int)

/**
 * Real usage/storage data from the platform (replaces the old FLAG_STOPPED
 * guesswork). Every call degrades to null/empty when Usage Access is missing.
 */
object UsageStatsHelper {

    @JvmStatic
    fun hasUsageAccess(context: Context): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        return try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /** Usage over the last [days] days keyed by package name. Blocking; call off the main thread. */
    @JvmStatic
    fun usageByPackage(context: Context, days: Int): Map<String, UsageInfo> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val from = now - TimeUnit.DAYS.toMillis(days.toLong())
        val result = mutableMapOf<String, UsageInfo>()
        try {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, from, now)
            for (s in stats) {
                val prev = result[s.packageName]
                result[s.packageName] = UsageInfo(
                    lastTimeUsed = maxOf(prev?.lastTimeUsed ?: 0L, s.lastTimeUsed),
                    totalForegroundMillis = (prev?.totalForegroundMillis ?: 0L) + s.totalTimeInForeground,
                    launchCount = prev?.launchCount ?: 0,
                )
            }
            // Launch counts live in usage events on newer APIs.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val events = usm.queryEvents(from, now)
                val event = android.app.usage.UsageEvents.Event()
                val counts = mutableMapOf<String, Int>()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                        counts[event.packageName] = (counts[event.packageName] ?: 0) + 1
                    }
                }
                for ((pkg, count) in counts) {
                    val prev = result[pkg]
                    if (prev != null) result[pkg] = prev.copy(launchCount = count)
                }
            }
        } catch (e: Exception) {
            // No usage access: callers treat an empty map as "unknown".
        }
        return result
    }

    /** Storage sizes for one app, or null when unavailable (needs API 26+ and usage access). */
    @JvmStatic
    fun storageFor(context: Context, packageName: String): StorageInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return try {
            val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val stats = ssm.queryStatsForUid(StorageManager.UUID_DEFAULT, appInfo.uid)
            StorageInfo(stats.appBytes, stats.dataBytes, stats.cacheBytes)
        } catch (e: Exception) {
            null
        }
    }
}
