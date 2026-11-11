package com.cerclerouge.app.ui.onboarding

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
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
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.Poster
import com.cerclerouge.app.ui.components.PxButton
import com.cerclerouge.app.ui.components.PxCheck
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.PxVariant
import com.cerclerouge.app.ui.components.SosBar
import com.cerclerouge.app.ui.components.TopBar
import com.cerclerouge.app.ui.components.pixelGrid
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
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

enum class Step { WELCOME, AI, CIRCLE, PROFILE, GUARDIAN, RETENTION, PIN, NOTIFICATIONS, SUMMARY }

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

/** Étape précédente pour la flèche « retour » des formulaires (les affiches n'en ont pas). */
private fun Step.previous(ui: OnboardingUi): Step? = when (this) {
    Step.WELCOME, Step.AI, Step.CIRCLE -> null
    Step.PROFILE -> Step.CIRCLE
    Step.GUARDIAN -> Step.PROFILE
    Step.RETENTION -> if (ui.band?.needsGuardian == true) Step.GUARDIAN else Step.PROFILE
    Step.PIN -> Step.RETENTION
    Step.NOTIFICATIONS -> Step.PIN
    Step.SUMMARY -> Step.NOTIFICATIONS
}

@Composable
fun OnboardingScreen(vm: OnboardingViewModel, onSos: () -> Unit, onDone: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val c = Cr.colors
    val back = ui.step.previous(ui)
    if (back != null) BackHandler { vm.goTo(back) }
    Box(Modifier.fillMaxSize().background(if (ui.step.ordinal <= Step.CIRCLE.ordinal) c.lime else c.surface).safeDrawingPadding()) {
        when (ui.step) {
            // Affiches lime (maquettes « Onboarding 1 à 3 »).
            Step.WELCOME -> Poster(
                1,
                3,
                PixelIcon.SHIELD,
                stringResource(R.string.poster1_title),
                stringResource(R.string.poster1_body),
                stringResource(R.string.action_continue),
                { vm.goTo(Step.AI) },
                { vm.goTo(Step.PROFILE) }
            ) {
                // R-ONB-06 : le SOS fonctionne dès le premier écran.
                Spacer(Modifier.height(20.dp))
                SosBar(onTrigger = onSos, onTap = {})
            }
            Step.AI -> Poster(
                2,
                3,
                PixelIcon.MIC,
                stringResource(R.string.poster2_title),
                stringResource(R.string.poster2_body),
                stringResource(R.string.action_continue),
                { vm.goTo(Step.CIRCLE) },
                { vm.goTo(Step.PROFILE) }
            )
            Step.CIRCLE -> Poster(
                3,
                3,
                PixelIcon.USERS,
                stringResource(R.string.poster3_title),
                stringResource(R.string.poster3_body),
                stringResource(R.string.action_start),
                { vm.goTo(Step.PROFILE) },
                null
            )
            Step.PROFILE -> Form(R.string.profile_screen_title, back, vm) { ProfileStep(ui, vm) }
            Step.GUARDIAN -> Form(R.string.guardian_screen_title, back, vm) { GuardianStep(ui, vm, onSos) }
            Step.RETENTION -> Form(R.string.retention_screen_title, back, vm) { RetentionStep(ui, vm) }
            Step.PIN -> Form(R.string.pin_screen_title, back, vm) {
                Intro(if (ui.pinFirst == null) R.string.pin_title else R.string.pin_confirm, R.string.pin_body)
                ui.pinError?.let { Text(stringResource(it).uppercase(), color = c.danger, style = PxType.label) }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    PinPad(ui.pin, vm::pinDigit, maxLength = 4)
                }
                PxButton(stringResource(R.string.action_later), { vm.goTo(Step.NOTIFICATIONS) }, variant = PxVariant.OUTLINE)
            }
            Step.NOTIFICATIONS -> Form(R.string.notif_screen_title, back, vm) { NotificationsStep(vm) }
            Step.SUMMARY -> Form(R.string.summary_title, back, vm) {
                Intro(null, R.string.summary_off)
                Note(PixelIcon.LOCK, stringResource(R.string.summary_privacy))
                PxButton(stringResource(R.string.action_finish), { vm.finish(onDone) }, icon = PixelIcon.CHECK, tall = true)
            }
        }
    }
}

/** Écran de formulaire : en-tête « retour + titre », fond quadrillé, contenu défilant. */
@Composable
private fun Form(title: Int, back: Step?, vm: OnboardingViewModel, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().pixelGrid(Cr.colors.grid)) {
        TopBar(stringResource(title), back?.let { { vm.goTo(it) } })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun Intro(title: Int?, body: Int) {
    title?.let { Text(stringResource(it).uppercase(), style = PxType.title) }
    Text(stringResource(body), style = PxType.body, color = Cr.colors.muted)
}

/** Panneau d'information : icône lime et texte. */
@Composable
private fun Note(icon: PixelIcon, text: String, warn: Boolean = false) {
    val c = Cr.colors
    PxPanel(Modifier.fillMaxWidth(), border = if (warn) c.warn else c.line) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.cerclerouge.app.ui.components.PixelIconView(icon, 24.dp, if (warn) c.warn else c.lime)
            Text(text, style = PxType.body)
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text
) {
    val c = Cr.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label).uppercase(), style = PxType.label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = true,
        textStyle = PxType.bodyBold,
        shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.lime,
            unfocusedBorderColor = c.lineControl,
            focusedLabelColor = c.lime,
            unfocusedLabelColor = c.muted,
            cursorColor = c.lime,
            focusedContainerColor = c.raised,
            unfocusedContainerColor = c.raised
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun ProfileStep(ui: OnboardingUi, vm: OnboardingViewModel) {
    val c = Cr.colors
    Intro(R.string.profile_title, R.string.profile_min_age)
    Field(ui.name, { v -> vm.edit { copy(name = v.take(40)) } }, R.string.profile_name)
    Text(stringResource(R.string.profile_birth).uppercase(), style = PxType.label, color = c.muted)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Field(ui.month, { v ->
            vm.edit { copy(month = v.filter(Char::isDigit).take(2)) }
        }, R.string.profile_month, Modifier.weight(1f), KeyboardType.Number)
        Field(ui.year, { v ->
            vm.edit { copy(year = v.filter(Char::isDigit).take(4)) }
        }, R.string.profile_year, Modifier.weight(1f), KeyboardType.Number)
    }
    val band = ui.band
    if (band == AgeBand.UNDER_12) {
        Note(PixelIcon.ALERT, stringResource(R.string.profile_under_12), warn = true)
        return
    }
    if (band == AgeBand.ADULT) {
        CheckLine(stringResource(R.string.profile_adult), ui.adultAttested) { vm.edit { copy(adultAttested = it) } }
    }
    // R-ONB-08 : anti-stalkerware, la personne protégée est le détenteur du téléphone.
    CheckLine(stringResource(R.string.profile_holder), ui.holderAttested) { vm.edit { copy(holderAttested = it) } }
    PxButton(
        stringResource(R.string.action_continue),
        { vm.saveProfile() },
        icon = PixelIcon.ARROW_RIGHT,
        enabled = ui.name.isNotBlank() && band != null && ui.holderAttested
    )
}

/** Ligne à cocher : toute la ligne est la zone tactile (case carrée du design). */
@Composable
private fun CheckLine(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Cr.colors
    PxPanel(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        border = if (checked) c.lime else c.lineControl
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PxCheck(checked)
            Text(text, style = PxType.body)
        }
    }
}

@Composable
private fun GuardianStep(ui: OnboardingUi, vm: OnboardingViewModel, onSos: () -> Unit) {
    val c = Cr.colors
    Intro(R.string.guardian_title, R.string.guardian_body)
    when (ui.guardian) {
        GuardianStatus.NONE -> {
            Field(ui.guardianPhone, { v ->
                vm.edit { copy(guardianPhone = v.take(20)) }
            }, R.string.guardian_phone, keyboard = KeyboardType.Phone)
            Text(stringResource(R.string.guardian_relation).uppercase(), style = PxType.label, color = c.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("parent" to R.string.relation_parent, "tuteur" to R.string.relation_tutor, "autre" to R.string.relation_other)
                    .forEach { (key, label) ->
                        val on = ui.relationship == key
                        PxPanel(
                            Modifier.weight(1f).selectable(on, role = Role.RadioButton) { vm.edit { copy(relationship = key) } },
                            border = if (on) c.lime else c.lineControl,
                            fill = if (on) c.lime else c.raised,
                            padding = PaddingValues(vertical = 14.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                stringResource(label).uppercase(),
                                style = PxType.label,
                                color = if (on) c.onLime else c.ink,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
            }
            PxButton(
                stringResource(R.string.guardian_send),
                { vm.requestGuardian() },
                icon = PixelIcon.ARROW_RIGHT,
                enabled = ui.guardianPhone.count(Char::isDigit) in 8..15
            )
        }
        GuardianStatus.PENDING -> {
            Note(PixelIcon.CLOCK, stringResource(R.string.guardian_waiting), warn = true)
            Text(stringResource(R.string.guardian_waiting_sos), style = PxType.body, color = c.muted)
            SosBar(onTrigger = onSos, onTap = {})
            PxButton(stringResource(R.string.guardian_change), {
                vm.edit { copy(guardian = GuardianStatus.NONE) }
            }, variant = PxVariant.OUTLINE)
            if (BuildConfig.DEBUG) {
                PxButton(stringResource(R.string.guardian_simulate), {
                    vm.simulateGuardian()
                }, variant = PxVariant.OUTLINE)
            }
            PxButton(stringResource(R.string.action_continue), { vm.goTo(Step.RETENTION) }, icon = PixelIcon.ARROW_RIGHT)
        }
        GuardianStatus.CONFIRMED -> {
            Note(PixelIcon.CHECK, stringResource(R.string.guardian_confirmed))
            PxButton(stringResource(R.string.action_continue), { vm.goTo(Step.RETENTION) }, icon = PixelIcon.ARROW_RIGHT)
        }
    }
}

@Composable
private fun RetentionStep(ui: OnboardingUi, vm: OnboardingViewModel) {
    val c = Cr.colors
    Intro(R.string.retention_title, R.string.retention_body)
    listOf(
        Triple(Retention.HOURS_24, R.string.retention_24h, R.string.retention_24h_ex),
        Triple(Retention.DAYS_7, R.string.retention_7d, R.string.retention_7d_ex),
        Triple(Retention.DAYS_30, R.string.retention_30d, R.string.retention_30d_ex),
        Triple(Retention.ON_CLOSURE, R.string.retention_closure, R.string.retention_closure_ex)
    ).forEach { (value, title, example) ->
        val selected = ui.retention == value
        PxPanel(
            Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton) { vm.chooseRetention(value) },
            border = if (selected) c.lime else c.lineControl
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PxCheck(selected)
                Column {
                    Text(stringResource(title).uppercase(), style = PxType.bodyBold)
                    Text(stringResource(example), style = PxType.caption, color = c.muted)
                }
            }
        }
    }
    // ADR-0017 : aucune option présélectionnée, « Continuer » reste désactivé tant que rien n'est choisi.
    PxButton(stringResource(R.string.action_continue), { vm.goTo(Step.PIN) }, icon = PixelIcon.ARROW_RIGHT, enabled = ui.retention != null)
}

@Composable
private fun NotificationsStep(vm: OnboardingViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.recordConsent(ConsentScope.NOTIFICATIONS, granted)
        vm.goTo(Step.SUMMARY)
    }
    Intro(R.string.notif_title, R.string.notif_body)
    Spacer(Modifier.width(0.dp))
    PxButton(stringResource(R.string.notif_allow), {
        // Avant Android 13, les notifications sont autorisées par défaut : rien à demander.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.recordConsent(ConsentScope.NOTIFICATIONS, true)
            vm.goTo(Step.SUMMARY)
        }
    }, icon = PixelIcon.CHECK)
    PxButton(stringResource(R.string.action_later), {
        vm.recordConsent(ConsentScope.NOTIFICATIONS, false)
        vm.goTo(Step.SUMMARY)
    }, variant = PxVariant.OUTLINE)
}
