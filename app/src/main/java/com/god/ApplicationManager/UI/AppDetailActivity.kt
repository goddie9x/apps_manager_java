package com.god.ApplicationManager.UI

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.god.ApplicationManager.Helper.IconCache
import com.god.ApplicationManager.Helper.UsageStatsHelper
import com.god.ApplicationManager.R
import com.god.ApplicationManager.data.ActivityLogger
import com.god.ApplicationManager.privilege.PrivilegeBackend
import com.god.ApplicationManager.privilege.PrivilegeManager
import com.god.ApplicationManager.privilege.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.DateFormat
import java.util.Date

/**
 * Full app detail (F1): identity, versions, dates, sizes, real enabled/stopped
 * state, usage stats (F4), permissions (F17), notification channels (F13) and
 * the privileged actions the current backend supports (F19/F21/F22/F23/F24/F26).
 */
class AppDetailActivity : AppCompatActivity() {

    private lateinit var packageName: String
    private lateinit var container: LinearLayout

    private val exportApkLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive"),
    ) { uri ->
        if (uri != null) exportApkTo(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: run {
            finish()
            return
        }
        val scroll = ScrollView(this)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
        }
        scroll.addView(container)
        setContentView(scroll)
        load()
    }

    private fun addText(text: String, bold: Boolean = false, size: Float = 15f) {
        val view = TextView(this).apply {
            this.text = text
            textSize = size
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 6, 0, 6)
        }
        container.addView(view)
    }

    private fun addButton(labelRes: Int, action: () -> Unit) {
        val button = Button(this).apply {
            setText(labelRes)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setOnClickListener { action() }
        }
        container.addView(button)
    }

    private fun load() {
        container.removeAllViews()
        lifecycleScope.launch {
            val pm = packageManager
            val pkgInfo = withContext(Dispatchers.IO) {
                try {
                    pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
            if (pkgInfo == null) {
                Toast.makeText(this@AppDetailActivity, R.string.target_not_install_or_not_have_ui, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            val appInfo = pkgInfo.applicationInfo
            title = appInfo?.loadLabel(pm) ?: packageName
            addText(title.toString(), bold = true, size = 20f)
            addText(packageName)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
            }
            addText(getString(R.string.detail_version, pkgInfo.versionName ?: "?", versionCode))
            val dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            addText(getString(R.string.detail_installed, dates.format(Date(pkgInfo.firstInstallTime))))
            addText(getString(R.string.detail_updated, dates.format(Date(pkgInfo.lastUpdateTime))))
            if (appInfo != null) {
                addText(getString(R.string.detail_apk_size, formatBytes(File(appInfo.sourceDir).length())))
                addText(getString(R.string.detail_target_sdk, appInfo.targetSdkVersion, appInfo.minSdkVersion))
                val enabledSetting = pm.getApplicationEnabledSetting(packageName)
                val enabledText = when (enabledSetting) {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> getString(R.string.detail_state_disabled)
                    else -> getString(R.string.detail_state_enabled)
                }
                val stopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
                addText(getString(R.string.detail_state, enabledText, if (stopped) getString(R.string.stopped) else getString(R.string.running)))
            }

            // Storage + usage (real platform data when Usage Access is granted)
            val storage = withContext(Dispatchers.IO) { UsageStatsHelper.storageFor(this@AppDetailActivity, packageName) }
            if (storage != null) {
                addText(
                    getString(
                        R.string.detail_storage,
                        formatBytes(storage.appBytes), formatBytes(storage.dataBytes), formatBytes(storage.cacheBytes),
                    ),
                    bold = true,
                )
            }
            val usage = withContext(Dispatchers.IO) { UsageStatsHelper.usageByPackage(this@AppDetailActivity, 7)[packageName] }
            if (usage != null && usage.lastTimeUsed > 0) {
                addText(getString(R.string.detail_last_used, dates.format(Date(usage.lastTimeUsed))))
                addText(getString(R.string.detail_foreground_7d, usage.totalForegroundMillis / 60000, usage.launchCount))
            }

            addText(getString(R.string.detail_actions), bold = true, size = 17f)
            addButton(R.string.open_app) { AppManagerFacadeOpenApp() }
            addButton(R.string.open_app_in_settings) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            addButton(R.string.detail_play_store) {
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                try {
                    startActivity(market)
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                }
            }
            addButton(R.string.detail_share) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "$packageName\nhttps://play.google.com/store/apps/details?id=$packageName")
                }
                startActivity(Intent.createChooser(send, getString(R.string.detail_share)))
            }
            addButton(R.string.detail_export_apk) {
                val label = (appInfo?.loadLabel(pm) ?: packageName).toString().replace(Regex("[^A-Za-z0-9._-]"), "_")
                exportApkLauncher.launch("${label}_${pkgInfo.versionName ?: "1"}.apk")
            }
            addButton(R.string.detail_notification_channels) { openNotificationSettings() }
            addButton(R.string.detail_components) {
                startActivity(Intent(this@AppDetailActivity, ComponentManagerActivity::class.java).putExtra(EXTRA_PACKAGE, packageName))
            }

            // Permissions viewer (F17)
            val requested = pkgInfo.requestedPermissions
            if (!requested.isNullOrEmpty()) {
                addText(getString(R.string.detail_permissions), bold = true, size = 17f)
                val flags = pkgInfo.requestedPermissionsFlags ?: IntArray(0)
                requested.forEachIndexed { index, perm ->
                    val granted = index < flags.size &&
                        (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    addText("• ${perm.substringAfterLast('.')} — ${getString(if (granted) R.string.pc_status_granted else R.string.pc_status_missing)}", size = 13f)
                }
            }

            addPrivilegedSection(appInfo)
        }
    }

    private fun addPrivilegedSection(appInfo: ApplicationInfo?) {
        val executor = PrivilegeManager.current(this)
        addText(getString(R.string.detail_privileged, executor.backend.name), bold = true, size = 17f)
        if (executor.backend == PrivilegeBackend.NON_ROOT) {
            addText(getString(R.string.detail_privileged_none))
            return
        }
        addButton(R.string.freeze_app) { runPrivileged("freeze") { it.freeze(this, packageName) } }
        addButton(R.string.detail_unfreeze) { runPrivileged("unfreeze") { it.unfreeze(this, packageName) } }
        addButton(R.string.force_stop) { runPrivileged("force_stop") { it.forceStop(this, packageName) } }
        if (executor.backend == PrivilegeBackend.ROOT || executor.backend == PrivilegeBackend.SHIZUKU) {
            addButton(R.string.detail_clear_cache) { clearCache() }
            addButton(R.string.detail_standby_bucket) { showStandbyDialog() }
            addButton(R.string.detail_appops_background) { showAppOpsDialog() }
            addButton(R.string.detail_tech_info) { showTechInfo() }
            val isSystem = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            addButton(if (isSystem) R.string.detail_debloat else R.string.detail_uninstall_user0) { confirmUninstallUser0() }
            addButton(R.string.detail_restore_user0) {
                runShell("restore") { PrivilegeManager.runShellCommand(this, "cmd package install-existing $packageName") }
            }
        }
    }

    private fun runPrivileged(action: String, op: (com.god.ApplicationManager.privilege.PrivilegedExecutor) -> Boolean) {
        val executor = PrivilegeManager.current(this)
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { op(executor) }
            ActivityLogger.log(this@AppDetailActivity, packageName, action, executor.backend.name, ok)
            Toast.makeText(
                this@AppDetailActivity,
                getString(if (ok) R.string.done else R.string.failed),
                Toast.LENGTH_SHORT,
            ).show()
            if (ok) load()
        }
    }

    private fun runShell(action: String, op: () -> Boolean) {
        val backend = PrivilegeManager.current(this).backend
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { op() }
            ActivityLogger.log(this@AppDetailActivity, packageName, action, backend.name, ok)
            Toast.makeText(this@AppDetailActivity, getString(if (ok) R.string.done else R.string.failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearCache() {
        val backend = PrivilegeManager.current(this).backend
        runShell("clear_cache") {
            when (backend) {
                PrivilegeBackend.ROOT -> RootShell.run(
                    "rm -rf /data/data/$packageName/cache /data/data/$packageName/code_cache " +
                        "/storage/emulated/0/Android/data/$packageName/cache",
                )
                PrivilegeBackend.SHIZUKU ->
                    // Shizuku cannot enter other apps' data dirs; this frees caches globally.
                    PrivilegeManager.runShellCommand(this, "pm trim-caches 999999999999")
                else -> false
            }
        }
    }

    private fun showStandbyDialog() {
        val buckets = arrayOf("active", "working_set", "frequent", "rare", "restricted")
        AlertDialog.Builder(this)
            .setTitle(R.string.detail_standby_bucket)
            .setItems(buckets) { _, which ->
                runShell("standby_bucket") {
                    PrivilegeManager.runShellCommand(this, "am set-standby-bucket $packageName ${buckets[which]}")
                }
            }
            .show()
    }

    private fun showAppOpsDialog() {
        val options = arrayOf("ignore", "allow")
        AlertDialog.Builder(this)
            .setTitle(R.string.detail_appops_background)
            .setItems(options) { _, which ->
                runShell("appops_background") {
                    PrivilegeManager.runShellCommand(this, "cmd appops set $packageName RUN_ANY_IN_BACKGROUND ${options[which]}")
                }
            }
            .show()
    }

    private fun showTechInfo() {
        lifecycleScope.launch {
            val lines = withContext(Dispatchers.IO) {
                PrivilegeManager.runShellCommandWithOutput(this@AppDetailActivity, "dumpsys meminfo $packageName")
            }
            val total = lines?.firstOrNull { it.contains("TOTAL") }?.trim() ?: getString(R.string.failed)
            val wakelocks = withContext(Dispatchers.IO) {
                PrivilegeManager.runShellCommandWithOutput(this@AppDetailActivity, "dumpsys power")
                    ?.count { it.contains(packageName) } ?: 0
            }
            AlertDialog.Builder(this@AppDetailActivity)
                .setTitle(R.string.detail_tech_info)
                .setMessage("PSS: $total\nWakelock mentions in dumpsys power: $wakelocks")
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun confirmUninstallUser0() {
        if (packageName in CRITICAL_PACKAGES || packageName == applicationContext.packageName) {
            Toast.makeText(this, R.string.detail_critical_refused, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.detail_debloat)
            .setMessage(getString(R.string.detail_debloat_confirm, packageName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                runPrivileged("uninstall_user0") { it.uninstallForUser(this, packageName) }
            }
            .show()
    }

    private fun openNotificationSettings() {
        // Per-channel control lives in the system channel screens; deep-link there (F13).
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun exportApkTo(uri: Uri) {
        val source = try {
            packageManager.getApplicationInfo(packageName, 0).sourceDir
        } catch (e: Exception) {
            null
        } ?: return
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(File(source)).use { input -> input.copyTo(out) }
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            ActivityLogger.log(this@AppDetailActivity, packageName, "export_apk", "SAF", ok)
            Toast.makeText(this@AppDetailActivity, getString(if (ok) R.string.done else R.string.failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun AppManagerFacadeOpenApp() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            startActivity(launch)
        } else {
            Toast.makeText(this, R.string.target_not_install_or_not_have_ui, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "?"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        private val CRITICAL_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.vending",
        )
    }
}
