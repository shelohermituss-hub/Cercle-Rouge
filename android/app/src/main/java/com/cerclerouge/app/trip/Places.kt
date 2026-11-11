package com.cerclerouge.app.trip

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.cerclerouge.location.GeoPoint
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Lieu nommé : favori ou résultat de recherche. */
data class Place(val name: String, val point: GeoPoint)

/**
 * Lieux favoris (maison, école, travail…) et accord pour la recherche d'adresse, dans la base chiffrée (SQLCipher) :
 * rien ne quitte le téléphone (R-TRIP-07).
 */
@Singleton
class PlacesRepository @Inject constructor(db: PrivacyDatabase) {
    private val settings = db.settings()

    suspend fun favorites(): List<Place> = settings.get(FAVORITES)?.lineSequence()?.mapNotNull(::decode)?.toList().orEmpty()

    /** Ajoute (ou remplace le lieu du même nom). Au plus [MAX_FAVORITES] : le plus ancien part en premier. */
    suspend fun save(place: Place) {
        val name = place.name.replace(Regex("[\\t\\n\\r]"), " ").trim().take(MAX_NAME)
        if (name.isEmpty()) return
        val list = (favorites().filterNot { it.name.equals(name, ignoreCase = true) } + place.copy(name = name)).takeLast(MAX_FAVORITES)
        settings.put(SettingEntity(FAVORITES, list.joinToString("\n") { "${it.name}\t${it.point.lat}\t${it.point.lon}" }))
    }

    suspend fun remove(name: String) {
        val list = favorites().filterNot { it.name == name }
        settings.put(SettingEntity(FAVORITES, list.joinToString("\n") { "${it.name}\t${it.point.lat}\t${it.point.lon}" }))
    }

    /** Recherche d'adresse : désactivée tant que l'utilisateur n'a pas lu et accepté l'avis (R-LE-03). */
    suspend fun searchAllowed(): Boolean = settings.get(SEARCH_CONSENT) == "yes"

    suspend fun allowSearch() = settings.put(SettingEntity(SEARCH_CONSENT, "yes"))

    private fun decode(line: String): Place? {
        val parts = line.split('\t')
        if (parts.size != 3) return null
        val lat = parts[1].toDoubleOrNull() ?: return null
        val lon = parts[2].toDoubleOrNull() ?: return null
        return Place(parts[0], GeoPoint(lat, lon))
    }

    companion object {
        const val MAX_FAVORITES = 10
        const val MAX_NAME = 40
        private const val FAVORITES = "trip.favorites"
        private const val SEARCH_CONSENT = "trip.search.consent"
    }
}

/**
 * Recherche d'adresse par le géocodeur du système (Google Play Services) : seul le texte tapé part, borné au rectangle d'Haïti.
 * La position de l'utilisateur n'est jamais jointe (R-LE-03). Opt-in : l'écran demande l'accord une fois (ADR-0019).
 */
@Singleton
class AddressSearch @Inject constructor(@param:ApplicationContext private val context: Context) {
    val available: Boolean get() = Geocoder.isPresent()

    suspend fun search(query: String): List<Place> {
        val q = query.trim().take(MAX_QUERY)
        if (q.length < 3 || !available) return emptyList()
        val geocoder = Geocoder(context, Locale.FRENCH)
        val addresses = withTimeoutOrNull(TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(
                        q,
                        MAX_RESULTS,
                        HAITI_SOUTH,
                        HAITI_WEST,
                        HAITI_NORTH,
                        HAITI_EAST,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.toList())

                            override fun onError(errorMessage: String?) = cont.resume(emptyList())
                        }
                    )
                }
            } else {
                withContext(Dispatchers.IO) { legacy(geocoder, q) }
            }
        }.orEmpty()
        return addresses.map { a -> Place(label(a), GeoPoint(a.latitude, a.longitude)) }
    }

    @Suppress("DEPRECATION") // API < 33 : seule forme disponible, appelée hors du fil principal.
    private fun legacy(geocoder: Geocoder, q: String): List<Address> = runCatching {
        geocoder.getFromLocationName(q, MAX_RESULTS, HAITI_SOUTH, HAITI_WEST, HAITI_NORTH, HAITI_EAST).orEmpty()
    }.getOrDefault(emptyList())

    private fun label(a: Address): String =
        (0..a.maxAddressLineIndex).mapNotNull { a.getAddressLine(it) }.joinToString(", ").ifBlank { a.featureName ?: "?" }

    private companion object {
        const val MAX_QUERY = 120
        const val MAX_RESULTS = 5
        const val TIMEOUT_MS = 10_000L
        const val HAITI_SOUTH = 17.9
        const val HAITI_WEST = -74.6
        const val HAITI_NORTH = 20.2
        const val HAITI_EAST = -71.5
    }
}
