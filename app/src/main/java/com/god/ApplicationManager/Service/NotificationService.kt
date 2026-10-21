package com.god.ApplicationManager.Service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.DB.SettingsDB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Cancels notifications from packages on the user's turn-off list.
 *
 * Rules are cached in memory and refreshed asynchronously from Room whenever
 * the listener connects or the master switch toggles; the posted-notification
 * path only reads the volatile cache, so it never touches the database and can
 * never NPE on an uninitialised list (the two historical failure modes here).
 */
class NotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    @Volatile
    private var masterDisabled: Boolean = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        refresh()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refresh()
        if (!masterDisabled) {
            try {
                activeNotifications?.forEach { sbn ->
                    if (sbn.packageName in blockedPackages) {
                        cancelNotification(sbn.key)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sweep active notifications", e)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || masterDisabled) return
        if (sbn.packageName in blockedPackages) {
            cancelNotification(sbn.key)
            archive(sbn)
        }
    }

    /** Keeps title/app/time of the cancelled notification in the in-app archive (F10), capped at 200 rows. */
    private fun archive(sbn: StatusBarNotification) {
        if (!SettingsDB.getInstance().storeBlockedNotifications) return
        val title = try {
            sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        } catch (e: Exception) {
            null
        }
        val appContext = applicationContext
        AppDatabase.dbIo.execute {
            try {
                val dao = AppDatabase.getInstance(appContext).featureDao()
                val item = com.god.ApplicationManager.DB.BlockedNotificationItem()
                item.packageName = sbn.packageName
                item.title = title
                item.at = System.currentTimeMillis()
                dao.insertBlocked(item)
                dao.trimBlocked(200)
            } catch (ignored: Exception) {
            }
        }
    }

    /** Reloads the rule list and the master-disable flag from storage, off the main thread. */
    fun refresh() {
        scope.launch {
            try {
                masterDisabled = SettingsDB.getInstance().isDisableTurnOffNotification
                blockedPackages = AppDatabase.getInstance(applicationContext)
                    .appInfoDao().turnOffNotifList
                    .map { it.packageName }
                    .toSet()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh notification rules", e)
            }
        }
    }

    companion object {
        private const val TAG = "NotificationService"

        @Volatile
        private var instance: NotificationService? = null

        /**
         * Legacy entry point used by SettingsDB/NotificationShortcutActivity:
         * nudges the running service (if any) to reload the disable flag + rules.
         */
        @JvmStatic
        fun getDisableNotificationStatus() {
            instance?.refresh()
        }

        /**
         * Legacy name kept for MainActivity's post-action callbacks: reload the
         * turn-off rules from the database (asynchronously, inside the service).
         */
        @JvmStatic
        fun getListTurnOffNotificationFromDB() {
            instance?.refresh()
        }
    }
}
