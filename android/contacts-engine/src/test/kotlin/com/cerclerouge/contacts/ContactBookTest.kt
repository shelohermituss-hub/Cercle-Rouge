package com.cerclerouge.contacts

import com.cerclerouge.domain.AlertLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactBookTest {
    private fun c(id: String, phone: String = "+5093712345${id.takeLast(1)}", role: ContactRole = ContactRole.NONE) = TrustedContact(
        id = id,
        name = "Contact $id",
        phone = E164(phone),
        group = ContactGroup.FAMILY,
        role = role,
        language = Language.FR,
        channels = listOf(Channel.SMS),
        status = ContactStatus.ACCEPTED,
        position = 0
    )

    private fun book(vararg ids: String) = ids.fold(ContactBook()) { b, id -> (b.add(c(id)) as BookResult.Ok).book }

    @Test fun maximumFiveContacts() { // R-CON-02
        val full = book("c1", "c2", "c3", "c4", "c5")
        assertEquals(5, full.contacts.size)
        assertEquals(BookError.FULL, (full.add(c("c6", phone = "+50937123456")) as BookResult.Refused).error)
    }

    @Test fun duplicatePhoneRefused() {
        val b = book("c1")
        val r = b.add(c("c2", phone = b.contacts.first().phone.value))
        assertEquals(BookError.DUPLICATE, (r as BookResult.Refused).error)
    }

    @Test fun onlyOnePrimaryResponder() {
        val b = (ContactBook().add(c("c1", role = ContactRole.PRIMARY)) as BookResult.Ok).book
        val r = b.add(c("c2", role = ContactRole.PRIMARY))
        assertEquals(BookError.SECOND_PRIMARY, (r as BookResult.Refused).error)
    }

    @Test fun positionsAreRenumberedAndReorderable() {
        val b = book("c1", "c2", "c3")
        assertEquals(listOf(0, 1, 2), b.contacts.map { it.position })
        val r = b.reorder(listOf("c3", "c1", "c2"))
        assertEquals(listOf("c3", "c1", "c2"), r.contacts.map { it.id })
        assertEquals(listOf(0, 1, 2), r.contacts.map { it.position })
        val removed = r.remove("c1")
        assertEquals(listOf("c3", "c2"), removed.contacts.map { it.id })
        assertEquals(listOf(0, 1), removed.contacts.map { it.position })
    }

    @Test fun reorderWithWrongIdsKeepsOrder() {
        val b = book("c1", "c2")
        assertEquals(listOf("c1", "c2"), b.reorder(listOf("c1")).contacts.map { it.id })
        assertEquals(listOf("c1", "c2"), b.reorder(listOf("c1", "zz")).contacts.map { it.id })
    }

    @Test fun guardianIsAddedByDefaultAsPrimaryAndCountsAmongFive() { // S2.03, Q3c
        val b = ContactBook().withGuardian("g1", "Tante Marie", E164("+50937000001"), Language.HT)
        val g = b.contacts.single()
        assertTrue(g.isGuardian)
        assertEquals(ContactRole.PRIMARY, g.role)
        assertEquals(ContactStatus.ACCEPTED, g.status) // l'adulte a déjà confirmé le consentement
        val full = listOf("c1", "c2", "c3", "c4").fold(b) { acc, id ->
            (acc.add(c(id, phone = "+5093712345${id.last()}")) as BookResult.Ok).book
        }
        assertEquals(5, full.contacts.size)
        assertEquals(BookError.FULL, (full.add(c("c5", phone = "+50937123459")) as BookResult.Refused).error)
    }

    @Test fun guardianCanBeRemovedSilently() { // R-PAR-07 : retirer l'adulte référent ne produit aucun effet
        val b = ContactBook().withGuardian("g1", "Tante Marie", E164("+50937000001"), Language.FR)
        val after = b.remove("g1")
        assertTrue(after.contacts.isEmpty())
        // `remove` ne renvoie qu'un carnet : aucune notification ni retrait de consentement possible ici.
    }

    @Test fun guardianAddedOnlyOnce() {
        val b = ContactBook().withGuardian(
            "g1",
            "A",
            E164("+50937000001"),
            Language.FR
        ).withGuardian("g2", "A", E164("+50937000001"), Language.FR)
        assertEquals(1, b.contacts.size)
    }

    @Test fun editKeepsPhoneStatusAndOrderAndValidatesEmail() { // PDF 12.1
        val b = book("c1", "c2")
        val phone = b.contacts[0].phone
        val r = b.edit("c1") {
            it.copy(name = "  Tante Marie ", email = " marie@example.org ", relation = "sœur", phone = E164("+50930000000"))
        }
        val e = (r as BookResult.Ok).book.contacts[0]
        assertEquals("Tante Marie", e.name)
        assertEquals("marie@example.org", e.email)
        assertEquals("sœur", e.relation)
        assertEquals(phone, e.phone) // le numéro ne change pas par une modification
        assertEquals(0, e.position)
        assertEquals(BookResult.Refused(BookError.INVALID_EMAIL), b.edit("c1") { it.copy(email = "pas-un-email") })
        assertEquals(null, ((b.edit("c1") { it.copy(email = "  ") }) as BookResult.Ok).book.contacts[0].email)
    }

    @Test fun makingSomeonePrimaryDemotesThePreviousOne() { // un seul contact principal
        val b = (book("c1", "c2").edit("c1") { it.copy(role = ContactRole.PRIMARY) } as BookResult.Ok).book
        val r = (b.edit("c2") { it.copy(role = ContactRole.PRIMARY) } as BookResult.Ok).book
        assertEquals(ContactRole.BACKUP, r.contacts[0].role)
        assertEquals(ContactRole.PRIMARY, r.contacts[1].role)
        assertEquals(1, r.contacts.count { it.role == ContactRole.PRIMARY })
    }

    @Test fun emptyChannelsFallBackToSms() {
        val r = (book("c1").edit("c1") { it.copy(channels = emptyList()) } as BookResult.Ok).book
        assertEquals(listOf(Channel.SMS), r.contacts[0].channels)
    }

    @Test fun emailChannelNeedsAnAddressAndConsent() { // canaux utilisables
        val c =
            TrustedContact(
                "e", "E",
                E164(
                    "+50937000099"
                ),
                ContactGroup.FAMILY, ContactRole.NONE, Language.FR,
                listOf(
                    Channel.EMAIL,
                    Channel.WHATSAPP,
                    Channel.SMS
                ),
                ContactStatus.ACCEPTED, 0, email = "e@example.org"
            )
        val online = DeviceStatus(hasData = true, canSendSms = true)
        val channels = resolveRecipients(AlertLevel.CONTACTS, true, listOf(c), online).map { it.channel }
        assertEquals(listOf(Channel.EMAIL, Channel.SMS), channels) // WhatsApp : pas encore
        val noMail = resolveRecipients(AlertLevel.CONTACTS, true, listOf(c.copy(email = null)), online).map { it.channel }
        assertEquals(listOf(Channel.SMS), noMail)
    }
}
