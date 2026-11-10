package com.cerclerouge.app.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerclerouge.app.BuildConfig
import com.cerclerouge.app.R
import com.cerclerouge.app.account.AccountRepository
import com.cerclerouge.app.account.GuardianStatus
import com.cerclerouge.app.ui.components.PinPad
import com.cerclerouge.app.ui.components.PrimaryButton
import com.cerclerouge.app.ui.components.SecondaryButton
import com.cerclerouge.app.ui.components.SosBarButton
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.HudType
import com.cerclerouge.domain.AgeBand
import com.cerclerouge.domain.Consent
import com.cerclerouge.domain.ConsentScope
import com.cerclerouge.domain.Retention
import com.cerclerouge.privacy.Settings
import com.cerclerouge.privacy.consent.ConsentRepository
import com.cerclerouge.privacy.pin.PinRepository
import com.cerclerouge.privacy.pin.PinRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Year
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Step { WELCOME, AI, PROFILE, GUARDIAN, RETENTION, PIN, NOTIFICATIONS, SUMMARY }

data class OnboardingUi(
    val step: Step = Step.WELCOME,
    val name: String = "",
    val month: String = "",
    val year: String = "",
    val adultAttested: Boolean = false,
    val holderAttested: Boolean = false,
    val guardianPhone: String = "+509",
    val relationship: String = "parent",
    val guardian: GuardianStatus = GuardianStatus.NONE,
    val retention: Retention? = null,
    val pin: String = "",
    val pinFirst: String? = null,
    val pinError: Int? = null
) {
    val band: AgeBand? get() {
        val y = year.toIntOrNull() ?: return null
        val m = month.toIntOrNull() ?: return null
        if (m !in 1..12 || y !in 1900..Year.now().value) return null
        return AgeBand.of(y, m, java.time.LocalDate.now())
    }
}

/** Onboarding (module produit 1, R-ONB-*). Le texte des consentements est versionné (R-ONB-03). */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val account: AccountRepository,
    private val settings: Settings,
    private val consents: ConsentRepository,
    private val pins: PinRepository,
    private val clock: Clock
) : ViewModel() {
    private val _ui = MutableStateFlow(OnboardingUi())
    val ui: StateFlow<OnboardingUi> = _ui

    fun edit(change: OnboardingUi.() -> OnboardingUi) = _ui.update { it.change() }

    fun goTo(step: Step) = edit { copy(step = step) }

    fun saveProfile() = viewModelScope.launch {
        val u = _ui.value
        val band = u.band ?: return@launch
        account.saveProfile(u.name, u.year.toInt(), u.month.toInt(), u.adultAttested && band == AgeBand.ADULT)
        goTo(if (band.needsGuardian) Step.GUARDIAN else Step.RETENTION)
    }

    fun requestGuardian() = viewModelScope.launch {
        account.requestGuardian(_ui.value.guardianPhone.filter { it.isDigit() || it == '+' })
        edit { copy(guardian = GuardianStatus.PENDING) }
    }

    fun simulateGuardian() = viewModelScope.launch {
        account.simulateGuardianConfirmation()
        edit { copy(guardian = GuardianStatus.CONFIRMED) }
    }

    fun chooseRetention(r: Retention) = viewModelScope.launch {
        settings.setRetention(r)
        edit { copy(retention = r) }
    }

    fun pinDigit(value: String) {
        val u = _ui.value
        edit { copy(pin = value, pinError = null) }
        if (value.length < 4) return
        if (u.pinFirst == null && value.length == 4) {
            if (PinRules.check(value) != null) {
                edit { copy(pin = "", pinError = R.string.pin_too_simple) }
            } else {
                edit { copy(pinFirst = value, pin = "") }
            }
        } else if (u.pinFirst != null && value.length == u.pinFirst.length) {
            if (value != u.pinFirst) {
                edit { copy(pin = "", pinFirst = null, pinError = R.string.pin_mismatch) }
            } else {
                viewModelScope.launch {
                    pins.setPin(value)
                    edit { copy(pin = "", pinFirst = null, step = Step.NOTIFICATIONS) }
                }
            }
        }
    }

    fun recordConsent(scope: ConsentScope, granted: Boolean) = viewModelScope.launch {
        consents.record(Consent(scope, granted, TEXT_VERSION, Locale.getDefault().language, clock.instant()))
    }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        recordConsent(ConsentScope.PRIVACY_POLICY, true).join()
        settings.setOnboardingDone()
        onDone()
    }

    private companion object {
        const val TEXT_VERSION = "2026-11-v1"
    }
}

@Composable
fun OnboardingScreen(vm: OnboardingViewModel, onSos: () -> Unit, onDone: () -> Unit) {
    val ui by vm.ui.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (ui.step) {
            Step.WELCOME -> Page(R.string.welcome_title, R.string.welcome_body) {
                Hud(stringResource(R.string.welcome_sos_hint))
                PrimaryButton(stringResource(R.string.action_start), { vm.goTo(Step.AI) })
                SosBarButton(onSos) // R-ONB-06 : SOS dès le premier écran
            }
            Step.AI -> Page(R.string.ia_title, R.string.ia_body) {
                Hud(stringResource(R.string.ia_limits))
                PrimaryButton(stringResource(R.string.action_continue), { vm.goTo(Step.PROFILE) })
            }
            Step.PROFILE -> ProfileStep(ui, vm)
            Step.GUARDIAN -> GuardianStep(ui, vm, onSos)
            Step.RETENTION -> RetentionStep(ui, vm)
            Step.PIN -> Page(if (ui.pinFirst == null) R.string.pin_title else R.string.pin_confirm, R.string.pin_body) {
                ui.pinError?.let { Text(stringResource(it), color = Cr.colors.alerte, style = MaterialTheme.typography.bodyMedium) }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    PinPad(ui.pin, vm::pinDigit, maxLength = 4)
                }
                SecondaryButton(stringResource(R.string.action_later), { vm.goTo(Step.NOTIFICATIONS) })
            }
            Step.NOTIFICATIONS -> NotificationsStep(vm)
            Step.SUMMARY -> Page(R.string.summary_title, R.string.summary_off) {
                Text(stringResource(R.string.summary_privacy), style = MaterialTheme.typography.bodyMedium, color = Cr.colors.textSecondary)
                PrimaryButton(stringResource(R.string.action_finish), { vm.finish(onDone) })
            }
        }
    }
}

@Composable
private fun Page(title: Int, body: Int, content: @Composable () -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, color = Cr.colors.textPrimary)
    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = Cr.colors.textSecondary)
    Spacer(Modifier.height(8.dp))
    content()
}

@Composable
private fun Hud(text: String) = Text(text.uppercase(), style = HudType.label, color = Cr.colors.textSecondary)

@Composable
private fun ProfileStep(ui: OnboardingUi, vm: OnboardingViewModel) {
    Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = ui.name,
        onValueChange = { v -> vm.edit { copy(name = v.take(40)) } },
        label = { Text(stringResource(R.string.profile_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Text(stringResource(R.string.profile_birth), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = ui.month,
            onValueChange = { v -> vm.edit { copy(month = v.filter(Char::isDigit).take(2)) } },
            label = { Text(stringResource(R.string.profile_month)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = ui.year,
            onValueChange = { v -> vm.edit { copy(year = v.filter(Char::isDigit).take(4)) } },
            label = { Text(stringResource(R.string.profile_year)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
    val band = ui.band
    if (band == AgeBand.UNDER_12) {
        Text(stringResource(R.string.profile_under_12), color = Cr.colors.vigilance, style = MaterialTheme.typography.bodyMedium)
        return
    }
    Text(stringResource(R.string.profile_min_age), style = MaterialTheme.typography.bodySmall, color = Cr.colors.textSecondary)
    if (band == AgeBand.ADULT) {
        CheckLine(stringResource(R.string.profile_adult), ui.adultAttested) { vm.edit { copy(adultAttested = it) } }
    }
    // R-ONB-08 : anti-stalkerware, la personne protégée est le détenteur du téléphone.
    CheckLine(stringResource(R.string.profile_holder), ui.holderAttested) { vm.edit { copy(holderAttested = it) } }
    PrimaryButton(
        stringResource(R.string.action_continue),
        vm::saveProfile,
        enabled = ui.name.isNotBlank() && band != null && ui.holderAttested
    )
}

@Composable
private fun CheckLine(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked, onCheckedChange = null)
        Spacer(Modifier.padding(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GuardianStep(ui: OnboardingUi, vm: OnboardingViewModel, onSos: () -> Unit) {
    Page(R.string.guardian_title, R.string.guardian_body) {
        when (ui.guardian) {
            GuardianStatus.NONE -> {
                OutlinedTextField(
                    value = ui.guardianPhone,
                    onValueChange = { v -> vm.edit { copy(guardianPhone = v.take(20)) } },
                    label = { Text(stringResource(R.string.guardian_phone)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.guardian_relation), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("parent" to R.string.relation_parent, "tuteur" to R.string.relation_tutor, "autre" to R.string.relation_other)
                        .forEach { (key, label) ->
                            FilterChip(
                                ui.relationship == key,
                                { vm.edit { copy(relationship = key) } },
                                { Text(stringResource(label)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Cr.colors.protege,
                                    selectedLabelColor = Cr.colors.onState
                                )
                            )
                        }
                }
                PrimaryButton(
                    stringResource(R.string.guardian_send),
                    vm::requestGuardian,
                    enabled = ui.guardianPhone.count(Char::isDigit) in 8..15
                )
            }
            GuardianStatus.PENDING -> {
                Hud(stringResource(R.string.guardian_waiting))
                Text(stringResource(R.string.guardian_waiting_sos), style = MaterialTheme.typography.bodyMedium)
                SosBarButton(onSos)
                SecondaryButton(stringResource(R.string.guardian_change), { vm.edit { copy(guardian = GuardianStatus.NONE) } })
                if (BuildConfig.DEBUG) SecondaryButton(stringResource(R.string.guardian_simulate), vm::simulateGuardian)
                PrimaryButton(stringResource(R.string.action_continue), { vm.goTo(Step.RETENTION) })
            }
            GuardianStatus.CONFIRMED -> {
                Text(stringResource(R.string.guardian_confirmed), color = Cr.colors.protege, style = MaterialTheme.typography.bodyLarge)
                PrimaryButton(stringResource(R.string.action_continue), { vm.goTo(Step.RETENTION) })
            }
        }
    }
}

@Composable
private fun RetentionStep(ui: OnboardingUi, vm: OnboardingViewModel) {
    Page(R.string.retention_title, R.string.retention_body) {
        listOf(
            Triple(Retention.HOURS_24, R.string.retention_24h, R.string.retention_24h_ex),
            Triple(Retention.DAYS_7, R.string.retention_7d, R.string.retention_7d_ex),
            Triple(Retention.DAYS_30, R.string.retention_30d, R.string.retention_30d_ex),
            Triple(Retention.ON_CLOSURE, R.string.retention_closure, R.string.retention_closure_ex)
        ).forEach { (value, title, example) ->
            val selected = ui.retention == value
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, if (selected) Cr.colors.protege else Cr.colors.lineBorder), RoundedCornerShape(16.dp))
                    .selectable(selected, role = Role.RadioButton) { vm.chooseRetention(value) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected, onClick = null)
                Spacer(Modifier.padding(4.dp))
                Column {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(example), style = MaterialTheme.typography.bodySmall, color = Cr.colors.textSecondary)
                }
            }
        }
        // ADR-0017 : aucune option présélectionnée, « Continuer » reste désactivé tant que rien n'est choisi.
        PrimaryButton(stringResource(R.string.action_continue), { vm.goTo(Step.PIN) }, enabled = ui.retention != null)
    }
}

@Composable
private fun NotificationsStep(vm: OnboardingViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.recordConsent(ConsentScope.NOTIFICATIONS, granted)
        vm.goTo(Step.SUMMARY)
    }
    Page(R.string.notif_title, R.string.notif_body) {
        PrimaryButton(stringResource(R.string.notif_allow), {
            // Avant Android 13, les notifications sont autorisées par défaut : rien à demander.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                vm.recordConsent(ConsentScope.NOTIFICATIONS, true)
                vm.goTo(Step.SUMMARY)
            }
        })
        SecondaryButton(stringResource(R.string.action_later), {
            vm.recordConsent(ConsentScope.NOTIFICATIONS, false)
            vm.goTo(Step.SUMMARY)
        })
    }
}
