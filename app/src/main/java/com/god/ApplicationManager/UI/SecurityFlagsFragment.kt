package com.god.ApplicationManager.UI

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tab 4: packages the user flagged by hand (junk / dangerous / ignored). */
class SecurityFlagsFragment : Fragment() {

    private lateinit var adapter: FlagAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_security_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list: RecyclerView = view.findViewById(R.id.list)
        val progress: ProgressBar = view.findViewById(R.id.progress)
        val empty: TextView = view.findViewById(R.id.empty_text)

        adapter = FlagAdapter { flag ->
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.security_flag_delete_confirm, flag.packageName))
                .setPositiveButton(R.string.delete) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        AppDatabase.getInstance(requireContext()).securityDao().deleteFlag(flag.packageName)
                        withContext(Dispatchers.Main) { refresh() }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        empty.setText(R.string.security_no_flags)

        refresh()
    }

    fun refresh() {
        val view = view ?: return
        val empty: TextView = view.findViewById(R.id.empty_text)
        val progress: ProgressBar = view.findViewById(R.id.progress)
        progress.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val flags = AppDatabase.getInstance(requireContext()).securityDao().allFlags()
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                progress.visibility = View.GONE
                adapter.flags = flags
                empty.visibility = if (flags.isEmpty()) View.VISIBLE else View.GONE
                if (flags.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.security_no_flags, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }
}
