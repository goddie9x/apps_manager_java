package com.god.ApplicationManager.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.DB.UserFlag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans all installed packages with [RiskAnalyzer] on a background thread.
 *
 * User flags are applied on top of the heuristic result:
 * - IGNORED packages are dropped from the result entirely (whitelist),
 * - JUNK forces the JUNK band (unless the score already says worse),
 * - DANGEROUS forces the DANGEROUS band.
 */
object SecurityScanner {
    data class ScanResult(
        val reports: List<RiskAnalyzer.RiskReport>,
        val total: Int,
        val scannedAt: Long,
    )

    /**
     * @param includeSystemApps when false, system apps are skipped (faster, less noise).
     * @param onProgress called with (done, total) from the background thread.
     */
    suspend fun scan(
        context: Context,
        includeSystemApps: Boolean,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): ScanResult = withContext(Dispatchers.Default) {
        val appContext = context.applicationContext
        val scanCtx = RiskAnalyzer.newScanContext(appContext)
        val pm = appContext.packageManager
        val flags: Map<String, String> = try {
            AppDatabase.getInstance(appContext).securityDao().allFlags()
                .associate { it.packageName to it.kind }
        } catch (e: Exception) {
            emptyMap()
        }

        val packages = try {
            pm.getInstalledPackages(0).map { it.packageName }
        } catch (e: Exception) {
            emptyList()
        }
        val reports = ArrayList<RiskAnalyzer.RiskReport>(packages.size)
        var done = 0
        for (pkg in packages) {
            if (pkg == appContext.packageName) {
                done++
                continue // never flag ourselves
            }
            val kind = flags[pkg]
            if (kind == UserFlag.KIND_IGNORED) {
                done++
                continue
            }
            val report = try {
                RiskAnalyzer.analyze(scanCtx, pkg)
            } catch (e: Exception) {
                null
            }
            if (report != null && (includeSystemApps || !report.isSystemApp)) {
                reports.add(applyFlag(report, kind))
            }
            done++
            if (done % 10 == 0) onProgress(done, packages.size)
        }
        onProgress(packages.size, packages.size)
        reports.sortWith(
            compareByDescending<RiskAnalyzer.RiskReport> { it.score }
                .thenBy { it.appName.lowercase() },
        )
        ScanResult(reports, packages.size, System.currentTimeMillis())
    }

    private fun applyFlag(
        report: RiskAnalyzer.RiskReport,
        kind: String?,
    ): RiskAnalyzer.RiskReport {
        if (kind == null) return report
        val forcedBand = when (kind) {
            UserFlag.KIND_DANGEROUS -> RiskAnalyzer.Band.DANGEROUS
            UserFlag.KIND_JUNK -> RiskAnalyzer.Band.JUNK
            else -> return report
        }
        // A user flag only ever escalates, never downgrades an intel hit.
        val escalate = forcedBand.ordinal < report.band.ordinal
        if (!escalate) return report
        val findingKind = if (kind == UserFlag.KIND_DANGEROUS) {
            RiskAnalyzer.FindingKind.USER_DANGEROUS
        } else {
            RiskAnalyzer.FindingKind.USER_JUNK
        }
        val findings = listOf(RiskAnalyzer.Finding(findingKind)) + report.findings
        return report.copy(
            band = forcedBand,
            score = maxOf(report.score, if (forcedBand == RiskAnalyzer.Band.DANGEROUS) 80 else 25),
            findings = findings,
        )
    }

    /** Packages that request any of these permissions, grouped for the permissions tab. */
    suspend fun permissionIndex(
        context: Context,
        includeSystemApps: Boolean,
    ): Map<String, List<RiskAnalyzer.RiskReport>> = withContext(Dispatchers.Default) {
        val appContext = context.applicationContext
        val pm = appContext.packageManager
        val index = HashMap<String, MutableList<RiskAnalyzer.RiskReport>>()
        val permsOfInterest = SensitivePermissionLabels.PERMISSIONS
        val packages = try {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        } catch (e: Exception) {
            emptyList()
        }
        for (pi in packages) {
            val pkg = pi.packageName
            if (pkg == appContext.packageName) continue
            val isSystem = try {
                val ai = pm.getApplicationInfo(pkg, 0)
                (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (e: Exception) {
                false
            }
            if (!includeSystemApps && isSystem) continue
            val requested = pi.requestedPermissions?.toSet().orEmpty()
            val hits = requested.intersect(permsOfInterest)
            if (hits.isEmpty()) continue
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
            val stub = RiskAnalyzer.RiskReport(pkg, appName, isSystem, null, 0, RiskAnalyzer.Band.CLEAN, emptyList())
            for (perm in hits) {
                index.getOrPut(perm) { ArrayList() }.add(stub)
            }
        }
        index.mapValues { (_, v) -> v.sortedBy { it.appName.lowercase() } }
    }
}
