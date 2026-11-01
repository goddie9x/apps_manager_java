package com.god.ApplicationManager.UI

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.god.ApplicationManager.R
import com.god.ApplicationManager.security.RiskAnalyzer
import com.god.ApplicationManager.security.SecurityScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Tab 1: run a scan, browse grouped results, quick actions per app. */
class SecurityScanFragment : Fragment() {

    private lateinit var adapter: ScanResultAdapter
    private var scanJob: Job? = null
    private var lastResult: SecurityScanner.ScanResult? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_security_scan, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val scanButton: Button = view.findViewById(R.id.scan_button)
        val includeSystem: CheckBox = view.findViewById(R.id.include_system)
        val progress: ProgressBar = view.findViewById(R.id.scan_progress)
        val status: TextView = view.findViewById(R.id.scan_status)
        val list: RecyclerView = view.findViewById(R.id.result_list)

        adapter = ScanResultAdapter { anchor, report ->
            activity?.let { SecurityActions.show(it, anchor, report) { refresh() } }
        }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        scanButton.setOnClickListener {
            scanJob?.cancel()
            progress.visibility = View.VISIBLE
            progress.isIndeterminate = false
            scanButton.isEnabled = false
            status.text = getString(R.string.security_scanning)
            val include = includeSystem.isChecked
            scanJob = viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val result = SecurityScanner.scan(requireContext(), include) { done, total ->
                        view.post {
                            progress.max = total
                            progress.progress = done
                            status.text = getString(R.string.security_scanning_progress, done, total)
                        }
                    }
                    lastResult = result
                    render(result)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Toast.makeText(requireContext(), R.string.security_scan_failed, Toast.LENGTH_LONG).show()
                } finally {
                    view.post {
                        progress.visibility = View.GONE
                        scanButton.isEnabled = true
                    }
                }
            }
        }
    }

    /** Re-runs the last scan (used after a quick action changed something). */
    fun refresh() {
        val result = lastResult ?: return
        view?.findViewById<Button>(R.id.scan_button)?.performClick()
    }

    private fun render(result: SecurityScanner.ScanResult) {
        val view = view ?: return
        val status: TextView = view.findViewById(R.id.scan_status)
        val counts = result.reports.groupingBy { it.band }.eachCount()
        val dangerous = counts[RiskAnalyzer.Band.DANGEROUS] ?: 0
        val suspicious = counts[RiskAnalyzer.Band.SUSPICIOUS] ?: 0
        val junk = counts[RiskAnalyzer.Band.JUNK] ?: 0
        status.text = getString(
            R.string.security_scan_done,
            result.total, dangerous, suspicious, junk,
        )
        adapter.rows = buildScanRows(requireContext(), result.reports)
        if (result.reports.none { it.band != RiskAnalyzer.Band.CLEAN }) {
            Toast.makeText(requireContext(), R.string.security_all_clean, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        scanJob?.cancel()
        super.onDestroyView()
    }
}
