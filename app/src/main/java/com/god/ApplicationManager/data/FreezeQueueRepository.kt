package com.god.ApplicationManager.data

import android.content.Context
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.DB.FreezeQueueItem

/**
 * Durable freeze queue: requests are rows in Room (`freeze_queue`) drained by
 * FreezeWorker. Enqueue is fire-and-forget onto the DB executor, so callers on
 * the main thread never block and the request survives the caller going away.
 */
object FreezeQueueRepository {

    @JvmStatic
    fun enqueue(context: Context, packageNames: List<String>) {
        if (packageNames.isEmpty()) return
        val appContext = context.applicationContext
        AppDatabase.dbIo.execute {
            val dao = AppDatabase.getInstance(appContext).freezeQueueDao()
            val now = System.currentTimeMillis()
            for (pkg in packageNames.distinct()) {
                val item = FreezeQueueItem()
                item.packageName = pkg
                item.attempts = 0
                item.enqueuedAt = now
                dao.insert(item)
            }
        }
    }

    @JvmStatic
    fun pending(context: Context): List<FreezeQueueItem> =
        AppDatabase.getInstance(context).freezeQueueDao().all

    @JvmStatic
    fun remove(context: Context, id: Long) {
        AppDatabase.getInstance(context).freezeQueueDao().deleteById(id)
    }

    @JvmStatic
    fun recordAttempt(context: Context, id: Long) {
        AppDatabase.getInstance(context).freezeQueueDao().incrementAttempts(id)
    }
}
