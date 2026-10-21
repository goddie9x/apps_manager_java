package com.god.ApplicationManager.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit
import androidx.work.WorkManager

/** All WorkManager entry points for freeze work. */
object FreezeWorkScheduler {
    private const val UNIQUE_FREEZE_NOW = "freeze-now"
    private const val UNIQUE_FREEZE_PERIODIC = "freeze-periodic-backstop"

    /** Run the queue drain as soon as possible (screen-off work honours the configured delay). */
    @JvmStatic
    fun enqueueFreezeNow(context: Context, trigger: String) {
        val delaySeconds = if (trigger == FreezeWorker.TRIGGER_SCREEN_OFF) {
            try {
                com.god.ApplicationManager.DB.SettingsDB.getInstance().screenOffFreezeDelaySeconds
                    .coerceIn(0, 600).toLong()
            } catch (e: Exception) {
                0L
            }
        } else {
            0L
        }
        val request = OneTimeWorkRequestBuilder<FreezeWorker>()
            .setInputData(Data.Builder().putString(FreezeWorker.KEY_TRIGGER, trigger).build())
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_FREEZE_NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Weekly unused-app suggestion check (F15). Suggestion only: never acts on its own. */
    @JvmStatic
    fun scheduleUsageReminder(context: Context) {
        val request = PeriodicWorkRequestBuilder<com.god.ApplicationManager.work.UsageReminderWorker>(
            7, TimeUnit.DAYS,
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "unused-app-reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Daily backstop so queued rows never sit forever (e.g. after long lock periods). */
    @JvmStatic
    fun schedulePeriodicBackstop(context: Context) {
        val request = PeriodicWorkRequestBuilder<FreezeWorker>(1, TimeUnit.DAYS)
            .setInputData(Data.Builder().putString(FreezeWorker.KEY_TRIGGER, FreezeWorker.TRIGGER_PERIODIC).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(UNIQUE_FREEZE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
