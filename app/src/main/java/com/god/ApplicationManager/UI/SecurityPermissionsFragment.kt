package com.god.ApplicationManager.UI

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.god.ApplicationManager.R
import com.god.ApplicationManager.security.SecurityScanner
import kotlinx.coroutines.launch

/** Tab 2: which apps request each sensitive permission. */
class SecurityPermissionsFragment : Fragment() {

    private lateinit var adapter: PermissionGroupAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_security_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list: RecyclerView = view.findViewById(R.id.list)
        val progress: ProgressBar = view.findViewById(R.id.progress)
        val empty: TextView = view.findViewById(R.id.empty_text)

        adapter = PermissionGroupAdapter()
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        progress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val index = SecurityScanner.permissionIndex(requireContext(), includeSystemApps = true)
                val rows = ArrayList<PermRow>()
                for ((perm, apps) in index.toSortedMap()) {
                    if (apps.isEmpty()) continue
                    rows.add(PermRow.Header(permissionLabel(perm), apps.size))
                    for (app in apps) {
                        rows.add(
                            PermRow.App(app) {
                                startActivity(
                                    Intent(requireContext(), AppDetailActivity::class.java)
                                        .putExtra(AppDetailActivity.EXTRA_PACKAGE, app.packageName),
                                )
                            },
                        )
                    }
                }
                adapter.rows = rows
                empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                if (rows.isEmpty()) empty.setText(R.string.security_no_sensitive_perms)
            } finally {
                if (isAdded) progress.visibility = View.GONE
            }
        }
    }

    private fun permissionLabel(perm: String): String {
        val key = "perm_" + perm.substringAfterLast('.').lowercase()
        val resId = resources.getIdentifier(key, "string", requireContext().packageName)
        return if (resId != 0) getString(resId) else perm
    }
}
