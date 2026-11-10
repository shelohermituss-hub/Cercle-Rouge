package com.cerclerouge.alert

import com.cerclerouge.domain.Alert
import com.cerclerouge.domain.AlertLevel
import com.cerclerouge.domain.Closure
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
import com.cerclerouge.domain.Trigger
import java.time.Duration
import java.time.Instant

/** Résultat de la vérification du PIN ou de la biométrie, faite par privacy-engine avant l'événement. */
enum class Auth { NO_PIN, VERIFIED, FAILED }

/** Politique d'alerte choisie par l'utilisateur, bornée par la spécification (R-ESC-04). */
data class Policy(
    val verifyDelay: Duration = Duration.ofSeconds(20),
    val escalationDelay: Duration = Duration.ofMinutes(5),
    val alertLifetime: Duration = Duration.ofHours(24),
    val refractoryPeriod: Duration = Duration.ofMinutes(2),
    val pinConfigured: Boolean = false,
    val autoEmergency: Boolean = false,
    /** Faux pour un 12–15 ans sans consentement confirmé d'un adulte référent (R-PAR-01). */
    val protectionAllowed: Boolean = true
) {
    init {
        require(verifyDelay in Duration.ofSeconds(10)..Duration.ofSeconds(30)) { "R-ESC-04 : vérification entre 10 et 30 s" }
        require(escalationDelay in Duration.ofMinutes(2)..Duration.ofMinutes(15)) { "R-ESC-04 : escalade entre 2 et 15 min" }
    }
}

/** Événements reçus par le moteur. `id` est unique : un événement rejoué est ignoré (R-SM-07). */
sealed interface Input {
    val id: String

    data class Arm(override val id: String) : Input

    data class Disarm(override val id: String, val auth: Auth) : Input

    data class StartTrip(override val id: String, val auth: Auth) : Input

    data class Arrived(override val id: String, val auth: Auth) : Input

    data class Sos(override val id: String) : Input

    data class SecretPhrase(override val id: String) : Input

    data class Detection(override val id: String, val action: RecommendedAction, val reason: Reason, val confidence: Confidence) : Input

    data class TripIssue(override val id: String, val reason: Reason) : Input

    data class ImSafe(override val id: String, val auth: Auth) : Input

    data class NeedHelp(override val id: String) : Input

    data class FalseAlarm(override val id: String, val auth: Auth) : Input

    data class TimerElapsed(override val id: String, val timer: Timer) : Input

    data class ContactAck(override val id: String) : Input

    data class PermissionsChanged(override val id: String, val mic: Boolean, val location: Boolean) : Input

    data class ProcessRestored(override val id: String) : Input

    data class CallEmergency(override val id: String) : Input
}

enum class Timer { VERIFY, ESCALATE, EXPIRY }

enum class LocationProfile { ARMED, TRIP, EMERGENCY }

enum class VibrationPattern { VERIFY, SENT, CLOSED }

/** Notifications locales, sur le téléphone de l'utilisateur. */
enum class NoticeKind { ALERT_SENT, EMERGENCY, PROTECTION_DOWNGRADED, PROTECTION_INTERRUPTED }

/** Messages destinés aux proches. Seuls les avis de fin peuvent partir hors d'une alerte. */
enum class DispatchKind(val allowedOutsideAlert: Boolean) {
    OPEN(false),
    ESCALATE(false),
    CONTACT_ACK(false),
    PIN_FAILURES(false),
    CLOSE_SAFE(true),
    CLOSE_FALSE_ALARM(true),
    CLOSE_EXPIRED(true),
    TRIP_ENDED(true),
    PROTECTION_DISABLED(true)
}

enum class RejectReason { CONSENT_REQUIRED, NO_SENSOR, PIN_REQUIRED, PIN_INVALID, LOCATION_REQUIRED }

/** Effets à exécuter par l'adaptateur Android, APRÈS avoir persisté le nouvel état (R-SM-01). */
sealed interface Effect {
    data class ConfigureEngines(val audio: Boolean, val location: LocationProfile?) : Effect

    data class Vibrate(val pattern: VibrationPattern) : Effect

    data class Notice(val kind: NoticeKind) : Effect

    data class ShowVerification(val deadline: Instant, val reason: Reason) : Effect

    data object DismissVerification : Effect

    data class SetTimer(val timer: Timer, val at: Instant) : Effect

    data class CancelTimer(val timer: Timer) : Effect

    data class StartRecording(val alertId: String) : Effect

    data class StopRecording(val alertId: String) : Effect

    data class Dispatch(val alert: Alert?, val kind: DispatchKind) : Effect

    data object OpenEmergencyDialer : Effect

    data class Rejected(val reason: RejectReason) : Effect

    data class Log(val text: String) : Effect
}

data class EngineState(
    val protection: ProtectionState = OFF,
    /** État armé auquel revenir à la clôture d'une alerte. */
    val base: ProtectionState = OFF,
    /** État d'avant le trajet, rétabli à l'arrivée. */
    val beforeTrip: ProtectionState = OFF,
    val alert: Alert? = null,
    val verifyAt: Instant? = null,
    val escalateAt: Instant? = null,
    val expiresAt: Instant? = null,
    val micGranted: Boolean = false,
    val locationGranted: Boolean = false,
    val pinFailures: Int = 0,
    val pinFailuresReported: Boolean = false,
    val refractoryUntil: Map<Reason, Instant> = emptyMap(),
    val recentInputIds: List<String> = emptyList()
)

data class Transition(val state: EngineState, val effects: List<Effect>)

/**
 * Machine à états d'alerte : fonction pure et déterministe (R-SM-03, R-AL-01).
 * Référence : docs/01-etats-et-escalade.md et docs/modules/alert-engine.md.
 */
object AlertEngine {
    private const val PIN_FAILURES_BEFORE_REPORT = 5
    private const val REMEMBERED_INPUTS = 64

    fun reduce(state: EngineState, input: Input, policy: Policy, now: Instant): Transition {
        if (input.id in state.recentInputIds) return Transition(state, emptyList()) // R-SM-07
        val tracked = state.copy(recentInputIds = (state.recentInputIds + input.id).takeLast(REMEMBERED_INPUTS))
        val (next, effects) = Step(tracked, policy, now).apply(input)
        val log = Effect.Log("${state.protection} → ${next.protection} : ${input::class.simpleName}") // R-SM-02
        return Transition(next, listOf(log) + effects)
    }

    private class Step(val s: EngineState, val policy: Policy, val now: Instant) {
        val effects = mutableListOf<Effect>()

        fun apply(input: Input): Pair<EngineState, List<Effect>> {
            val next = when (input) {
                is Input.Arm -> arm()
                is Input.Disarm -> if (s.protection.isAlert) {
                    closeByUser(
                        input.auth,
                        Closure.USER_SAFE,
                        toOff = true
                    )
                } else {
                    disarm(input.auth)
                }
                is Input.StartTrip -> startTrip(input.auth)
                is Input.Arrived -> arrived(input.auth)
                is Input.Sos -> emergency(Trigger.SOS, silent = false, newAlertId = input.id)
                is Input.SecretPhrase -> if (s.protection ==
                    OFF
                ) {
                    s
                } else {
                    emergency(Trigger.SECRET_PHRASE, silent = true, newAlertId = input.id)
                }
                is Input.Detection -> detection(input)
                is Input.TripIssue -> if (s.protection ==
                    TRIP_ACTIVE
                ) {
                    openOrIgnore(input.id, Trigger.TRIP, input.reason, Confidence.MEDIUM)
                } else {
                    s
                }
                is Input.ImSafe -> if (s.protection.isAlert) closeByUser(input.auth, Closure.USER_SAFE, toOff = false) else s
                is Input.FalseAlarm -> if (s.protection.isAlert) closeByUser(input.auth, Closure.FALSE_ALARM, toOff = false) else s
                is Input.NeedHelp -> if (s.protection.isAlert) emergency(Trigger.SOS, silent = false, newAlertId = input.id) else s
                is Input.TimerElapsed -> timer(input.timer)
                is Input.ContactAck -> contactAck()
                is Input.PermissionsChanged -> permissions(input.mic, input.location)
                is Input.ProcessRestored -> restored()
                is Input.CallEmergency -> s.also { effects += Effect.OpenEmergencyDialer } // R-AL-04 : action de l'utilisateur
            }
            if (engines(next) != engines(s)) effects.add(0, engines(next))
            return next to effects
        }

        // --- Armement et trajet ---

        private fun arm(): EngineState = when {
            s.protection != OFF -> s
            !policy.protectionAllowed -> reject(RejectReason.CONSENT_REQUIRED)
            !s.micGranted && !s.locationGranted -> reject(RejectReason.NO_SENSOR)
            else -> s.copy(protection = armedState(s.locationGranted))
        }

        private fun disarm(auth: Auth): EngineState {
            if (s.protection == OFF) return s
            if (!authorized(auth)) return reject(RejectReason.PIN_INVALID)
            if (s.protection == TRIP_ACTIVE) dispatch(null, DispatchKind.PROTECTION_DISABLED) // R-SM-06
            return s.copy(protection = OFF, base = OFF, beforeTrip = OFF)
        }

        private fun startTrip(auth: Auth): EngineState = when {
            s.protection.isAlert || s.protection == TRIP_ACTIVE -> s
            !policy.protectionAllowed -> reject(RejectReason.CONSENT_REQUIRED)
            !policy.pinConfigured || auth != Auth.VERIFIED -> reject(RejectReason.PIN_REQUIRED) // ADR-0015
            !s.locationGranted -> reject(RejectReason.LOCATION_REQUIRED)
            else -> s.copy(protection = TRIP_ACTIVE, beforeTrip = s.protection)
        }

        private fun arrived(auth: Auth): EngineState {
            if (s.protection != TRIP_ACTIVE) return s
            if (!authorized(auth)) return reject(RejectReason.PIN_INVALID)
            dispatch(null, DispatchKind.TRIP_ENDED)
            return s.copy(protection = s.beforeTrip, beforeTrip = OFF)
        }

        // --- Détection et escalade ---

        private fun detection(input: Input.Detection): EngineState = when (input.action) {
            RecommendedAction.NONE -> s
            RecommendedAction.LOG -> s // R-AUD-04 : journal local seulement (l'entrée Log est ajoutée par reduce)
            RecommendedAction.VERIFY, RecommendedAction.ALERT_CONTACTS -> when (s.protection) {
                ALERT_SENT -> emergency(Trigger.AUTOMATIC, silent = false, newAlertId = input.id) // nouveau signal au niveau 2
                ARMED_AUDIO_ONLY, ARMED_FULL, TRIP_ACTIVE -> {
                    val direct = input.action == RecommendedAction.ALERT_CONTACTS && policy.autoEmergency
                    openOrIgnore(input.id, Trigger.AUTOMATIC, input.reason, input.confidence, direct)
                }
                else -> s
            }
        }

        private fun openOrIgnore(
            id: String,
            trigger: Trigger,
            reason: Reason,
            confidence: Confidence,
            direct: Boolean = false
        ): EngineState {
            val refractory = s.refractoryUntil[reason]
            if (refractory != null && now < refractory) return s
            val alert = Alert(id, AlertLevel.VERIFICATION, trigger, reason, confidence, silent = false, openedAt = now)
            val expiresAt = now + policy.alertLifetime
            effects += Effect.SetTimer(Timer.EXPIRY, expiresAt)
            val pending = s.copy(
                protection = ALERT_PENDING,
                base = s.protection,
                alert = alert,
                expiresAt = expiresAt,
                pinFailures = 0,
                pinFailuresReported = false
            )
            if (direct) {
                val step = Step(pending, policy, now)
                return step.toContacts(fromPending = false).also { effects += step.effects }
            }
            val verifyAt = now + policy.verifyDelay
            effects += Effect.Vibrate(VibrationPattern.VERIFY)
            effects += Effect.ShowVerification(verifyAt, reason)
            effects += Effect.SetTimer(Timer.VERIFY, verifyAt)
            return pending.copy(verifyAt = verifyAt)
        }

        /** Niveau 2 : les proches sont prévenus. */
        fun toContacts(fromPending: Boolean): EngineState {
            val alert = checkNotNull(s.alert).copy(level = AlertLevel.CONTACTS)
            if (fromPending) effects += Effect.DismissVerification
            val escalateAt = now + policy.escalationDelay
            dispatch(alert, DispatchKind.OPEN)
            effects += Effect.StartRecording(alert.id)
            effects += Effect.SetTimer(Timer.ESCALATE, escalateAt)
            if (!alert.silent) {
                effects += Effect.Vibrate(VibrationPattern.SENT)
                effects += Effect.Notice(NoticeKind.ALERT_SENT)
            }
            return s.copy(protection = ALERT_SENT, alert = alert, verifyAt = null, escalateAt = escalateAt)
        }

        /** Niveau 3 : SOS, phrase secrète, « J'ai besoin d'aide », escalade ou nouveau signal. */
        private fun emergency(trigger: Trigger, silent: Boolean, newAlertId: String): EngineState {
            val current = s.alert
            if (s.protection == EMERGENCY_MODE) return s
            val alert = current?.copy(level = AlertLevel.EXTENDED, silent = current.silent || silent)
                ?: Alert(newAlertId, AlertLevel.EXTENDED, trigger, Reason.SOS, Confidence.HIGH, silent, openedAt = now)
            var expiresAt = s.expiresAt
            when (s.protection) {
                ALERT_PENDING -> {
                    effects += Effect.DismissVerification
                    effects += Effect.CancelTimer(Timer.VERIFY)
                    dispatch(alert, DispatchKind.OPEN)
                    effects += Effect.StartRecording(alert.id)
                }
                ALERT_SENT -> {
                    effects += Effect.CancelTimer(Timer.ESCALATE)
                    dispatch(alert, DispatchKind.ESCALATE)
                }
                else -> {
                    expiresAt = now + policy.alertLifetime
                    dispatch(alert, DispatchKind.OPEN)
                    effects += Effect.StartRecording(alert.id)
                    effects += Effect.SetTimer(Timer.EXPIRY, expiresAt)
                }
            }
            if (!alert.silent) {
                effects += Effect.Vibrate(VibrationPattern.SENT)
                effects += Effect.Notice(NoticeKind.EMERGENCY)
            }
            val base = if (s.protection.isAlert) s.base else s.protection
            val fresh = current == null
            return s.copy(
                protection = EMERGENCY_MODE,
                base = base,
                alert = alert,
                verifyAt = null,
                escalateAt = null,
                expiresAt = expiresAt,
                pinFailures = if (fresh) 0 else s.pinFailures,
                pinFailuresReported = if (fresh) false else s.pinFailuresReported
            )
        }

        private fun timer(timer: Timer): EngineState {
            fun due(at: Instant?) = at != null && now >= at
            return when {
                timer == Timer.VERIFY && s.protection == ALERT_PENDING && due(s.verifyAt) -> toContacts(fromPending = true)
                timer == Timer.ESCALATE && s.protection == ALERT_SENT && due(s.escalateAt) ->
                    emergency(Trigger.AUTOMATIC, silent = false, newAlertId = "")
                timer == Timer.EXPIRY && s.protection.isAlert && due(s.expiresAt) -> close(Closure.EXPIRED, toOff = false)
                else -> s // minuterie périmée ou annulée
            }
        }

        private fun contactAck(): EngineState {
            if (s.protection != ALERT_SENT && s.protection != EMERGENCY_MODE) return s
            if (s.escalateAt != null) effects += Effect.CancelTimer(Timer.ESCALATE)
            dispatch(s.alert, DispatchKind.CONTACT_ACK)
            return s.copy(escalateAt = null)
        }

        // --- Clôture ---

        private fun closeByUser(auth: Auth, closure: Closure, toOff: Boolean): EngineState {
            if (authorized(auth)) return close(closure, toOff)
            val failures = s.pinFailures + if (auth == Auth.FAILED) 1 else 0
            var reported = s.pinFailuresReported
            if (failures >= PIN_FAILURES_BEFORE_REPORT && !reported) {
                dispatch(s.alert, DispatchKind.PIN_FAILURES) // R-SEC-02
                reported = true
            }
            effects += Effect.Rejected(RejectReason.PIN_INVALID)
            return s.copy(pinFailures = failures, pinFailuresReported = reported)
        }

        private fun close(closure: Closure, toOff: Boolean): EngineState {
            val alert = checkNotNull(s.alert)
            if (s.protection == ALERT_PENDING) effects += Effect.DismissVerification
            if (s.verifyAt != null) effects += Effect.CancelTimer(Timer.VERIFY)
            if (s.escalateAt != null) effects += Effect.CancelTimer(Timer.ESCALATE)
            if (s.expiresAt != null && closure != Closure.EXPIRED) effects += Effect.CancelTimer(Timer.EXPIRY)
            if (alert.level.value >= AlertLevel.CONTACTS.value) {
                effects += Effect.StopRecording(alert.id)
                val kind = when (closure) {
                    Closure.USER_SAFE -> DispatchKind.CLOSE_SAFE
                    Closure.FALSE_ALARM -> DispatchKind.CLOSE_FALSE_ALARM
                    Closure.EXPIRED -> DispatchKind.CLOSE_EXPIRED
                }
                dispatch(alert, kind) // R-ALR-03
            }
            if (!alert.silent) effects += Effect.Vibrate(VibrationPattern.CLOSED)
            val refractory = if (closure ==
                Closure.EXPIRED
            ) {
                s.refractoryUntil
            } else {
                s.refractoryUntil + (alert.reason to now + policy.refractoryPeriod)
            }
            val target = if (toOff) OFF else validArmedState(s.base)
            return s.copy(
                protection = target,
                base = OFF,
                beforeTrip = if (target == TRIP_ACTIVE) s.beforeTrip else OFF,
                alert = null,
                verifyAt = null,
                escalateAt = null,
                expiresAt = null,
                pinFailures = 0,
                pinFailuresReported = false,
                refractoryUntil = refractory
            )
        }

        // --- Permissions et redémarrage ---

        private fun permissions(mic: Boolean, location: Boolean): EngineState {
            val updated = s.copy(micGranted = mic, locationGranted = location)
            return when (s.protection) {
                ARMED_AUDIO_ONLY, ARMED_FULL -> {
                    val target = if (!mic && !location) OFF else armedState(location)
                    if (target == OFF || (s.protection == ARMED_FULL && target == ARMED_AUDIO_ONLY)) {
                        effects += Effect.Notice(NoticeKind.PROTECTION_DOWNGRADED) // R-PE-01
                    }
                    updated.copy(protection = target)
                }
                TRIP_ACTIVE -> updated.also { if (!location) effects += Effect.Notice(NoticeKind.PROTECTION_DOWNGRADED) }
                else -> updated
            }
        }

        private fun restored(): EngineState = when {
            s.protection.isArmed -> {
                // R-SM-08 : Android interdit de relancer micro et position depuis l'arrière-plan.
                effects += Effect.Notice(NoticeKind.PROTECTION_INTERRUPTED)
                s.copy(protection = OFF, base = OFF, beforeTrip = OFF)
            }
            s.protection.isAlert -> s.also {
                s.verifyAt?.let { effects += Effect.SetTimer(Timer.VERIFY, it) }
                s.escalateAt?.let { effects += Effect.SetTimer(Timer.ESCALATE, it) }
                s.expiresAt?.let { effects += Effect.SetTimer(Timer.EXPIRY, it) }
            }
            else -> s
        }

        // --- Utilitaires ---

        private fun authorized(auth: Auth) = if (policy.pinConfigured) auth == Auth.VERIFIED else auth != Auth.FAILED

        private fun armedState(location: Boolean) = if (location) ARMED_FULL else ARMED_AUDIO_ONLY

        private fun validArmedState(state: ProtectionState): ProtectionState = when {
            !state.isArmed -> OFF
            state == TRIP_ACTIVE -> if (s.locationGranted) TRIP_ACTIVE else OFF
            !s.micGranted && !s.locationGranted -> OFF
            else -> armedState(s.locationGranted)
        }

        private fun reject(reason: RejectReason): EngineState = s.also { effects += Effect.Rejected(reason) }

        private fun dispatch(alert: Alert?, kind: DispatchKind) {
            effects += Effect.Dispatch(alert, kind)
        }
    }

    /** Moteurs nécessaires dans un état donné : micro et position seulement quand l'état l'autorise (P1, P4). */
    fun engines(state: EngineState): Effect.ConfigureEngines {
        val p = state.protection
        val audio = state.micGranted && p != OFF
        val location = when {
            !state.locationGranted -> null
            p == ARMED_FULL -> LocationProfile.ARMED
            p == TRIP_ACTIVE -> LocationProfile.TRIP
            p.isAlert -> LocationProfile.EMERGENCY
            else -> null
        }
        return Effect.ConfigureEngines(audio, location)
    }
}
