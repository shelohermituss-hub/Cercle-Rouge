package com.cerclerouge.alert

import com.cerclerouge.domain.Alert
import com.cerclerouge.domain.AlertLevel
import com.cerclerouge.domain.Confidence
import com.cerclerouge.domain.ProtectionState
import com.cerclerouge.domain.Reason
import com.cerclerouge.domain.Trigger
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** R-SM-01 / R-AL-02 : l'état persisté doit revenir identique après la mort du processus. */
class EngineStateCodecTest {
    private val t0 = Instant.parse("2026-11-11T10:00:00Z")

    private fun roundTrip(s: EngineState) = assertEquals(s, EngineStateCodec.decode(EngineStateCodec.encode(s)))

    @Test fun freshStateRoundTrips() = roundTrip(EngineState())

    @Test fun stateWithAlertTimersAndHistoryRoundTrips() = roundTrip(
        EngineState(
            protection = ProtectionState.ALERT_SENT,
            base = ProtectionState.ARMED_FULL,
            beforeTrip = ProtectionState.ARMED_AUDIO_ONLY,
            alert = Alert("a b/c,é", AlertLevel.CONTACTS, Trigger.SOS, Reason.SOS, Confidence.HIGH, true, t0),
            verifyAt = t0.plusSeconds(20),
            escalateAt = t0.plusSeconds(300),
            expiresAt = t0.plus(Duration.ofHours(24)),
            micGranted = true,
            locationGranted = true,
            pinFailures = 2,
            pinFailuresReported = true,
            refractoryUntil = mapOf(Reason.DISTRESS_SCREAM to t0.plusSeconds(120), Reason.VIOLENCE_SOUND to t0.plusSeconds(60)),
            recentInputIds = listOf("id-1", "id,with,commas", "é")
        )
    )

    @Test fun everyReachableStateOfTheEngineRoundTrips() { // suites aléatoires d'événements : tout état produit se restaure à l'identique
        val r = Random(7)
        var state = EngineState()
        val policy = Policy()
        var now = t0
        repeat(3000) { n ->
            val input: Input = when (r.nextInt(8)) {
                0 -> Input.Arm("r$n")
                1 -> Input.Sos("r$n")
                2 -> Input.Disarm("r$n", Auth.NO_PIN)
                3 -> Input.ImSafe("r$n", Auth.NO_PIN)
                4 -> Input.TimerElapsed("r$n", Timer.entries[r.nextInt(3)])
                5 -> Input.PermissionsChanged("r$n", r.nextBoolean(), r.nextBoolean())
                6 -> Input.NeedHelp("r$n")
                else -> Input.SecretPhrase("r$n")
            }
            now = now.plusSeconds(r.nextLong(1, 400))
            state = AlertEngine.reduce(state, input, policy, now).state
            roundTrip(state)
        }
    }

    @Test fun garbageOrOtherVersionGivesNullInsteadOfCrashing() {
        assertNull(EngineStateCodec.decode(""))
        assertNull(EngineStateCodec.decode("n'importe quoi"))
        assertNull(EngineStateCodec.decode("v=999\nprotection=OFF"))
        assertNull(EngineStateCodec.decode("v=1\nprotection=PAS_UN_ETAT"))
        assertNotNull(EngineStateCodec.decode(EngineStateCodec.encode(EngineState())))
    }
}
