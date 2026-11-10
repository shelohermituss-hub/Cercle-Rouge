package com.cerclerouge.domain

import java.time.LocalDate
import java.time.Period

/** Tranche d'âge (ADR-0009). Même règle que la fonction SQL public.age_band côté Supabase. */
enum class AgeBand {
    UNDER_12,
    YOUNG_12_15,
    TEEN_16_17,
    ADULT
    ;

    /** Consentement d'un adulte référent requis (ADR-0013). */
    val needsGuardian: Boolean get() = this == YOUNG_12_15

    companion object {
        fun of(birthYear: Int, birthMonth: Int, today: LocalDate): AgeBand {
            val age = Period.between(LocalDate.of(birthYear, birthMonth, 1), today).years
            return when {
                age < 12 -> UNDER_12
                age < 16 -> YOUNG_12_15
                age < 18 -> TEEN_16_17
                else -> ADULT
            }
        }
    }
}

fun AgeBand.profile(): VulnerabilityProfile = when (this) {
    AgeBand.UNDER_12, AgeBand.YOUNG_12_15 -> VulnerabilityProfile.YOUNG_12_14
    AgeBand.TEEN_16_17 -> VulnerabilityProfile.TEEN_15_17
    AgeBand.ADULT -> VulnerabilityProfile.ADULT
}
