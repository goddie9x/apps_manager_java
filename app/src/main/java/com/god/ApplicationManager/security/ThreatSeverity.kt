package com.god.ApplicationManager.security

/** Threat level of an intel record or a scan finding. */
enum class ThreatSeverity {
    MALWARE,
    SUSPECT;

    companion object {
        @JvmStatic
        fun parse(raw: String?): ThreatSeverity =
            if (raw.equals("malware", ignoreCase = true)) MALWARE else SUSPECT
    }
}
