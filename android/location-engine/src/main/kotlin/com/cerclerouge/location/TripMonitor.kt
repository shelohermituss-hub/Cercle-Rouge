package com.cerclerouge.location

import java.time.Duration
import java.time.Instant
import kotlin.math.max

/** État du suivi d'un trajet. Immuable : chaque position ou tic d'horloge produit un nouvel état et des événements. */
data class MonitorState(
    val recent: List<GeoPoint> = emptyList(),
    val lastPosition: GeoPoint? = null,
    val lastTime: Instant? = null,
    val refDistanceToDestination: Double? = null,
    val increasingSince: Instant? = null,
    val offRouteSince: Instant? = null,
    val stoppedSince: Instant? = null,
    val farthestFromOrigin: Double = 0.0,
    val stopUntil: Instant? = null,
    val active: Set<TripEventType> = emptySet(),
    val inZones: Set<Int> = emptySet(),
    val arrived: Boolean = false
)

data class Step(val state: MonitorState, val events: List<TripEvent>, val ignored: Boolean = false)

/**
 * Détection d'événements d'un trajet (module produit 4), en Kotlin pur et déterministe.
 *
 * Filtres : une position de précision > 100 m est ignorée (seulement journalisée) ; la position utilisée est la médiane des 3
 * dernières. Chaque événement n'est émis qu'une fois tant que sa condition dure (pas de répétition à chaque position) ; il
 * peut revenir si la condition disparaît puis réapparaît. Un événement de trajet donne au plus la vérification (niveau 1) : la
 * décision appartient à alert-engine (R-TRIP-04).
 */
class TripMonitor(private val plan: TripPlan) {
    fun start() = MonitorState()

    /** « Je fais un arrêt » : suspend la détection d'arrêt, pas les autres ; durée bornée entre 5 et 60 min. */
    fun declareStop(state: MonitorState, now: Instant, minutes: Int) = state.copy(
        stopUntil = now.plus(Duration.ofMinutes(minutes.coerceIn(MIN_STOP_MINUTES, MAX_STOP_MINUTES).toLong())),
        stoppedSince = null
    )

    fun onFix(state: MonitorState, fix: TrackFix): Step {
        if (state.arrived) return Step(state, emptyList())
        if (fix.accuracyMeters > MAX_ACCURACY_M) return Step(state, emptyList(), ignored = true)
        val events = mutableListOf<TripEvent>()
        val active = state.active.toMutableSet()
        fun emit(type: TripEventType, meters: Double? = null) {
            if (active.add(type)) events += TripEvent(type, fix.time, meters)
        }

        val recent = (state.recent + fix.point).takeLast(MEDIAN_WINDOW)
        val pos = Geo.median(recent)
        val toDestination = Geo.distance(pos, plan.destination)
        val toOrigin = Geo.distance(pos, plan.origin)
        val farthest = max(state.farthestFromOrigin, toOrigin)

        if (toDestination <= ARRIVAL_RADIUS_M) {
            emit(TripEventType.ARRIVED, toDestination)
            return Step(state.copy(recent = recent, lastPosition = pos, lastTime = fix.time, active = active, arrived = true), events)
        }

        // Écart au corridor : plus de 60 s au-delà du seuil du mode. Position approximative : seuils sous 1 km désactivés.
        val threshold = plan.mode.offRouteMeters
        val offRouteEnabled = !(plan.approximateOnly && threshold < APPROXIMATE_MIN_M)
        var offRouteSince = state.offRouteSince
        val corridor = Geo.distanceToSegment(pos, plan.origin, plan.destination)
        if (offRouteEnabled && corridor > threshold) {
            offRouteSince = offRouteSince ?: fix.time
            if (Duration.between(offRouteSince, fix.time) >= OFF_ROUTE_SUSTAIN) emit(TripEventType.OFF_ROUTE, corridor)
        } else {
            offRouteSince = null
            active.remove(TripEventType.OFF_ROUTE)
        }

        // Direction opposée : la distance à la destination ne cesse de croître depuis plus de 5 min (tolérance de 10 m).
        var ref = state.refDistanceToDestination ?: toDestination
        var increasingSince = state.increasingSince
        if (toDestination > ref + DISTANCE_TOLERANCE_M) {
            increasingSince = increasingSince ?: fix.time
            ref = toDestination
        } else if (toDestination < ref - DISTANCE_TOLERANCE_M) {
            increasingSince = null
            ref = toDestination
            active.remove(TripEventType.WRONG_DIRECTION)
        }
        if (increasingSince != null &&
            Duration.between(increasingSince, fix.time) >= WRONG_DIRECTION_AFTER
        ) {
            emit(TripEventType.WRONG_DIRECTION, toDestination)
        }

        // Retour au départ : revenu à moins de 150 m après s'en être éloigné de plus de 500 m, sans fin de trajet.
        if (farthest >= RETURN_FAR_M && toOrigin <= RETURN_RADIUS_M) emit(TripEventType.RETURN_TO_ORIGIN, toOrigin)

        // Arrêt inhabituel : vitesse < 0,5 m/s plus longtemps que le seuil du mode, hors arrêt prévu et sans micro-mouvements.
        val speed = fix.speedMps?.toDouble() ?: derivedSpeed(state, pos, fix.time)
        val stationary = speed < STOP_SPEED_MPS && fix.moving != true
        var stoppedSince = state.stoppedSince
        val plannedStop = state.stopUntil?.let { fix.time < it } == true
        if (stationary && !plannedStop && !plan.approximateOnly) {
            stoppedSince = stoppedSince ?: fix.time
            if (Duration.between(stoppedSince, fix.time) >=
                Duration.ofMinutes(plan.mode.stopMinutes.toLong())
            ) {
                emit(TripEventType.PROLONGED_STOP)
            }
        } else {
            stoppedSince = null
            active.remove(TripEventType.PROLONGED_STOP)
        }

        // Zones non sûres : entrée dans un cercle, signalée une fois par entrée.
        val inZones = mutableSetOf<Int>()
        plan.unsafeZones.forEachIndexed { i, zone ->
            if (Geo.distance(pos, zone.center) <= zone.radiusMeters) {
                inZones += i
                if (i !in state.inZones) events += TripEvent(TripEventType.UNSAFE_ZONE_ENTERED, fix.time, Geo.distance(pos, zone.center))
            }
        }

        val next = state.copy(
            recent = recent,
            lastPosition = pos,
            lastTime = fix.time,
            refDistanceToDestination = ref,
            increasingSince = increasingSince,
            offRouteSince = offRouteSince,
            stoppedSince = stoppedSince,
            farthestFromOrigin = farthest,
            active = active,
            inZones = inZones
        )
        val timed = onTick(next, fix.time)
        return Step(timed.state, events + timed.events)
    }

    /** Contrôles qui ne dépendent que de l'heure (aucune position nécessaire) : trajet trop long, arrivée non confirmée. */
    fun onTick(state: MonitorState, now: Instant): Step {
        if (state.arrived) return Step(state, emptyList())
        val events = mutableListOf<TripEvent>()
        val active = state.active.toMutableSet()
        val planned = Duration.between(plan.startedAt, plan.plannedArrival)
        val elapsed = Duration.between(plan.startedAt, now)
        if (elapsed > planned.multipliedBy(3).dividedBy(2) &&
            elapsed > planned.plus(OVERDUE_MARGIN) &&
            active.add(TripEventType.TOO_LONG)
        ) {
            events += TripEvent(TripEventType.TOO_LONG, now)
        }
        if (now >= plan.plannedArrival.plus(OVERDUE_MARGIN) && active.add(TripEventType.ARRIVAL_OVERDUE)) {
            events += TripEvent(TripEventType.ARRIVAL_OVERDUE, now)
        }
        return Step(state.copy(active = active), events)
    }

    private fun derivedSpeed(state: MonitorState, pos: GeoPoint, time: Instant): Double {
        val last = state.lastPosition ?: return Double.MAX_VALUE
        val dt = Duration.between(state.lastTime ?: return Double.MAX_VALUE, time).toMillis() / 1000.0
        return if (dt > 0) Geo.distance(last, pos) / dt else Double.MAX_VALUE
    }

    companion object {
        const val MAX_ACCURACY_M = 100f
        const val ARRIVAL_RADIUS_M = 100.0
        const val MIN_STOP_MINUTES = 5
        const val MAX_STOP_MINUTES = 60
        private const val MEDIAN_WINDOW = 3
        private const val STOP_SPEED_MPS = 0.5
        private const val DISTANCE_TOLERANCE_M = 10.0
        private const val RETURN_FAR_M = 500.0
        private const val RETURN_RADIUS_M = 150.0
        private const val APPROXIMATE_MIN_M = 1_000
        private val OFF_ROUTE_SUSTAIN: Duration = Duration.ofSeconds(60)
        private val WRONG_DIRECTION_AFTER: Duration = Duration.ofMinutes(5)
        private val OVERDUE_MARGIN: Duration = Duration.ofMinutes(15)
    }
}
