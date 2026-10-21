package com.god.ApplicationManager.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** All WorkManager entry points for freeze work. */
object FreezeWorkScheduler {
    private const val UNIQUE_FREEZE_NOW = "freeze-now"
    private const val UNIQUE_FREEZE_PERIODIC = "freeze-periodic-backstop"

    /** Run the queue drain as soon as possible. */
    @JvmStatic
    fun enqueueFreezeNow(context: Context, trigger: String) {
        val request = OneTimeWorkRequestBuilder<FreezeWorker>()
            .setInputData(Data.Builder().putString(FreezeWorker.KEY_TRIGGER, trigger).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_FREEZE_NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
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
