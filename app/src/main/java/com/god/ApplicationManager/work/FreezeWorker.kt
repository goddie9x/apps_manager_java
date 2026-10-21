package com.god.ApplicationManager.work

import android.app.KeyguardManager
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.data.FreezeQueueRepository
import com.god.ApplicationManager.privilege.PrivilegeManager

/**
 * Drains the durable freeze queue with the strongest privileged backend.
 *
 * Triggers: "manual" (shortcut / in-app freeze-all), "screen_off" (the
 * screen-off receiver), "periodic" (daily backstop). When the queue is empty
 * and the trigger is screen_off, the current freeze rule list is the work set.
 * Rows that fail 3 attempts are dropped so a bad package cannot loop forever.
 * Runs entirely on WorkManager's background thread.
 */
class FreezeWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val appContext = applicationContext
        PrivilegeManager.refreshRootBlocking()

        val queued = FreezeQueueRepository.pending(appContext)
        val trigger = inputData.getString(KEY_TRIGGER)
        val targets: List<Pair<Long, String>> = if (queued.isNotEmpty()) {
            queued.map { it.id to it.packageName }
        } else if (trigger == TRIGGER_SCREEN_OFF || trigger == TRIGGER_BATTERY_LOW) {
            AppDatabase.getInstance(appContext).appInfoDao().freezeList
                .map { -1L to it.packageName }
        } else {
            emptyList()
        }
        if (targets.isEmpty()) return Result.success()

        val keyguard = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguard.isKeyguardLocked) {
            Log.i(TAG, "Screen locked; deferring freeze of ${targets.size} apps")
            return Result.retry()
        }

        val executor = PrivilegeManager.current(appContext)
        var allDone = true
        for ((rowId, pkg) in targets) {
            if (pkg == appContext.packageName) continue
            val ok = executor.standbyAndStop(appContext, pkg)
            com.god.ApplicationManager.data.ActivityLogger.log(
                appContext, pkg, "freeze", executor.backend.name, ok,
            )
            if (rowId >= 0) {
                if (ok) {
                    FreezeQueueRepository.remove(appContext, rowId)
                } else {
                    val item = queued.firstOrNull { it.id == rowId }
                    if (item != null && item.attempts + 1 >= MAX_ATTEMPTS) {
                        Log.w(TAG, "Giving up on $pkg after ${item.attempts + 1} attempts")
                        FreezeQueueRepository.remove(appContext, rowId)
                    } else {
                        FreezeQueueRepository.recordAttempt(appContext, rowId)
                        allDone = false
                    }
                }
            } else if (!ok) {
                allDone = false
            }
        }
        return if (allDone) Result.success() else Result.retry()
    }

    companion object {
        private const val TAG = "FreezeWorker"
        const val KEY_TRIGGER = "trigger"
        const val TRIGGER_MANUAL = "manual"
        const val TRIGGER_SCREEN_OFF = "screen_off"
        const val TRIGGER_BATTERY_LOW = "battery_low"
        const val TRIGGER_PERIODIC = "periodic"
        private const val MAX_ATTEMPTS = 3
    }
}
