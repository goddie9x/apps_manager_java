package com.god.ApplicationManager.UI

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.god.ApplicationManager.R
import com.god.ApplicationManager.privilege.PrivilegeManager
import com.god.ApplicationManager.privilege.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Component manager (F25): lists an app's activities/services/receivers/providers
 * and toggles them via root `pm enable/disable <component>`. Read-only without
 * root (component state itself is always readable). The app never toggles its
 * own components.
 */
class ComponentManagerActivity : AppCompatActivity() {

    private data class Row(val component: String, val kind: String, var enabledSetting: Int) {
        override fun toString(): String {
            val state = when (enabledSetting) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "ON"
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "OFF"
                else -> "default"
            }
            return "[$kind] ${component.substringAfterLast('.')}  ($state)\n$component"
        }
    }

    private lateinit var packageName: String
    private val rows = mutableListOf<Row>()
    private lateinit var adapter: ArrayAdapter<Row>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageName = intent.getStringExtra(AppDetailActivity.EXTRA_PACKAGE) ?: run {
            finish()
            return
        }
        val listView = ListView(this)
        setContentView(listView)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        listView.adapter = adapter
        title = packageName

        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { loadComponents() }
            rows.clear()
            rows.addAll(loaded)
            adapter.notifyDataSetChanged()
            if (!PrivilegeManager.rootAvailable) {
                Toast.makeText(this@ComponentManagerActivity, R.string.components_readonly, Toast.LENGTH_LONG).show()
            }
        }
        listView.setOnItemClickListener { _, _, position, _ -> toggle(rows[position]) }
    }

    @Suppress("DEPRECATION")
    private fun loadComponents(): List<Row> {
        val pm = packageManager
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
            PackageManager.GET_DISABLED_COMPONENTS
        val info = try {
            pm.getPackageInfo(packageName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            return emptyList()
        }
        val result = mutableListOf<Row>()
        fun addAll(components: Array<out android.content.pm.ComponentInfo>?, kind: String) {
            components?.forEach { component ->
                val name = ComponentName(packageName, component.name)
                result.add(Row(component.name, kind, pm.getComponentEnabledSetting(name)))
            }
        }
        addAll(info.activities, "activity")
        addAll(info.services, "service")
        addAll(info.receivers, "receiver")
        addAll(info.providers, "provider")
        return result
    }

    private fun toggle(row: Row) {
        if (packageName == applicationContext.packageName) {
            Toast.makeText(this, R.string.detail_critical_refused, Toast.LENGTH_LONG).show()
            return
        }
        if (!PrivilegeManager.rootAvailable) {
            Toast.makeText(this, R.string.components_readonly, Toast.LENGTH_SHORT).show()
            return
        }
        val enable = row.enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val component = "$packageName/${row.component}"
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                RootShell.run("pm ${if (enable) "enable" else "disable"} $component")
            }
            Toast.makeText(this@ComponentManagerActivity, getString(if (ok) R.string.done else R.string.failed), Toast.LENGTH_SHORT).show()
            if (ok) {
                row.enabledSetting = if (enable) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                adapter.notifyDataSetChanged()
            }
        }
    }
}
