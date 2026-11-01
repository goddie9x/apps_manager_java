package com.god.ApplicationManager.UI

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.god.ApplicationManager.DB.FeedInfo
import com.god.ApplicationManager.DB.UserFlag
import com.god.ApplicationManager.R
import com.god.ApplicationManager.security.RiskAnalyzer
import java.text.DateFormat
import java.util.Date

/** Short human label for a finding kind (detail carries the technical specifics). */
fun Context.findingLabel(kind: RiskAnalyzer.FindingKind): String = getString(
    when (kind) {
        RiskAnalyzer.FindingKind.INTEL_MALWARE_PKG -> R.string.finding_intel_malware_pkg
        RiskAnalyzer.FindingKind.INTEL_SUSPECT_PKG -> R.string.finding_intel_suspect_pkg
        RiskAnalyzer.FindingKind.INTEL_MALWARE_CERT -> R.string.finding_intel_malware_cert
        RiskAnalyzer.FindingKind.INTEL_SUSPECT_CERT -> R.string.finding_intel_suspect_cert
        RiskAnalyzer.FindingKind.USER_DANGEROUS -> R.string.finding_user_dangerous
        RiskAnalyzer.FindingKind.USER_JUNK -> R.string.finding_user_junk
        RiskAnalyzer.FindingKind.SIGNATURE_SPOOF -> R.string.finding_signature_spoof
        RiskAnalyzer.FindingKind.SIDELOAD -> R.string.finding_sideload
        RiskAnalyzer.FindingKind.UNKNOWN_INSTALLER -> R.string.finding_unknown_installer
        RiskAnalyzer.FindingKind.NO_LAUNCHER -> R.string.finding_no_launcher
        RiskAnalyzer.FindingKind.DANGEROUS_PERMISSIONS -> R.string.finding_dangerous_permissions
        RiskAnalyzer.FindingKind.SENSITIVE_PERMISSIONS -> R.string.finding_sensitive_permissions
        RiskAnalyzer.FindingKind.SMS_EXFILTRATION_PATTERN -> R.string.finding_sms_exfiltration_pattern
        RiskAnalyzer.FindingKind.QUERY_ALL_PACKAGES_PATTERN -> R.string.finding_query_all_packages_pattern
        RiskAnalyzer.FindingKind.ACCESSIBILITY_SERVICE_ACTIVE -> R.string.finding_accessibility_service_active
        RiskAnalyzer.FindingKind.NOTIFICATION_LISTENER_ACTIVE -> R.string.finding_notification_listener_active
        RiskAnalyzer.FindingKind.DEVICE_ADMIN_ACTIVE -> R.string.finding_device_admin_active
        RiskAnalyzer.FindingKind.CAN_DRAW_OVERLAYS -> R.string.finding_can_draw_overlays
        RiskAnalyzer.FindingKind.CAN_INSTALL_PACKAGES -> R.string.finding_can_install_packages
        RiskAnalyzer.FindingKind.USAGE_STATS_ACCESS -> R.string.finding_usage_stats_access
        RiskAnalyzer.FindingKind.REQUESTS_DEVICE_ADMIN -> R.string.finding_requests_device_admin
        RiskAnalyzer.FindingKind.DEBUGGABLE -> R.string.finding_debuggable
        RiskAnalyzer.FindingKind.TEST_ONLY -> R.string.finding_test_only
        RiskAnalyzer.FindingKind.OLD_TARGET_SDK -> R.string.finding_old_target_sdk
        RiskAnalyzer.FindingKind.NEVER_USED -> R.string.finding_never_used
    },
)

fun bandColor(band: RiskAnalyzer.Band): Int = when (band) {
    RiskAnalyzer.Band.DANGEROUS -> Color.parseColor("#D32F2F")
    RiskAnalyzer.Band.SUSPICIOUS -> Color.parseColor("#F57C00")
    RiskAnalyzer.Band.JUNK -> Color.parseColor("#FBC02D")
    RiskAnalyzer.Band.CLEAN -> Color.parseColor("#388E3C")
}

fun Context.bandLabel(band: RiskAnalyzer.Band): String = getString(
    when (band) {
        RiskAnalyzer.Band.DANGEROUS -> R.string.security_band_dangerous
        RiskAnalyzer.Band.SUSPICIOUS -> R.string.security_band_suspicious
        RiskAnalyzer.Band.JUNK -> R.string.security_band_junk
        RiskAnalyzer.Band.CLEAN -> R.string.security_band_clean
    },
)

/** Builds sectioned rows (header + reports) for the scan tab. */
fun buildScanRows(
    context: Context,
    reports: List<RiskAnalyzer.RiskReport>,
): List<ScanRow> {
    val rows = ArrayList<ScanRow>()
    val order = listOf(
        RiskAnalyzer.Band.DANGEROUS,
        RiskAnalyzer.Band.SUSPICIOUS,
        RiskAnalyzer.Band.JUNK,
    )
    for (band in order) {
        val group = reports.filter { it.band == band }
        if (group.isEmpty()) continue
        rows.add(ScanRow.Header(context.bandLabel(band), group.size, bandColor(band)))
        group.forEach { rows.add(ScanRow.Report(it)) }
    }
    return rows
}

sealed class ScanRow {
    data class Header(val title: String, val count: Int, val color: Int) : ScanRow()
    data class Report(val report: RiskAnalyzer.RiskReport) : ScanRow()
}

/** Scan results with band headers; the overflow button opens quick actions. */
class ScanResultAdapter(
    private val onAction: (View, RiskAnalyzer.RiskReport) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var rows: List<ScanRow> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is ScanRow.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderHolder(inflater.inflate(R.layout.item_security_header, parent, false))
        } else {
            ReportHolder(inflater.inflate(R.layout.item_security_result, parent, false))
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is ScanRow.Header -> (holder as HeaderHolder).bind(row)
            is ScanRow.Report -> (holder as ReportHolder).bind(row.report, onAction)
        }
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view.findViewById(R.id.header_text)
        fun bind(header: ScanRow.Header) {
            text.text = "${header.title} (${header.count})"
            text.setTextColor(header.color)
        }
    }

    class ReportHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.app_icon)
        private val name: TextView = view.findViewById(R.id.app_name)
        private val pkg: TextView = view.findViewById(R.id.package_name)
        private val findingsView: TextView = view.findViewById(R.id.findings)
        private val scoreView: TextView = view.findViewById(R.id.score)
        private val actions: ImageButton = view.findViewById(R.id.actions)

        fun bind(report: RiskAnalyzer.RiskReport, onAction: (View, RiskAnalyzer.RiskReport) -> Unit) {
            val context = itemView.context
            name.text = report.appName
            pkg.text = report.packageName
            try {
                icon.setImageDrawable(context.packageManager.getApplicationIcon(report.packageName))
            } catch (e: Exception) {
                icon.setImageDrawable(null)
            }
            val top = report.findings.take(3)
            findingsView.text = top.joinToString("\n") { f ->
                "• ${context.findingLabel(f.kind)}${if (f.detail.isNotBlank()) ": ${f.detail.take(60)}" else ""}"
            }
            scoreView.text = report.score.toString()
            scoreView.setTextColor(bandColor(report.band))
            actions.setOnClickListener { onAction(actions, report) }
            itemView.setOnClickListener { onAction(actions, report) }
        }
    }
}

sealed class PermRow {
    data class Header(val label: String, val count: Int) : PermRow()
    data class App(val report: RiskAnalyzer.RiskReport, val onOpen: () -> Unit) : PermRow()
}

/** Permission -> apps index with section headers. */
class PermissionGroupAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var rows: List<PermRow> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is PermRow.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            ScanResultAdapter.HeaderHolder(inflater.inflate(R.layout.item_security_header, parent, false))
        } else {
            PermAppHolder(inflater.inflate(R.layout.item_security_result, parent, false))
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is PermRow.Header -> (holder as ScanResultAdapter.HeaderHolder)
                .bind(ScanRow.Header(row.label, row.count, Color.parseColor("#F57C00")))
            is PermRow.App -> (holder as PermAppHolder).bind(row)
        }
    }

    class PermAppHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.app_icon)
        private val name: TextView = view.findViewById(R.id.app_name)
        private val pkg: TextView = view.findViewById(R.id.package_name)
        private val findingsView: TextView = view.findViewById(R.id.findings)
        private val scoreView: TextView = view.findViewById(R.id.score)
        private val actions: ImageButton = view.findViewById(R.id.actions)

        fun bind(row: PermRow.App) {
            val context = itemView.context
            val report = row.report
            name.text = report.appName
            pkg.text = report.packageName
            try {
                icon.setImageDrawable(context.packageManager.getApplicationIcon(report.packageName))
            } catch (e: Exception) {
                icon.setImageDrawable(null)
            }
            findingsView.text = if (report.isSystemApp) context.getString(R.string.system_app) else ""
            scoreView.visibility = View.GONE
            actions.visibility = View.GONE
            itemView.setOnClickListener { row.onOpen() }
        }
    }
}

/** Remote feeds with enable toggle + delete. */
class FeedAdapter(
    private val onToggle: (FeedInfo, Boolean) -> Unit,
    private val onDelete: (FeedInfo) -> Unit,
) : RecyclerView.Adapter<FeedAdapter.Holder>() {

    var feeds: List<FeedInfo> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_feed, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = feeds.size

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(feeds[position], onToggle, onDelete)

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameView: TextView = view.findViewById(R.id.feed_name)
        private val urlView: TextView = view.findViewById(R.id.feed_url)
        private val statusView: TextView = view.findViewById(R.id.feed_status)
        private val enabledBox: CheckBox = view.findViewById(R.id.feed_enabled)
        private val deleteBtn: ImageButton = view.findViewById(R.id.feed_delete)

        fun bind(feed: FeedInfo, onToggle: (FeedInfo, Boolean) -> Unit, onDelete: (FeedInfo) -> Unit) {
            val context = itemView.context
            nameView.text = feed.name.ifBlank { feed.url }
            urlView.text = feed.url
            val lastFetch = if (feed.lastFetch > 0) {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(feed.lastFetch))
            } else {
                "—"
            }
            statusView.text = context.getString(
                R.string.security_feed_status,
                feed.lastStatus, feed.entryCount, lastFetch,
            )
            enabledBox.setOnCheckedChangeListener(null)
            enabledBox.isChecked = feed.enabled
            enabledBox.setOnCheckedChangeListener { _, checked -> onToggle(feed, checked) }
            deleteBtn.setOnClickListener { onDelete(feed) }
        }
    }
}

/** User flags with delete. */
class FlagAdapter(
    private val onDelete: (UserFlag) -> Unit,
) : RecyclerView.Adapter<FlagAdapter.Holder>() {

    var flags: List<UserFlag> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_flag, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = flags.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(flags[position], onDelete)

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val pkgView: TextView = view.findViewById(R.id.flag_package)
        private val kindView: TextView = view.findViewById(R.id.flag_kind)
        private val noteView: TextView = view.findViewById(R.id.flag_note)
        private val deleteBtn: ImageButton = view.findViewById(R.id.flag_delete)

        fun bind(flag: UserFlag, onDelete: (UserFlag) -> Unit) {
            val context = itemView.context
            pkgView.text = flag.packageName
            kindView.text = when (flag.kind) {
                UserFlag.KIND_DANGEROUS -> context.getString(R.string.security_flag_dangerous)
                UserFlag.KIND_IGNORED -> context.getString(R.string.security_flag_ignored)
                else -> context.getString(R.string.security_flag_junk)
            }
            kindView.setTextColor(
                when (flag.kind) {
                    UserFlag.KIND_DANGEROUS -> Color.parseColor("#D32F2F")
                    UserFlag.KIND_IGNORED -> Color.parseColor("#388E3C")
                    else -> Color.parseColor("#F57C00")
                },
            )
            noteView.text = flag.note.orEmpty()
            noteView.visibility = if (flag.note.isNullOrBlank()) View.GONE else View.VISIBLE
            deleteBtn.setOnClickListener { onDelete(flag) }
        }
    }
}
