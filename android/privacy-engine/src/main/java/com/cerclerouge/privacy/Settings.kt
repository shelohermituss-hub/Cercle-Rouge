package com.cerclerouge.privacy

import com.cerclerouge.domain.Retention
import com.cerclerouge.privacy.db.SettingDao
import com.cerclerouge.privacy.db.SettingEntity

/** Réglages non secrets mais personnels, dans la base chiffrée. */
class Settings(private val dao: SettingDao) {
    /** Choix de l'onboarding ; null tant que l'utilisateur n'a rien choisi (24 h s'appliquent alors, ADR-0017). */
    suspend fun retention(): Retention? = dao.get(RETENTION)?.let(Retention::valueOf)

    suspend fun effectiveRetention(): Retention = retention() ?: Retention.HOURS_24

    suspend fun setRetention(value: Retention) = dao.put(SettingEntity(RETENTION, value.name))

    /** Message personnalisé ajouté aux SMS d'alerte (PDF 5.1), 80 caractères au plus ; vide = aucun. */
    suspend fun customMessage(): String = dao.get(CUSTOM_MESSAGE).orEmpty()

    suspend fun setCustomMessage(text: String) = dao.put(SettingEntity(CUSTOM_MESSAGE, text.replace(Regex("\\s+"), " ").trim().take(80)))

    suspend fun onboardingDone(): Boolean = dao.get(ONBOARDING) == "done"

    suspend fun setOnboardingDone() = dao.put(SettingEntity(ONBOARDING, "done"))

    private companion object {
        const val RETENTION = "retention"
        const val ONBOARDING = "onboarding"
        const val CUSTOM_MESSAGE = "alert.custom"
    }
}
