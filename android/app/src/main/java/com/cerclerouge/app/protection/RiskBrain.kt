package com.cerclerouge.app.protection

import com.cerclerouge.alert.Component
import com.cerclerouge.alert.DeciderState
import com.cerclerouge.alert.Decision
import com.cerclerouge.alert.DecisionContext
import com.cerclerouge.alert.Input
import com.cerclerouge.alert.Observation
import com.cerclerouge.alert.RecommendedAction
import com.cerclerouge.alert.RiskDecider
import com.cerclerouge.app.account.AccountRepository
import com.cerclerouge.app.device.DeviceMonitor
import com.cerclerouge.app.trip.TripController
import com.cerclerouge.brain.GuardState
import com.cerclerouge.brain.SituationContext
import com.cerclerouge.brain.SituationEvent
import com.cerclerouge.brain.SoundFamilies
import com.cerclerouge.brain.SoundFamily
import com.cerclerouge.contacts.ContactStatus
import com.cerclerouge.domain.AgeBand
import com.cerclerouge.domain.Confidence
import com.cerclerouge.domain.ProtectionState
import com.cerclerouge.domain.Reason
import com.cerclerouge.domain.VulnerabilityProfile
import com.cerclerouge.domain.profile
import com.cerclerouge.location.Geo
import com.cerclerouge.location.TripEvent
import com.cerclerouge.location.TripEventType
import com.cerclerouge.privacy.log.EventLog
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Le cerveau de l'application dans le téléphone (PDF 8.2, 8.5 ; S5.08) : reçoit les observations de tous les détecteurs
 * (trajet, réseau, capteurs, écoute), les fusionne avec [RiskDecider] toutes les 2 s, inscrit chaque décision au journal avec
 * ses composantes (R-AL-06) et transmet au moteur une vérification ou une alerte (`Input.Detection`). Il ne décide jamais
 * d'appeler les secours (R-AUD-03). Il ne tourne que protection active ; tout est oublié à l'arrêt.
 */
@Singleton
class RiskBrain @Inject constructor(
    private val controller: ProtectionController,
    private val trips: TripController,
    private val monitor: DeviceMonitor,
    private val account: AccountRepository,
    private val listen: ListenSettings,
    private val motion: com.cerclerouge.app.device.MotionSensors,
    private val audio: com.cerclerouge.app.audio.AudioListener,
    private val log: EventLog,
    private val clock: Clock,
    private val contacts: com.cerclerouge.app.contacts.ContactsRepository,
    private val gemma: com.cerclerouge.app.brain.GemmaReasoner
) {
    // Règles d'abord ; le modèle de langage (s'il est installé et permis) ne peut qu'ajouter (ADR-0029).
    private val brain = BrainOrchestrator(com.cerclerouge.brain.CombinedReasoner(com.cerclerouge.brain.RuleReasoner, gemma))
    private val _thought = MutableStateFlow<com.cerclerouge.brain.Thought?>(null)

    /** Dernière réflexion du cerveau au-dessus du plancher (explication du visualiseur « Veilleur »), null sans rien. */
    val thought: StateFlow<com.cerclerouge.brain.Thought?> = _thought
    private var lastBrainJournal: Pair<String, Instant>? = null
    private val lastSoundJournal = mutableMapOf<SoundFamily, Instant>()
    private var lastHasData = true
    private var wasAlert = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var state = DeciderState()
    private var tripHold: Pair<Observation, Instant>? = null
    private var lastJournal: Pair<String, Instant>? = null
    private var started = false
    private val _decision = MutableStateFlow<Decision?>(null)

    /** Dernière décision (visualiseur « Réflexion IA »), null protection coupée. */
    val decision: StateFlow<Decision?> = _decision

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch { listen.load() }
        scope.launch { trips.tripEvents.collect { onTripEvent(it) } }
        scope.launch { trips.trip.collect { if (it == null) mutex.withLock { tripHold = null } } }
        // Capteurs seulement protection active (batterie, R-SE-01).
        scope.launch {
            controller.state.collect { if (it.protection == ProtectionState.OFF) motion.stop() else motion.start() }
        }
        scope.launch { motion.events.collect { onMotion(it) } }
        scope.launch { audio.signals.collect { onAudio(it) } }
        scope.launch { audio.sounds.collect { onSound(it) } }
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    /** Point d'entrée des détecteurs (capteurs, écoute, mots-clés). */
    fun observe(o: Observation) {
        scope.launch {
            mutex.withLock { state = RiskDecider.observe(state, o, context(), clock.instant()) }
            tick()
        }
    }

    /** Chute, lutte, arrachage → B (bruit/geste de violence) ; immobilité prolongée → D. Jamais une alerte à eux seuls. */
    private suspend fun onMotion(e: com.cerclerouge.sensor.MotionEvent) {
        val now = clock.instant()
        val o = when (e.kind) {
            com.cerclerouge.sensor.MotionKind.FALL -> Observation(Component.B, e.strength, Reason.VIOLENCE_SOUND, "sensor.fall", now)
            com.cerclerouge.sensor.MotionKind.VIOLENT_MOTION ->
                Observation(Component.B, e.strength, Reason.VIOLENCE_SOUND, "sensor.violent", now)
            com.cerclerouge.sensor.MotionKind.SNATCH -> Observation(Component.B, e.strength, Reason.VIOLENCE_SOUND, "sensor.snatch", now)
            com.cerclerouge.sensor.MotionKind.STILL -> Observation(Component.D, 0.5, Reason.CONTACT_LOST, "sensor.still", now)
        }
        val kind = when (e.kind) {
            com.cerclerouge.sensor.MotionKind.FALL -> com.cerclerouge.brain.MotionKind.FALL
            com.cerclerouge.sensor.MotionKind.VIOLENT_MOTION -> com.cerclerouge.brain.MotionKind.VIOLENT
            com.cerclerouge.sensor.MotionKind.SNATCH -> com.cerclerouge.brain.MotionKind.SNATCH
            com.cerclerouge.sensor.MotionKind.STILL -> com.cerclerouge.brain.MotionKind.STILL
        }
        mutex.withLock { brain.add(SituationEvent.Motion(now, kind, e.strength)) }
        log.append("sensor", "${e.kind.name.lowercase()} strength=${"%.2f".format(java.util.Locale.ROOT, e.strength)}")
        observe(o)
    }

    /** Mot-clé enrôlé → A (confiance) ; sûr au-dessus de 0,75 (R-ESC-07). Silence brutal après une voix forte → D. */
    private suspend fun onAudio(sig: com.cerclerouge.audio.AudioSignal) {
        val now = clock.instant()
        val o = when (sig.kind) {
            com.cerclerouge.audio.AudioSignal.Kind.KEYWORD -> Observation(
                Component.A,
                sig.confidence.coerceIn(0.0, 1.0),
                Reason.DISTRESS_KEYWORD,
                "keyword",
                now,
                keywordHighConfidence = sig.confidence >= KEYWORD_SURE
            )
            com.cerclerouge.audio.AudioSignal.Kind.SILENCE_AFTER_VOICE ->
                Observation(Component.D, 0.5, Reason.CONTACT_LOST, "audio.silence", now)
            else -> return
        }
        if (sig.kind == com.cerclerouge.audio.AudioSignal.Kind.KEYWORD) {
            mutex.withLock { brain.add(SituationEvent.Keyword(now, sig.confidence.coerceIn(0.0, 1.0))) }
        }
        // R-AUD-04 : chaque détection est journalisée (score arrondi, version du modèle, aucun audio envoyé ; jamais le mot).
        log.append(
            "audio",
            "${sig.kind.name.lowercase()} confidence=${"%.2f".format(java.util.Locale.ROOT, sig.confidence)} model=${sig.modelVersion}"
        )
        observe(o)
    }

    /**
     * Son reconnu par YAMNet (IA.2) : détresse → A (cri), violence et arme → B ; véhicule et contexte ne nourrissent que la
     * situation du cerveau. Journalisé au plus une fois par famille toutes les 10 s (R-AUD-04), jamais le son.
     */
    private suspend fun onSound(h: SoundFamilies.Heard) {
        val now = clock.instant()
        val p = h.p.toDouble().coerceIn(0.0, 1.0)
        mutex.withLock { brain.add(SituationEvent.Sound(now, h.family, h.label, p)) }
        val cls = SoundFamilies.audioClass(h.family, h.label)
        val o = when (h.family) {
            SoundFamily.DISTRESS -> Observation(Component.A, p, Reason.DISTRESS_SCREAM, "yamnet", now, audioClass = cls)
            SoundFamily.VIOLENCE, SoundFamily.WEAPON -> Observation(Component.B, p, Reason.VIOLENCE_SOUND, "yamnet", now, audioClass = cls)
            SoundFamily.VEHICLE, SoundFamily.CONTEXT -> null
        }
        val last = lastSoundJournal[h.family]
        if (last == null || Duration.between(last, now) >= SOUND_JOURNAL_GAP) {
            lastSoundJournal[h.family] = now
            log.append(
                "audio",
                "sound family=${h.family.name.lowercase()} p=${"%.2f".format(
                    java.util.Locale.ROOT,
                    p
                )} model=${com.cerclerouge.app.audio.Yamnet.VERSION}"
            )
        }
        o?.let { observe(it) }
    }

    private suspend fun onTripEvent(e: TripEvent) {
        val tripKind = when (e.type) {
            TripEventType.OFF_ROUTE, TripEventType.WRONG_DIRECTION, TripEventType.UNSAFE_ZONE_ENTERED ->
                com.cerclerouge.brain.TripKind.OFF_ROUTE
            TripEventType.RETURN_TO_ORIGIN -> com.cerclerouge.brain.TripKind.FAST_DEVIATION
            TripEventType.PROLONGED_STOP -> com.cerclerouge.brain.TripKind.STOP
            TripEventType.SIGNAL_LOST -> com.cerclerouge.brain.TripKind.SIGNAL_LOST
            TripEventType.ARRIVAL_OVERDUE, TripEventType.TOO_LONG -> com.cerclerouge.brain.TripKind.OVERDUE
            TripEventType.ARRIVED -> null
        }
        tripKind?.let { k -> mutex.withLock { brain.add(SituationEvent.Trip(clock.instant(), k)) } }
        val reason = e.reason ?: return
        val plan = trips.trip.value?.plan
        val value = when (e.type) {
            // C = 0,5 au seuil du mode, 1 au double (docs/ia/modele-ia-locale.md).
            TripEventType.OFF_ROUTE -> plan?.let { p -> ((e.meters ?: 0.0) / p.offRouteMeters * 0.5) } ?: 0.6
            TripEventType.UNSAFE_ZONE_ENTERED -> 0.8
            TripEventType.RETURN_TO_ORIGIN -> 0.7
            TripEventType.WRONG_DIRECTION, TripEventType.PROLONGED_STOP, TripEventType.ARRIVAL_OVERDUE -> 0.6
            TripEventType.SIGNAL_LOST -> 0.5
            TripEventType.TOO_LONG -> 0.5
            TripEventType.ARRIVED -> return
        }.coerceIn(0.0, 1.0)
        val now = clock.instant()
        // Un incident de trajet reste pertinent 5 min : il peut se combiner avec un son ou un capteur pendant ce temps.
        mutex.withLock { tripHold = Observation(Component.C, value, reason, "trip", now) to now.plus(TRIP_HOLD) }
    }

    private suspend fun tick() {
        val protection = controller.state.value.protection
        val now = clock.instant()
        if (protection == ProtectionState.OFF) {
            mutex.withLock {
                state = DeciderState()
                tripHold = null
                brain.reset()
            }
            gemma.release()
            _decision.value = null
            _thought.value = null
            return
        }
        val ctx = context()
        val decision = mutex.withLock {
            // Conditions qui durent : incident de trajet récent, perte de réseau (D).
            tripHold?.let { (o, until) ->
                if (now.isBefore(until)) {
                    state = RiskDecider.observe(state, o.copy(at = now), ctx, now)
                } else {
                    tripHold =
                        null
                }
            }
            val st = monitor.status.value
            if (st.hasData != lastHasData) {
                lastHasData = st.hasData
                brain.add(SituationEvent.Network(now, st.hasData))
            }
            if (!st.hasData && !st.airplaneMode) {
                state = RiskDecider.observe(state, Observation(Component.D, 0.5, Reason.CONTACT_LOST, "network", now), ctx, now)
            }
            val (next, d) = RiskDecider.evaluate(state, ctx, now)
            state = next
            d
        }
        _decision.value = decision
        journal(decision, now)
        if (decision.action >= RecommendedAction.VERIFY) {
            controller.send { Input.Detection(it, decision.action, decision.reason ?: Reason.VIOLENCE_SOUND, decision.confidence) }
        }
        reflect(decision, ctx, now)
    }

    /**
     * Le cerveau au-dessus du plancher (ADR-0029) : ne peut que demander plus (vérification, alerte), jamais moins. Toute
     * erreur est absorbée ici : le plancher, le SOS et la phrase secrète ne dépendent pas de lui.
     */
    private suspend fun reflect(floor: Decision, ctx: DecisionContext, now: Instant) {
        val engine = controller.state.value
        val outcome = runCatching {
            mutex.withLock {
                val alert = engine.protection.isAlert
                // Alerte réglée (l'utilisateur va bien, ou fin) : la situation qui l'a ouverte ne doit pas la relancer.
                if (wasAlert && !alert) brain.reset()
                wasAlert = alert
                brain.think(now, situation(ctx, now), guardState())
            }
        }.getOrNull() ?: return
        _thought.value = outcome.thought
        val line = outcome.journal()
        val last = lastBrainJournal
        if (last == null || last.first != line || Duration.between(last.second, now) >= JOURNAL_GAP) {
            lastBrainJournal = line to now
            log.append("brain", line)
        }
        if (outcome.request > floor.action && !engine.protection.isAlert) {
            val reason = brainReason(now)
            controller.send { Input.Detection(it, outcome.request, reason, Confidence.MEDIUM) }
        }
    }

    private fun brainReason(now: Instant): Reason {
        val recent = brain.log.recent(now)
        return when {
            recent.any { it is SituationEvent.Keyword } -> Reason.DISTRESS_KEYWORD
            recent.any { it is SituationEvent.Sound && it.family == SoundFamily.DISTRESS } -> Reason.DISTRESS_SCREAM
            else -> Reason.VIOLENCE_SOUND
        }
    }

    private fun situation(ctx: DecisionContext, now: Instant): SituationContext {
        val engine = controller.state.value
        return SituationContext(
            hour = now.atZone(ZoneId.systemDefault()).hour,
            profile = ctx.profile.name.lowercase(),
            protection = engine.protection.name.lowercase(),
            alertLevel = engine.alert?.level?.value ?: 0,
            tripActive = trips.trip.value != null,
            acceptedContacts = accepted(),
            ignoredContexts = ctx.ignored.map { it.name.lowercase() }
        )
    }

    private fun guardState(): GuardState {
        val engine = controller.state.value
        return GuardState(
            armed = engine.protection.isArmed,
            alertOpen = engine.protection.isAlert,
            alertLevel = engine.alert?.level?.value ?: 0,
            tripActive = trips.trip.value != null,
            acceptedContacts = accepted()
        )
    }

    private fun accepted() = contacts.book.value.contacts.count { it.status == ContactStatus.ACCEPTED }

    /** R-AL-06, R-AUD-04 : toute décision au-dessus du niveau 0 est journalisée (au plus une ligne identique par 30 s). */
    private suspend fun journal(d: Decision, now: Instant) {
        if (d.action == RecommendedAction.NONE && d.engaged == RecommendedAction.NONE) return
        val line = d.journal()
        val last = lastJournal
        val key = line.substringAfter("act=")
        if (last != null && last.first == key && Duration.between(last.second, now) < JOURNAL_GAP) return
        lastJournal = key to now
        log.append("risk", line)
    }

    private suspend fun context(): DecisionContext {
        val now = clock.instant()
        val prefs = listen.prefs.value
        val a = account.account.value
        val profile = a?.let { AgeBand.of(it.birthYear, it.birthMonth, LocalDate.now(clock)).profile() } ?: VulnerabilityProfile.ADULT
        val trip = trips.trip.value
        val hour = now.atZone(ZoneId.systemDefault()).hour
        val night = trip != null && (hour >= 22 || hour < 5)
        val position = trip?.position
        val unsafe = position != null && trip.plan.unsafeZones.any { Geo.distance(position, it.center) <= it.radiusMeters }
        return DecisionContext(
            profile = profile,
            nightTrip = night,
            sensitivity = prefs.sensitivity,
            autoEmergency = false, // urgence automatique : réglage explicite à venir (P3), jamais par défaut
            ignored = prefs.activeContexts(now),
            e = DecisionContext.vulnerability(night, profile, unsafe)
        )
    }

    private companion object {
        const val TICK_MS = 2_000L
        const val KEYWORD_SURE = 0.75
        val TRIP_HOLD: Duration = Duration.ofMinutes(5)
        val JOURNAL_GAP: Duration = Duration.ofSeconds(30)
        val SOUND_JOURNAL_GAP: Duration = Duration.ofSeconds(10)
    }
}
