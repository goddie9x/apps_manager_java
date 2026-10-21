package com.god.ApplicationManager.UI

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.god.ApplicationManager.DB.SettingsDB
import com.god.ApplicationManager.Facade.AppManagerFacade
import com.god.ApplicationManager.R
import com.god.ApplicationManager.Service.FreezeService

/**
 * Permission Center: one screen that shows, for every capability the app can use,
 * whether it is granted and a direct route to the system screen that grants it.
 * This replaces the old pattern of dialogs appearing at first use of each feature.
 */
class PermissionCenterActivity : AppCompatActivity() {

    private data class Row(
        val titleRes: Int,
        val status: () -> Pair<Boolean, Int>,
        val action: (() -> Unit)?,
    )

    override fun getTheme(): Resources.Theme {
        val theme = super.getTheme()
        theme.applyStyle(
            if (SettingsDB.getInstance().isEnableDarkMode) {
                R.style.Theme_ApplicationManager_DarkMode
            } else {
                R.style.Theme_ApplicationManager
            },
            true,
        )
        return theme
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_center)
        val toolbar = findViewById<Toolbar>(R.id.permission_center_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        // Kick an async root re-check so the row is fresh when the user returns.
        AppManagerFacade.getRootPermission()
    }

    override fun onResume() {
        super.onResume()
        renderRows()
    }

    private fun rows(): List<Row> = listOf(
        Row(
            R.string.pc_accessibility,
            {
                val enabled = AppManagerFacade.isAccessibilityServiceEnabled(this, FreezeService::class.java)
                enabled to if (enabled) R.string.pc_status_granted else R.string.pc_status_missing
            },
            { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        ),
        Row(
            R.string.pc_notification_access,
            {
                val enabled = AppManagerFacade.isNotificationServiceEnabled(this)
                enabled to if (enabled) R.string.pc_status_granted else R.string.pc_status_missing
            },
            { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        ),
        Row(
            R.string.pc_usage_access,
            {
                val enabled = hasUsageAccess()
                enabled to if (enabled) R.string.pc_status_granted else R.string.pc_status_missing
            },
            { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        ),
        Row(
            R.string.pc_post_notifications,
            {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    true to R.string.pc_status_not_required
                } else {
                    val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                    granted to if (granted) R.string.pc_status_granted else R.string.pc_status_missing
                }
            },
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
                }
            },
        ),
        Row(
            R.string.pc_battery,
            {
                val power = getSystemService(Context.POWER_SERVICE) as PowerManager
                val exempt = power.isIgnoringBatteryOptimizations(packageName)
                exempt to if (exempt) R.string.pc_status_granted else R.string.pc_status_missing
            },
            {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                )
                startActivity(intent)
            },
        ),
        Row(
            R.string.pc_root,
            {
                val granted = AppManagerFacade.hasRootPermission
                granted to if (granted) R.string.pc_status_granted else R.string.pc_status_missing
            },
            null,
        ),
        Row(
            R.string.pc_shizuku,
            {
                val installed = isPackageInstalled(SHIZUKU_PACKAGE)
                installed to if (installed) R.string.pc_shizuku_installed else R.string.pc_shizuku_missing
            },
            null,
        ),
    )

    private fun renderRows() {
        val container = findViewById<LinearLayout>(R.id.permission_rows)
        container.removeAllViews()
        for (row in rows()) {
            val rowView = layoutInflater.inflate(R.layout.item_permission_row, container, false)
            rowView.findViewById<TextView>(R.id.permission_title).setText(row.titleRes)
            val (ok, statusRes) = row.status()
            val statusView = rowView.findViewById<TextView>(R.id.permission_status)
            statusView.setText(statusRes)
            statusView.setTextColor(
                getColor(if (ok) android.R.color.holo_green_dark else android.R.color.holo_red_dark),
            )
            val button = rowView.findViewById<Button>(R.id.permission_action)
            if (row.action == null) {
                button.visibility = View.GONE
            } else {
                button.setOnClickListener { row.action.invoke() }
            }
            container.addView(rowView)
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isPackageInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 1001
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
