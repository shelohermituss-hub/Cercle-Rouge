package com.cerclerouge.app.protection

import com.cerclerouge.alert.AlertEngine
import com.cerclerouge.alert.Auth
import com.cerclerouge.alert.Effect
import com.cerclerouge.alert.EngineState
import com.cerclerouge.alert.EngineStateCodec
import com.cerclerouge.alert.Input
import com.cerclerouge.alert.LocationProfile
import com.cerclerouge.alert.Policy
import com.cerclerouge.alert.RejectReason
import com.cerclerouge.alert.Timer
import com.cerclerouge.app.account.AccountRepository
import com.cerclerouge.app.alert.AlertDispatcher
import com.cerclerouge.app.audio.AudioListener
import com.cerclerouge.app.audio.PhraseRepository
import com.cerclerouge.app.evidence.EvidenceRecorder
import com.cerclerouge.app.location.LocationTracker
import com.cerclerouge.audio.AudioSignal
import com.cerclerouge.location.TrackingProfile
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import com.cerclerouge.privacy.log.EventLog
import com.cerclerouge.privacy.pin.PinRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Événements ponctuels pour l'interface. */
sealed interface UiEvent {
    data object OpenEmergencyDialer : UiEvent

    data class Rejected(val reason: RejectReason) : UiEvent
}

/**
 * Adaptateur Android d'alert-engine : passe les intentions de l'utilisateur au moteur pur et exécute ses effets.
 *
 * S1 : l'état vit en mémoire et les minuteries sont des coroutines. Au Sprint 2, il passe dans le ProtectionService
 * (service de premier plan), l'état est persisté avant chaque effet (R-SM-01) et les envois partent par SMS.
 */
@Singleton
class ProtectionController @Inject constructor(
    private val account: AccountRepository,
    private val pins: PinRepository,
    private val log: EventLog,
    private val notifier: Notifier,
    private val clock: Clock,
    private val alarms: AlarmTimers,
    private val dispatcher: AlertDispatcher,
    private val tracker: LocationTracker,
    private val audio: AudioListener,
    private val phrases: PhraseRepository,
    private val recorder: EvidenceRecorder,
    db: PrivacyDatabase
) {
    private val settings = db.settings()
    private var restored = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val timers = mutableMapOf<Timer, Job>()
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    init {
        // Phrase secrète reconnue → urgence silencieuse (S5.06), jamais d'appel automatique aux secours (R-AUD-03).
        scope.launch {
            audio.signals.collect { sig ->
                if (sig.kind != AudioSignal.Kind.SECRET_PHRASE) return@collect
                // R-AUD-04 : détection inscrite au journal (score arrondi, version du modèle, aucune donnée audio envoyée).
                log.append(
                    "audio",
                    "secret_phrase confidence=${"%.2f".format(java.util.Locale.ROOT, sig.confidence)} model=${sig.modelVersion}"
                )
                dispatch(Input.SecretPhrase(UUID.randomUUID().toString()))
            }
        }
    }

    fun send(make: (id: String) -> Input) {
        scope.launch { dispatch(make(UUID.randomUUID().toString())) }
    }

    /** Appelé par une minuterie (coroutine ou alarme système). Identifiant fixe : les deux voies ne comptent qu'une fois. */
    suspend fun timerElapsed(timer: Timer, at: Instant) {
        dispatch(Input.TimerElapsed(timerInputId(timer, at), timer))
    }

    /**
     * Restaure l'état persisté après la mort du processus ou un redémarrage, puis le signale au moteur : une alerte rearme ses
     * minuteries, une protection armée devient « interrompue » (Android interdit de relancer micro et position en arrière-plan,
     * R-SM-08) avec une notification invitant à réarmer. Idempotent.
     */
    suspend fun ensureRestored() {
        mutex.withLock {
            if (restored) return
            restored = true
            settings.get(STATE_KEY)?.let(EngineStateCodec::decode)?.let { _state.value = it }
        }
        if (_state.value != EngineState()) dispatch(Input.ProcessRestored("restored-${clock.millis()}"))
    }

    /** Vérifie le PIN (si configuré) puis transmet le résultat au moteur. */
    suspend fun authFor(pin: String?): Auth = when {
        !pins.isConfigured() -> Auth.NO_PIN
        pin == null -> Auth.FAILED
        pins.verify(pin) == com.cerclerouge.privacy.pin.PinResult.Ok -> Auth.VERIFIED
        else -> Auth.FAILED
    }

    private suspend fun policy() = Policy(pinConfigured = pins.isConfigured(), protectionAllowed = account.protectionAllowed())

    private suspend fun dispatch(input: Input) {
        ensureRestored()
        val effects = mutex.withLock {
            val tr = AlertEngine.reduce(_state.value, input, policy(), clock.instant())
            // R-SM-01 / R-AL-02 : l'état est écrit AVANT l'exécution des effets. Si le processus meurt entre les deux,
            // la restauration rejoue les minuteries ; les envois sont idempotents (file d'attente).
            settings.put(SettingEntity(STATE_KEY, EngineStateCodec.encode(tr.state)))
            _state.value = tr.state
            tr.effects
        }
        effects.forEach { execute(it) }
    }

    private fun timerInputId(timer: Timer, at: Instant) = "timer-${timer.name}-${at.toEpochMilli()}"

    private suspend fun execute(effect: Effect) {
        when (effect) {
            is Effect.Log -> log.append("transition", effect.text)
            is Effect.Notice -> notifier.notice(effect.kind)
            is Effect.Vibrate -> notifier.vibrate(effect.pattern)
            is Effect.SetTimer -> {
                timers.remove(effect.timer)?.cancel()
                // Double sécurité (R-AL-03) : une coroutine, et une alarme système qui survit à la mise en veille (Doze).
                alarms.schedule(effect.timer, effect.at)
                timers[effect.timer] = scope.launch {
                    delay(Duration.between(clock.instant(), effect.at).toMillis().coerceAtLeast(0))
                    timerElapsed(effect.timer, effect.at)
                }
            }
            is Effect.CancelTimer -> {
                timers.remove(effect.timer)?.cancel()
                alarms.cancel(effect.timer)
            }
            is Effect.OpenEmergencyDialer -> _events.emit(UiEvent.OpenEmergencyDialer)
            is Effect.Rejected -> _events.emit(UiEvent.Rejected(effect.reason))
            // Envoi aux proches (S2–S3), moteurs micro et position (S2, S4, S5), preuves (S6) : journalisés en attendant.
            is Effect.Dispatch -> {
                log.append("dispatch", "${effect.kind} ${effect.alert?.id.orEmpty()}")
                // L'envoi (position jusqu'à 10 s, SMS, nouvelles tentatives) ne bloque ni le moteur ni les autres effets.
                scope.launch { dispatcher.onDispatch(effect.alert, effect.kind) }
            }
            is Effect.ConfigureEngines -> {
                log.append("engines", "audio=${effect.audio} location=${effect.location}")
                // Écoute : seulement avec une phrase secrète enregistrée (sans détecteur, le micro ne servirait à rien).
                val phrase = if (effect.audio) phrases.load() else null
                if (phrase != null) audio.start(phrase) else audio.stop()
                // Le suivi de position n'existe que dans les états qui l'autorisent (R-TRIP-01) : arrêté dans tous les autres.
                when (effect.location) {
                    LocationProfile.ARMED -> tracker.start(TrackingProfile.ARMED)
                    LocationProfile.TRIP -> tracker.start(TrackingProfile.TRIP)
                    LocationProfile.EMERGENCY -> tracker.start(TrackingProfile.EMERGENCY)
                    null -> tracker.stop()
                }
            }
            // Preuves (S6.01) : niveau 2 ou SOS seulement ; l'enregistreur respecte l'interrupteur de l'utilisateur.
            is Effect.StartRecording -> recorder.start(effect.alertId)
            is Effect.StopRecording -> recorder.stop(effect.alertId)
            is Effect.ShowVerification, Effect.DismissVerification
            -> Unit
        }
    }

    private companion object {
        const val STATE_KEY = "engine.state"
    }
}
