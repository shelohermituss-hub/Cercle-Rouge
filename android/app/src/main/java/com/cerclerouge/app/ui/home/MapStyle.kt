package com.cerclerouge.app.ui.home

/**
 * Style MapLibre de la carte d'accueil, sur les tuiles vectorielles OpenFreeMap (données OpenStreetMap, sans clé ni pistage).
 * Design « Verre » : carte claire, grands axes en bleu doux quand la protection est active. Décision Q14 / ADR-0018.
 */
object MapStyle {
    const val TILES = "https://tiles.openfreemap.org/planet"
    const val ATTRIBUTION = "© OpenMapTiles © OpenStreetMap"

    private data class Palette(
        val background: String,
        val water: String,
        val urban: String,
        val park: String,
        val building: String,
        val minor: String,
        val major: String,
        val motorway: String,
        val border: String,
        val label: String,
        val halo: String
    )

    /** Veille : carte claire et grise (design « Verre »). */
    private val MONO = Palette(
        background = "#EEF0F3",
        water = "#D6E6F2",
        urban = "#E9ECEF",
        park = "#E3EDE3",
        building = "#E1E5E9",
        minor = "#FFFFFF",
        major = "#FFFFFF",
        motorway = "#D0D6DC",
        border = "#AEB8C2",
        label = "#7A8796",
        halo = "#EEF0F3"
    )

    /** Protection active : même carte claire, grands axes en bleu doux. */
    private val LIME = Palette(
        background = "#EEF0F3",
        water = "#CFE2F3",
        urban = "#E9ECEF",
        park = "#DDEBDD",
        building = "#E1E5E9",
        minor = "#FFFFFF",
        major = "#FFFFFF",
        motorway = "#B9D5FB",
        border = "#AEB8C2",
        label = "#5D6E7E",
        halo = "#EEF0F3"
    )

    /** JSON du style ; `armed` choisit la palette (couleurs quand la protection est active). */
    fun json(armed: Boolean): String {
        val p = if (armed) LIME else MONO
        fun road(id: String, classes: String, color: String, w10: Double, w16: Double) = """
            {"id":"$id","type":"line","source":"omt","source-layer":"transportation",
             "filter":["match",["get","class"],[$classes],true,false],
             "layout":{"line-cap":"round","line-join":"round"},
             "paint":{"line-color":"$color","line-width":["interpolate",["linear"],["zoom"],10,$w10,16,$w16]}}
        """.trimIndent()

        fun place(id: String, classes: String, size: Int, minZoom: Int) = """
            {"id":"$id","type":"symbol","source":"omt","source-layer":"place","minzoom":$minZoom,
             "filter":["match",["get","class"],[$classes],true,false],
             "layout":{"text-field":["coalesce",["get","name:fr"],["get","name"]],"text-font":["Noto Sans Bold"],
                       "text-size":$size,"text-letter-spacing":0.02},
             "paint":{"text-color":"${p.label}","text-halo-color":"${p.halo}","text-halo-width":1.5}}
        """.trimIndent()
        return """
            {"version":8,"name":"cercle-rouge-${if (armed) "on" else "off"}",
             "sources":{"omt":{"type":"vector","url":"$TILES","attribution":"$ATTRIBUTION"}},
             "glyphs":"https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
             "layers":[
              {"id":"background","type":"background","paint":{"background-color":"${p.background}"}},
              {"id":"water","type":"fill","source":"omt","source-layer":"water","paint":{"fill-color":"${p.water}"}},
              {"id":"urban","type":"fill","source":"omt","source-layer":"landuse",
               "filter":["match",["get","class"],["residential","suburb","neighbourhood","commercial","industrial"],true,false],
               "paint":{"fill-color":"${p.urban}"}},
              {"id":"park","type":"fill","source":"omt","source-layer":"landcover",
               "filter":["match",["get","class"],["wood","grass","farmland"],true,false],"paint":{"fill-color":"${p.park}"}},
              {"id":"building","type":"fill","source":"omt","source-layer":"building","minzoom":14,"paint":{"fill-color":"${p.building}"}},
              ${road("road-minor", "\"minor\",\"service\",\"track\"", p.minor, 0.5, 4.0)},
              ${road("road-major", "\"secondary\",\"tertiary\"", p.major, 1.0, 6.0)},
              ${road("road-primary", "\"primary\",\"trunk\",\"motorway\"", p.motorway, 1.5, 8.0)},
              {"id":"border","type":"line","source":"omt","source-layer":"boundary","filter":["==",["get","admin_level"],2],
               "paint":{"line-color":"${p.border}","line-width":1.5,"line-dasharray":[2,2]}},
              ${place("place-small", "\"suburb\",\"village\",\"neighbourhood\"", 10, 12)},
              ${place("place-city", "\"city\",\"town\"", 13, 6)}
             ]}
        """.trimIndent()
    }
}
