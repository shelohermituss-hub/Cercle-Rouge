package com.cerclerouge.alert

import com.cerclerouge.domain.AlertLevel
import com.cerclerouge.domain.Confidence
import com.cerclerouge.domain.ProtectionState
import com.cerclerouge.domain.Reason
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Propriétés vérifiées sur des milliers de suites d'événements aléatoires (R-AL-01). */
class AlertEnginePropertiesTest {
    private val t0 = Instant.parse("2026-11-10T22:00:00Z")

    private fun randomInput(r: Random, n: Int): Input {
        val id = "r$n"
        val auth = Auth.entries[r.nextInt(Auth.entries.size)]
        return when (r.nextInt(17)) {
            0 -> Input.Arm(id)
            1 -> Input.Disarm(id, auth)
            2 -> Input.StartTrip(id, auth)
            3 -> Input.Arrived(id, auth)
            4 -> Input.Sos(id)
            5 -> Input.SecretPhrase(id)
            6 -> Input.Detection(
                id,
                RecommendedAction.entries[r.nextInt(4)],
                Reason.entries[r.nextInt(Reason.entries.size)],
                Confidence.MEDIUM
            )
            7 -> Input.TripIssue(id, Reason.TRIP_DEVIATION)
            8 -> Input.ImSafe(id, auth)
            9 -> Input.NeedHelp(id)
            10 -> Input.FalseAlarm(id, auth)
            11 -> Input.TimerElapsed(id, Timer.entries[r.nextInt(Timer.entries.size)])
            12 -> Input.ContactAck(id)
            13 -> Input.PermissionsChanged(id, r.nextBoolean(), r.nextBoolean())
            14 -> Input.ProcessRestored(id)
            15 -> Input.CallEmergency(id)
            else -> Input.Sos(id)
        }
    }

    private fun explore(check: (before: EngineState, input: Input, policy: Policy, tr: Transition) -> Unit) {
        repeat(400) { seed ->
            val r = Random(seed)
            val policy = Policy(
                pinConfigured = r.nextBoolean(),
                autoEmergency = r.nextBoolean(),
                protectionAllowed = r.nextInt(4) != 0
            )
            var state = EngineState(micGranted = r.nextBoolean(), locationGranted = r.nextBoolean())
            var now = t0
            repeat(60) { n ->
                now += Duration.ofSeconds(r.nextLong(0, 600))
                val input = randomInput(r, seed * 1000 + n)
                val tr = AlertEngine.reduce(state, input, policy, now)
                check(state, input, policy, tr)
                state = tr.state
            }
        }
    }

    @Test fun `R-ESC-02 jamais de niveau 4 automatique`() = explore { _, _, _, tr ->
        assertNotEquals(AlertLevel.AUTHORITIES, tr.state.alert?.level)
    }

    @Test fun `P4 jamais de position en OFF`() = explore { _, _, _, tr ->
        if (tr.state.protection == ProtectionState.OFF) {
            tr.effects.filterIsInstance<Effect.ConfigureEngines>().forEach { assertEquals(null, it.location) }
        }
    }

    @Test fun `R-SM-05 le SOS mene toujours au mode urgence`() = explore { _, input, _, tr ->
        if (input is Input.Sos) assertEquals(ProtectionState.EMERGENCY_MODE, tr.state.protection)
    }

    @Test fun `R-PAR-01 un mineur sans consentement n est jamais arme`() = explore { _, _, policy, tr ->
        if (!policy.protectionAllowed) assertTrue(!tr.state.protection.isArmed)
    }

    @Test fun `une alerte existe si et seulement si l etat est une alerte`() = explore { _, _, _, tr ->
        assertEquals(tr.state.protection.isAlert, tr.state.alert != null)
    }

    @Test fun `le niveau d une alerte ne redescend jamais sans la clore`() = explore { before, _, _, tr ->
        val a = before.alert
        val b = tr.state.alert
        if (a != null && b != null && a.id == b.id && b.level.value < a.level.value) fail("niveau redescendu : $a → $b")
    }

    @Test fun `R-SM-04 avec un PIN une alerte ne se clot jamais sans PIN verifie`() = explore { before, input, policy, tr ->
        val userClosure = input is Input.ImSafe || input is Input.FalseAlarm || input is Input.Disarm
        if (policy.pinConfigured && before.protection.isAlert && !tr.state.protection.isAlert && userClosure) {
            val auth = when (input) {
                is Input.ImSafe -> input.auth
                is Input.FalseAlarm -> input.auth
                is Input.Disarm -> input.auth
            }
            assertEquals(Auth.VERIFIED, auth)
        }
    }

    @Test fun `aucune donnee ne part en etat non alerte sauf avis prevus`() = explore { _, _, _, tr ->
        if (!tr.state.protection.isAlert) {
            tr.effects.filterIsInstance<Effect.Dispatch>().forEach {
                assertTrue(it.kind.allowedOutsideAlert, "envoi inattendu hors alerte : $it")
            }
        }
    }
}
