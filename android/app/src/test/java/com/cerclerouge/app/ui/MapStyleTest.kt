package com.cerclerouge.app.ui

import com.cerclerouge.app.ui.home.MapStyle
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Le style est un JSON valide, ne charge que les tuiles OpenFreeMap et ne contient aucune position (P4). */
@RunWith(RobolectricTestRunner::class)
class MapStyleTest {
    @Test fun stylesAreValidAndPrivate() {
        for (armed in listOf(false, true)) {
            val json = MapStyle.json(armed)
            val style = JSONObject(json)
            assertEquals(8, style.getInt("version"))
            val sources = style.getJSONObject("sources")
            assertEquals(listOf("omt"), sources.keys().asSequence().toList())
            assertTrue(sources.getJSONObject("omt").getString("url").startsWith("https://tiles.openfreemap.org/"))
            assertTrue(style.getJSONArray("layers").length() >= 10)
            File("build/map-style-${if (armed) "on" else "off"}.json").writeText(json)
        }
    }
}
