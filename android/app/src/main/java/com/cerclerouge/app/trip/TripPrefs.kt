package com.cerclerouge.app.trip

import com.cerclerouge.location.TransportMode
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Seuils d'un mode de transport, réglés par l'utilisateur dans les bornes du mode (PDF 9.2). */
data class ModeThresholds(val stopMinutes: Int, val offRouteMeters: Int)

/**
 * Préférences de trajet, dans la base chiffrée : seuils par mode et proches à prévenir pour les trajets (PDF 5.4).
 * Une valeur hors bornes (ancienne version, base modifiée) est ramenée dans les bornes du mode.
 */
@Singleton
class TripPrefs @Inject constructor(db: PrivacyDatabase) {
    private val settings = db.settings()

    suspend fun thresholds(mode: TransportMode): ModeThresholds {
        val parts = settings.get(KEY + mode.name)?.split(';')?.mapNotNull { it.toIntOrNull() }
        return ModeThresholds(
            (parts?.getOrNull(0) ?: mode.stopMinutes).coerceIn(mode.stopRange),
            (parts?.getOrNull(1) ?: mode.offRouteMeters).coerceIn(mode.offRouteRange)
        )
    }

    suspend fun setThresholds(mode: TransportMode, t: ModeThresholds): ModeThresholds {
        val safe = ModeThresholds(t.stopMinutes.coerceIn(mode.stopRange), t.offRouteMeters.coerceIn(mode.offRouteRange))
        settings.put(SettingEntity(KEY + mode.name, "${safe.stopMinutes};${safe.offRouteMeters}"))
        return safe
    }

    /** Identifiants des proches choisis pour les trajets ; vide : les 3 premiers proches ayant accepté. */
    suspend fun recipients(): Set<String> = settings.get(RECIPIENTS).orEmpty().split(',').filter { it.isNotBlank() }.toSet()

    suspend fun setRecipients(ids: Set<String>) = settings.put(SettingEntity(RECIPIENTS, ids.joinToString(",")))

    private companion object {
        const val KEY = "trip.thresholds."
        const val RECIPIENTS = "trip.recipients"
    }
}
