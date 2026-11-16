package com.cerclerouge.location

import com.cerclerouge.domain.Reason
import java.time.Duration
import java.time.Instant

/**
 * Modes de transport et seuils (module produit 4, PDF 9.2). ⚠ Points de départ à calibrer avec des données de terrain (pilote à
 * Port-au-Prince) : `stopMinutes` = arrêt inhabituel, `offRouteMeters` = écart au corridor, `speedMps` = vitesse moyenne pour
 * estimer la durée (circulation dense). `stopRange` et `offRouteRange` : bornes dans lesquelles l'utilisateur peut régler ces
 * seuils. Tap-tap et moto ne figurent pas au PDF : bornes du bus et du taxi.
 */
enum class TransportMode(
    val stopMinutes: Int,
    val offRouteMeters: Int,
    val speedMps: Double,
    val stopRange: IntRange,
    val offRouteRange: IntRange
) {
    WALK(10, 300, 1.3, 10..15, 100..300),
    CAR(15, 1_000, 5.5, 15..30, 300..1_000),
    TAP_TAP(10, 800, 4.5, 10..40, 500..1_000),
    TAXI(10, 1_000, 5.5, 10..20, 300..1_000),
    MOTO(10, 1_000, 6.0, 10..20, 300..1_000),
    BUS(20, 1_000, 4.5, 20..40, 500..1_000),
    TRAIN(30, 3_000, 12.0, 30..60, 1_000..3_000)
}

/** Zone non sûre : cercle marqué par l'utilisateur, stocké seulement sur le téléphone, chiffré (R-TRIP-07). */
data class UnsafeZone(val name: String, val center: GeoPoint, val radiusMeters: Double)

/** Une position reçue. `moving` : micro-mouvements vus par l'accéléromètre (null = inconnu). */
data class TrackFix(
    val point: GeoPoint,
    val accuracyMeters: Float,
    val time: Instant,
    val speedMps: Float? = null,
    val moving: Boolean? = null
)

/**
 * Trajet déclaré. `approximateOnly` : seule la position approximative est accordée ; les détections fines (écart sous 1 km, arrêt)
 * sont alors désactivées (R-LE-02).
 */
data class TripPlan(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val mode: TransportMode,
    val startedAt: Instant,
    val plannedArrival: Instant,
    val unsafeZones: List<UnsafeZone> = emptyList(),
    val approximateOnly: Boolean = false,
    /** Route par les rues (au moins 2 points) : le corridor la suit ; vide : ligne droite départ → arrivée. */
    val route: List<GeoPoint> = emptyList(),
    /** Seuils de ce trajet : ceux du mode, ou ceux réglés par l'utilisateur (bornés par le mode). */
    val stopMinutes: Int = mode.stopMinutes,
    val offRouteMeters: Int = mode.offRouteMeters
) {
    companion object {
        /** Durée estimée en ligne droite à la vitesse moyenne du mode, minimum 5 min : proposée puis modifiable. */
        fun estimatedDuration(origin: GeoPoint, destination: GeoPoint, mode: TransportMode): Duration =
            Duration.ofSeconds((Geo.distance(origin, destination) / mode.speedMps).toLong().coerceAtLeast(300))
    }
}

enum class TripEventType {
    PROLONGED_STOP,
    OFF_ROUTE,
    WRONG_DIRECTION,
    RETURN_TO_ORIGIN,
    TOO_LONG,
    ARRIVAL_OVERDUE,
    UNSAFE_ZONE_ENTERED,

    /** Plus aucune position utilisable depuis 5 min (GPS coupé, tunnel, téléphone masqué). */
    SIGNAL_LOST,
    ARRIVED
}

data class TripEvent(val type: TripEventType, val at: Instant, val meters: Double? = null) {
    /** Raison montrée aux proches (liste fermée). `ARRIVED` n'est pas un incident. Un événement de trajet donne au plus le niveau 1. */
    val reason: Reason?
        get() = when (type) {
            TripEventType.OFF_ROUTE,
            TripEventType.WRONG_DIRECTION,
            TripEventType.RETURN_TO_ORIGIN,
            TripEventType.UNSAFE_ZONE_ENTERED
            -> Reason.TRIP_DEVIATION
            TripEventType.PROLONGED_STOP -> Reason.PROLONGED_STOP
            TripEventType.SIGNAL_LOST -> Reason.CONTACT_LOST
            TripEventType.TOO_LONG, TripEventType.ARRIVAL_OVERDUE -> Reason.ARRIVAL_OVERDUE
            TripEventType.ARRIVED -> null
        }
}
