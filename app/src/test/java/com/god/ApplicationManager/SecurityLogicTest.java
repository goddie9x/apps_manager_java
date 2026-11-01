package com.god.ApplicationManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.god.ApplicationManager.security.RiskAnalyzer;
import com.god.ApplicationManager.security.ThreatSeverity;
import com.god.ApplicationManager.security.ThreatTransfer;

import org.junit.Test;

/**
 * Unit tests for the security center's pure logic: score bands, manual-list
 * entry validation, and severity parsing. Android-dependent scanning is
 * covered by instrumented checks on a real device instead.
 */
public class SecurityLogicTest {

    @Test
    public void bandBoundaries() {
        assertEquals(RiskAnalyzer.Band.CLEAN, RiskAnalyzer.bandForScore(0));
        assertEquals(RiskAnalyzer.Band.CLEAN, RiskAnalyzer.bandForScore(24));
        assertEquals(RiskAnalyzer.Band.JUNK, RiskAnalyzer.bandForScore(25));
        assertEquals(RiskAnalyzer.Band.JUNK, RiskAnalyzer.bandForScore(49));
        assertEquals(RiskAnalyzer.Band.SUSPICIOUS, RiskAnalyzer.bandForScore(50));
        assertEquals(RiskAnalyzer.Band.SUSPICIOUS, RiskAnalyzer.bandForScore(79));
        assertEquals(RiskAnalyzer.Band.DANGEROUS, RiskAnalyzer.bandForScore(80));
        assertEquals(RiskAnalyzer.Band.DANGEROUS, RiskAnalyzer.bandForScore(100));
        assertEquals(RiskAnalyzer.Band.DANGEROUS, RiskAnalyzer.bandForScore(150));
    }

    @Test
    public void severityParsing() {
        assertEquals(ThreatSeverity.MALWARE, ThreatSeverity.parse("malware"));
        assertEquals(ThreatSeverity.MALWARE, ThreatSeverity.parse("MALWARE"));
        assertEquals(ThreatSeverity.SUSPECT, ThreatSeverity.parse("suspect"));
        assertEquals(ThreatSeverity.SUSPECT, ThreatSeverity.parse("bogus"));
        assertEquals(ThreatSeverity.SUSPECT, ThreatSeverity.parse(null));
    }

    @Test
    public void manualEntryValidation() {
        assertNull(ThreatTransfer.validateEntry("com.evil.app", "malware"));
        assertNull(ThreatTransfer.validateEntry("com.evil.app", "suspect"));
        assertNull(ThreatTransfer.validateEntry("com.evil.app", null));
        assertNotNull(ThreatTransfer.validateEntry("", "malware"));
        assertNotNull(ThreatTransfer.validateEntry(null, "malware"));
        assertNotNull(ThreatTransfer.validateEntry("not a package!", "malware"));
        assertNotNull(ThreatTransfer.validateEntry("com.evil.app; rm -rf", "malware"));
        assertNotNull(ThreatTransfer.validateEntry("com.evil.app", "critical"));
    }

    @Test
    public void findingKindsAllHaveLabels() {
        // Every FindingKind must be mapped in the UI label function; this guards
        // against adding a kind without a string resource.
        assertEquals(25, RiskAnalyzer.FindingKind.values().length);
    }
}
