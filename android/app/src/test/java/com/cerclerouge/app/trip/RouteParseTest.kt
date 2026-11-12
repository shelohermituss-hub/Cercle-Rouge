package com.cerclerouge.app.trip

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Lecture de la réponse du service de routage (format OSRM : GeoJSON en [lon, lat]). */
@RunWith(RobolectricTestRunner::class)
class RouteParseTest {
    @Test fun readsTheFirstRoute() {
        val json = """{"code":"Ok","routes":[{"distance":1200.5,"duration":300,
            "geometry":{"type":"LineString","coordinates":[[-72.3386,18.5425],[-72.3300,18.5400],[-72.3200,18.5300]]}}],"waypoints":[]}"""
        val points = RouteService.parse(json)!!
        assertEquals(3, points.size)
        assertEquals(18.5425, points.first().lat, 1e-9) // lat vient en second dans GeoJSON
        assertEquals(-72.3386, points.first().lon, 1e-9)
        assertEquals(-72.3200, points.last().lon, 1e-9)
    }

    @Test fun refusesUnusableAnswers() {
        assertNull(RouteService.parse("""{"code":"NoRoute","routes":[]}"""))
        assertNull(RouteService.parse("pas du json"))
        assertNull(RouteService.parse("""{"code":"Ok","routes":[{"geometry":{"coordinates":[[-72.3,18.5]]}}]}"""))
        assertNull(RouteService.parse("""{"code":"Ok","routes":[]}"""))
    }
}
