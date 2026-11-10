package com.cerclerouge.alert

import com.cerclerouge.domain.AlertLevel
import com.cerclerouge.domain.Confidence
import com.cerclerouge.domain.ProtectionState
import com.cerclerouge.domain.ProtectionState.ALERT_PENDING
import com.cerclerouge.domain.ProtectionState.ALERT_SENT
import com.cerclerouge.domain.ProtectionState.ARMED_AUDIO_ONLY
import com.cerclerouge.domain.ProtectionState.ARMED_FULL
import com.cerclerouge.domain.ProtectionState.EMERGENCY_MODE
import com.cerclerouge.domain.ProtectionState.OFF
import com.cerclerouge.domain.ProtectionState.TRIP_ACTIVE
import com.cerclerouge.domain.Reason
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Table des transitions de docs/01-etats-et-escalade.md. Chaque test cite ses exigences. */
class AlertEngineTest {
    private val t0 = Instant.parse("2026-11-10T22:00:00Z")
    private val policy = Policy()
    private var seq = 0

    private fun id() = "e${++seq}"

    private fun run(start: EngineState, vararg inputs: (String) -> Input, p: Policy = policy, at: Instant = t0): Transition {
        var tr = Transition(start, emptyList())
        inputs.forEach { make -> tr = AlertEngine.reduce(tr.state, make(id()), p, at) }
        return tr
    }

    private val permitted = EngineState(micGranted = true, locationGranted = true)
    private val armedFull = run(permitted, { Input.Arm(it) }).state
    private val pinPolicy = Policy(pinConfigured = true)

    // --- Armement ---

    @Test fun `R-SM arm avec micro et position mene a ARMED_FULL`() {
        val tr = run(permitted, { Input.Arm(it) })
        assertEquals(ARMED_FULL, tr.state.protection)
        assertTrue(Effect.ConfigureEngines(audio = true, location = LocationProfile.ARMED) in tr.effects)
    }

    @Test fun `R-ONB-01 arm sans position mene a ARMED_AUDIO_ONLY`() {
        val tr = run(permitted.copy(locationGranted = false), { Input.Arm(it) })
        assertEquals(ARMED_AUDIO_ONLY, tr.state.protection)
        assertTrue(Effect.ConfigureEngines(audio = true, location = null) in tr.effects)
    }

    @Test fun `arm sans micro ni position est refuse`() {
        val tr = run(EngineState(), { Input.Arm(it) })
        assertEquals(OFF, tr.state.protection)
        assertTrue(tr.effects.any { it is Effect.Rejected })
    }

    @Test fun `R-PAR-01 mineur sans consentement ne peut pas armer`() {
        val tr = run(permitted, { Input.Arm(it) }, p = Policy(protectionAllowed = false))
        assertEquals(OFF, tr.state.protection)
        assertTrue(Effect.Rejected(RejectReason.CONSENT_REQUIRED) in tr.effects)
    }

    @Test fun `R-SM-04 desarmer exige le PIN quand il existe`() {
        val refused = run(armedFull, { Input.Disarm(it, Auth.FAILED) }, p = pinPolicy)
        assertEquals(ARMED_FULL, refused.state.protection)
        val ok = run(armedFull, { Input.Disarm(it, Auth.VERIFIED) }, p = pinPolicy)
        assertEquals(OFF, ok.state.protection)
        assertTrue(Effect.ConfigureEngines(audio = false, location = null) in ok.effects)
    }

    @Test fun `R-SEC-01 sans PIN desarmer hors trajet se fait en un geste`() {
        assertEquals(OFF, run(armedFull, { Input.Disarm(it, Auth.NO_PIN) }).state.protection)
    }

    // --- Trajet ---

    @Test fun `ADR-0015 demarrer un trajet exige un PIN configure et verifie`() {
        assertEquals(Effect.Rejected(RejectReason.PIN_REQUIRED), run(permitted, { Input.StartTrip(it, Auth.NO_PIN) }).effects.last())
        val ok = run(permitted, { Input.StartTrip(it, Auth.VERIFIED) }, p = pinPolicy)
        assertEquals(TRIP_ACTIVE, ok.state.protection)
        assertTrue(Effect.ConfigureEngines(audio = true, location = LocationProfile.TRIP) in ok.effects)
    }

    @Test fun `un trajet exige la position`() {
        val tr = run(permitted.copy(locationGranted = false), { Input.StartTrip(it, Auth.VERIFIED) }, p = pinPolicy)
        assertEquals(Effect.Rejected(RejectReason.LOCATION_REQUIRED), tr.effects.last())
    }

    @Test fun `arrivee avec PIN revient a l etat d avant le trajet et previent les proches`() {
        val tr = run(armedFull, { Input.StartTrip(it, Auth.VERIFIED) }, { Input.Arrived(it, Auth.VERIFIED) }, p = pinPolicy)
        assertEquals(ARMED_FULL, tr.state.protection)
        assertTrue(tr.effects.any { it is Effect.Dispatch && it.kind == DispatchKind.TRIP_ENDED })
    }

    @Test fun `R-SM-06 desarmer pendant un trajet previent les proches`() {
        val tr = run(permitted, { Input.StartTrip(it, Auth.VERIFIED) }, { Input.Disarm(it, Auth.VERIFIED) }, p = pinPolicy)
        assertEquals(OFF, tr.state.protection)
        assertTrue(tr.effects.any { it is Effect.Dispatch && it.kind == DispatchKind.PROTECTION_DISABLED })
    }

    @Test fun `R-TRIP-04 un evenement de trajet ne produit qu une verification`() {
        val tr = run(permitted, { Input.StartTrip(it, Auth.VERIFIED) }, { Input.TripIssue(it, Reason.TRIP_DEVIATION) }, p = pinPolicy)
        assertEquals(ALERT_PENDING, tr.state.protection)
        assertEquals(AlertLevel.VERIFICATION, tr.state.alert?.level)
    }

    // --- Vérification et escalade ---

    @Test fun `R-ESC detection VERIFY ouvre une verification avec delai`() {
        val tr = run(armedFull, { Input.Detection(it, RecommendedAction.VERIFY, Reason.DISTRESS_KEYWORD, Confidence.HIGH) })
        assertEquals(ALERT_PENDING, tr.state.protection)
        assertEquals(t0 + policy.verifyDelay, tr.state.verifyAt)
        assertTrue(Effect.SetTimer(Timer.VERIFY, t0 + policy.verifyDelay) in tr.effects)
        assertTrue(Effect.Vibrate(VibrationPattern.VERIFY) in tr.effects)
        assertFalse(tr.effects.any { it is Effect.Dispatch }) // R-ESC : rien ne part avant le niveau 2
    }

    @Test fun `R-AUD-04 une detection LOG reste locale`() {
        val tr = run(armedFull, { Input.Detection(it, RecommendedAction.LOG, Reason.DISTRESS_SCREAM, Confidence.LOW) })
        assertEquals(ARMED_FULL, tr.state.protection)
        assertFalse(tr.effects.any { it is Effect.Dispatch })
        assertTrue(tr.effects.any { it is Effect.Log })
    }

    @Test fun `sans reponse au delai la verification passe au niveau 2`() {
        val pending = run(armedFull, { Input.Detection(it, RecommendedAction.VERIFY, Reason.DISTRESS_SCREAM, Confidence.MEDIUM) }).state
        val tr = run(pending, { Input.TimerElapsed(it, Timer.VERIFY) }, at = t0 + policy.verifyDelay)
        assertEquals(ALERT_SENT, tr.state.protection)
        assertEquals(AlertLevel.CONTACTS, tr.state.alert?.level)
        assertTrue(tr.effects.any { it is Effect.Dispatch && it.kind == DispatchKind.OPEN })
        assertTrue(tr.effects.any { it is Effect.StartRecording })
        assertTrue(Effect.SetTimer(Timer.ESCALATE, t0 + policy.verifyDelay + policy.escalationDelay) in tr.effects)
    }

    @Test fun `une minuterie perimee est ignoree`() {
        val pending = run(armedFull, { Input.Detection(it, RecommendedAction.VERIFY, Reason.DISTRESS_SCREAM, Confidence.MEDIUM) }).state
        val tr = run(pending, { Input.TimerElapsed(it, Timer.VERIFY) }, at = t0 + Duration.ofSeconds(5))
        assertEquals(ALERT_PENDING, tr.state.protection)
    }

    @Test fun `je vais bien pendant la verification annule sans rien envoyer`() {
        val pending = run(armedFull, { Input.Detection(it, RecommendedAction.VERIFY, Reason.DISTRESS_SCREAM, Confidence.MEDIUM) }).state
        val tr = run(pending, { Input.ImSafe(it, Auth.NO_PIN) })
        assertEquals(ARMED_FULL, tr.state.protection)
        assertNull(tr.state.alert)
        assertTrue(Effect.CancelTimer(Timer.VERIFY) in tr.effects)
        assertFalse(tr.effects.any { it is Effect.Dispatch })
    }

    @Test fun `periode refractaire apres une fausse alerte pour la meme raison`() {
        val pending = run(armedFull, { Input.Detection(it, RecommendedAction.VERIFY, Reason.DISTRESS_SCREAM, Confidence.MEDIUM) }).state
        val safe = run(pending, { Input.ImSafe(it, Auth.NO_PIN) }).state
        val again = run(
            safe,
            { Input.Detection(it, RecommendedAction.VERIFY, Reason.DISTRESS_SCREAM, Confidence.MEDIUM) },
            at =
            t0 + Duration.ofSeconds(30)
        )
        assertEquals(ARMED_FULL, again.state.protection)
        val later = run(
            safe,
            { Input.Detection(it, RecommendedAction.VERIFY, Reason.DISTRESS_SCREAM, Confidence.MEDIUM) },
            at =
            t0 + Duration.ofMinutes(3)
        )
        assertEquals(ALERT_PENDING, later.state.protection)
    }

    @Test fun `j ai besoin d aide passe en mode urgence`() {
        val pending = run(armedFull, { Input.Detection(it, RecommendedAction.VERIFY, Reason.DISTRESS_SCREAM, Confidence.MEDIUM) }).state
        val tr = run(pending, { Input.NeedHelp(it) })
        assertEquals(EMERGENCY_MODE, tr.state.protection)
        assertEquals(AlertLevel.EXTENDED, tr.state.alert?.level)
    }

    @Test fun `R-ESC-04 sans accuse de reception le niveau 2 escalade au niveau 3`() {
        val sent = run(armedFull, {
            Input.Detection(it, RecommendedAction.ALERT_CONTACTS, Reason.DISTRESS_SCREAM, Confidence.HIGH)
        }, p = Policy(autoEmergency = true)).state
        assertEquals(ALERT_SENT, sent.protection)
        val tr = run(sent, { Input.TimerElapsed(it, Timer.ESCALATE) }, at = t0 + policy.escalationDelay)
        assertEquals(EMERGENCY_MODE, tr.state.protection)
        assertEquals(AlertLevel.EXTENDED, tr.state.alert?.level)
        assertTrue(tr.effects.any { it is Effect.Dispatch && it.kind == DispatchKind.ESCALATE })
    }

    @Test fun `R-ESC-03 j ai pris contact arrete l escalade sans clore l alerte`() {
        val sent = run(armedFull, {
            Input.Detection(it, RecommendedAction.ALERT_CONTACTS, Reason.DISTRESS_SCREAM, Confidence.HIGH)
        }, p = Policy(autoEmergency = true)).state
        val tr = run(sent, { Input.ContactAck(it) })
        assertEquals(ALERT_SENT, tr.state.protection)
        assertNull(tr.state.escalateAt)
        assertTrue(Effect.CancelTimer(Timer.ESCALATE) in tr.effects)
    }

    @Test fun `sans urgence automatique ALERT_CONTACTS devient une verification`() {
        val tr = run(armedFull, { Input.Detection(it, RecommendedAction.ALERT_CONTACTS, Reason.DISTRESS_SCREAM, Confidence.HIGH) })
        assertEquals(ALERT_PENDING, tr.state.protection)
    }

    @Test fun `nouveau signal au niveau 2 escalade au niveau 3`() {
        val sent = run(armedFull, {
            Input.Detection(it, RecommendedAction.ALERT_CONTACTS, Reason.DISTRESS_SCREAM, Confidence.HIGH)
        }, p = Policy(autoEmergency = true)).state
        val tr = run(sent, { Input.Detection(it, RecommendedAction.VERIFY, Reason.VIOLENCE_SOUND, Confidence.MEDIUM) })
        assertEquals(EMERGENCY_MODE, tr.state.protection)
    }

    @Test fun `fausse alerte avec PIN clot et previent les proches`() {
        val sent = run(armedFull, { Input.Sos(it) }).state
        val tr = run(sent, { Input.FalseAlarm(it, Auth.VERIFIED) }, p = pinPolicy)
        assertEquals(ARMED_FULL, tr.state.protection)
        assertTrue(tr.effects.any { it is Effect.Dispatch && it.kind == DispatchKind.CLOSE_FALSE_ALARM })
        assertTrue(tr.effects.any { it is Effect.StopRecording })
    }

    @Test fun `R-SEC-02 cinq PIN errones pendant une alerte previennent les proches une fois`() {
        var s = run(armedFull, { Input.Sos(it) }).state
        val dispatches = mutableListOf<Effect>()
        repeat(6) {
            val tr = run(s, { Input.ImSafe(it, Auth.FAILED) }, p = pinPolicy)
            s = tr.state
            dispatches += tr.effects.filter { it is Effect.Dispatch && it.kind == DispatchKind.PIN_FAILURES }
        }
        assertEquals(EMERGENCY_MODE, s.protection)
        assertEquals(1, dispatches.size)
    }

    @Test fun `R-ESC-06 une alerte expire apres 24 h`() {
        val sent = run(armedFull, { Input.Sos(it) }).state
        val tr = run(sent, { Input.TimerElapsed(it, Timer.EXPIRY) }, at = t0 + policy.alertLifetime)
        assertEquals(ARMED_FULL, tr.state.protection)
        assertTrue(tr.effects.any { it is Effect.Dispatch && it.kind == DispatchKind.CLOSE_EXPIRED })
    }

    // --- SOS et phrase secrète ---

    @Test fun `R-SM-05 SOS depuis OFF passe au niveau 3`() {
        val tr = run(EngineState(), { Input.Sos(it) })
        assertEquals(EMERGENCY_MODE, tr.state.protection)
        assertEquals(AlertLevel.EXTENDED, tr.state.alert?.level)
        assertTrue(tr.effects.any { it is Effect.Dispatch && it.kind == DispatchKind.OPEN })
    }

    @Test fun `R-PAR-01 le SOS reste possible pour un mineur sans consentement`() {
        val tr = run(EngineState(), { Input.Sos(it) }, p = Policy(protectionAllowed = false))
        assertEquals(EMERGENCY_MODE, tr.state.protection)
    }

    @Test fun `R-AL-05 la phrase secrete declenche une alerte silencieuse`() {
        val tr = run(armedFull, { Input.SecretPhrase(it) })
        assertEquals(EMERGENCY_MODE, tr.state.protection)
        assertEquals(true, tr.state.alert?.silent)
        assertFalse(tr.effects.any { it is Effect.Vibrate || it is Effect.Notice || it is Effect.ShowVerification })
    }

    // --- Robustesse ---

    @Test fun `R-SM-07 un evenement deja traite est ignore`() {
        val first = AlertEngine.reduce(armedFull, Input.Sos("same"), policy, t0)
        val second = AlertEngine.reduce(first.state, Input.Sos("same"), policy, t0)
        assertEquals(first.state, second.state)
        assertTrue(second.effects.isEmpty())
    }

    @Test fun `R-SM-02 chaque transition est journalisee`() {
        val tr = run(permitted, { Input.Arm(it) })
        assertTrue(tr.effects.any { it is Effect.Log })
    }

    @Test fun `R-PE-01 retirer le micro d ARMED_AUDIO_ONLY desarme et previent`() {
        val armedAudio = run(permitted.copy(locationGranted = false), { Input.Arm(it) }).state
        val tr = run(armedAudio, { Input.PermissionsChanged(it, mic = false, location = false) })
        assertEquals(OFF, tr.state.protection)
        assertTrue(tr.effects.any { it is Effect.Notice && it.kind == NoticeKind.PROTECTION_DOWNGRADED })
    }

    @Test fun `accorder la position en ARMED_AUDIO_ONLY passe en ARMED_FULL`() {
        val armedAudio = run(permitted.copy(locationGranted = false), { Input.Arm(it) }).state
        val tr = run(armedAudio, { Input.PermissionsChanged(it, mic = true, location = true) })
        assertEquals(ARMED_FULL, tr.state.protection)
    }

    @Test fun `R-SM-08 apres la mort du processus la protection armee doit etre rearmee`() {
        val tr = run(armedFull, { Input.ProcessRestored(it) })
        assertEquals(OFF, tr.state.protection)
        assertTrue(tr.effects.any { it is Effect.Notice && it.kind == NoticeKind.PROTECTION_INTERRUPTED })
    }

    @Test fun `R-SM-08 une alerte ouverte reprend ses minuteries apres la mort du processus`() {
        val sent = run(armedFull, { Input.Sos(it) }).state
        val tr = run(sent, { Input.ProcessRestored(it) })
        assertEquals(EMERGENCY_MODE, tr.state.protection)
        assertTrue(tr.effects.any { it is Effect.SetTimer && it.timer == Timer.EXPIRY })
    }

    @Test fun `R-AL-04 le composeur des secours ne s ouvre que sur action de l utilisateur`() {
        val tr = run(armedFull, { Input.CallEmergency(it) })
        assertTrue(Effect.OpenEmergencyDialer in tr.effects)
        assertEquals(ProtectionState.ARMED_FULL, tr.state.protection)
    }
}
