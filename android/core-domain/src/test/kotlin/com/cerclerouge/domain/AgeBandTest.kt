package com.cerclerouge.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AgeBandTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test fun `ADR-0009 tranches d age au mois pres`() {
        assertEquals(AgeBand.UNDER_12, AgeBand.of(2014, 12, today)) // 11 ans
        assertEquals(AgeBand.YOUNG_12_15, AgeBand.of(2014, 11, today)) // 12 ans ce mois-ci
        assertEquals(AgeBand.YOUNG_12_15, AgeBand.of(2010, 12, today)) // 15 ans
        assertEquals(AgeBand.TEEN_16_17, AgeBand.of(2010, 11, today)) // 16 ans
        assertEquals(AgeBand.ADULT, AgeBand.of(2008, 11, today)) // 18 ans
    }

    @Test fun `seuls les 12-15 ans ont besoin d un adulte referent`() {
        assertEquals(listOf(AgeBand.YOUNG_12_15), AgeBand.entries.filter { it.needsGuardian })
    }
}
