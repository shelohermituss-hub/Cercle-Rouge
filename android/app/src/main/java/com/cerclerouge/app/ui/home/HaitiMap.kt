package com.cerclerouge.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Contour simplifié d'Haïti (longitude, latitude), île de la Gonâve à part.
 * Carte décorative dessinée en local : aucune tuile réseau, aucun point de position (P4, R-PE-02).
 */
private val MAINLAND = listOf(
    -73.38 to 19.83, -73.10 to 19.92, -72.83 to 19.94, -72.50 to 19.88, -72.20 to 19.77, -71.84 to 19.70, -71.74 to 19.72,
    -71.70 to 19.40, -71.76 to 19.10, -71.65 to 18.95, -71.80 to 18.62, -71.70 to 18.32, -71.77 to 18.04, -72.10 to 18.15,
    -72.53 to 18.22, -73.00 to 18.20, -73.40 to 18.20, -73.75 to 18.18, -74.10 to 18.28, -74.46 to 18.40, -74.40 to 18.60,
    -74.12 to 18.66, -73.80 to 18.60, -73.50 to 18.54, -73.09 to 18.45, -72.63 to 18.52, -72.34 to 18.55, -72.38 to 18.80,
    -72.55 to 18.95, -72.72 to 19.12, -72.75 to 19.30, -72.69 to 19.45, -72.90 to 19.60, -73.20 to 19.62, -73.43 to 19.66
)
private val GONAVE = listOf(-73.32 to 18.92, -73.05 to 18.97, -72.80 to 18.87, -72.75 to 18.80, -73.00 to 18.76, -73.25 to 18.82)

private const val WEST = -74.6
private const val EAST = -71.5
private const val NORTH = 20.1
private const val SOUTH = 17.95

private fun inside(poly: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
    var hit = false
    var j = poly.lastIndex
    for (i in poly.indices) {
        val (xi, yi) = poly[i]
        val (xj, yj) = poly[j]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) hit = !hit
        j = i
    }
    return hit
}

private fun land(lon: Double, lat: Double) = inside(MAINLAND, lon, lat) || inside(GONAVE, lon, lat)

/**
 * Haïti en pixels de 6 dp : terres, côte plus marquée, mer en pointillés.
 * Monochrome quand la protection est coupée, en couleurs (lime) quand elle est active.
 */
@Composable
fun HaitiMap(landColor: Color, coastColor: Color, seaColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = 6.dp.toPx()
        val gap = 1.dp.toPx()
        // Carte calée sur la largeur, centrée verticalement ; 1° de longitude ≈ cos(19°) × 1° de latitude.
        val mapW = size.width
        val degPerPx = (EAST - WEST) / mapW
        val latPerPx = degPerPx * 0.946
        val mapH = (NORTH - SOUTH) / latPerPx
        val top = (size.height - mapH) / 2
        val cols = (size.width / cell).toInt()
        val rows = (size.height / cell).toInt()
        for (r in 0..rows) {
            for (col in 0..cols) {
                val x = col * cell
                val y = r * cell
                val lon = WEST + (x + cell / 2) * degPerPx
                val lat = NORTH - (y + cell / 2 - top) * latPerPx
                if (land(lon, lat)) {
                    val coast = !land(lon - cell * degPerPx, lat) ||
                        !land(lon + cell * degPerPx, lat) ||
                        !land(lon, lat + cell * latPerPx) ||
                        !land(lon, lat - cell * latPerPx)
                    drawRect(if (coast) coastColor else landColor, Offset(x, y), Size(cell - gap, cell - gap))
                } else if (r % 4 == 0 && col % 4 == 0) {
                    drawRect(seaColor, Offset(x, y), Size(gap * 2, gap * 2))
                }
            }
        }
    }
}
