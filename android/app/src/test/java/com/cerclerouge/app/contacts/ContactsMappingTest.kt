package com.cerclerouge.app.contacts

import com.cerclerouge.contacts.Channel
import com.cerclerouge.contacts.ContactBook
import com.cerclerouge.contacts.ContactGroup
import com.cerclerouge.contacts.ContactRole
import com.cerclerouge.contacts.ContactStatus
import com.cerclerouge.contacts.E164
import com.cerclerouge.contacts.Language
import com.cerclerouge.contacts.TrustedContact
import com.cerclerouge.privacy.db.ContactEntity
import kotlin.test.Test
import kotlin.test.assertEquals

/** Le carnet doit ressortir de la base exactement comme il y est entré (statuts, ordre, canaux, adulte référent). */
class ContactsMappingTest {
    private val contact = TrustedContact(
        id = "c1",
        name = "Tante Marie",
        phone = E164("+50937123456"),
        group = ContactGroup.NEIGHBORS,
        role = ContactRole.BACKUP,
        language = Language.HT,
        channels = listOf(Channel.PUSH, Channel.SMS),
        status = ContactStatus.WITHDRAWN,
        position = 3,
        isGuardian = true,
        hasApp = true,
        email = "marie@example.org",
        relation = "tante"
    )

    @Test fun roundTripKeepsEveryField() {
        assertEquals(contact, contact.toEntity().toContact())
    }

    @Test fun entityStoresReadableColumns() {
        val e: ContactEntity = contact.toEntity()
        assertEquals("PUSH,SMS", e.channels)
        assertEquals("NEIGHBORS", e.grp)
        assertEquals(3, e.position)
    }

    @Test fun emptyChannelsSurvive() {
        assertEquals(emptyList(), contact.copy(channels = emptyList()).toEntity().toContact().channels)
    }

    @Test fun bookKeepsOrderThroughMapping() {
        val book =
            ContactBook(listOf(contact.copy(id = "a", position = 0), contact.copy(id = "b", position = 1, phone = E164("+50937000002"))))
        val back = ContactBook(book.contacts.map { it.toEntity() }.sortedBy { it.position }.map { it.toContact() })
        assertEquals(book, back)
    }
}
