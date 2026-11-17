package com.cerclerouge.app.settings

import com.cerclerouge.app.ui.theme.ThemeMode
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Apparence choisie (1.20) : sombre par défaut, clair, système ou contraste élevé. Gardée dans la base chiffrée. */
@Singleton
class Appearance @Inject constructor(db: PrivacyDatabase) {
    private val settings = db.settings()
    private val _mode = MutableStateFlow(ThemeMode.DARK)
    val mode: StateFlow<ThemeMode> = _mode

    suspend fun load(): ThemeMode =
        (settings.get(KEY)?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.DARK).also { _mode.value = it }

    suspend fun set(mode: ThemeMode) {
        settings.put(SettingEntity(KEY, mode.name))
        _mode.value = mode
    }

    private companion object {
        const val KEY = "ui.theme"
    }
}
