package com.cerclerouge.app.contacts

import com.cerclerouge.contacts.BookResult
import com.cerclerouge.contacts.Channel
import com.cerclerouge.contacts.ContactBook
import com.cerclerouge.contacts.ContactGroup
import com.cerclerouge.contacts.ContactRole
import com.cerclerouge.contacts.ContactStatus
import com.cerclerouge.contacts.E164
import com.cerclerouge.contacts.Language
import com.cerclerouge.contacts.Phone
import com.cerclerouge.contacts.PhoneResult
import com.cerclerouge.contacts.TrustedContact
import com.cerclerouge.privacy.db.ContactEntity
import com.cerclerouge.privacy.db.PrivacyDatabase
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Résultat d'un ajout depuis l'écran : un numéro invalide bloque l'ajout avec un message clair (R-CE-03). */
sealed interface AddOutcome {
    data object Added : AddOutcome
    data class InvalidPhone(val reason: String) : AddOutcome
    data class Refused(val error: com.cerclerouge.contacts.BookError) : AddOutcome
}

/**
 * Carnet de contacts de confiance, stocké dans la base chiffrée. La logique (limites, ordre, doublons) est dans
 * `contacts-engine` ; ce dépôt la persiste. Les invitations (statut PENDING → ACCEPTED) viendront avec le serveur au Sprint 3 :
 * en attendant, un nouveau contact reste PENDING et ne reçoit que le SOS manuel (ADR-0015).
 */
@Singleton
class ContactsRepository @Inject constructor(db: PrivacyDatabase) {
    private val dao = db.contacts()
    private val mutex = Mutex()
    private val _book = MutableStateFlow(ContactBook())
    val book: StateFlow<ContactBook> = _book

    suspend fun load() = mutex.withLock { _book.value = ContactBook(dao.all().map { it.toContact() }) }

    suspend fun add(name: String, rawPhone: String, defaultPrefix: String = Phone.HAITI): AddOutcome = mutex.withLock {
        val phone = when (val r = Phone.normalize(rawPhone, defaultPrefix)) {
            is PhoneResult.Valid -> r.number
            is PhoneResult.Invalid -> return@withLock AddOutcome.InvalidPhone(r.reason)
        }
        val contact = TrustedContact(
            id = UUID.randomUUID().toString(),
            name = name.trim().take(40).ifBlank { phone.value },
            phone = phone,
            group = ContactGroup.FAMILY,
            role = ContactRole.NONE,
            language = Language.FR,
            channels = listOf(Channel.SMS, Channel.PUSH),
            status = ContactStatus.PENDING,
            position = 0
        )
        when (val r = _book.value.add(contact)) {
            is BookResult.Ok -> {
                save(r.book)
                AddOutcome.Added
            }
            is BookResult.Refused -> AddOutcome.Refused(r.error)
        }
    }

    suspend fun remove(id: String) = mutex.withLock { save(_book.value.remove(id)) }

    /** Décale un contact dans l'ordre d'alerte (delta −1 = plus haut). */
    suspend fun move(id: String, delta: Int) = mutex.withLock {
        val ids = _book.value.contacts.map { it.id }.toMutableList()
        val i = ids.indexOf(id)
        val j = i + delta
        if (i < 0 || j !in ids.indices) return@withLock
        ids.add(j, ids.removeAt(i))
        save(_book.value.reorder(ids))
    }

    /** Répondant principal : au plus un ; en désigner un autre retire le rôle au précédent. */
    suspend fun setPrimary(id: String) = mutex.withLock {
        save(
            _book.value.update { c ->
                c.copy(
                    role = if (c.id ==
                        id
                    ) {
                        ContactRole.PRIMARY
                    } else if (c.role == ContactRole.PRIMARY) {
                        ContactRole.BACKUP
                    } else {
                        c.role
                    }
                )
            }
        )
    }

    suspend fun setLanguage(id: String, language: Language) = mutex.withLock {
        save(_book.value.update { c -> if (c.id == id) c.copy(language = language) else c })
    }

    /** Adulte référent ajouté par défaut comme contact (Q3c, S2.03). Sans effet s'il y est déjà ou si le carnet est plein. */
    suspend fun addGuardian(name: String, phone: E164, language: Language) = mutex.withLock {
        save(_book.value.withGuardian(UUID.randomUUID().toString(), name, phone, language))
    }

    /** Statuts relus sur le serveur (accord, refus, STOP) : seuls les contacts connus du serveur changent. */
    suspend fun applyServerStatuses(statuses: Map<String, ContactStatus>) = mutex.withLock {
        if (statuses.isEmpty()) return@withLock
        val book = _book.value
        val updated = book.update { c -> statuses[c.id]?.let { s -> c.copy(status = s) } ?: c }
        if (updated != book) save(updated)
    }

    /** Builds de développement : simule l'acceptation d'un contact sans passer par le SMS. */
    suspend fun simulateAccepted(id: String) = mutex.withLock {
        save(_book.value.update { c -> if (c.id == id) c.copy(status = ContactStatus.ACCEPTED) else c })
    }

    private suspend fun save(book: ContactBook) {
        dao.replaceAll(book.contacts.map { it.toEntity() })
        _book.value = book
    }
}

private fun ContactBook.update(change: (TrustedContact) -> TrustedContact) = ContactBook(contacts.map(change))

internal fun TrustedContact.toEntity() = ContactEntity(
    id, name, phone.value, group.name, role.name, language.name, channels.joinToString(",") { it.name }, status.name, position,
    isGuardian, hasApp
)

internal fun ContactEntity.toContact() = TrustedContact(
    id = id,
    name = name,
    phone = E164(phone),
    group = ContactGroup.valueOf(grp),
    role = ContactRole.valueOf(role),
    language = Language.valueOf(language),
    channels = channels.split(',').filter { it.isNotBlank() }.map { Channel.valueOf(it) },
    status = ContactStatus.valueOf(status),
    position = position,
    isGuardian = isGuardian,
    hasApp = hasApp
)
