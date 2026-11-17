package com.cerclerouge.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmergencyNumbersTest {
    @Test fun haitiUsesPolice114WithFireAndAmbulance() {
        val ht = EmergencyNumbers.resolve(null, "ht", null)
        assertEquals("114", ht.primary)
        assertEquals(listOf("114", "115", "116"), ht.numbers.map { it.second })
    }

    @Test fun userChoiceWinsOverSimAndNetwork() {
        assertEquals("FR", EmergencyNumbers.resolve("fr", "ht", "us").iso)
        assertEquals("112", EmergencyNumbers.resolve("fr", "ht", "us").primary)
    }

    @Test fun simWinsOverNetworkThenNetworkThenHaiti() {
        assertEquals("911", EmergencyNumbers.resolve(null, "us", "ht").primary)
        assertEquals("911", EmergencyNumbers.resolve(null, "", "ca").primary)
        assertEquals("114", EmergencyNumbers.resolve(null, null, null).primary) // pays de lancement
    }

    @Test fun europeAndUnknownCountriesFallBackTo112() {
        assertEquals("112", EmergencyNumbers.resolve(null, "de", null).primary)
        assertEquals("112", EmergencyNumbers.resolve(null, "jp", null).primary)
        assertNull(EmergencyNumbers.forCountry("x"))
    }
}
