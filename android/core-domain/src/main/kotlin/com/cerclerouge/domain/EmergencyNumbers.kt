package com.cerclerouge.domain

/** Service d'urgence ; `GENERAL` : numéro unique (112, 911) qui joint tous les services. */
enum class EmergencyService { GENERAL, POLICE, AMBULANCE, FIRE }

/** Numéros d'urgence d'un pays (PDF 22). Le premier de `numbers` est celui du bouton « Appeler les secours ». */
data class EmergencyCountry(val iso: String, val numbers: List<Pair<EmergencyService, String>>) {
    val primary: String get() = numbers.first().second
}

/**
 * Numéros d'urgence par pays (1.18), choisis d'après le pays de la carte SIM ou du réseau, modifiables par l'utilisateur.
 * L'appel passe **toujours par le composeur** (R-HOME-07, R-AUD-03) : l'app ne compose jamais seule.
 * ⚠ Liste à faire relire avant le lancement de chaque pays. Pays inconnu : 112 (joignable depuis la plupart des mobiles GSM).
 */
object EmergencyNumbers {
    private val HAITI = EmergencyCountry(
        "HT",
        listOf(EmergencyService.POLICE to "114", EmergencyService.FIRE to "115", EmergencyService.AMBULANCE to "116")
    )

    val all: List<EmergencyCountry> = listOf(
        HAITI,
        EmergencyCountry(
            "FR",
            listOf(
                EmergencyService.GENERAL to "112",
                EmergencyService.POLICE to "17",
                EmergencyService.AMBULANCE to "15",
                EmergencyService.FIRE to "18"
            )
        ),
        EmergencyCountry("US", listOf(EmergencyService.GENERAL to "911")),
        EmergencyCountry("CA", listOf(EmergencyService.GENERAL to "911")),
        EmergencyCountry("DO", listOf(EmergencyService.GENERAL to "911")),
        EmergencyCountry(
            "BE",
            listOf(
                EmergencyService.GENERAL to "112",
                EmergencyService.POLICE to "101",
                EmergencyService.AMBULANCE to "100"
            )
        ),
        EmergencyCountry(
            "CH",
            listOf(
                EmergencyService.GENERAL to "112",
                EmergencyService.POLICE to "117",
                EmergencyService.AMBULANCE to "144",
                EmergencyService.FIRE to "118"
            )
        )
    )

    /** Pays de l'Union européenne (hors FR et BE déjà détaillés) : 112. */
    private val EU = setOf(
        "AT", "BG", "CY", "CZ", "DE", "DK", "EE", "ES", "FI", "GR", "HR", "HU", "IE", "IT", "LT", "LU", "LV", "MT", "NL", "PL",
        "PT", "RO", "SE", "SI", "SK"
    )

    private val FALLBACK = EmergencyCountry("", listOf(EmergencyService.GENERAL to "112"))

    /** Choix de l'utilisateur d'abord, puis pays de la SIM, puis du réseau ; Haïti (pays de lancement) si rien n'est connu. */
    fun resolve(userChoice: String?, simCountry: String?, networkCountry: String?): EmergencyCountry {
        listOf(userChoice, simCountry, networkCountry).forEach { iso ->
            forCountry(iso)?.let { return it }
        }
        return HAITI
    }

    /** Numéros d'un pays ; null si le code est vide. Pays non listé : 112. */
    fun forCountry(iso: String?): EmergencyCountry? {
        val code = iso?.trim()?.uppercase()?.takeIf { it.length == 2 } ?: return null
        return all.firstOrNull { it.iso == code } ?: if (code in EU) EmergencyCountry(code, FALLBACK.numbers) else FALLBACK.copy(iso = code)
    }
}
