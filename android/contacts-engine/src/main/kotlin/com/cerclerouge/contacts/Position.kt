package com.cerclerouge.contacts

import java.time.Duration
import java.time.Instant
import java.util.Locale

data class Fix(val lat: Double, val lon: Double, val accuracyMeters: Float, val at: Instant) {
    /** Coordonnées brutes « 18.5392,-72.3364 » : utilisables sans Internet par le destinataire. */
    fun raw(): String = String.format(Locale.ROOT, "%.4f,%.4f", lat, lon)
}

/** Position jointe à une alerte. */
sealed interface LocationShare {
    data class Fresh(val fix: Fix) : LocationShare
    data class LastKnown(val fix: Fix, val ageSeconds: Long) : LocationShare
    data object NotShared : LocationShare
}

private val FRESH_MAX_AGE: Duration = Duration.ofSeconds(10)
private val LAST_KNOWN_MAX_AGE: Duration = Duration.ofMinutes(30)

/**
 * Position ponctuelle d'une alerte (S2.09) : une position obtenue il y a 10 s au plus est « fraîche » ; sinon on joint la plus
 * récente des deux avec son âge, jusqu'à 30 min ; au-delà, ou sans autorisation, elle n'est pas partagée (P4).
 */
fun chooseFix(permissionGranted: Boolean, lastKnown: Fix?, fresh: Fix?, now: Instant): LocationShare {
    if (!permissionGranted) return LocationShare.NotShared
    val newest = listOfNotNull(fresh, lastKnown).maxByOrNull { it.at } ?: return LocationShare.NotShared
    val age = Duration.between(newest.at, now).coerceAtLeast(Duration.ZERO)
    return when {
        newest === fresh && age <= FRESH_MAX_AGE -> LocationShare.Fresh(newest)
        age <= LAST_KNOWN_MAX_AGE -> LocationShare.LastKnown(newest, age.seconds)
        else -> LocationShare.NotShared
    }
}
