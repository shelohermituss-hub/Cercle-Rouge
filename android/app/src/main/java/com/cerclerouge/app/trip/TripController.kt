package com.cerclerouge.app.trip

import com.cerclerouge.alert.Auth
import com.cerclerouge.alert.Input
import com.cerclerouge.app.alert.LocationProvider
import com.cerclerouge.app.location.LocationTracker
import com.cerclerouge.app.protection.ProtectionController
import com.cerclerouge.contacts.LocationShare
import com.cerclerouge.domain.ProtectionState
import com.cerclerouge.location.GeoPoint
import com.cerclerouge.location.MonitorState
import com.cerclerouge.location.TrackFix
import com.cerclerouge.location.TransportMode
import com.cerclerouge.location.TripEvent
import com.cerclerouge.location.TripEventType
import com.cerclerouge.location.TripMonitor
import com.cerclerouge.location.TripPlan
import com.cerclerouge.location.UnsafeZone
import com.cerclerouge.privacy.log.EventLog
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Trajet déclaré par l'utilisateur, avant son démarrage. */
data class TripDraft(
    val destination: GeoPoint,
    val mode: TransportMode,
    val plannedArrival: Instant,
    val unsafeZones: List<UnsafeZone> = emptyList()
)

/** Ce que l'écran affiche d'un trajet en cours. */
data class TripUi(val plan: TripPlan, val stopUntil: Instant?, val arrivalDetected: Boolean, val incidents: Int, val position: GeoPoint?)

/**
 * Pilote un trajet protégé (S4.05, S4.06) : démarre via le moteur (PIN obligatoire, localisation requise), nourrit le moniteur
 * avec les positions du suivi, et transmet chaque incident au moteur comme `TripIssue` : la vérification (niveau 1) au plus,
 * jamais une alerte directe (R-TRIP-04). L'arrivée détectée est seulement proposée : la fin du trajet demande le PIN.
 *
 * Aucun point n'est conservé : seules les 3 dernières positions vivent en mémoire, effacées à la fin du trajet (R-LE-04,
 * S4.11). Au journal, un résumé sans coordonnées. Après la mort du processus le trajet est interrompu (le moteur ramène l'état à
 * OFF : Android interdit de relancer la position depuis l'arrière-plan, R-SM-08).
 */
@Singleton
class TripController @Inject constructor(
    private val protection: ProtectionController,
    private val tracker: LocationTracker,
    private val locationProvider: LocationProvider,
    private val log: EventLog,
    private val clock: Clock
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var pending: TripPlan? = null
    private var monitor: TripMonitor? = null
    private var activePlan: TripPlan? = null
    private var monitorState = MonitorState()
    private var incidents = 0
    private var position: GeoPoint? = null
    private var arrivalDetected = false

    private val _trip = MutableStateFlow<TripUi?>(null)
    val trip: StateFlow<TripUi?> = _trip

    private val _tripEvents = MutableSharedFlow<TripEvent>(extraBufferCapacity = 8)

    /** Incidents du trajet (écart, arrêt, retard, zone non sûre…), aussi transmis au cerveau ([com.cerclerouge.app.protection.RiskBrain]). */
    val tripEvents: SharedFlow<TripEvent> = _tripEvents

    init {
        scope.launch { protection.state.map { it.protection }.distinctUntilChanged().collect { onProtection(it) } }
        scope.launch { tracker.fixes.collect { onFix(it) } }
        scope.launch {
            while (true) {
                delay(TICK_MS)
                onTick()
            }
        }
    }

    /** Position actuelle pour proposer le départ : fraîche si possible, sinon la dernière connue. Null si non partagée. */
    suspend fun currentPosition(): GeoPoint? = when (val s = locationProvider.share()) {
        is LocationShare.Fresh -> GeoPoint(s.fix.lat, s.fix.lon)
        is LocationShare.LastKnown -> GeoPoint(s.fix.lat, s.fix.lon)
        LocationShare.NotShared -> null
    }

    /** Démarre le trajet : le PIN (s'il existe) est vérifié ici, le moteur refuse sans PIN ou sans localisation (Q4). */
    suspend fun start(origin: GeoPoint, draft: TripDraft, pin: String?, biometricVerified: Boolean = false) {
        val now = clock.instant()
        mutex.withLock {
            pending = TripPlan(origin, draft.destination, draft.mode, now, draft.plannedArrival, draft.unsafeZones, tracker.approximateOnly)
        }
        val auth = if (biometricVerified) Auth.VERIFIED else protection.authFor(pin)
        protection.send { Input.StartTrip(it, auth) }
    }

    /** « Je fais un arrêt » : suspend la détection d'arrêt (5 à 60 min). */
    suspend fun declareStop(minutes: Int) = mutex.withLock {
        val m = monitor ?: return@withLock
        monitorState = m.declareStop(monitorState, clock.instant(), minutes)
        publish()
    }

    /** « Je suis arrivé » : demande le PIN s'il existe (R-SM-04). */
    suspend fun arrived(pin: String?, biometricVerified: Boolean = false) {
        val auth = if (biometricVerified) Auth.VERIFIED else protection.authFor(pin)
        protection.send { Input.Arrived(it, auth) }
        if (auth == Auth.FAILED) log.append("trip", "arrival_pin_refused")
    }

    private suspend fun onProtection(p: ProtectionState) = mutex.withLock {
        when {
            p == ProtectionState.TRIP_ACTIVE && monitor == null -> pending?.let { begin(it) }
            // Les états d'alerte ne terminent pas le trajet : on revient à TRIP_ACTIVE après « Je vais bien ».
            (p == ProtectionState.OFF || p.isArmed && p != ProtectionState.TRIP_ACTIVE) && monitor != null -> end()
        }
    }

    private fun begin(plan: TripPlan) {
        pending = null
        activePlan = plan
        val m = TripMonitor(plan)
        monitor = m
        monitorState = m.start()
        incidents = 0
        position = null
        arrivalDetected = false
        publish()
    }

    private suspend fun end() {
        val plan = activePlan
        val minutes = plan?.let { Duration.between(it.startedAt, clock.instant()).toMinutes() }
        // Fin du trajet : plus aucun point (R-LE-04). Seul un résumé sans coordonnées reste au journal.
        log.append("trip", "ended mode=${plan?.mode} minutes=$minutes incidents=$incidents")
        monitor = null
        activePlan = null
        monitorState = MonitorState()
        position = null
        _trip.value = null
    }

    private suspend fun onFix(fix: TrackFix) = mutex.withLock {
        val m = monitor ?: return@withLock
        val step = m.onFix(monitorState, fix)
        monitorState = step.state
        if (step.ignored) log.append("trip", "fix_ignored accuracy=${fix.accuracyMeters.toInt()}")
        if (!step.ignored) position = monitorState.lastPosition
        dispatch(step.events)
        publish()
    }

    private suspend fun onTick() = mutex.withLock {
        val m = monitor ?: return@withLock
        val step = m.onTick(monitorState, clock.instant())
        monitorState = step.state
        dispatch(step.events)
        publish()
    }

    private suspend fun dispatch(events: List<TripEvent>) {
        for (e in events) {
            if (e.type == TripEventType.ARRIVED) {
                arrivalDetected = true
                log.append("trip", "arrival_detected")
                continue
            }
            val reason = e.reason ?: continue
            incidents++
            _tripEvents.tryEmit(e)
            log.append("trip", "event ${e.type}")
            protection.send { Input.TripIssue(it, reason) }
        }
    }

    private fun publish() {
        val plan = activePlan ?: return
        _trip.value = TripUi(plan, monitorState.stopUntil, arrivalDetected, incidents, position)
    }

    private companion object {
        const val TICK_MS = 30_000L
    }
}
