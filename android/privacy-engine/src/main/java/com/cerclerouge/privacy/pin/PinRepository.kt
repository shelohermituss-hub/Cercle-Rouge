package com.cerclerouge.privacy.pin

import com.cerclerouge.privacy.db.SettingDao
import com.cerclerouge.privacy.db.SettingEntity
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface PinResult {
    data object Ok : PinResult

    data object NotConfigured : PinResult

    data class Wrong(val retryAfter: Instant) : PinResult

    data class Locked(val until: Instant) : PinResult
}

/** Stocke le PIN haché dans la base chiffrée et applique la temporisation progressive. */
class PinRepository(private val settings: SettingDao, private val clock: Clock) {
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    suspend fun isConfigured(): Boolean = settings.get(HASH) != null

    suspend fun setPin(pin: String) {
        val problem = PinRules.check(pin)
        require(problem == null) { "PIN refusé : $problem" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin, salt) }
        settings.put(SettingEntity(SALT, b64.encodeToString(salt)))
        settings.put(SettingEntity(HASH, b64.encodeToString(hash)))
        settings.put(SettingEntity(ITERATIONS, PinHasher.ITERATIONS.toString()))
        resetFailures()
    }

    suspend fun verify(pin: String): PinResult {
        val hash = settings.get(HASH)?.let(unb64::decode) ?: return PinResult.NotConfigured
        val salt = settings.get(SALT)?.let(unb64::decode) ?: return PinResult.NotConfigured
        val iterations = settings.get(ITERATIONS)?.toIntOrNull() ?: PinHasher.ITERATIONS
        val now = clock.instant()
        val failures = settings.get(FAILURES)?.toIntOrNull() ?: 0
        val lastFailure = settings.get(LAST_FAILURE)?.toLongOrNull()?.let(Instant::ofEpochMilli)
        if (lastFailure != null) {
            val until = Lockout.lockedUntil(failures, lastFailure)
            if (now < until) return PinResult.Locked(until)
        }
        val ok = withContext(Dispatchers.Default) { PinHasher.matches(pin, salt, hash, iterations) }
        if (ok) {
            resetFailures()
            return PinResult.Ok
        }
        settings.put(SettingEntity(FAILURES, (failures + 1).toString()))
        settings.put(SettingEntity(LAST_FAILURE, now.toEpochMilli().toString()))
        return PinResult.Wrong(Lockout.lockedUntil(failures + 1, now))
    }

    private suspend fun resetFailures() {
        settings.remove(FAILURES)
        settings.remove(LAST_FAILURE)
    }

    private companion object {
        const val HASH = "pin.hash"
        const val SALT = "pin.salt"
        const val ITERATIONS = "pin.iterations"
        const val FAILURES = "pin.failures"
        const val LAST_FAILURE = "pin.lastFailure"
    }
}
