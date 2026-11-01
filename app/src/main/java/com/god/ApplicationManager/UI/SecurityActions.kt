package com.god.ApplicationManager.UI

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.DB.UserFlag
import com.god.ApplicationManager.R
import com.god.ApplicationManager.data.ActivityLogger
import com.god.ApplicationManager.data.FreezeQueueRepository
import com.god.ApplicationManager.privilege.PrivilegeManager
import com.god.ApplicationManager.security.RiskAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick actions for one scanned package: details, uninstall, freeze,
 * flag junk/dangerous, whitelist. All privileged work runs off the main
 * thread and every action is written to the activity log.
 */
object SecurityActions {

    /** Packages the scanner must never offer to uninstall. */
    private val PROTECTED = setOf(
        "android",
        "com.android.systemui",
        "com.android.phone",
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.vending",
    )

    fun show(
        activity: Activity,
        anchor: android.view.View,
        report: RiskAnalyzer.RiskReport,
        onChanged: () -> Unit,
    ) {
        val popup = PopupMenu(activity, anchor)
        popup.menu.add(0, 1, 0, activity.getString(R.string.security_action_details))
        if (report.packageName !in PROTECTED) {
            popup.menu.add(0, 2, 0, activity.getString(R.string.security_action_uninstall))
            popup.menu.add(0, 3, 0, activity.getString(R.string.security_action_freeze))
        }
        popup.menu.add(0, 4, 0, activity.getString(R.string.security_action_flag_junk))
        popup.menu.add(0, 5, 0, activity.getString(R.string.security_action_flag_dangerous))
        popup.menu.add(0, 6, 0, activity.getString(R.string.security_action_ignore))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> openDetails(activity, report.packageName)
                2 -> confirmUninstall(activity, report, onChanged)
                3 -> freeze(activity, report, onChanged)
                4 -> flagDialog(activity, report.packageName, UserFlag.KIND_JUNK, onChanged)
                5 -> flagDialog(activity, report.packageName, UserFlag.KIND_DANGEROUS, onChanged)
                6 -> setFlag(activity, report.packageName, UserFlag.KIND_IGNORED, null, onChanged)
            }
            true
        }
        popup.show()
    }

    private fun openDetails(activity: Activity, pkg: String) {
        activity.startActivity(
            Intent(activity, AppDetailActivity::class.java)
                .putExtra(AppDetailActivity.EXTRA_PACKAGE, pkg),
        )
    }

    private fun confirmUninstall(
        activity: Activity,
        report: RiskAnalyzer.RiskReport,
        onChanged: () -> Unit,
    ) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.security_action_uninstall)
            .setMessage(activity.getString(R.string.security_uninstall_confirm, report.appName, report.packageName))
            .setPositiveButton(R.string.security_action_uninstall) { _, _ ->
                uninstall(activity, report, onChanged)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun uninstall(
        activity: Activity,
        report: RiskAnalyzer.RiskReport,
        onChanged: () -> Unit,
    ) {
        Toast.makeText(activity, R.string.working, Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            val executor = PrivilegeManager.current(activity)
            val ok = executor.uninstallForUser(activity, report.packageName)
            ActivityLogger.log(activity, report.packageName, "uninstall", executor.backend.name, ok)
            withContext(Dispatchers.Main) {
                if (ok) {
                    Toast.makeText(activity, R.string.security_uninstalled, Toast.LENGTH_SHORT).show()
                    onChanged()
                } else {
                    // No privileged backend: fall back to the system uninstall screen.
                    try {
                        activity.startActivity(
                            Intent(Intent.ACTION_DELETE, Uri.parse("package:${report.packageName}")),
                        )
                    } catch (e: Exception) {
                        Toast.makeText(activity, R.string.security_action_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun freeze(
        activity: Activity,
        report: RiskAnalyzer.RiskReport,
        onChanged: () -> Unit,
    ) {
        FreezeQueueRepository.enqueue(activity, listOf(report.packageName))
        CoroutineScope(Dispatchers.IO).launch {
            ActivityLogger.log(activity, report.packageName, "freeze", "queue", true)
        }
        Toast.makeText(activity, R.string.security_freeze_queued, Toast.LENGTH_SHORT).show()
        onChanged()
    }

    private fun flagDialog(activity: Activity, pkg: String, kind: String, onChanged: () -> Unit) {
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.security_flag_note_hint)
        }
        AlertDialog.Builder(activity)
            .setTitle(
                if (kind == UserFlag.KIND_JUNK) R.string.security_action_flag_junk
                else R.string.security_action_flag_dangerous,
            )
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                setFlag(activity, pkg, kind, input.text.toString().ifBlank { null }, onChanged)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setFlag(activity: Activity, pkg: String, kind: String, note: String?, onChanged: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val flag = UserFlag().apply {
                packageName = pkg
                this.kind = kind
                this.note = note
                flaggedAt = System.currentTimeMillis()
            }
            AppDatabase.getInstance(activity).securityDao().upsertFlag(flag)
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, R.string.security_flag_saved, Toast.LENGTH_SHORT).show()
                onChanged()
            }
        }
    }
}
