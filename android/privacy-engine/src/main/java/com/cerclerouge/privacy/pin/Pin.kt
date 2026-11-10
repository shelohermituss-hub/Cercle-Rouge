package com.cerclerouge.privacy.pin

import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hachage du PIN : PBKDF2-HMAC-SHA256, 310 000 itérations, sel de 16 octets (docs/securite/cryptographie.md).
 * Le PIN n'est jamais stocké en clair ni envoyé au serveur (R-PE-02). Fournisseur de la plateforme, sans dépendance.
 */
object PinHasher {
    const val ITERATIONS = 310_000
    private const val KEY_BITS = 256

    fun hash(pin: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Comparaison en temps constant. */
    fun matches(pin: String, salt: ByteArray, expected: ByteArray, iterations: Int = ITERATIONS): Boolean =
        MessageDigest.isEqual(hash(pin, salt, iterations), expected)
}

/** Règles de choix d'un PIN : 4 à 8 chiffres, pas de suite triviale. */
object PinRules {
    enum class Problem { LENGTH, NOT_DIGITS, TOO_SIMPLE }

    fun check(pin: String): Problem? {
        if (pin.length !in 4..8) return Problem.LENGTH
        if (!pin.all { it in '0'..'9' }) return Problem.NOT_DIGITS
        val diffs = pin.zipWithNext { a, b -> b - a }.toSet()
        if (diffs.size == 1 && diffs.first() in setOf(-1, 0, 1)) return Problem.TOO_SIMPLE // 0000, 1234, 4321
        return null
    }
}

/**
 * Temporisation progressive après des échecs : 3 essais libres, puis 30 s, 1 min, 2 min… plafonnée à 15 min.
 * Le moteur d'alerte compte séparément les échecs pendant une alerte (R-SEC-02).
 */
object Lockout {
    private const val FREE_ATTEMPTS = 3
    private val MAX: Duration = Duration.ofMinutes(15)

    fun delayAfter(failures: Int): Duration {
        if (failures < FREE_ATTEMPTS) return Duration.ZERO
        val steps = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        return Duration.ofSeconds(30L shl steps).coerceAtMost(MAX)
    }

    fun lockedUntil(failures: Int, lastFailure: Instant): Instant = lastFailure + delayAfter(failures)
}
