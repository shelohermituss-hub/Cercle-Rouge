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

    suspend fun onboardingDone(): Boolean = dao.get(ONBOARDING) == "done"

    suspend fun setOnboardingDone() = dao.put(SettingEntity(ONBOARDING, "done"))

    private companion object {
        const val RETENTION = "retention"
        const val ONBOARDING = "onboarding"
    }
}
