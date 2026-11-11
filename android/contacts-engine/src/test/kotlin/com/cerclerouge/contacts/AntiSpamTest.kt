package com.cerclerouge.contacts

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** R-ALR-06, R-CON-06 : anti-spam. */
class AntiSpamTest {
    private val t0 = Instant.parse("2026-11-11T10:00:00Z")

    @Test fun oneRealAlertPerContactEveryTwoMinutes() {
        val l = SendLedger().record("c1", "A1", t0, test = false, sms = 1)
        val other = l.check("c1", "A2", test = false, sms = 1, now = t0.plusSeconds(90))
        assertEquals(Decision.Block(BlockReason.CONTACT_RATE_LIMIT), other)
        assertEquals(Decision.Allow, l.check("c1", "A2", test = false, sms = 1, now = t0.plusSeconds(121)))
        assertEquals(Decision.Allow, l.check("c2", "A2", test = false, sms = 1, now = t0.plusSeconds(10))) // autre contact
    }

    @Test fun updatesOfTheSameAlertAreNotRateLimited() {
        val l = SendLedger().record("c1", "A1", t0, test = false, sms = 1)
        assertEquals(Decision.Allow, l.check("c1", "A1", test = false, sms = 1, now = t0.plusSeconds(30)))
    }

    @Test fun twelveSmsMaximumPerAlert() {
        var l = SendLedger()
        repeat(12) { l = l.record("c${it % 5}", "A1", t0.plusSeconds(it.toLong()), test = false, sms = 1) }
        assertEquals(Decision.Block(BlockReason.ALERT_SMS_CAP), l.check("c0", "A1", test = false, sms = 1, now = t0.plusSeconds(60)))
        assertEquals(Decision.Allow, l.check("c0", "A2", test = false, sms = 1, now = t0.plusSeconds(400)))
    }

    @Test fun multipartMessagesCountEachSegment() {
        val l = SendLedger().record("c1", "A1", t0, test = false, sms = 10)
        assertEquals(Decision.Allow, l.check("c1", "A1", test = false, sms = 2, now = t0.plusSeconds(5)))
        assertIs<Decision.Block>(l.check("c1", "A1", test = false, sms = 3, now = t0.plusSeconds(5)))
    }

    @Test fun threeTestsPerDayAndPerContact() {
        var l = SendLedger()
        repeat(3) { l = l.record("c1", "T$it", t0.plus(Duration.ofHours(it.toLong())), test = true, sms = 1) }
        assertEquals(
            Decision.Block(BlockReason.TEST_DAILY_CAP),
            l.check("c1", "T9", test = true, sms = 1, now = t0.plus(Duration.ofHours(5)))
        )
        assertEquals(Decision.Allow, l.check("c2", "T9", test = true, sms = 1, now = t0.plus(Duration.ofHours(5))))
        assertEquals(Decision.Allow, l.check("c1", "T9", test = true, sms = 1, now = t0.plus(Duration.ofHours(25)))) // fenêtre de 24 h
    }

    @Test fun testsNeverBlockRealAlerts() {
        var l = SendLedger()
        repeat(3) { l = l.record("c1", "T$it", t0, test = true, sms = 1) }
        assertEquals(Decision.Allow, l.check("c1", "A1", test = false, sms = 1, now = t0.plusSeconds(5)))
    }
}
