package com.god.ApplicationManager.security

import android.content.Context
import android.util.Log
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.DB.ManualThreat
import com.god.ApplicationManager.R
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Merges the three threat-intel sources into one lookup snapshot.
 *
 * Priority (highest wins): hand-entered list > remote feed caches > bundled
 * offline copy. A snapshot is built on a background thread per scan; there is
 * no long-lived cache to invalidate, so manual edits take effect on the next
 * scan automatically.
 */
object ThreatIntelStore {
    private const val TAG = "ThreatIntelStore"
    const val BUNDLED_SOURCE = "bundled"

    data class Snapshot(
        val packages: Map<String, IntelHit>,
        val certs: Map<String, IntelHit>,
        val sourceCounts: Map<String, Int>,
    ) {
        fun lookupPackage(packageName: String): IntelHit? = packages[packageName]
        fun lookupCert(sha1Upper: String): IntelHit? = certs[sha1Upper.uppercase()]
    }

    @JvmStatic
    fun loadSnapshot(context: Context): Snapshot {
        val packages = HashMap<String, IntelHit>()
        val certs = HashMap<String, IntelHit>()
        val counts = HashMap<String, Int>()
        fun count(source: String) {
            counts[source] = (counts[source] ?: 0) + 1
        }

        // 1. Bundled offline copy (lowest priority).
        try {
            context.resources.openRawResource(R.raw.threat_intel_bundled).use { stream ->
                val json = JSONObject(stream.bufferedReader().readText())
                if (json.optInt("schema", -1) == 1) {
                    putEntries(json.optJSONArray("entries"), packages, certs, BUNDLED_SOURCE, ::count, forCerts = false)
                    putEntries(json.optJSONArray("certs"), packages, certs, BUNDLED_SOURCE, ::count, forCerts = true)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bundled threat intel missing/unreadable", e)
        }

        // 2. Remote feed caches (middle priority).
        try {
            val dir = feedCacheDir(context)
            val db = AppDatabase.getInstance(context)
            for (feed in db.securityDao().allFeeds()) {
                val file = File(dir, "feed_${feed.id}.json")
                if (!file.exists()) continue
                try {
                    val json = JSONObject(file.readText())
                    if (json.optInt("schema", -1) != 1) continue
                    val source = "feed:${feed.name.ifBlank { feed.id.toString() }}"
                    putEntries(json.optJSONArray("entries"), packages, certs, source, ::count, forCerts = false)
                    putEntries(json.optJSONArray("certs"), packages, certs, source, ::count, forCerts = true)
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping unreadable feed cache ${file.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote feed caches unreadable", e)
        }

        // 3. Hand-entered list (highest priority).
        try {
            val db = AppDatabase.getInstance(context)
            for (t in db.securityDao().allManual()) {
                val hit = IntelHit(
                    ThreatSeverity.parse(t.severity),
                    t.label?.ifBlank { t.packageName } ?: t.packageName,
                    "manual",
                )
                packages[t.packageName] = hit
                count("manual")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Manual threat list unreadable", e)
        }

        return Snapshot(packages, certs, counts)
    }

    private fun putEntries(
        array: JSONArray?,
        packages: MutableMap<String, IntelHit>,
        certs: MutableMap<String, IntelHit>,
        source: String,
        count: (String) -> Unit,
        forCerts: Boolean,
    ) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val severity = ThreatSeverity.parse(o.optString("severity"))
            val label = o.optString("label").ifBlank { o.optString("package", "?") }
            if (forCerts) {
                val sha1 = o.optString("sha1").uppercase()
                if (sha1.length == 40) {
                    certs[sha1] = IntelHit(severity, label, source)
                    count(source)
                }
            } else {
                val pkg = o.optString("package")
                if (pkg.isNotBlank()) {
                    packages[pkg] = IntelHit(severity, label, source)
                    count(source)
                }
            }
        }
    }

    /** Directory holding normalized per-feed cache files (`feed_<id>.json`). */
    @JvmStatic
    fun feedCacheDir(context: Context): File =
        File(context.filesDir, "threat_feeds").apply { mkdirs() }

    @JvmStatic
    fun cacheFile(context: Context, feedId: Long): File =
        File(feedCacheDir(context), "feed_$feedId.json")

    /** Builds the JSON the user-facing import/export uses for one manual entry. */
    @JvmStatic
    fun manualToJson(t: ManualThreat): JSONObject = JSONObject()
        .put("package", t.packageName)
        .put("severity", t.severity.lowercase())
        .put("label", t.label ?: JSONObject.NULL)
        .put("note", t.note ?: JSONObject.NULL)
}
