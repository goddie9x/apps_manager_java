package com.god.ApplicationManager.UI

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.DB.FeedInfo
import com.god.ApplicationManager.DB.ManualThreat
import com.god.ApplicationManager.R
import com.god.ApplicationManager.security.ThreatIntelStore
import com.god.ApplicationManager.security.ThreatSeverity
import com.god.ApplicationManager.security.ThreatTransfer
import com.god.ApplicationManager.work.ThreatIntelRefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

/** Tab 3: manage the three intel sources (bundled / manual / online feeds). */
class SecuritySourcesFragment : Fragment() {

    private lateinit var feedAdapter: FeedAdapter
    private var feeds: List<FeedInfo> = emptyList()

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = try {
                val json = ThreatTransfer.exportJson(requireContext())
                requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(StandardCharsets.UTF_8))
                }
                true
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    if (ok) R.string.export_done else R.string.failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            var message: String? = null
            try {
                val json = requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader().readText()
                } ?: throw IllegalArgumentException("empty file")
                val report = ThreatTransfer.importJson(requireContext(), json)
                message = getString(
                    R.string.security_import_done,
                    report.applied, report.rejected.size, report.notInstalled.size,
                )
                if (report.rejected.isNotEmpty()) {
                    message += "\n" + report.rejected.take(5).joinToString("\n")
                }
            } catch (e: Exception) {
                message = getString(R.string.security_import_failed, e.message ?: "?")
            }
            val finalMessage = message
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), finalMessage, Toast.LENGTH_LONG).show()
                refresh()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_security_sources, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val bundledInfo: TextView = view.findViewById(R.id.bundled_info)
        val manualInfo: TextView = view.findViewById(R.id.manual_info)
        val manualAdd: Button = view.findViewById(R.id.manual_add)
        val manualImport: Button = view.findViewById(R.id.manual_import)
        val manualExport: Button = view.findViewById(R.id.manual_export)
        val feedList: RecyclerView = view.findViewById(R.id.feed_list)
        val feedAdd: Button = view.findViewById(R.id.feed_add)
        val feedRefresh: Button = view.findViewById(R.id.feed_refresh)

        feedAdapter = FeedAdapter(
            onToggle = { feed, enabled ->
                lifecycleScope.launch(Dispatchers.IO) {
                    feed.enabled = enabled
                    AppDatabase.getInstance(requireContext()).securityDao().updateFeed(feed)
                }
            },
            onDelete = { feed ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.delete)
                    .setMessage(getString(R.string.security_feed_delete_confirm, feed.name))
                    .setPositiveButton(R.string.delete) { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            AppDatabase.getInstance(requireContext()).securityDao().deleteFeed(feed.id)
                            try {
                                ThreatIntelStore.cacheFile(requireContext(), feed.id).delete()
                            } catch (ignored: Exception) {
                            }
                            withContext(Dispatchers.Main) { refresh() }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            },
        )
        feedList.layoutManager = LinearLayoutManager(requireContext())
        feedList.adapter = feedAdapter

        manualAdd.setOnClickListener { showAddManualDialog() }
        manualImport.setOnClickListener { importLauncher.launch(arrayOf("application/json")) }
        manualExport.setOnClickListener { exportLauncher.launch("apps-manager-threat-list.json") }
        feedAdd.setOnClickListener { showAddFeedDialog() }
        feedRefresh.setOnClickListener {
            Toast.makeText(requireContext(), R.string.security_refreshing, Toast.LENGTH_SHORT).show()
            lifecycleScope.launch(Dispatchers.IO) {
                ThreatIntelRefreshWorker.refreshNow(requireContext())
                withContext(Dispatchers.Main) { refresh() }
            }
        }

        refresh()
    }

    fun refresh() {
        val view = view ?: return
        val bundledInfo: TextView = view.findViewById(R.id.bundled_info)
        val manualInfo: TextView = view.findViewById(R.id.manual_info)
        lifecycleScope.launch(Dispatchers.IO) {
            val snapshot = ThreatIntelStore.loadSnapshot(requireContext())
            val db = AppDatabase.getInstance(requireContext())
            val manualCount = db.securityDao().allManual().size
            feeds = db.securityDao().allFeeds()
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                val bundledPkgs = snapshot.sourceCounts[ThreatIntelStore.BUNDLED_SOURCE] ?: 0
                bundledInfo.text = getString(R.string.security_bundled_info, bundledPkgs)
                manualInfo.text = getString(R.string.security_manual_info, manualCount)
                feedAdapter.feeds = feeds
            }
        }
    }

    private fun showAddManualDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val pkgInput = EditText(context).apply { hint = getString(R.string.security_package_hint) }
        val labelInput = EditText(context).apply { hint = getString(R.string.security_label_hint) }
        layout.addView(pkgInput)
        layout.addView(labelInput)
        val severities = arrayOf(
            getString(R.string.security_severity_malware),
            getString(R.string.security_severity_suspect),
        )
        var severity = ThreatSeverity.MALWARE
        AlertDialog.Builder(context)
            .setTitle(R.string.security_add_manual)
            .setView(layout)
            .setSingleChoiceItems(severities, 0) { _, which ->
                severity = if (which == 0) ThreatSeverity.MALWARE else ThreatSeverity.SUSPECT
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val pkg = pkgInput.text.toString().trim()
                val error = ThreatTransfer.validateEntry(pkg, severity.name.lowercase())
                if (error != null) {
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    val entity = ManualThreat().apply {
                        packageName = pkg
                        this.severity = severity.name.lowercase()
                        label = labelInput.text.toString().ifBlank { null }
                        addedAt = System.currentTimeMillis()
                    }
                    AppDatabase.getInstance(context).securityDao().upsertManual(entity)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.saved, Toast.LENGTH_SHORT).show()
                        refresh()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddFeedDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameInput = EditText(context).apply { hint = getString(R.string.security_feed_name_hint) }
        val urlInput = EditText(context).apply { hint = getString(R.string.security_feed_url_hint) }
        layout.addView(nameInput)
        layout.addView(urlInput)
        AlertDialog.Builder(context)
            .setTitle(R.string.security_add_feed)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = urlInput.text.toString().trim()
                if (!url.startsWith("https://")) {
                    Toast.makeText(context, R.string.security_feed_https_only, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    val feed = FeedInfo().apply {
                        this.url = url
                        name = nameInput.text.toString().trim().ifBlank { url }
                        format = if (url.endsWith(".yaml") || url.endsWith(".yml")) "yaml" else "json"
                        enabled = true
                    }
                    AppDatabase.getInstance(context).securityDao().upsertFeed(feed)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.saved, Toast.LENGTH_SHORT).show()
                        refresh()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
