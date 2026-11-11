package com.cerclerouge.app.ui

import com.cerclerouge.app.ui.home.MapStyle
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Le style est un JSON valide, ne charge que les tuiles EOX (satellite) et OpenFreeMap, et ne contient aucune position (P4). */
@RunWith(RobolectricTestRunner::class)
class MapStyleTest {
    @Test fun stylesAreValidAndPrivate() {
        for (armed in listOf(false, true)) {
            val json = MapStyle.json(armed)
            val style = JSONObject(json)
            assertEquals(8, style.getInt("version"))
            val sources = style.getJSONObject("sources")
            assertEquals(setOf("sat", "omt"), sources.keys().asSequence().toSet())
            assertTrue(sources.getJSONObject("sat").getJSONArray("tiles").getString(0).startsWith("https://tiles.maps.eox.at/"))
            // Couche 2016 : la seule sous CC BY 4.0 (les années suivantes sont non commerciales).
            assertTrue("/s2cloudless_3857/" in sources.getJSONObject("sat").getJSONArray("tiles").getString(0))
            assertTrue(sources.getJSONObject("omt").getString("url").startsWith("https://tiles.openfreemap.org/"))
            assertTrue(style.getJSONArray("layers").length() >= 4)
            File("build/map-style-${if (armed) "on" else "off"}.json").writeText(json)
        }
    }
}
