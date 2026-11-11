package com.cerclerouge.location

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lon: Double)

/** Géométrie sur le globe pour de courtes distances (un trajet urbain) : sans dépendance, déterministe. */
object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    private fun rad(deg: Double) = deg * PI / 180.0

    /** Distance en mètres (formule de haversine). */
    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /** Distance en mètres du point `p` au segment `a`–`b`, en projection plane locale (précise à quelques mètres sur 50 km). */
    fun distanceToSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val cosLat = cos(rad(a.lat))
        fun x(q: GeoPoint) = rad(q.lon - a.lon) * cosLat * EARTH_RADIUS_M
        fun y(q: GeoPoint) = rad(q.lat - a.lat) * EARTH_RADIUS_M
        val bx = x(b)
        val by = y(b)
        val px = x(p)
        val py = y(p)
        val len2 = bx * bx + by * by
        if (len2 == 0.0) return hypot(px, py)
        val t = max(0.0, min(1.0, (px * bx + py * by) / len2))
        return hypot(px - t * bx, py - t * by)
    }

    /** Médiane composante par composante de 1 à 3 points : écarte une position aberrante isolée. */
    fun median(points: List<GeoPoint>): GeoPoint {
        require(points.isNotEmpty())
        val lats = points.map { it.lat }.sorted()
        val lons = points.map { it.lon }.sorted()
        return GeoPoint(lats[lats.size / 2], lons[lons.size / 2])
    }

    /** Point situé à `meters` du point `p` dans la direction `bearingDeg` (0 = nord, 90 = est) : sert aux scénarios de test. */
    fun offset(p: GeoPoint, bearingDeg: Double, meters: Double): GeoPoint {
        val d = meters / EARTH_RADIUS_M
        val brg = rad(bearingDeg)
        val lat1 = rad(p.lat)
        val lon1 = rad(p.lon)
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(brg))
        val lon2 = lon1 + kotlin.math.atan2(sin(brg) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return GeoPoint(lat2 * 180.0 / PI, lon2 * 180.0 / PI)
    }
}
