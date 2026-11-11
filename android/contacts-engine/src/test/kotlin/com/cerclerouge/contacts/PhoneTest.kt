package com.cerclerouge.contacts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** R-CE-03, R-CON-07 : numéros normalisés en E.164, préfixe par défaut Haïti (+509). */
class PhoneTest {
    private fun ok(raw: String, expected: String) {
        val r = Phone.normalize(raw)
        assertIs<PhoneResult.Valid>(r, raw)
        assertEquals(expected, r.number.value)
    }

    @Test fun haitiLocalNumberGetsDefaultPrefix() {
        ok("3712 3456", "+50937123456")
        ok("37-12-34-56", "+50937123456")
        ok("(509) 3712 3456", "+50937123456")
    }

    @Test fun internationalFormsAreAccepted() {
        ok("+509 3712 3456", "+50937123456")
        ok("00509 37123456", "+50937123456")
        ok("+1 (305) 555-0147", "+13055550147")
        ok("+33 6 12 34 56 78", "+33612345678")
    }

    @Test fun otherDefaultPrefixIsUsedForLocalNumbers() {
        val r = Phone.normalize("0612345678", defaultPrefix = "+33")
        assertIs<PhoneResult.Valid>(r)
        assertEquals("+33612345678", r.number.value) // le 0 national tombe
    }

    @Test fun invalidNumbersAreRefused() {
        listOf("", "abc", "123", "+509 1234", "+509 123456789", "+0 123456789", "+1234567890123456", "37 12 34 5x").forEach {
            assertIs<PhoneResult.Invalid>(Phone.normalize(it), "« $it » doit être refusé")
        }
    }
}
