package com.god.ApplicationManager.data

import android.content.Context
import com.god.ApplicationManager.DB.ActivityLogItem
import com.god.ApplicationManager.DB.AppDatabase

/** Fire-and-forget writer for the in-app activity history (F8). */
object ActivityLogger {
    @JvmStatic
    fun log(context: Context, packageName: String, action: String, backend: String, success: Boolean) {
        val appContext = context.applicationContext
        AppDatabase.dbIo.execute {
            try {
                val item = ActivityLogItem()
                item.packageName = packageName
                item.action = action
                item.backend = backend
                item.success = success
                item.at = System.currentTimeMillis()
                AppDatabase.getInstance(appContext).featureDao().insertLog(item)
            } catch (ignored: Exception) {
                // History must never break the action it records.
            }
        }
    }
}
