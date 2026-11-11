package com.cerclerouge.location

import java.time.Instant
import java.util.Locale

/**
 * Banc de rejeu : lecture et écriture de traces GPX (points `trkpt` avec `time`, `hdop` en guise de précision, vitesse en
 * extension). Les scénarios sont écrits en GPX puis relus : on teste le même chemin que des traces enregistrées sur téléphone
 * (S0.21), qui pourront être ajoutées telles quelles dans `src/test/resources`.
 */
object Gpx {
    fun write(name: String, fixes: List<TrackFix>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<gpx version="1.1" creator="cercle-rouge-replay"><trk><name>$name</name><trkseg>""")
        fixes.forEach {
            appendLine(
                String.format(
                    Locale.ROOT,
                    """<trkpt lat="%.6f" lon="%.6f"><time>%s</time><hdop>%.1f</hdop>%s%s</trkpt>""",
                    it.point.lat,
                    it.point.lon,
                    it.time,
                    it.accuracyMeters,
                    it.speedMps?.let { s -> String.format(Locale.ROOT, "<extensions><speed>%.2f</speed></extensions>", s) }.orEmpty(),
                    it.moving?.let { m -> "<!--moving:$m-->" }.orEmpty()
                )
            )
        }
        appendLine("</trkseg></trk></gpx>")
    }

    private val point = Regex("""<trkpt lat="([-0-9.]+)" lon="([-0-9.]+)">(.*?)</trkpt>""", RegexOption.DOT_MATCHES_ALL)

    fun parse(gpx: String): List<TrackFix> = point.findAll(gpx).map { m ->
        val body = m.groupValues[3]
        fun tag(t: String) = Regex("<$t>([^<]*)</$t>").find(body)?.groupValues?.get(1)
        TrackFix(
            point = GeoPoint(m.groupValues[1].toDouble(), m.groupValues[2].toDouble()),
            accuracyMeters = tag("hdop")?.toFloat() ?: 10f,
            time = Instant.parse(requireNotNull(tag("time")) { "point sans heure" }),
            speedMps = tag("speed")?.toFloat(),
            moving = Regex("<!--moving:(true|false)-->").find(body)?.groupValues?.get(1)?.toBooleanStrict()
        )
    }.toList()
}
