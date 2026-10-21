package com.god.ApplicationManager.UI

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * One reusable read-only list screen with a clear button, used for the
 * activity history (F8) and the blocked-notification archive (F10).
 */
class SimpleListActivity : AppCompatActivity() {

    private lateinit var mode: String
    private lateinit var adapter: ArrayAdapter<String>
    private val rows = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LOG
        setContentView(R.layout.activity_simple_list)
        title = getString(if (mode == MODE_BLOCKED) R.string.blocked_archive_title else R.string.activity_log_title)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        findViewById<ListView>(R.id.simple_list).adapter = adapter
        findViewById<Button>(R.id.simple_list_action).setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val dao = AppDatabase.getInstance(this@SimpleListActivity).featureDao()
                    if (mode == MODE_BLOCKED) dao.clearBlocked() else dao.clearLog()
                }
                load()
            }
        }
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            val loaded = withContext(Dispatchers.IO) {
                val dao = AppDatabase.getInstance(this@SimpleListActivity).featureDao()
                if (mode == MODE_BLOCKED) {
                    dao.recentBlocked(300).map { item ->
                        "${format.format(Date(item.at))} · ${item.packageName}\n${item.title ?: ""}"
                    }
                } else {
                    dao.recentLog(300).map { item ->
                        "${format.format(Date(item.at))} · ${item.action} · ${item.packageName} · ${item.backend} · ${if (item.success) "OK" else "FAIL"}"
                    }
                }
            }
            rows.clear()
            if (loaded.isEmpty()) {
                rows.add(getString(R.string.nothing_here_yet))
            } else {
                rows.addAll(loaded)
            }
            adapter.notifyDataSetChanged()
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_LOG = "log"
        const val MODE_BLOCKED = "blocked"
    }
}
