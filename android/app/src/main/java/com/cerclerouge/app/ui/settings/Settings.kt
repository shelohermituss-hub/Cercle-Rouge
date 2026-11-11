package com.cerclerouge.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerclerouge.app.BuildConfig
import com.cerclerouge.app.R
import com.cerclerouge.app.evidence.EvidenceSettings
import com.cerclerouge.app.security.Biometric
import com.cerclerouge.app.security.SecureScreen
import com.cerclerouge.app.security.SecuritySettings
import com.cerclerouge.app.settings.DataControl
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PxButton
import com.cerclerouge.app.ui.components.PxCheck
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.PxVariant
import com.cerclerouge.app.ui.components.TopBar
import com.cerclerouge.app.ui.home.PinDialog
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.cerclerouge.domain.Retention
import com.cerclerouge.privacy.Settings
import com.cerclerouge.privacy.pin.PinRepository
import com.cerclerouge.privacy.pin.PinResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUi(
    val retention: Retention? = null,
    val recording: Boolean = true,
    val biometric: Boolean = false,
    val biometricAlerts: Boolean = false,
    val busy: Boolean = false,
    val message: Int? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: Settings,
    private val evidence: EvidenceSettings,
    private val security: SecuritySettings,
    private val pins: PinRepository,
    val data: DataControl
) : ViewModel() {
    private val _ui = MutableStateFlow(SettingsUi())
    val ui: StateFlow<SettingsUi> = _ui

    init {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    retention = settings.retention(),
                    recording = evidence.audioEnabled(),
                    biometric = security.biometric(),
                    biometricAlerts = security.biometricForAlerts()
                )
            }
        }
    }

    suspend fun pinOk(pin: String) = pins.verify(pin) == PinResult.Ok

    fun setRetention(r: Retention) = viewModelScope.launch {
        settings.setRetention(r)
        _ui.update { it.copy(retention = r) }
    }

    fun setRecording(on: Boolean) = viewModelScope.launch {
        evidence.setAudioEnabled(on)
        _ui.update { it.copy(recording = on) }
    }

    fun setBiometric(on: Boolean) = viewModelScope.launch {
        security.setBiometric(on)
        if (!on) security.setBiometricForAlerts(false)
        _ui.update { it.copy(biometric = on, biometricAlerts = on && it.biometricAlerts) }
    }

    fun setBiometricAlerts(on: Boolean) = viewModelScope.launch {
        security.setBiometricForAlerts(on)
        _ui.update { it.copy(biometricAlerts = on) }
    }

    fun wipe() = viewModelScope.launch {
        _ui.update { it.copy(busy = true) }
        data.emergencyWipe()
        _ui.update { it.copy(busy = false, message = R.string.settings_wiped) }
    }

    /** Vrai si tout est effacé : l'app redémarre alors sur l'accueil d'inscription. */
    suspend fun deleteAll(): Boolean {
        _ui.update { it.copy(busy = true) }
        val ok = data.deleteEverything()
        _ui.update { it.copy(busy = false, message = if (ok) null else R.string.settings_delete_failed) }
        return ok
    }

    fun said(message: Int?) = _ui.update { it.copy(message = message) }
}

private enum class Guarded { WIPE, DELETE, BIOMETRIC_ALERTS }

/**
 * Réglages (S3.12, S5.13, S7.02, S7.05, S7.06) : conservation des preuves, enregistrement, biométrie, effacement d'urgence,
 * export et suppression du compte, fiabilité en arrière-plan, langue, confidentialité. Les actions graves demandent le PIN.
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onListen: () -> Unit) {
    SecureScreen()
    val c = Cr.colors
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var guarded by remember { mutableStateOf<Guarded?>(null) }
    var confirm by remember { mutableStateOf<Guarded?>(null) }
    var bioAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { bioAvailable = Biometric.available(activity) }
    val saveExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = vm.data.export()
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } != null
            }.getOrDefault(false)
            vm.said(if (ok) R.string.settings_exported else R.string.settings_export_failed)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.settings_title), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ui.message?.let {
                Text(
                    stringResource(it),
                    style = PxType.bodyBold,
                    color = c.lime,
                    modifier = Modifier.semantics {
                        liveRegion =
                            LiveRegionMode.Polite
                    }
                )
            }

            Section(R.string.settings_section_evidence)
            Text(stringResource(R.string.retention_title), style = PxType.body)
            listOf(
                Retention.HOURS_24 to R.string.retention_24h,
                Retention.DAYS_7 to R.string.retention_7d,
                Retention.DAYS_30 to R.string.retention_30d,
                Retention.ON_CLOSURE to R.string.retention_closure
            ).forEach { (r, label) ->
                val selected = ui.retention == r || (ui.retention == null && r == Retention.HOURS_24)
                Row(
                    Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton) { vm.setRetention(r) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PxCheck(selected)
                    Text(stringResource(label), style = PxType.body)
                }
            }
            Toggle(R.string.vault_audio_toggle, R.string.vault_audio_toggle_ex, ui.recording) { vm.setRecording(it) }
            PxButton(stringResource(R.string.settings_listen), onListen, variant = PxVariant.OUTLINE, icon = PixelIcon.MIC)

            Section(R.string.settings_section_security)
            if (bioAvailable) {
                Toggle(R.string.settings_bio, R.string.settings_bio_ex, ui.biometric) { vm.setBiometric(it) }
                if (ui.biometric) {
                    Toggle(R.string.settings_bio_alerts, R.string.settings_bio_alerts_ex, ui.biometricAlerts) { on ->
                        if (on) guarded = Guarded.BIOMETRIC_ALERTS else vm.setBiometricAlerts(false)
                    }
                }
            } else {
                Text(stringResource(R.string.settings_bio_unavailable), style = PxType.caption, color = c.muted)
            }
            Text(stringResource(R.string.settings_wipe_ex), style = PxType.caption, color = c.muted)
            PxButton(stringResource(R.string.settings_wipe), {
                confirm = Guarded.WIPE
            }, variant = PxVariant.DARK_DANGER, enabled = !ui.busy)

            Section(R.string.settings_section_background)
            Text(stringResource(BatteryGuide.forDevice()), style = PxType.body)
            PxButton(stringResource(R.string.settings_battery_open), {
                runCatching { context.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    .onFailure { context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS)) }
            }, variant = PxVariant.OUTLINE)

            Section(R.string.settings_section_language)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PxButton(stringResource(R.string.settings_language), {
                    val intent = Intent(AndroidSettings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri())
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(Intent(AndroidSettings.ACTION_LOCALE_SETTINGS))
                    }
                }, variant = PxVariant.OUTLINE)
            } else {
                Text(stringResource(R.string.settings_language_old), style = PxType.caption, color = c.muted)
            }

            Section(R.string.settings_section_data)
            PxButton(stringResource(R.string.settings_export), {
                saveExport.launch("cercle-rouge-mes-donnees.json")
            }, variant = PxVariant.OUTLINE)
            Text(stringResource(R.string.settings_export_ex), style = PxType.caption, color = c.muted)
            PxButton(stringResource(R.string.settings_privacy), {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri())) }
            }, variant = PxVariant.OUTLINE)
            PxButton(stringResource(R.string.settings_delete), { confirm = Guarded.DELETE }, variant = PxVariant.DANGER, enabled = !ui.busy)
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = PxType.caption, color = c.muted)
        }
    }

    confirm?.let { what ->
        Dialog(onDismissRequest = { confirm = null }) {
            PxPanel(border = c.danger, fill = c.surface, padding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val (title, body) = if (what == Guarded.WIPE) {
                        R.string.settings_wipe to R.string.settings_wipe_confirm
                    } else {
                        R.string.settings_delete to R.string.settings_delete_confirm
                    }
                    Text(stringResource(title).uppercase(), style = PxType.title)
                    Text(stringResource(body), style = PxType.body)
                    PxButton(stringResource(R.string.settings_continue_pin), {
                        confirm = null
                        guarded = what
                    }, variant = PxVariant.DANGER)
                    PxButton(stringResource(R.string.action_cancel), { confirm = null }, variant = PxVariant.OUTLINE)
                }
            }
        }
    }

    guarded?.let { what ->
        PinDialog(
            onDismiss = { guarded = null },
            verify = { vm.pinOk(it) },
            onDone = {
                guarded = null
                when (what) {
                    Guarded.WIPE -> vm.wipe()
                    Guarded.BIOMETRIC_ALERTS -> vm.setBiometricAlerts(true)
                    Guarded.DELETE -> scope.launch {
                        if (vm.deleteAll()) {
                            // Tout est effacé : on repart de zéro (inscription), dans un processus neuf.
                            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                context.startActivity(it)
                            }
                            activity?.finishAffinity()
                            Process.killProcess(Process.myPid())
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun Section(title: Int) {
    Text(stringResource(title).uppercase(), style = PxType.label, color = Cr.colors.lime, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Toggle(title: Int, detail: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Cr.colors
    PxPanel(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange),
        border = if (checked) c.lime else c.lineControl
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PxCheck(checked)
            Column {
                Text(stringResource(title).uppercase(), style = PxType.bodyBold)
                Text(stringResource(detail), style = PxType.caption, color = c.muted)
            }
        }
    }
}

/** S7.06 : consigne de fiabilité en arrière-plan selon la marque (les plus répandues en Haïti en premier). */
object BatteryGuide {
    fun forDevice(manufacturer: String = Build.MANUFACTURER): Int = when (manufacturer.lowercase()) {
        "tecno", "infinix", "itel" -> R.string.battery_transsion
        "samsung" -> R.string.battery_samsung
        "xiaomi", "redmi", "poco" -> R.string.battery_xiaomi
        "huawei", "honor" -> R.string.battery_huawei
        "oppo", "realme", "oneplus", "vivo" -> R.string.battery_oppo
        else -> R.string.battery_generic
    }
}

private const val PRIVACY_URL = "https://shelohermituss-hub.github.io/Cercle-Rouge/confidentialite.html"
