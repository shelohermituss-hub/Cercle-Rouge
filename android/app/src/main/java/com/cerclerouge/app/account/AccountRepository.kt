package com.cerclerouge.app.account

import com.cerclerouge.domain.AgeBand
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Account(
    val displayName: String,
    val birthYear: Int,
    val birthMonth: Int,
    val adultAttested: Boolean,
    val guardian: GuardianStatus
)

enum class GuardianStatus { NONE, PENDING, CONFIRMED }

/**
 * Compte de l'utilisateur, stocké dans la base chiffrée.
 *
 * ⚠ Provisoire (S1) : en attendant les comptes Supabase et Vonage, l'inscription par code SMS et le consentement
 * de l'adulte référent sont simulés localement. Le branchement réel (RPC request/confirm_guardian_consent) remplacera
 * [requestGuardian] et [simulateGuardianConfirmation] sans changer les écrans.
 */
@Singleton
class AccountRepository @Inject constructor(db: PrivacyDatabase, private val clock: Clock) {
    private val dao = db.settings()
    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account

    suspend fun load(): Account? {
        val name = dao.get(NAME) ?: return null
        return Account(
            displayName = name,
            birthYear = dao.get(BIRTH_YEAR)?.toIntOrNull() ?: return null,
            birthMonth = dao.get(BIRTH_MONTH)?.toIntOrNull() ?: return null,
            adultAttested = dao.get(ADULT) == "true",
            guardian = dao.get(GUARDIAN)?.let(GuardianStatus::valueOf) ?: GuardianStatus.NONE
        ).also { _account.value = it }
    }

    fun ageBand(year: Int, month: Int): AgeBand = AgeBand.of(year, month, LocalDate.now(clock.withZone(ZoneId.systemDefault())))

    suspend fun saveProfile(name: String, year: Int, month: Int, adultAttested: Boolean) {
        require(ageBand(year, month) != AgeBand.UNDER_12) { "under_minimum_age" }
        dao.put(SettingEntity(NAME, name.trim().take(40)))
        dao.put(SettingEntity(BIRTH_YEAR, year.toString()))
        dao.put(SettingEntity(BIRTH_MONTH, month.toString()))
        dao.put(SettingEntity(ADULT, adultAttested.toString()))
        load()
    }

    /** R-PAR-01 : la protection automatique est-elle permise ? */
    fun protectionAllowed(): Boolean {
        val a = _account.value ?: return false
        return !ageBand(a.birthYear, a.birthMonth).needsGuardian || a.guardian == GuardianStatus.CONFIRMED
    }

    suspend fun requestGuardian(phoneE164: String, relationship: String = "parent") {
        dao.put(SettingEntity(GUARDIAN_PHONE, phoneE164))
        dao.put(SettingEntity(GUARDIAN_RELATION, relationship))
        dao.put(SettingEntity(GUARDIAN, GuardianStatus.PENDING.name))
        load()
    }

    /** Numéro et lien de l'adulte référent en attente (pour l'ajouter aux contacts à la confirmation). */
    suspend fun pendingGuardian(): Pair<String, String>? {
        val phone = dao.get(GUARDIAN_PHONE) ?: return null
        return phone to (dao.get(GUARDIAN_RELATION) ?: "parent")
    }

    /** Confirmation par l'adulte référent (serveur), ou simulée dans les builds de développement. */
    suspend fun simulateGuardianConfirmation() {
        dao.remove(GUARDIAN_PHONE) // comme le serveur : le numéro en clair disparaît à la décision
        dao.put(SettingEntity(GUARDIAN, GuardianStatus.CONFIRMED.name))
        load()
    }

    private companion object {
        const val NAME = "account.name"
        const val BIRTH_YEAR = "account.birthYear"
        const val BIRTH_MONTH = "account.birthMonth"
        const val ADULT = "account.adultAttested"
        const val GUARDIAN = "account.guardian"
        const val GUARDIAN_PHONE = "account.guardianPhone"
        const val GUARDIAN_RELATION = "account.guardianRelation"
    }
}
