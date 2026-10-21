package com.god.ApplicationManager.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.god.ApplicationManager.data.ActivityLogger
import com.god.ApplicationManager.privilege.PrivilegeManager

/**
 * Restores a set of packages frozen earlier (focus sessions, scheduled profile
 * unfreeze, rescue follow-ups). Input: string array under [KEY_PACKAGES].
 */
class UnfreezeWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val packages = inputData.getStringArray(KEY_PACKAGES) ?: return Result.success()
        PrivilegeManager.refreshRootBlocking()
        val executor = PrivilegeManager.current(applicationContext)
        for (pkg in packages) {
            val ok = executor.unfreeze(applicationContext, pkg)
            ActivityLogger.log(applicationContext, pkg, "unfreeze", executor.backend.name, ok)
        }
        return Result.success()
    }

    companion object {
        const val KEY_PACKAGES = "packages"
    }
}
