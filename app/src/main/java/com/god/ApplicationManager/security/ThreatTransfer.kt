package com.god.ApplicationManager.security

import android.content.Context
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.DB.ManualThreat
import com.god.ApplicationManager.Facade.AppManagerFacade
import org.json.JSONArray
import org.json.JSONObject

/** Result of a manual-list import: applied, rejected (with reason) and not-installed. */
data class ThreatImportReport(val applied: Int, val rejected: List<String>, val notInstalled: List<String>)

/**
 * Export/import of the hand-entered threat list as versioned JSON (same UX
 * pattern as RulesTransfer). Validation is pure and unit-testable: schema must
 * match, severity must be malware|suspect, package names must be valid.
 * Entries for packages that are not installed are reported, not applied.
 */
object ThreatTransfer {
    const val SCHEMA_VERSION = 1

    @JvmStatic
    fun exportJson(context: Context): String {
        val db = AppDatabase.getInstance(context)
        val entries = JSONArray()
        for (t in db.securityDao().allManual()) {
            entries.put(ThreatIntelStore.manualToJson(t))
        }
        return JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("type", "apps-manager-threat-list")
            .put("exported_at", System.currentTimeMillis())
            .put("entries", entries)
            .toString(2)
    }

    /** Pure validation of one entry, returning an error message or null when valid. */
    @JvmStatic
    fun validateEntry(packageName: String?, severity: String?): String? = when {
        packageName.isNullOrBlank() -> "missing package name"
        !AppManagerFacade.isValidPackageName(packageName) -> "invalid package name format: $packageName"
        severity != null && severity != "malware" && severity != "suspect" ->
            "invalid severity (want malware|suspect): $severity"
        else -> null
    }

    @JvmStatic
    fun importJson(context: Context, json: String): ThreatImportReport {
        val root = JSONObject(json)
        val schema = root.optInt("schema", -1)
        require(schema == SCHEMA_VERSION) { "unsupported schema version: $schema" }
        val rejected = mutableListOf<String>()
        val notInstalled = mutableListOf<String>()
        var applied = 0
        val dao = AppDatabase.getInstance(context).securityDao()
        val installed = try {
            context.packageManager.getInstalledPackages(0).map { it.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
        val entries = root.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until entries.length()) {
            val line = entries.optJSONObject(i)
            val pkg = line?.optString("package", "")
            val severity = line?.optString("severity", "suspect")
            val error = validateEntry(pkg, severity)
            if (line == null || error != null) {
                rejected.add("line ${i + 1}: ${error ?: "not an object"}")
                continue
            }
            pkg!!
            if (pkg !in installed) {
                notInstalled.add(pkg)
                continue
            }
            val entity = dao.findManual(pkg) ?: ManualThreat().apply { packageName = pkg }
            entity.severity = severity ?: "suspect"
            entity.label = line.optString("label", entity.label ?: "").ifBlank { null }
            entity.note = line.optString("note", entity.note ?: "").ifBlank { null }
            entity.addedAt = System.currentTimeMillis()
            dao.upsertManual(entity)
            applied++
        }
        return ThreatImportReport(applied, rejected, notInstalled)
    }
}
