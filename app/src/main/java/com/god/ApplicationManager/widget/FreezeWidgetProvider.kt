package com.god.ApplicationManager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.R
import com.god.ApplicationManager.UI.FreezeShortcutActivity
import com.god.ApplicationManager.UI.MainActivity

/**
 * Home-screen widget (F9): live counts of frozen/blocked rules plus a
 * one-tap "freeze all" that goes through the same exported=false shortcut
 * activity as the launcher shortcut. Counts refresh on the widget's update
 * period and whenever the system nudges the provider.
 */
class FreezeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        AppDatabase.dbIo.execute {
            try {
                val db = AppDatabase.getInstance(context)
                val frozen = db.appInfoDao().freezeList.size
                val blocked = db.appInfoDao().turnOffNotifList.size
                val views = RemoteViews(context.packageName, R.layout.widget_freeze)
                views.setTextViewText(
                    R.id.widget_counts,
                    context.getString(R.string.widget_counts, frozen, blocked),
                )
                val openApp = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widget_root, openApp)
                val freezeAll = PendingIntent.getActivity(
                    context,
                    1,
                    Intent(context, FreezeShortcutActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widget_freeze_all, freezeAll)
                for (id in ids) {
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
