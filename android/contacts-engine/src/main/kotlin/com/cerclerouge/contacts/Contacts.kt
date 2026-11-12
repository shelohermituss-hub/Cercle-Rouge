package com.cerclerouge.contacts

enum class Language { FR, HT, EN }

/**
 * Canaux préférés d'un proche (PDF 12.1). Utilisables aujourd'hui : SMS, e-mail (envoyé par le serveur). PUSH est sans objet
 * depuis ADR-0026 (pas de Firebase). WHATSAPP et CALL sont choisis dès maintenant mais n'enverront rien avant la phase 2.
 */
enum class Channel { SMS, PUSH, EMAIL, WHATSAPP, CALL }

enum class ContactGroup { FAMILY, FRIENDS, NEIGHBORS, WORK, CUSTOM }

/**
 * Rôles (PDF 12.2) : au plus un [PRIMARY] (contact principal) ; [BACKUP] = contact secondaire ; [MEDICAL] reçoit les infos
 * médicales si l'utilisateur les a autorisées ; [PROFESSIONAL] (travail) ; [NEIGHBOR] (voisinage, proche géographiquement).
 */
enum class ContactRole { PRIMARY, BACKUP, MEDICAL, PROFESSIONAL, NEIGHBOR, NONE }

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
    val hasApp: Boolean = false,
    /** E-mail du proche (PDF 12.1) : jamais envoyé au serveur avant une alerte, puis chiffré (R-CE-02). */
    val email: String? = null,
    /** Lien avec l'utilisateur en quelques mots (« sœur », « voisine »), affiché dans la liste. */
    val relation: String? = null
)

enum class BookError { FULL, DUPLICATE, SECOND_PRIMARY, INVALID_EMAIL }

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

    /**
     * Modifie un contact (nom, e-mail, relation, rôle, groupe, canaux, langue) sans toucher au numéro, au statut ni à l'ordre.
     * Donner le rôle principal à un contact le retire au précédent (qui devient secondaire). Un e-mail invalide est refusé.
     */
    fun edit(id: String, change: (TrustedContact) -> TrustedContact): BookResult {
        val old = contacts.firstOrNull { it.id == id } ?: return BookResult.Ok(this)
        val new = change(
            old
        ).copy(id = old.id, phone = old.phone, status = old.status, position = old.position, isGuardian = old.isGuardian)
        val email = new.email?.trim()?.takeIf { it.isNotEmpty() }
        if (email != null && !isEmail(email)) return BookResult.Refused(BookError.INVALID_EMAIL)
        val fixed = new.copy(
            email = email,
            relation = new.relation?.trim()?.take(MAX_RELATION)?.takeIf { it.isNotEmpty() },
            name = new.name.trim().take(MAX_NAME).ifBlank { old.name },
            channels = new.channels.distinct().ifEmpty { listOf(Channel.SMS) }
        )
        val demote = fixed.role == ContactRole.PRIMARY && old.role != ContactRole.PRIMARY
        return BookResult.Ok(
            ContactBook(
                contacts.map {
                    when {
                        it.id == id -> fixed
                        demote && it.role == ContactRole.PRIMARY -> it.copy(role = ContactRole.BACKUP)
                        else -> it
                    }
                }
            )
        )
    }

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
        const val MAX_NAME = 40
        const val MAX_RELATION = 30
        private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$")

        /** Vérification simple (une arobase, un domaine avec un point) : le serveur ne fait que transmettre. */
        fun isEmail(text: String) = text.length <= 254 && EMAIL.matches(text)
    }
}
