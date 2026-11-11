package com.cerclerouge.location

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Trajet de 2 km vers l'est, départ à t0. */
private class Scenario(
    val mode: TransportMode = TransportMode.WALK,
    val zones: List<UnsafeZone> = emptyList(),
    val approximateOnly: Boolean = false,
    plannedMinutes: Long = 30
) {
    val origin = GeoPoint(18.5392, -72.3364)
    val destination = Geo.offset(origin, 90.0, 2_000.0)
    val t0: Instant = Instant.parse("2026-11-11T14:00:00Z")
    val plan = TripPlan(origin, destination, mode, t0, t0.plus(Duration.ofMinutes(plannedMinutes)), zones, approximateOnly)
    val fixes = mutableListOf<TrackFix>()

    /** Position à `along` m du départ vers l'est et `side` m au nord du corridor, à `sec` secondes après le départ. */
    fun at(sec: Long, along: Double, side: Double = 0.0, accuracy: Float = 10f, speed: Float? = null, moving: Boolean? = null) {
        fixes += TrackFix(Geo.offset(Geo.offset(origin, 90.0, along), 0.0, side), accuracy, t0.plusSeconds(sec), speed, moving)
    }

    /** Rejoue la trace via un fichier GPX, comme une trace enregistrée. */
    fun replay(name: String, tickAt: Instant? = null, declareStopAtSec: Pair<Long, Int>? = null): List<TripEvent> {
        val monitor = TripMonitor(plan)
        var state = monitor.start()
        val out = mutableListOf<TripEvent>()
        for (fix in Gpx.parse(Gpx.write(name, fixes))) {
            declareStopAtSec?.let { (sec, minutes) -> if (fix.time == t0.plusSeconds(sec)) state = monitor.declareStop(state, fix.time, minutes) }
            val step = monitor.onFix(state, fix)
            state = step.state
            out += step.events
        }
        tickAt?.let { out += monitor.onTick(state, it).events }
        return out
    }
}

private fun List<TripEvent>.types() = map { it.type }

class TripMonitorTest {
    @Test fun normalWalkOnlyArrives() {
        val s = Scenario()
        var along = 0.0
        var sec = 0L
        while (along < 1_950) {
            s.at(sec, along, speed = 1.3f)
            along += 13.0
            sec += 10
        }
        assertEquals(listOf(TripEventType.ARRIVED), s.replay("marche-normale").types())
    }

    @Test fun walkingDetourOf500mTriggersOffRouteAfterOneMinute() {
        val s = Scenario()
        for (i in 0..20) s.at(i * 10L, i * 13.0, speed = 1.3f) // 260 m sur le trajet
        for (i in 21..45) s.at(i * 10L, 260.0 + (i - 21) * 3.0, side = 500.0, speed = 1.3f) // détour de 500 m
        val events = s.replay("detour-marche")
        assertTrue(TripEventType.OFF_ROUTE in events.types())
        assertEquals(1, events.count { it.type == TripEventType.OFF_ROUTE }, "une seule fois tant que ça dure")
        val first = events.first { it.type == TripEventType.OFF_ROUTE }
        assertTrue(Duration.between(s.t0.plusSeconds(210), first.at) >= Duration.ofSeconds(60), "pas avant 60 s hors corridor")
        assertTrue((first.meters ?: 0.0) in 450.0..550.0)
    }

    @Test fun briefExcursionUnderOneMinuteIsIgnored() {
        val s = Scenario()
        for (i in 0..10) s.at(i * 10L, i * 13.0, speed = 1.3f)
        for (i in 11..14) s.at(i * 10L, 143.0 + (i - 10) * 3.0, side = 500.0, speed = 1.3f) // 40 s dehors
        for (i in 15..30) s.at(i * 10L, 160.0 + (i - 15) * 13.0, speed = 1.3f)
        assertFalse(TripEventType.OFF_ROUTE in s.replay("excursion-courte").types())
    }

    @Test fun carAcceptsAWiderCorridorThanWalking() {
        val car = Scenario(TransportMode.CAR)
        for (i in 0..30) car.at(i * 10L, i * 60.0, side = 600.0, speed = 6f) // 600 m : sous le seuil de 1 000 m
        assertFalse(TripEventType.OFF_ROUTE in car.replay("voiture-600m").types())
        val far = Scenario(TransportMode.CAR)
        for (i in 0..30) far.at(i * 10L, i * 20.0, side = 1_300.0, speed = 6f)
        assertTrue(TripEventType.OFF_ROUTE in far.replay("voiture-1300m").types())
    }

    @Test fun trafficJamShorterThanTheThresholdIsNotAnUnusualStop() {
        val s = Scenario(TransportMode.CAR)
        for (i in 0..5) s.at(i * 10L, i * 50.0, speed = 5f)
        for (i in 0..35) s.at(60 + i * 10L, 250.0, speed = 0f) // 6 min à l'arrêt (< 8 min)
        for (i in 0..5) s.at(420 + i * 10L, 250.0 + i * 50.0, speed = 5f)
        assertFalse(TripEventType.PROLONGED_STOP in s.replay("embouteillage-6min").types())
    }

    @Test fun longStopTriggersOnceAfterTheThreshold() {
        val s = Scenario(TransportMode.CAR)
        for (i in 0..5) s.at(i * 10L, i * 50.0, speed = 5f)
        for (i in 0..80) s.at(60 + i * 10L, 250.0, speed = 0f) // 13 min
        val events = s.replay("arret-13min")
        assertEquals(1, events.count { it.type == TripEventType.PROLONGED_STOP })
        val at = events.first { it.type == TripEventType.PROLONGED_STOP }.at
        assertTrue(Duration.between(s.t0.plusSeconds(60), at) >= Duration.ofMinutes(8))
    }

    @Test fun declaredStopSuspendsStopDetection() { // « Je fais un arrêt »
        val s = Scenario(TransportMode.WALK)
        for (i in 0..5) s.at(i * 10L, i * 13.0, speed = 1.3f)
        for (i in 0..100) s.at(60 + i * 10L, 65.0, speed = 0f) // 16 min d'arrêt
        assertTrue(TripEventType.PROLONGED_STOP in s.replay("arret-non-declare").types())
        assertFalse(TripEventType.PROLONGED_STOP in s.replay("arret-declare", declareStopAtSec = 60L to 30).types())
    }

    @Test fun declaredStopIsBoundedBetweenFiveAndSixtyMinutes() {
        val s = Scenario()
        val m = TripMonitor(s.plan)
        assertEquals(s.t0.plus(Duration.ofMinutes(5)), m.declareStop(m.start(), s.t0, 1).stopUntil)
        assertEquals(s.t0.plus(Duration.ofMinutes(60)), m.declareStop(m.start(), s.t0, 600).stopUntil)
    }

    @Test fun microMovementsPreventAStopFromStarting() { // tolérance embouteillage
        val s = Scenario(TransportMode.CAR)
        for (i in 0..5) s.at(i * 10L, i * 50.0, speed = 5f)
        for (i in 0..90) s.at(60 + i * 10L, 250.0, speed = 0.2f, moving = true) // 15 min, mais l'accéléromètre voit du mouvement
        assertFalse(TripEventType.PROLONGED_STOP in s.replay("embouteillage-micro-mouvements").types())
    }

    @Test fun walkingAwayFromTheDestinationThenBackToTheStart() {
        val s = Scenario()
        for (i in 0..5) s.at(i * 10L, i * 13.0, speed = 1.3f)
        // demi-tour : ils s'éloignent vers l'ouest, puis reviennent au départ
        for (i in 0..60) s.at(60 + i * 10L, 65.0 - i * 13.0, speed = 1.3f) // jusqu'à 730 m derrière le départ
        for (i in 0..60) s.at(660 + i * 10L, -715.0 + i * 13.0, speed = 1.3f)
        val events = s.replay("demi-tour")
        assertTrue(TripEventType.WRONG_DIRECTION in events.types())
        assertTrue(TripEventType.RETURN_TO_ORIGIN in events.types())
        assertEquals(1, events.count { it.type == TripEventType.RETURN_TO_ORIGIN })
    }

    @Test fun lowAccuracyFixesAreIgnoredAndNeverRaiseAnIncident() { // précision > 100 m : journalisée seulement
        val s = Scenario()
        for (i in 0..40) {
            if (i % 3 == 1) s.at(i * 10L, 900.0, side = 3_000.0, accuracy = 180f, speed = 1.3f) else s.at(i * 10L, i * 13.0, speed = 1.3f)
        }
        assertTrue(s.replay("gps-imprecis").none { it.type == TripEventType.OFF_ROUTE })
        val m = TripMonitor(s.plan)
        assertTrue(m.onFix(m.start(), s.fixes[1]).ignored)
    }

    @Test fun isolatedOutlierWithGoodAccuracyIsAbsorbedByTheMedianFilter() {
        val s = Scenario()
        for (i in 0..40) {
            if (i == 20) s.at(i * 10L, 260.0, side = 2_500.0, accuracy = 15f, speed = 1.3f) else s.at(i * 10L, i * 13.0, speed = 1.3f)
        }
        assertTrue(s.replay("aberrant-isole").none { it.type == TripEventType.OFF_ROUTE })
    }

    @Test fun lostSignalIsCaughtByTheClockNotByPositions() { // plus aucune position : seul le tic d'horloge parle
        val s = Scenario(plannedMinutes = 30)
        for (i in 0..10) s.at(i * 10L, i * 13.0, speed = 1.3f)
        val tooEarly = s.replay("signal-perdu", tickAt = s.t0.plus(Duration.ofMinutes(40)))
        assertTrue(tooEarly.none { it.type == TripEventType.ARRIVAL_OVERDUE })
        val events = s.replay("signal-perdu", tickAt = s.t0.plus(Duration.ofMinutes(46)))
        assertTrue(TripEventType.ARRIVAL_OVERDUE in events.types())
        assertTrue(TripEventType.TOO_LONG in events.types()) // 46 min > 30 × 1,5 = 45 et > 30 + 15
    }

    @Test fun arrivalIsDetectedWithin100mAndStopsEverythingAfter() {
        val s = Scenario()
        s.at(0, 1_950.0, speed = 1.3f)
        s.at(10, 1_960.0, speed = 1.3f)
        val events = s.replay("arrivee", tickAt = s.t0.plus(Duration.ofHours(3)))
        assertEquals(listOf(TripEventType.ARRIVED), events.types())
    }

    @Test fun unsafeZoneIsSignalledOncePerEntry() {
        val s0 = Scenario()
        val zone = UnsafeZone("Carrefour", Geo.offset(s0.origin, 90.0, 800.0), 100.0)
        val s = Scenario(zones = listOf(zone))
        for (i in 0..120) s.at(i * 10L, i * 13.0, speed = 1.3f)
        assertEquals(1, s.replay("zone-non-sure").count { it.type == TripEventType.UNSAFE_ZONE_ENTERED })
    }

    @Test fun approximateLocationDisablesFineDetectionsOnly() { // R-LE-02
        val walk = Scenario(approximateOnly = true)
        for (i in 0..30) walk.at(i * 10L, i * 5.0, side = 500.0, speed = 1.3f) // 500 m : détection fine
        for (i in 0..120) walk.at(310 + i * 10L, 150.0, side = 500.0, speed = 0f) // et arrêt prolongé
        val events = walk.replay("approximative").types()
        assertFalse(TripEventType.OFF_ROUTE in events)
        assertFalse(TripEventType.PROLONGED_STOP in events)
        val car = Scenario(TransportMode.CAR, approximateOnly = true)
        for (i in 0..30) car.at(i * 10L, i * 20.0, side = 1_500.0, speed = 6f) // 1,5 km reste détectable
        assertTrue(TripEventType.OFF_ROUTE in car.replay("approximative-voiture").types())
    }

    @Test fun everyIncidentMapsToAClosedReasonAndArrivalToNone() {
        TripEventType.entries.forEach {
            val r = TripEvent(it, Instant.EPOCH).reason
            if (it == TripEventType.ARRIVED) assertEquals(null, r) else assertTrue(r != null, "$it")
        }
    }

    @Test fun estimatedDurationUsesTheModeSpeedWithAFiveMinuteFloor() {
        val a = GeoPoint(18.54, -72.34)
        assertEquals(Duration.ofSeconds(1_538), TripPlan.estimatedDuration(a, Geo.offset(a, 90.0, 2_000.0), TransportMode.WALK))
        assertEquals(Duration.ofMinutes(5), TripPlan.estimatedDuration(a, Geo.offset(a, 90.0, 50.0), TransportMode.CAR))
    }

    @Test fun gpxRoundTripKeepsEveryField() {
        val s = Scenario()
        s.at(0, 0.0, accuracy = 12.5f, speed = 1.3f, moving = true)
        s.at(10, 13.0, accuracy = 8f)
        val back = Gpx.parse(Gpx.write("x", s.fixes))
        assertEquals(2, back.size)
        assertEquals(12.5f, back[0].accuracyMeters)
        assertEquals(1.3f, back[0].speedMps)
        assertEquals(true, back[0].moving)
        assertEquals(null, back[1].speedMps)
        assertTrue(Geo.distance(s.fixes[0].point, back[0].point) < 0.2)
    }
}
