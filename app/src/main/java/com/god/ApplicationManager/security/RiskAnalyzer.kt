package com.god.ApplicationManager.security

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import java.security.MessageDigest

/**
 * Offline heuristic risk engine. Scores every installed package 0-100 from
 * observable signals - no network, no root needed:
 *
 * - threat-intel hits (package name AND signing-cert SHA-1, so a renamed
 *   malware APK is still caught),
 * - installer provenance (sideload vs known stores),
 * - signature spoofing (system-looking package name, wrong signer),
 * - hidden apps (no launcher activity),
 * - dangerous / sensitive permissions requested,
 * - *granted* sensitive access checked cross-package via AppOpsManager and
 *   Settings.Secure (overlay, install-packages, usage stats, accessibility
 *   service, notification listener, device admin),
 * - debuggable / testOnly / ancient targetSdk,
 * - never launched.
 *
 * Bands: >=80 DANGEROUS, >=50 SUSPICIOUS, >=25 JUNK, else CLEAN.
 */
object RiskAnalyzer {
    private const val TAG = "RiskAnalyzer"

    enum class Band { DANGEROUS, SUSPICIOUS, JUNK, CLEAN }

    enum class FindingKind {
        INTEL_MALWARE_PKG,
        INTEL_SUSPECT_PKG,
        INTEL_MALWARE_CERT,
        INTEL_SUSPECT_CERT,
        USER_DANGEROUS,
        USER_JUNK,
        SIGNATURE_SPOOF,
        SIDELOAD,
        UNKNOWN_INSTALLER,
        NO_LAUNCHER,
        DANGEROUS_PERMISSIONS,
        SENSITIVE_PERMISSIONS,
        SMS_EXFILTRATION_PATTERN,
        QUERY_ALL_PACKAGES_PATTERN,
        ACCESSIBILITY_SERVICE_ACTIVE,
        NOTIFICATION_LISTENER_ACTIVE,
        DEVICE_ADMIN_ACTIVE,
        CAN_DRAW_OVERLAYS,
        CAN_INSTALL_PACKAGES,
        USAGE_STATS_ACCESS,
        REQUESTS_DEVICE_ADMIN,
        DEBUGGABLE,
        TEST_ONLY,
        OLD_TARGET_SDK,
        NEVER_USED,
    }

    data class Finding(val kind: FindingKind, val detail: String = "")

    data class RiskReport(
        val packageName: String,
        val appName: String,
        val isSystemApp: Boolean,
        val installer: String?,
        val score: Int,
        val band: Band,
        val findings: List<Finding>,
    )

    /** Installer packages we trust as real stores; anything else sideload-like. */
    private val KNOWN_STORES = setOf(
        "com.android.vending",
        "com.amazon.venezia",
        "com.sec.android.app.samsungapps",
        "com.huawei.appmarket",
        "com.xiaomi.mipicks",
        "com.oppo.market",
        "com.vivo.appstore",
        "com.oneplus.market",
        "com.transsion.appstore",
        "org.fdroid.fdroid",
        "com.aurora.store",
    )

    /** protectionLevel==dangerous permissions worth flagging. */
    private val DANGEROUS_PERMS = setOf(
        "android.permission.READ_SMS", "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS", "android.permission.RECEIVE_MMS",
        "android.permission.READ_CALL_LOG", "android.permission.WRITE_CALL_LOG",
        "android.permission.PROCESS_OUTGOING_CALLS",
        "android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS",
        "android.permission.GET_ACCOUNTS",
        "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.RECORD_AUDIO", "android.permission.CAMERA",
        "android.permission.READ_PHONE_STATE", "android.permission.READ_PHONE_NUMBERS",
        "android.permission.CALL_PHONE", "android.permission.ANSWER_PHONE_CALLS",
        "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.BODY_SENSORS", "android.permission.ACTIVITY_RECOGNITION",
        "android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR",
    )

    /** Non-dangerous but sensitive permissions / capabilities. */
    private val SENSITIVE_PERMS = setOf(
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.PACKAGE_USAGE_STATS",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.BIND_DEVICE_ADMIN",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.READ_CALL_LOG",
    )

    /** Raw op string: AppOpsManager.OPSTR_REQUEST_INSTALL_PACKAGES is hidden from the public SDK. */
    private const val OPSTR_REQUEST_INSTALL_PACKAGES_RAW = "android:request_install_packages"

    /** Per-scan caches: platform certs + launcher/activity lookups are expensive. */
    class ScanContext(val context: Context, val snapshot: ThreatIntelStore.Snapshot) {
        val pm: PackageManager = context.packageManager
        val platformCerts: Set<String> by lazy { certSha1List(context, "android").toSet() }
        private val launcherCache = HashMap<String, Boolean>()

        fun hasLauncherActivity(pkg: String): Boolean = launcherCache.getOrPut(pkg) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(pkg)
                pm.queryIntentActivities(intent, 0).isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }
    }

    @JvmStatic
    fun newScanContext(context: Context): ScanContext =
        ScanContext(context.applicationContext, ThreatIntelStore.loadSnapshot(context.applicationContext))

    fun analyze(scan: ScanContext, pkg: String): RiskReport? {
        val context = scan.context
        val pm = scan.pm
        val appInfo: ApplicationInfo = try {
            pm.getApplicationInfo(pkg, 0)
        } catch (e: Exception) {
            return null
        }
        val pkgInfo: PackageInfo = try {
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        } catch (e: Exception) {
            return null
        }
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val appName = try {
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            pkg
        }

        var score = 0
        val findings = ArrayList<Finding>()
        fun add(kind: FindingKind, points: Int, detail: String = "") {
            findings.add(Finding(kind, detail))
            score += points
        }

        // ---- 1. Threat intel: package name, then signing cert ----
        scan.snapshot.lookupPackage(pkg)?.let { hit ->
            if (hit.severity == ThreatSeverity.MALWARE) {
                add(FindingKind.INTEL_MALWARE_PKG, 100, "${hit.label} [${hit.source}]")
            } else {
                add(FindingKind.INTEL_SUSPECT_PKG, 55, "${hit.label} [${hit.source}]")
            }
        }
        val certHits = certSha1List(context, pkg)
            .mapNotNull { scan.snapshot.lookupCert(it) }
            .distinctBy { it.label }
        for (hit in certHits) {
            if (hit.severity == ThreatSeverity.MALWARE) {
                add(FindingKind.INTEL_MALWARE_CERT, 100, "${hit.label} [${hit.source}]")
            } else {
                add(FindingKind.INTEL_SUSPECT_CERT, 55, "${hit.label} [${hit.source}]")
            }
        }

        // ---- 2. Installer provenance ----
        val installer = installerOf(pm, pkg)
        if (!isSystemApp) {
            when {
                installer == null -> add(FindingKind.SIDELOAD, 15, "installer=unknown")
                installer !in KNOWN_STORES ->
                    add(FindingKind.UNKNOWN_INSTALLER, 10, "installer=$installer")
            }
        }

        // ---- 3. Signature spoof: system-looking name, wrong signer ----
        val looksSystem = pkg == "android" || pkg.startsWith("android.") || pkg.startsWith("com.android.")
        if (looksSystem && !isSystemApp) {
            add(FindingKind.SIGNATURE_SPOOF, 75, pkg)
        } else if (looksSystem && isSystemApp && certHits.isEmpty()) {
            val overlap = certSha1List(context, pkg).intersect(scan.platformCerts)
            if (overlap.isEmpty() && scan.platformCerts.isNotEmpty()) {
                add(FindingKind.SIGNATURE_SPOOF, 75, "signer != platform")
            }
        }

        // ---- 4. Hidden app: user app with no launcher entry ----
        if (!isSystemApp && !scan.hasLauncherActivity(pkg)) {
            add(FindingKind.NO_LAUNCHER, 20)
        }

        // ---- 5. Permissions ----
        val requested = pkgInfo.requestedPermissions?.toList().orEmpty()
        val dangerous = requested.filter { it in DANGEROUS_PERMS }
        if (dangerous.isNotEmpty()) {
            add(FindingKind.DANGEROUS_PERMISSIONS, minOf(20, dangerous.size * 4), dangerous.joinToString(", "))
        }
        val sensitive = requested.filter { it in SENSITIVE_PERMS }
        if (sensitive.isNotEmpty()) {
            add(FindingKind.SENSITIVE_PERMISSIONS, minOf(24, sensitive.size * 8), sensitive.joinToString(", "))
        }
        val hasInternet = "android.permission.INTERNET" in requested
        val hasSms = requested.any { it.contains("SMS") }
        if (hasSms && hasInternet) {
            add(FindingKind.SMS_EXFILTRATION_PATTERN, 20, "SMS+INTERNET")
        }
        if ("android.permission.QUERY_ALL_PACKAGES" in requested && hasInternet) {
            add(FindingKind.QUERY_ALL_PACKAGES_PATTERN, 15, "QUERY_ALL_PACKAGES+INTERNET")
        }
        if ("android.permission.BIND_DEVICE_ADMIN" in requested) {
            add(FindingKind.REQUESTS_DEVICE_ADMIN, 20)
        }

        // ---- 6. Granted sensitive access (cross-package, no special permission needed) ----
        val uid = appInfo.uid
        if (isAccessibilityEnabledFor(context, pkg)) add(FindingKind.ACCESSIBILITY_SERVICE_ACTIVE, 25)
        if (isNotificationListenerEnabledFor(context, pkg)) add(FindingKind.NOTIFICATION_LISTENER_ACTIVE, 15)
        if (isDeviceAdminFor(context, pkg)) add(FindingKind.DEVICE_ADMIN_ACTIVE, 25)
        if (checkOp(context, AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, pkg)) {
            add(FindingKind.CAN_DRAW_OVERLAYS, 10)
        }
        if (Build.VERSION.SDK_INT >= 26 &&
            checkOp(context, OPSTR_REQUEST_INSTALL_PACKAGES_RAW, uid, pkg)
        ) {
            add(FindingKind.CAN_INSTALL_PACKAGES, 15)
        }
        if (checkOp(context, AppOpsManager.OPSTR_GET_USAGE_STATS, uid, pkg)) {
            add(FindingKind.USAGE_STATS_ACCESS, 10)
        }

        // ---- 7. Build flags ----
        if ((appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) add(FindingKind.DEBUGGABLE, 15)
        if ((appInfo.flags and ApplicationInfo.FLAG_TEST_ONLY) != 0) add(FindingKind.TEST_ONLY, 10)
        if (appInfo.targetSdkVersion < 23) add(FindingKind.OLD_TARGET_SDK, 10, "targetSdk=${appInfo.targetSdkVersion}")

        // ---- 8. Never launched ----
        if (!isSystemApp && neverLaunched(context, pkg, pkgInfo)) {
            add(FindingKind.NEVER_USED, 15)
        }

        score = score.coerceIn(0, 100)
        val band = when {
            score >= 80 -> Band.DANGEROUS
            score >= 50 -> Band.SUSPICIOUS
            score >= 25 -> Band.JUNK
            else -> Band.CLEAN
        }
        return RiskReport(pkg, appName, isSystemApp, installer, score, band, findings)
    }

    // ---------- helpers ----------

    private fun installerOf(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) {
            pm.getInstallSourceInfo(pkg).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(pkg)
        }
    } catch (e: Exception) {
        null
    }

    /** SHA-1 (uppercase hex) of every signing certificate of a package. */
    @JvmStatic
    fun certSha1List(context: Context, pkg: String): List<String> {
        return try {
            val pm = context.packageManager
            val pi = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            }
            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                val si = pi.signingInfo ?: return emptyList()
                if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                pi.signatures
            } ?: return emptyList()
            val md = MessageDigest.getInstance("SHA-1")
            sigs.map { sig ->
                md.reset()
                md.digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }
            }.distinct()
        } catch (e: Exception) {
            Log.w(TAG, "cert read failed for $pkg", e)
            emptyList()
        }
    }

    private fun checkOp(context: Context, op: String, uid: Int, pkg: String): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(op, uid, pkg) == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    private fun isAccessibilityEnabledFor(context: Context, pkg: String): Boolean = try {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        enabled.split(':').any { it.startsWith("$pkg/") }
    } catch (e: Exception) {
        false
    }

    private fun isNotificationListenerEnabledFor(context: Context, pkg: String): Boolean = try {
        val enabled = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners",
        ).orEmpty()
        enabled.split(':').any { it.startsWith("$pkg/") }
    } catch (e: Exception) {
        false
    }

    private fun isDeviceAdminFor(context: Context, pkg: String): Boolean = try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.activeAdmins?.any { it.packageName == pkg } == true
    } catch (e: Exception) {
        false
    }

    /** True when the app was installed >7 days ago and never launched (yearly stats). */
    private fun neverLaunched(context: Context, pkg: String, pkgInfo: PackageInfo): Boolean {
        return try {
            val installedOld = System.currentTimeMillis() - pkgInfo.firstInstallTime > 7L * 24 * 3600 * 1000
            if (!installedOld) return false
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE)
                as android.app.usage.UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_YEARLY,
                now - 365L * 24 * 3600 * 1000, now,
            )
            (stats?.find { it.packageName == pkg }?.lastTimeUsed ?: 0L) == 0L
        } catch (e: Exception) {
            false
        }
    }

    /** Pure score->band mapping, unit-testable without Android. */
    @JvmStatic
    fun bandForScore(score: Int): Band = when {
        score >= 80 -> Band.DANGEROUS
        score >= 50 -> Band.SUSPICIOUS
        score >= 25 -> Band.JUNK
        else -> Band.CLEAN
    }
}
