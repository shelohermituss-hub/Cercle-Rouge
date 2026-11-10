package com.cerclerouge.privacy

import com.cerclerouge.privacy.db.SettingDao
import com.cerclerouge.privacy.db.SettingEntity
import com.cerclerouge.privacy.pin.Lockout
import com.cerclerouge.privacy.pin.PinHasher
import com.cerclerouge.privacy.pin.PinRepository
import com.cerclerouge.privacy.pin.PinResult
import com.cerclerouge.privacy.pin.PinRules
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PinTest {
    private class FakeSettings : SettingDao {
        val map = mutableMapOf<String, String>()

        override suspend fun put(setting: SettingEntity) {
            map[setting.key] = setting.value
        }

        override suspend fun get(key: String) = map[key]

        override suspend fun remove(key: String) {
            map.remove(key)
        }
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?) = this

        override fun instant() = now
    }

    @Test fun `R-PE-02 le hachage depend du sel et la comparaison fonctionne`() {
        val salt = ByteArray(16) { 1 }
        val h = PinHasher.hash("2580", salt, iterations = 1_000)
        assertTrue(PinHasher.matches("2580", salt, h, iterations = 1_000))
        assertFalse(PinHasher.matches("2581", salt, h, iterations = 1_000))
        assertFalse(PinHasher.hash("2580", ByteArray(16) { 2 }, 1_000).contentEquals(h))
    }

    @Test fun `regles du PIN`() {
        assertEquals(PinRules.Problem.LENGTH, PinRules.check("123"))
        assertEquals(PinRules.Problem.NOT_DIGITS, PinRules.check("12a4"))
        listOf("0000", "1234", "9876", "12345678").forEach { assertEquals(PinRules.Problem.TOO_SIMPLE, PinRules.check(it)) }
        assertNull(PinRules.check("2580"))
    }

    @Test fun `temporisation progressive plafonnee`() {
        assertEquals(Duration.ZERO, Lockout.delayAfter(2))
        assertEquals(Duration.ofSeconds(30), Lockout.delayAfter(3))
        assertEquals(Duration.ofSeconds(60), Lockout.delayAfter(4))
        assertEquals(Duration.ofMinutes(15), Lockout.delayAfter(50))
    }

    @Test fun `depot du PIN verification echecs et blocage`() = runBlocking {
        val settings = FakeSettings()
        val clock = MutableClock(Instant.parse("2026-11-10T22:00:00Z"))
        val repo = PinRepository(settings, clock)
        assertEquals(PinResult.NotConfigured, repo.verify("2580"))
        repo.setPin("2580")
        assertTrue(settings.map.values.none { it.contains("2580") }) // jamais en clair
        assertEquals(PinResult.Ok, repo.verify("2580"))
        repeat(3) { assertIs<PinResult.Wrong>(repo.verify("1111")) }
        assertIs<PinResult.Locked>(repo.verify("2580")) // bloqué 30 s même avec le bon PIN
        clock.now = clock.now + Duration.ofSeconds(31)
        assertEquals(PinResult.Ok, repo.verify("2580"))
        assertIs<PinResult.Wrong>(repo.verify("1111")) // compteur remis à zéro après succès : pas de blocage immédiat
        assertEquals(PinResult.Ok, repo.verify("2580"))
    }
}
