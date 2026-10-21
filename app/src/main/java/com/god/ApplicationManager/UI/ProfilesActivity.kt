package com.god.ApplicationManager.UI

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.DB.FreezeProfileItem
import com.god.ApplicationManager.R
import com.god.ApplicationManager.data.FreezeQueueRepository
import com.god.ApplicationManager.work.FreezeWorkScheduler
import com.god.ApplicationManager.work.FreezeWorker
import com.god.ApplicationManager.work.UnfreezeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Freeze profiles (F11) + focus sessions (F14).
 * A profile is a named package set created from the current freeze list.
 * Applying a profile enqueues its packages into the durable freeze queue.
 * A focus session freezes the freeze-list now and schedules an automatic
 * unfreeze when the session ends; the pending restore is WorkManager-backed,
 * so it survives the process being killed.
 */
class ProfilesActivity : AppCompatActivity() {

    private val rows = mutableListOf<String>()
    private var profiles: List<FreezeProfileItem> = emptyList()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.profiles_title)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        val hint = TextView(this).apply { setText(R.string.profiles_hint) }
        root.addView(hint)
        root.addView(button(R.string.profiles_create) { createProfile() })
        root.addView(button(R.string.focus_start) { startFocus() })
        val listView = ListView(this)
        root.addView(listView)
        setContentView(root)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ -> profileActions(profiles[position]) }
        load()
    }

    private fun button(labelRes: Int, action: () -> Unit): Button = Button(this).apply {
        setText(labelRes)
        setOnClickListener { action() }
    }

    private fun load() {
        lifecycleScope.launch {
            profiles = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@ProfilesActivity).featureDao().allProfiles()
            }
            rows.clear()
            profiles.mapTo(rows) { profile ->
                val count = profile.packages.split(",").count { it.isNotBlank() }
                "${profile.name} ($count)"
            }
            if (rows.isEmpty()) rows.add(getString(R.string.nothing_here_yet))
            adapter.notifyDataSetChanged()
        }
    }

    private fun createProfile() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.profiles_create)
            .setMessage(R.string.profiles_create_from_freeze_list)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val db = AppDatabase.getInstance(this@ProfilesActivity)
                        val packages = db.appInfoDao().freezeList.map { it.packageName }
                        val profile = FreezeProfileItem()
                        profile.name = name
                        profile.packages = packages.joinToString(",")
                        db.featureDao().insertProfile(profile)
                    }
                    load()
                }
            }
            .show()
    }

    private fun profileActions(profile: FreezeProfileItem) {
        val packages = profile.packages.split(",").filter { it.isNotBlank() }
        AlertDialog.Builder(this)
            .setTitle(profile.name)
            .setItems(arrayOf(getString(R.string.profiles_apply), getString(R.string.delete))) { _, which ->
                lifecycleScope.launch {
                    if (which == 0) {
                        FreezeQueueRepository.enqueue(this@ProfilesActivity, packages)
                        FreezeWorkScheduler.enqueueFreezeNow(this@ProfilesActivity, FreezeWorker.TRIGGER_MANUAL)
                        Toast.makeText(this@ProfilesActivity, R.string.done, Toast.LENGTH_SHORT).show()
                    } else {
                        withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(this@ProfilesActivity).featureDao().deleteProfile(profile.id)
                        }
                        load()
                    }
                }
            }
            .show()
    }

    private fun startFocus() {
        val labels = arrayOf("15", "30", "60", "120")
        AlertDialog.Builder(this)
            .setTitle(R.string.focus_start)
            .setItems(labels.map { getString(R.string.focus_minutes, it) }.toTypedArray()) { _, which ->
                val minutes = labels[which].toLong()
                lifecycleScope.launch {
                    val packages = withContext(Dispatchers.IO) {
                        AppDatabase.getInstance(this@ProfilesActivity).appInfoDao().freezeList.map { it.packageName }
                    }
                    if (packages.isEmpty()) {
                        Toast.makeText(this@ProfilesActivity, R.string.focus_empty, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    FreezeQueueRepository.enqueue(this@ProfilesActivity, packages)
                    FreezeWorkScheduler.enqueueFreezeNow(this@ProfilesActivity, FreezeWorker.TRIGGER_MANUAL)
                    val restore = OneTimeWorkRequestBuilder<UnfreezeWorker>()
                        .setInputData(Data.Builder().putStringArray(UnfreezeWorker.KEY_PACKAGES, packages.toTypedArray()).build())
                        .setInitialDelay(minutes, TimeUnit.MINUTES)
                        .addTag("focus-restore")
                        .build()
                    WorkManager.getInstance(this@ProfilesActivity)
                        .enqueueUniqueWork("focus-session", ExistingWorkPolicy.REPLACE, restore)
                    Toast.makeText(this@ProfilesActivity, getString(R.string.focus_started, minutes), Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }
}
