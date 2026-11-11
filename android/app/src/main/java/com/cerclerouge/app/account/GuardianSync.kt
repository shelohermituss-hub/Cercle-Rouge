package com.cerclerouge.app.account

import com.cerclerouge.app.contacts.ContactsRepository
import com.cerclerouge.app.server.ServerSync
import com.cerclerouge.contacts.Language
import com.cerclerouge.contacts.Phone
import com.cerclerouge.contacts.PhoneResult
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accord de l'adulte référent pour un jeune de 12 à 15 ans (ADR-0013, S1.22 à S1.24), côté jeune :
 * envoi de la demande (serveur + SMS), puis vérification de la confirmation. À la confirmation, l'adulte devient contact
 * de confiance par défaut (S2.03) et la protection automatique se débloque (R-PAR-01).
 */
@Singleton
class GuardianSync @Inject constructor(
    private val account: AccountRepository,
    private val contacts: ContactsRepository,
    private val server: ServerSync
) {
    /** Null si la demande est partie, sinon un code d'erreur du serveur. */
    suspend fun request(rawPhone: String, relationship: String): String? {
        val phone = (Phone.normalize(rawPhone) as? PhoneResult.Valid)?.number?.value ?: return "invalid_phone"
        val lang = Locale.getDefault().language.takeIf { it in setOf("fr", "ht", "en") } ?: "fr"
        val error = server.requestGuardian(phone, relationship, lang)
        if (error == null) account.requestGuardian(phone, relationship)
        return error
    }

    /** Vérifie sur le serveur si l'adulte a confirmé. Vrai si la confirmation vient d'être enregistrée. */
    suspend fun refresh(): Boolean {
        val a = account.account.value ?: account.load() ?: return false
        if (a.guardian != GuardianStatus.PENDING) return false
        if (server.guardianConfirmed() != true) return false
        confirmLocally()
        return true
    }

    /** Ajoute l'adulte aux contacts et débloque la protection (aussi utilisé par la simulation des builds de test). */
    suspend fun confirmLocally() {
        account.pendingGuardian()?.let { (phone, relationship) ->
            (Phone.normalize(phone) as? PhoneResult.Valid)?.let {
                contacts.load()
                contacts.addGuardian(relationship.replaceFirstChar { c -> c.uppercase() }, it.number, Language.FR)
            }
        }
        account.simulateGuardianConfirmation()
    }
}
