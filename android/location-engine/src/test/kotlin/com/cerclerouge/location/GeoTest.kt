package com.cerclerouge.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoTest {
    private val pap = GeoPoint(18.5392, -72.3364) // Port-au-Prince

    @Test fun oneDegreeOfLatitudeIsAbout111Km() {
        val d = Geo.distance(pap, GeoPoint(pap.lat + 1, pap.lon))
        assertEquals(111_195.0, d, 200.0)
    }

    @Test fun offsetAndDistanceAreConsistent() {
        for (bearing in listOf(0.0, 45.0, 90.0, 200.0, 315.0)) {
            val q = Geo.offset(pap, bearing, 1_500.0)
            assertEquals(1_500.0, Geo.distance(pap, q), 1.0, "cap $bearing")
        }
    }

    @Test fun distanceToSegmentIsPerpendicularInTheMiddle() {
        val a = pap
        val b = Geo.offset(pap, 90.0, 2_000.0)
        val p = Geo.offset(Geo.offset(pap, 90.0, 1_000.0), 0.0, 400.0)
        assertEquals(400.0, Geo.distanceToSegment(p, a, b), 3.0)
    }

    @Test fun beyondTheEndsTheDistanceIsToTheNearestEnd() {
        val a = pap
        val b = Geo.offset(pap, 90.0, 2_000.0)
        val p = Geo.offset(pap, 270.0, 700.0) // derrière le départ
        assertEquals(700.0, Geo.distanceToSegment(p, a, b), 3.0)
    }

    @Test fun degenerateSegmentIsAPoint() {
        val p = Geo.offset(pap, 0.0, 250.0)
        assertEquals(250.0, Geo.distanceToSegment(p, pap, pap), 2.0)
    }

    @Test fun medianDropsAnIsolatedOutlier() {
        val good = Geo.offset(pap, 90.0, 100.0)
        val spike = Geo.offset(pap, 0.0, 5_000.0)
        val m = Geo.median(listOf(pap, spike, good))
        assertTrue(Geo.distance(m, pap) < 200.0, "la médiane ne suit pas l'aberrant")
    }
}
