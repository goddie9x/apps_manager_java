package com.god.ApplicationManager.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.god.ApplicationManager.DB.SettingsDB
import com.god.ApplicationManager.Helper.UsageStatsHelper
import com.god.ApplicationManager.R
import com.god.ApplicationManager.UI.MainActivity
import java.util.concurrent.TimeUnit

/**
 * Weekly suggestion (never an automatic action): finds user apps not used for
 * longer than the configured threshold and posts ONE summary notification.
 * Requires Usage Access and the POST_NOTIFICATIONS runtime permission.
 */
class UsageReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        if (!UsageStatsHelper.hasUsageAccess(ctx)) return Result.success()
        val thresholdDays = try {
            SettingsDB.getInstance().unusedAppThresholdDays.coerceIn(1, 3650)
        } catch (e: Exception) {
            60
        }
        val usage = UsageStatsHelper.usageByPackage(ctx, 90)
        val now = System.currentTimeMillis()
        val cutoff = now - TimeUnit.DAYS.toMillis(thresholdDays.toLong())
        val unused = mutableListOf<String>()
        for (pkg in ctx.packageManager.getInstalledPackages(0)) {
            val info = pkg.applicationInfo ?: continue
            if (info.packageName == ctx.packageName) continue
            if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            val lastUsed = usage[info.packageName]?.lastTimeUsed ?: 0L
            val reference = if (lastUsed > 0) lastUsed else pkg.firstInstallTime
            if (reference in 1 until cutoff) {
                unused.add(info.loadLabel(ctx.packageManager).toString())
            }
        }
        if (unused.isEmpty()) return Result.success()
        postNotification(ctx, unused, thresholdDays)
        return Result.success()
    }

    @android.annotation.SuppressLint("MissingPermission") // permission checked explicitly above
    private fun postNotification(ctx: Context, unused: List<String>, thresholdDays: Int) {
        // Suggestions stay silent unless the user granted POST_NOTIFICATIONS.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, ctx.getString(R.string.notif_channel_suggestions), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val intent = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = ctx.getString(R.string.unused_apps_notification, unused.size, thresholdDays, unused.take(3).joinToString(", "))
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        try {
            //noinspection NotificationPermission -- POST_NOTIFICATIONS verified at the top of this method
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted: suggestions stay silent by design.
        }
    }

    companion object {
        private const val CHANNEL_ID = "suggestions"
        private const val NOTIFICATION_ID = 4201
    }
}
