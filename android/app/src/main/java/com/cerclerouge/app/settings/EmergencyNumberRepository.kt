package com.cerclerouge.app.settings

import android.content.Context
import android.telephony.TelephonyManager
import com.cerclerouge.domain.EmergencyCountry
import com.cerclerouge.domain.EmergencyNumbers
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Numéros d'urgence à proposer (1.18) : pays choisi par l'utilisateur, sinon celui de la carte SIM, sinon du réseau, sinon Haïti.
 * Aucune permission : `TelephonyManager.simCountryIso` et `networkCountryIso` sont publics. L'appel passe par le composeur.
 */
@Singleton
class EmergencyNumberRepository @Inject constructor(@param:ApplicationContext private val context: Context, db: PrivacyDatabase) {
    private val settings = db.settings()
    private val _country = MutableStateFlow(detect(null))

    /** Numéros du pays retenu ; disponible tout de suite (SIM), affiné après lecture du choix de l'utilisateur. */
    val country: StateFlow<EmergencyCountry> = _country

    /** Choix de l'utilisateur (code ISO), null : automatique. */
    suspend fun choice(): String? = settings.get(KEY)

    suspend fun load(): EmergencyCountry = detect(choice()).also { _country.value = it }

    suspend fun choose(iso: String?) {
        if (iso == null) settings.remove(KEY) else settings.put(SettingEntity(KEY, iso))
        load()
    }

    private fun detect(choice: String?): EmergencyCountry {
        val tm = context.getSystemService(TelephonyManager::class.java)
        return EmergencyNumbers.resolve(choice, tm?.simCountryIso, tm?.networkCountryIso)
    }

    private companion object {
        const val KEY = "emergency.country"
    }
}
