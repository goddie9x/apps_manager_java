package com.god.ApplicationManager.data

import android.content.Context
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.DB.AppInfoDB
import com.god.ApplicationManager.DB.SettingsDB
import com.god.ApplicationManager.Facade.AppManagerFacade
import org.json.JSONArray
import org.json.JSONObject

/** Result of an import: how many rules applied, which lines were rejected and why. */
data class ImportReport(val applied: Int, val rejected: List<String>, val notInstalled: List<String>)

/**
 * Export/import of the user's rules (freeze list, notification list, settings)
 * as versioned JSON (F7). Validation is pure and unit-testable: the schema
 * version must match and every package name must pass the Android package
 * format check before anything is written.
 */
object RulesTransfer {
    const val SCHEMA_VERSION = 1

    @JvmStatic
    fun exportJson(context: Context): String {
        val db = AppDatabase.getInstance(context)
        val rules = JSONArray()
        for (rule in db.appInfoDao().all) {
            if (!rule.isHaveToBeFreeze && !rule.isHaveToTurnOffNotif) continue
            rules.put(
                JSONObject()
                    .put("package", rule.packageName)
                    .put("name", rule.appName ?: JSONObject.NULL)
                    .put("freeze", rule.isHaveToBeFreeze)
                    .put("blockNotifications", rule.isHaveToTurnOffNotif),
            )
        }
        val s = SettingsDB.getInstance()
        val settings = JSONObject()
            .put("darkMode", s.isEnableDarkMode)
            .put("showSystemInfo", s.isShowSystemInfo)
            .put("disableTurnOffNotification", s.isDisableTurnOffNotification)
            .put("storeBlockedNotifications", s.storeBlockedNotifications)
            .put("screenOffFreezeDelaySeconds", s.screenOffFreezeDelaySeconds)
            .put("unusedAppThresholdDays", s.unusedAppThresholdDays)
        return JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("rules", rules)
            .put("settings", settings)
            .toString(2)
    }

    /** Pure validation of one rule line, returning an error message or null when valid. */
    @JvmStatic
    fun validateRuleLine(packageName: String?): String? = when {
        packageName.isNullOrBlank() -> "missing package name"
        !AppManagerFacade.isValidPackageName(packageName) -> "invalid package name format: $packageName"
        else -> null
    }

    @JvmStatic
    fun importJson(context: Context, json: String): ImportReport {
        val root = JSONObject(json)
        val schema = root.optInt("schema", -1)
        require(schema == SCHEMA_VERSION) { "unsupported schema version: $schema" }
        val rejected = mutableListOf<String>()
        val notInstalled = mutableListOf<String>()
        var applied = 0
        val dao = AppDatabase.getInstance(context).appInfoDao()
        val installed = try {
            context.packageManager.getInstalledPackages(0).map { it.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
        val rules = root.optJSONArray("rules") ?: JSONArray()
        for (i in 0 until rules.length()) {
            val line = rules.optJSONObject(i)
            val pkg = line?.optString("package", "")
            val error = validateRuleLine(pkg)
            if (line == null || error != null) {
                rejected.add("line ${i + 1}: ${error ?: "not an object"}")
                continue
            }
            pkg!!
            if (pkg !in installed) {
                notInstalled.add(pkg)
                continue
            }
            val entity = dao.findByPackage(pkg) ?: AppInfoDB().apply { packageName = pkg }
            entity.appName = line.optString("name", entity.appName ?: "")
            entity.isHaveToBeFreeze = line.optBoolean("freeze", entity.isHaveToBeFreeze)
            entity.isHaveToTurnOffNotif = line.optBoolean("blockNotifications", entity.isHaveToTurnOffNotif)
            dao.upsert(entity)
            applied++
        }
        root.optJSONObject("settings")?.let { so ->
            val s = SettingsDB.getInstance()
            if (so.has("darkMode")) s.isEnableDarkMode = so.optBoolean("darkMode")
            if (so.has("showSystemInfo")) s.isShowSystemInfo = so.optBoolean("showSystemInfo")
            if (so.has("disableTurnOffNotification")) {
                s.isDisableTurnOffNotification = so.optBoolean("disableTurnOffNotification")
            }
            if (so.has("storeBlockedNotifications")) {
                s.storeBlockedNotifications = so.optBoolean("storeBlockedNotifications")
            }
            if (so.has("screenOffFreezeDelaySeconds")) {
                s.screenOffFreezeDelaySeconds = so.optInt("screenOffFreezeDelaySeconds").coerceIn(0, 600)
            }
            if (so.has("unusedAppThresholdDays")) {
                s.unusedAppThresholdDays = so.optInt("unusedAppThresholdDays").coerceIn(1, 3650)
            }
            s.save()
        }
        return ImportReport(applied, rejected, notInstalled)
    }
}
