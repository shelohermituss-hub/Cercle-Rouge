package com.cerclerouge.contacts

import com.cerclerouge.domain.AlertLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Table de résolution des destinataires (R-CE-01, R-ALR-01, ADR-0015, ADR-0017). */
class RecipientsTest {
    private val online = DeviceStatus(hasData = true, canSendSms = true)
    private val offline = DeviceStatus(hasData = false, canSendSms = true)

    private fun c(
        id: String,
        pos: Int,
        role: ContactRole = ContactRole.NONE,
        status: ContactStatus = ContactStatus.ACCEPTED,
        channels: List<Channel> = listOf(Channel.SMS),
        app: Boolean = false
    ) = TrustedContact(id, id, E164("+509370000$pos$pos"), ContactGroup.FAMILY, role, Language.FR, channels, status, pos, hasApp = app)

    private val five = listOf(
        c("a", 0),
        c("b", 1, ContactRole.PRIMARY),
        c("c", 2, ContactRole.BACKUP),
        c("d", 3, ContactRole.BACKUP),
        c("e", 4, ContactRole.BACKUP)
    )

    private fun ids(d: List<Delivery>) = d.map { it.contactId }.distinct()

    @Test fun belowLevelTwoNobodyIsAlerted() {
        assertTrue(resolveRecipients(AlertLevel.INFO, false, five, online).isEmpty())
        assertTrue(resolveRecipients(AlertLevel.VERIFICATION, false, five, online).isEmpty())
    }

    @Test fun authoritiesLevelNeverSendsAnything() { // niveau 4 = composeur seulement (R-ESC-02)
        assertTrue(resolveRecipients(AlertLevel.AUTHORITIES, true, five, online).isEmpty())
    }

    @Test fun levelTwoIsPrimaryThenBackupsMaxThree() {
        assertEquals(listOf("b", "c", "d"), ids(resolveRecipients(AlertLevel.CONTACTS, false, five, online)))
    }

    @Test fun levelTwoWithoutRolesTakesTheFirstThreeInOrder() {
        val plain = listOf(c("a", 0), c("b", 1), c("c", 2), c("d", 3))
        assertEquals(listOf("a", "b", "c"), ids(resolveRecipients(AlertLevel.CONTACTS, false, plain, online)))
    }

    @Test fun levelThreeAlertsEveryone() {
        assertEquals(listOf("a", "b", "c", "d", "e"), ids(resolveRecipients(AlertLevel.EXTENDED, false, five, online)))
    }

    @Test fun automaticAlertsOnlyReachAcceptedContacts() {
        val mixed = listOf(
            c("a", 0, status = ContactStatus.PENDING),
            c("b", 1),
            c("c", 2, status = ContactStatus.DECLINED),
            c("d", 3, status = ContactStatus.WITHDRAWN)
        )
        assertEquals(listOf("b"), ids(resolveRecipients(AlertLevel.EXTENDED, false, mixed, online)))
    }

    @Test fun manualSosAlsoReachesPendingContactsButNeverDeclinedOrWithdrawn() { // ADR-0015
        val mixed = listOf(
            c("a", 0, status = ContactStatus.PENDING),
            c("b", 1),
            c("c", 2, status = ContactStatus.DECLINED),
            c("d", 3, status = ContactStatus.WITHDRAWN)
        )
        val d = resolveRecipients(AlertLevel.EXTENDED, true, mixed, online)
        assertEquals(listOf("a", "b"), ids(d))
        assertTrue(d.first { it.contactId == "a" }.unconsented)
        assertFalse(d.first { it.contactId == "b" }.unconsented)
    }

    @Test fun unconsentedContactsOnlyGetSms() {
        val pending = c("a", 0, status = ContactStatus.PENDING, channels = listOf(Channel.PUSH, Channel.SMS), app = true)
        val d = resolveRecipients(AlertLevel.EXTENDED, true, listOf(pending), online)
        assertEquals(listOf(Channel.SMS), d.map { it.channel })
    }

    @Test fun twoIndependentChannelsWhenTheyExist() { // R-ALR-01
        val both = c("a", 0, channels = listOf(Channel.PUSH, Channel.SMS), app = true)
        val d = resolveRecipients(AlertLevel.EXTENDED, false, listOf(both), online)
        assertEquals(listOf(Channel.PUSH, Channel.SMS), d.map { it.channel })
    }

    @Test fun withoutDataSmsComesFirstAndPushIsDropped() { // « GSM d'abord si pas de données »
        val both = c("a", 0, channels = listOf(Channel.PUSH, Channel.SMS), app = true)
        val d = resolveRecipients(AlertLevel.EXTENDED, false, listOf(both), offline)
        assertEquals(listOf(Channel.SMS), d.map { it.channel })
    }

    @Test fun pushNeedsTheContactToHaveTheApp() {
        val noApp = c("a", 0, channels = listOf(Channel.PUSH, Channel.SMS), app = false)
        assertEquals(listOf(Channel.SMS), resolveRecipients(AlertLevel.EXTENDED, false, listOf(noApp), online).map { it.channel })
    }

    @Test fun noUsableChannelFallsBackToSmsSoTheQueueKeepsIt() {
        val pushOnly = c("a", 0, channels = listOf(Channel.PUSH), app = true)
        val d = resolveRecipients(AlertLevel.EXTENDED, false, listOf(pushOnly), offline)
        assertEquals(listOf(Channel.SMS), d.map { it.channel })
    }

    @Test fun neverMoreThanFiveContactsEvenIfTheListIsLonger() {
        val seven = (0..6).map { c("c$it", it) }
        assertEquals(5, ids(resolveRecipients(AlertLevel.EXTENDED, false, seven, online)).size)
    }
}
