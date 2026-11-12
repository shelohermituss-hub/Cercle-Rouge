package com.cerclerouge.app.protection

import com.cerclerouge.alert.IgnoreContext
import com.cerclerouge.alert.Sensitivity
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Réglages de l'écoute et de la décision (PDF 8.3, 8.6 ; S5.09). */
data class ListenPrefs(
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
    /** Contextes à ignorer actifs, avec leur fin (ils expirent tout seuls). */
    val ignored: Map<IgnoreContext, Instant> = emptyMap(),
    /** Délai de la vérification « Es-tu en sécurité ? » avant l'alerte des proches (PDF 8.3 : 10 à 30 s). */
    val verifySeconds: Int = 20
) {
    fun activeContexts(now: Instant): Set<IgnoreContext> = ignored.filterValues { it.isAfter(now) }.keys
}

/** Réglages d'écoute, dans la base chiffrée. Lus par le cerveau ([RiskBrain]) à chaque décision. */
@Singleton
class ListenSettings @Inject constructor(db: PrivacyDatabase, private val clock: Clock) {
    private val settings = db.settings()
    private val _prefs = MutableStateFlow(ListenPrefs())
    val prefs: StateFlow<ListenPrefs> = _prefs

    suspend fun load(): ListenPrefs {
        val sensitivity = settings.get(SENSITIVITY)?.let { v -> Sensitivity.entries.firstOrNull { it.name == v } } ?: Sensitivity.NORMAL
        val ignored = settings.get(IGNORED).orEmpty().split(';').mapNotNull { item ->
            val (name, until) = item.split('@').takeIf { it.size == 2 } ?: return@mapNotNull null
            val ctx = IgnoreContext.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            until.toLongOrNull()?.let { ctx to Instant.ofEpochMilli(it) }
        }.toMap().filterValues { it.isAfter(clock.instant()) }
        val verify = settings.get(VERIFY)?.toIntOrNull()?.takeIf { it in VERIFY_CHOICES } ?: 20
        return ListenPrefs(sensitivity, ignored, verify).also { _prefs.value = it }
    }

    suspend fun setSensitivity(s: Sensitivity) {
        settings.put(SettingEntity(SENSITIVITY, s.name))
        _prefs.value = _prefs.value.copy(sensitivity = s)
    }

    suspend fun setVerifySeconds(seconds: Int) {
        require(seconds in VERIFY_CHOICES)
        settings.put(SettingEntity(VERIFY, seconds.toString()))
        _prefs.value = _prefs.value.copy(verifySeconds = seconds)
    }

    /** Active un contexte jusqu'à `until`, ou le retire si `until` est null. */
    suspend fun setContext(ctx: IgnoreContext, until: Instant?) {
        val map = _prefs.value.ignored.toMutableMap()
        if (until == null) map.remove(ctx) else map[ctx] = until
        settings.put(SettingEntity(IGNORED, map.entries.joinToString(";") { "${it.key.name}@${it.value.toEpochMilli()}" }))
        _prefs.value = _prefs.value.copy(ignored = map)
    }

    companion object {
        val VERIFY_CHOICES = listOf(10, 20, 30)
        private const val VERIFY = "listen.verify_seconds"
        private const val SENSITIVITY = "listen.sensitivity"
        private const val IGNORED = "listen.ignored"
    }
}
