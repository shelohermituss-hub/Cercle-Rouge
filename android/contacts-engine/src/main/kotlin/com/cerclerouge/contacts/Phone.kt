package com.cerclerouge.contacts

/** Numéro au format E.164 (« +50937123456 »), toujours validé avant construction par [Phone.normalize]. */
@JvmInline
value class E164(val value: String)

sealed interface PhoneResult {
    data class Valid(val number: E164) : PhoneResult
    data class Invalid(val reason: String) : PhoneResult
}

/**
 * Normalisation E.164 (R-CE-03, R-CON-07). Choix volontairement simple : sans libphonenumber, on vérifie la forme
 * E.164 (+ chiffre 1–9, 8 à 15 chiffres) et la longueur exacte pour Haïti (+509 suivi de 8 chiffres). Le préfixe par
 * défaut est celui de la carte SIM (Haïti +509 à défaut).
 */
object Phone {
    const val HAITI = "+509"
    private val e164 = Regex("""^\+[1-9][0-9]{7,14}$""")

    fun normalize(raw: String, defaultPrefix: String = HAITI): PhoneResult {
        val cleaned = raw.trim().filterNot { it in " -.()" }
        if (cleaned.isEmpty()) return PhoneResult.Invalid("vide")
        if (cleaned.any { !it.isDigit() && it != '+' } || cleaned.drop(1).contains('+')) return PhoneResult.Invalid("caractères interdits")
        val international = when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("00") -> "+" + cleaned.drop(2)
            else -> {
                // Numéro local : on retire le 0 national (France, etc.) et on ajoute le préfixe de la SIM.
                val digits = cleaned.removePrefix("0").takeIf { defaultPrefix != HAITI } ?: cleaned
                // Haïti : un numéro local a 8 chiffres, ou 11 s'il commence déjà par 509.
                if (defaultPrefix == HAITI && digits.startsWith("509") && digits.length == 11) "+$digits" else defaultPrefix + digits
            }
        }
        if (!e164.matches(international)) return PhoneResult.Invalid("forme E.164 invalide")
        if (international.startsWith(HAITI) &&
            international.length != HAITI.length + 8
        ) {
            return PhoneResult.Invalid("un numéro haïtien a 8 chiffres")
        }
        return PhoneResult.Valid(E164(international))
    }
}
