package com.cerclerouge.app.ui.home

/**
 * Style MapLibre de la carte d'accueil : imagerie satellite Sentinel-2 (EOX, couche 2016 sous CC BY 4.0)
 * et, par-dessus, routes principales et noms de lieux (OpenFreeMap, OpenStreetMap). Sans clé ni compte (ADR-0018).
 * Veille : image en noir et blanc. Protection active : couleurs, routes en vert.
 */
object MapStyle {
    const val SATELLITE = "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless_3857/default/g/{z}/{y}/{x}.jpg"
    const val VECTOR = "https://tiles.openfreemap.org/planet"

    /** Attribution exigée par les licences (CC BY 4.0, ODbL). */
    const val ATTRIBUTION = "Sentinel-2 cloudless 2016 © EOX (Copernicus) · © OpenStreetMap"

    /** JSON du style ; `armed` : couleurs (protection active), sinon noir et blanc. */
    fun json(armed: Boolean): String {
        val saturation = if (armed) 0.15 else -1.0
        val brightness = if (armed) 0.85 else 0.7
        val road = if (armed) "#4BE38C" else "#C9CFCB"
        val label = if (armed) "#E9F2EC" else "#D5D9D7"
        return """
            {"version":8,"name":"cercle-rouge-${if (armed) "on" else "off"}",
             "sources":{
              "sat":{"type":"raster","tiles":["$SATELLITE"],"tileSize":256,"maxzoom":15,
                     "attribution":"Sentinel-2 cloudless 2016 by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)"},
              "omt":{"type":"vector","url":"$VECTOR","attribution":"© OpenMapTiles © OpenStreetMap"}},
             "glyphs":"https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
             "layers":[
              {"id":"background","type":"background","paint":{"background-color":"#070908"}},
              {"id":"satellite","type":"raster","source":"sat",
               "paint":{"raster-saturation":$saturation,"raster-brightness-max":$brightness,"raster-contrast":0.1,"raster-fade-duration":0}},
              {"id":"roads","type":"line","source":"omt","source-layer":"transportation","minzoom":11,
               "filter":["match",["get","class"],["primary","trunk","motorway","secondary"],true,false],
               "paint":{"line-color":"$road","line-opacity":0.55,"line-width":["interpolate",["linear"],["zoom"],11,0.6,16,3]}},
              {"id":"places","type":"symbol","source":"omt","source-layer":"place","minzoom":8,
               "filter":["match",["get","class"],["city","town","suburb","village"],true,false],
               "layout":{"text-field":["coalesce",["get","name:fr"],["get","name"]],"text-font":["Noto Sans Bold"],
                         "text-size":["match",["get","class"],["city","town"],13,10],"text-transform":"uppercase","text-letter-spacing":0.1},
               "paint":{"text-color":"$label","text-halo-color":"#070908","text-halo-width":1.6}}
             ]}
        """.trimIndent()
    }
}
