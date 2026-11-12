package com.cerclerouge.app.trip

import com.cerclerouge.location.GeoPoint
import com.cerclerouge.location.TransportMode
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Route à suivre, tracée sur la carte. `road` vrai : tracé par les rues ; faux : ligne droite (aucune donnée envoyée),
 * affichée en pointillés.
 */
data class RouteLine(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val mode: TransportMode,
    val points: List<GeoPoint>,
    val road: Boolean
)

/**
 * Itinéraire par les rues (OpenStreetMap, serveurs de routage OSM Allemagne). **Opt-in** : le départ et l'arrivée du trajet
 * (deux points arrondis à environ 1 m, sans nom ni identifiant) sont envoyés au service, qui voit aussi l'adresse IP. Sans accord,
 * ou sans réseau, la carte montre une ligne droite en pointillés : rien ne part (R-LE-03, R-TRIP-07). Le dernier tracé reste
 * en mémoire pour l'écran du trajet en cours ; rien n'est écrit sur le disque.
 */
@Singleton
class RouteService @Inject constructor(db: PrivacyDatabase) {
    private val settings = db.settings()
    private val _last = MutableStateFlow<RouteLine?>(null)

    /** Dernier tracé calculé (planification, puis trajet en cours). */
    val last: StateFlow<RouteLine?> = _last

    suspend fun allowed(): Boolean = settings.get(CONSENT) == "yes"

    suspend fun allow() = settings.put(SettingEntity(CONSENT, "yes"))

    /** Tracé de secours : ligne droite, local. */
    fun straight(origin: GeoPoint, destination: GeoPoint, mode: TransportMode) =
        RouteLine(origin, destination, mode, listOf(origin, destination), road = false)

    /** Rend toujours un tracé : par les rues si l'accord est donné et que le service répond, sinon la ligne droite. */
    suspend fun route(origin: GeoPoint, destination: GeoPoint, mode: TransportMode): RouteLine {
        val road = if (allowed()) fetch(origin, destination, mode) else null
        val line = road?.let { RouteLine(origin, destination, mode, it, road = true) } ?: straight(origin, destination, mode)
        _last.value = line
        return line
    }

    /** Tracé mémorisé pour ce trajet, ou ligne droite s'il n'y en a pas (trajet repris après une fermeture de l'écran). */
    fun cachedOrStraight(origin: GeoPoint, destination: GeoPoint, mode: TransportMode): RouteLine =
        _last.value?.takeIf { it.origin == origin && it.destination == destination } ?: straight(origin, destination, mode)

    private suspend fun fetch(origin: GeoPoint, destination: GeoPoint, mode: TransportMode): List<GeoPoint>? = withContext(Dispatchers.IO) {
        val (service, profile) = if (mode == TransportMode.WALK) "routed-foot" to "foot" else "routed-car" to "driving"
        val coords = listOf(origin, destination).joinToString(";") { String.format(Locale.ROOT, "%.5f,%.5f", it.lon, it.lat) }
        val url = URL("https://routing.openstreetmap.de/$service/route/v1/$profile/$coords?overview=full&geometries=geojson")
        val c = url.openConnection() as HttpURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.setRequestProperty("User-Agent", "CercleRouge")
            if (c.responseCode !in 200..299) return@withContext null
            parse(c.inputStream.bufferedReader().use { it.readText() })
        } catch (_: IOException) {
            null
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val CONSENT = "trip.route.consent"
        private const val TIMEOUT_MS = 8_000
        private const val MAX_POINTS = 4_000

        /** Géométrie GeoJSON de la première route d'une réponse OSRM ; null si la réponse est inutilisable. */
        fun parse(json: String): List<GeoPoint>? = runCatching {
            val root = JSONObject(json)
            if (root.optString("code") != "Ok") return null
            val coords = root.getJSONArray("routes").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
            if (coords.length() < 2 || coords.length() > MAX_POINTS) return null
            (0 until coords.length()).map { i ->
                val p = coords.getJSONArray(i)
                GeoPoint(p.getDouble(1), p.getDouble(0))
            }
        }.getOrNull()
    }
}
