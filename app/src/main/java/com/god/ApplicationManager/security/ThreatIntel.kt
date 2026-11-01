package com.god.ApplicationManager.security

/**
 * One normalized threat-intel record, no matter which source it came from
 * (bundled offline copy, remote feed cache, or the hand-entered list).
 */
data class ThreatIntelEntry(
    val packageName: String,
    val severity: ThreatSeverity,
    val label: String,
    val source: String,
)

data class ThreatCertEntry(
    /** Uppercase hex SHA-1 of the signing certificate. */
    val sha1: String,
    val severity: ThreatSeverity,
    val label: String,
    val source: String,
)

/** A merged lookup hit, with the winning source attached. */
data class IntelHit(
    val severity: ThreatSeverity,
    val label: String,
    val source: String,
)
