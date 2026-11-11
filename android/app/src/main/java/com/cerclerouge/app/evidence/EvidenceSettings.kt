package com.cerclerouge.app.evidence

import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Réglages des preuves, dans la base chiffrée. Enregistrement audio activé par défaut (spécification, Q23 : avis à l'onboarding
 * et interrupteur dans le coffre-fort) ; l'avis lu est mémorisé.
 */
@Singleton
class EvidenceSettings @Inject constructor(db: PrivacyDatabase) {
    private val settings = db.settings()

    suspend fun audioEnabled(): Boolean = settings.get(AUDIO) != "off"

    suspend fun setAudioEnabled(on: Boolean) = settings.put(SettingEntity(AUDIO, if (on) "on" else "off"))

    private companion object {
        const val AUDIO = "evidence.audio"
    }
}
