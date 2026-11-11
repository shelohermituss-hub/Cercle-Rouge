package com.cerclerouge.contacts

import java.time.Duration
import java.time.Instant

enum class BlockReason { CONTACT_RATE_LIMIT, ALERT_SMS_CAP, TEST_DAILY_CAP }

sealed interface Decision {
    data object Allow : Decision
    data class Block(val reason: BlockReason) : Decision
}

data class SendRecord(val contactId: String, val alertId: String, val at: Instant, val test: Boolean, val sms: Int)

/**
 * Registre des envois pour l'anti-spam (R-ALR-06, R-CON-06) :
 * 1 alerte réelle par contact toutes les 2 min (les mises à jour de la même alerte ne comptent pas),
 * 12 SMS au maximum par alerte (chaque segment compte), 3 tests par contact sur 24 h glissantes (sans effet sur les vraies alertes).
 */
data class SendLedger(val records: List<SendRecord> = emptyList()) {
    fun record(contactId: String, alertId: String, at: Instant, test: Boolean, sms: Int) = copy(
        records =
        records + SendRecord(contactId, alertId, at, test, sms)
    )

    fun check(contactId: String, alertId: String, test: Boolean, sms: Int, now: Instant): Decision {
        if (test) {
            val today = records.count { it.test && it.contactId == contactId && Duration.between(it.at, now) < DAY }
            return if (today >= MAX_TESTS_PER_DAY) Decision.Block(BlockReason.TEST_DAILY_CAP) else Decision.Allow
        }
        val recent = records.any {
            !it.test && it.contactId == contactId && it.alertId != alertId && Duration.between(it.at, now) < MIN_GAP
        }
        if (recent) return Decision.Block(BlockReason.CONTACT_RATE_LIMIT)
        val used = records.filter { !it.test && it.alertId == alertId }.sumOf { it.sms }
        return if (used + sms > MAX_SMS_PER_ALERT) Decision.Block(BlockReason.ALERT_SMS_CAP) else Decision.Allow
    }

    private companion object {
        val MIN_GAP: Duration = Duration.ofMinutes(2)
        val DAY: Duration = Duration.ofHours(24)
        const val MAX_SMS_PER_ALERT = 12
        const val MAX_TESTS_PER_DAY = 3
    }
}
