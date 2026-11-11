package com.cerclerouge.contacts

enum class Language { FR, HT, EN }

/** Canaux utilisables par ce module. L'appel automatique est en V2 (ADR-0010), WhatsApp et e-mail viennent avec le serveur. */
enum class Channel { SMS, PUSH }

enum class ContactGroup { FAMILY, FRIENDS, NEIGHBORS, WORK, CUSTOM }

/** Au plus un [PRIMARY] (répondant principal). [MEDICAL] reçoit les infos médicales si l'utilisateur les a autorisées. */
enum class ContactRole { PRIMARY, BACKUP, MEDICAL, NONE }

enum class ContactStatus { PENDING, ACCEPTED, DECLINED, WITHDRAWN }

/**
 * Contact de confiance (module produit 7). `position` = ordre d'alerte (0 = premier). `hasApp` : le proche a l'application
 * (condition d'une notification push). `isGuardian` : adulte référent d'un mineur (ADR-0013).
 */
data class TrustedContact(
    val id: String,
    val name: String,
    val phone: E164,
    val group: ContactGroup,
    val role: ContactRole,
    val language: Language,
    val channels: List<Channel>,
    val status: ContactStatus,
    val position: Int,
    val isGuardian: Boolean = false,
    val hasApp: Boolean = false
)

enum class BookError { FULL, DUPLICATE, SECOND_PRIMARY }

sealed interface BookResult {
    data class Ok(val book: ContactBook) : BookResult
    data class Refused(val error: BookError) : BookResult
}

/**
 * Carnet de contacts : 5 au maximum, adulte référent compris (R-CON-02), numéros uniques, un seul répondant principal,
 * ordre d'alerte renuméroté sans trou. Immuable : chaque opération renvoie un nouveau carnet. Retirer un contact ne produit
 * aucun effet (ni notification, ni retrait de consentement) : c'est vrai en particulier pour l'adulte référent (R-PAR-07).
 */
data class ContactBook(val contacts: List<TrustedContact> = emptyList()) {
    fun add(contact: TrustedContact): BookResult = when {
        contacts.size >= MAX -> BookResult.Refused(BookError.FULL)
        contacts.any { it.phone == contact.phone } -> BookResult.Refused(BookError.DUPLICATE)
        contact.role == ContactRole.PRIMARY &&
            contacts.any {
                it.role == ContactRole.PRIMARY
            } -> BookResult.Refused(BookError.SECOND_PRIMARY)
        else -> BookResult.Ok(ContactBook(renumber(contacts + contact)))
    }

    fun remove(id: String) = ContactBook(renumber(contacts.filterNot { it.id == id }))

    /** `ids` doit être une permutation exacte des identifiants actuels ; sinon l'ordre reste inchangé. */
    fun reorder(ids: List<String>): ContactBook {
        if (ids.size != contacts.size || ids.toSet() != contacts.map { it.id }.toSet()) return this
        val byId = contacts.associateBy { it.id }
        return ContactBook(renumber(ids.map { byId.getValue(it) }))
    }

    /**
     * Ajoute l'adulte référent comme contact par défaut (Q3c) : répondant principal s'il n'y en a pas, déjà « accepté »
     * (il a confirmé le consentement), en tête de liste. Sans effet s'il est déjà là, ou si le carnet est plein.
     */
    fun withGuardian(id: String, name: String, phone: E164, language: Language): ContactBook {
        if (contacts.any { it.isGuardian || it.phone == phone }) return this
        val primary = if (contacts.none { it.role == ContactRole.PRIMARY }) ContactRole.PRIMARY else ContactRole.BACKUP
        val guardian = TrustedContact(
            id, name, phone, ContactGroup.FAMILY, primary, language, listOf(Channel.SMS, Channel.PUSH), ContactStatus.ACCEPTED, 0,
            isGuardian = true
        )
        if (contacts.size >= MAX) return this
        return ContactBook(renumber(listOf(guardian) + contacts))
    }

    private fun renumber(list: List<TrustedContact>) = list.mapIndexed { i, c -> c.copy(position = i) }

    companion object {
        const val MAX = 5
    }
}
