package com.cerclerouge.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Classification des états et des niveaux : elle décide de l'écran d'urgence, du service et des envois. */
class ModelTest {
    @Test fun onlyAlertStatesAreAlerts() {
        val alerts = ProtectionState.entries.filter { it.isAlert }.toSet()
        assertEquals(setOf(ProtectionState.ALERT_PENDING, ProtectionState.ALERT_SENT, ProtectionState.EMERGENCY_MODE), alerts)
    }

    @Test fun armedStatesAreArmedAndNeverAlsoAlerts() {
        val armed = ProtectionState.entries.filter { it.isArmed }.toSet()
        assertEquals(setOf(ProtectionState.ARMED_AUDIO_ONLY, ProtectionState.ARMED_FULL, ProtectionState.TRIP_ACTIVE), armed)
        assertTrue(ProtectionState.entries.none { it.isArmed && it.isAlert })
        assertFalse(ProtectionState.OFF.isArmed || ProtectionState.OFF.isAlert)
    }

    @Test fun alertLevelsGoFromZeroToFourInOrder() { // R-ESC-02 : le niveau 4 n'est jamais automatique
        assertEquals(listOf(0, 1, 2, 3, 4), AlertLevel.entries.map { it.value })
        assertEquals(AlertLevel.entries.sortedBy { it.value }, AlertLevel.entries)
    }

    @Test fun ageBandsMapToVulnerabilityProfiles() {
        assertEquals(VulnerabilityProfile.YOUNG_12_14, AgeBand.UNDER_12.profile())
        assertEquals(VulnerabilityProfile.YOUNG_12_14, AgeBand.YOUNG_12_15.profile())
        assertEquals(VulnerabilityProfile.TEEN_15_17, AgeBand.TEEN_16_17.profile())
        assertEquals(VulnerabilityProfile.ADULT, AgeBand.ADULT.profile())
    }

    @Test fun alertAndConsentCarryTheirFields() {
        val at = Instant.parse("2026-11-17T10:00:00Z")
        val a = Alert("a1", AlertLevel.CONTACTS, Trigger.SOS, Reason.SOS, Confidence.HIGH, silent = false, openedAt = at)
        assertEquals(a, a.copy())
        assertEquals(AlertLevel.CONTACTS, a.level)
        val c = Consent(ConsentScope.MICROPHONE, granted = true, textVersion = "v1", locale = "fr", at = at)
        assertTrue(c.granted)
        assertEquals(ConsentScope.entries.size, ConsentScope.entries.toSet().size)
        assertEquals(5, Retention.entries.size)
    }
}
