package com.god.ApplicationManager.UI

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.Helper.UsageStatsHelper
import com.god.ApplicationManager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Background health report (F30): a weekly, shareable summary built from
 * UsageStats + the app's own rule/archive data. Every number names its
 * source; foreground time is a platform figure, battery impact is not
 * claimed (Android does not expose per-app battery use to normal apps).
 */
class HealthReportActivity : AppCompatActivity() {

    private var reportText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.health_report_title)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }
        val textView = TextView(this)
        val share = Button(this).apply {
            setText(R.string.detail_share)
            setOnClickListener {
                if (reportText.isNotBlank()) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, reportText)
                    }
                    startActivity(Intent.createChooser(send, getString(R.string.health_report_title)))
                }
            }
        }
        root.addView(share)
        val scroll = ScrollView(this)
        scroll.addView(textView)
        root.addView(scroll)
        setContentView(root)

        lifecycleScope.launch {
            reportText = withContext(Dispatchers.IO) { buildReport() }
            textView.text = reportText
        }
    }

    private fun buildReport(): String {
        val ctx = this
        val sb = StringBuilder()
        val dates = DateFormat.getDateInstance(DateFormat.MEDIUM)
        sb.appendLine(getString(R.string.health_report_title))
        sb.appendLine(dates.format(Date()))
        sb.appendLine()
        val db = AppDatabase.getInstance(ctx)
        val frozenCount = db.appInfoDao().freezeList.size
        val blockedCount = db.appInfoDao().turnOffNotifList.size
        val archivedCount = db.featureDao().recentBlocked(500).size
        sb.appendLine(getString(R.string.health_rules_line, frozenCount, blockedCount, archivedCount))
        sb.appendLine()
        if (!UsageStatsHelper.hasUsageAccess(ctx)) {
            sb.appendLine(getString(R.string.health_no_usage_access))
            return sb.toString()
        }
        val usage = UsageStatsHelper.usageByPackage(ctx, 7)
        val pm = packageManager
        fun label(pkg: String): String = try {
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        } catch (e: Exception) {
            pkg
        }
        val topForeground = usage.entries
            .filter { it.value.totalForegroundMillis > 0 }
            .sortedByDescending { it.value.totalForegroundMillis }
            .take(5)
        sb.appendLine(getString(R.string.health_top_foreground))
        for ((pkg, info) in topForeground) {
            sb.appendLine("• ${label(pkg)} — ${info.totalForegroundMillis / 60000} min, ${info.launchCount} launches")
        }
        sb.appendLine()
        val now = System.currentTimeMillis()
        val staleCutoff = now - TimeUnit.DAYS.toMillis(30)
        val stale = usage.entries
            .filter { it.value.lastTimeUsed in 1 until staleCutoff }
            .sortedBy { it.value.lastTimeUsed }
            .take(5)
        if (stale.isNotEmpty()) {
            sb.appendLine(getString(R.string.health_stale))
            for ((pkg, info) in stale) {
                sb.appendLine("• ${label(pkg)} — ${getString(R.string.detail_last_used, dates.format(Date(info.lastTimeUsed)))}")
            }
            sb.appendLine()
        }
        var cacheBytes = 0L
        var counted = 0
        for (pkg in usage.keys.take(40)) {
            val storage = UsageStatsHelper.storageFor(ctx, pkg) ?: continue
            cacheBytes += storage.cacheBytes
            counted++
        }
        sb.appendLine(getString(R.string.health_cache_estimate, cacheBytes / (1024 * 1024), counted))
        return sb.toString()
    }
}
