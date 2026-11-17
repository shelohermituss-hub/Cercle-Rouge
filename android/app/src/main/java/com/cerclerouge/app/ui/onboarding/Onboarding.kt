package com.cerclerouge.app.ui.onboarding

import android.Manifest
import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerclerouge.app.BuildConfig
import com.cerclerouge.app.R
import com.cerclerouge.app.account.AccountRepository
import com.cerclerouge.app.account.GuardianStatus
import com.cerclerouge.app.account.GuardianSync
import com.cerclerouge.app.contacts.ContactsRepository
import com.cerclerouge.app.ui.components.Orb
import com.cerclerouge.app.ui.components.PinPad
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PixelIconView
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
import com.cerclerouge.contacts.Phone
import com.cerclerouge.domain.AgeBand
import com.cerclerouge.domain.Consent
import com.cerclerouge.domain.ConsentScope
import com.cerclerouge.domain.Retention
import com.cerclerouge.privacy.Settings
import com.cerclerouge.privacy.consent.ConsentRepository
import com.cerclerouge.privacy.pin.PinRepository
import com.cerclerouge.privacy.pin.PinRules
import com.valentinerutto.orbmotion.orbs.OrbState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Year
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Step { WELCOME, LOGIN, AI, CIRCLE, PROFILE, GUARDIAN, RETENTION, PIN, NOTIFICATIONS, SUMMARY }

data class LoginUi(val pending: String? = null, val busy: Boolean = false, val message: Int? = null)

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
    val pinError: Int? = null,
    val guardianError: Int? = null,
    val guardianBusy: Boolean = false
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
    private val contacts: ContactsRepository,
    private val guardians: GuardianSync,
    private val clock: Clock,
    private val api: com.cerclerouge.app.server.SupabaseApi,
    private val restore: com.cerclerouge.app.account.AccountRestore
) : ViewModel() {
    /** Connexion sur un nouveau téléphone (1.19) : numéro en attente de code, message, travail en cours. */
    private val _login = MutableStateFlow(LoginUi())
    val login: StateFlow<LoginUi> = _login

    fun requestLogin(raw: String) = viewModelScope.launch {
        val phone = (com.cerclerouge.contacts.Phone.normalize(raw) as? com.cerclerouge.contacts.PhoneResult.Valid)?.number?.value
        if (phone == null) {
            _login.update { it.copy(message = R.string.settings_phone_invalid) }
            return@launch
        }
        _login.update { it.copy(busy = true, message = null) }
        val ok = runCatching { api.requestLoginCode(phone) }.isSuccess
        _login.update {
            it.copy(
                busy = false,
                pending = if (ok) phone else null,
                message = if (ok) R.string.settings_phone_sent else R.string.login_failed
            )
        }
    }

    fun verifyLogin(code: String, onRestored: () -> Unit) = viewModelScope.launch {
        val phone = _login.value.pending ?: return@launch
        _login.update { it.copy(busy = true, message = null) }
        if (runCatching { api.verifyLoginCode(phone, code.trim()) }.isFailure) {
            _login.update { it.copy(busy = false, message = R.string.settings_phone_wrong_code) }
            return@launch
        }
        val result = restore.restoreAfterLogin()
        _login.update {
            it.copy(
                busy = false,
                message = when (result) {
                    com.cerclerouge.app.account.RestoreResult.RESTORED -> null
                    com.cerclerouge.app.account.RestoreResult.NO_PROFILE -> R.string.login_no_profile
                    com.cerclerouge.app.account.RestoreResult.FAILED -> R.string.login_failed
                }
            )
        }
        if (result == com.cerclerouge.app.account.RestoreResult.RESTORED) {
            account.load()?.let { a -> edit { copy(name = a.displayName, year = a.birthYear.toString(), month = a.birthMonth.toString()) } }
            onRestored()
        }
    }

    fun cancelLogin() = _login.update { LoginUi() }

    /** Résumé final (1.20) : PIN posé, nombre de proches. Les autorisations sont lues par l'écran. */
    suspend fun summary(): Pair<Boolean, Int> {
        contacts.load()
        return pins.isConfigured() to contacts.book.value.contacts.size
    }

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

    /** Envoie la demande au serveur, qui prévient l'adulte par SMS (S1.17, S1.22). */
    fun requestGuardian() = viewModelScope.launch {
        edit { copy(guardianBusy = true, guardianError = null) }
        val error = guardians.request(_ui.value.guardianPhone, _ui.value.relationship)
        edit {
            if (error == null) {
                copy(guardianBusy = false, guardian = GuardianStatus.PENDING)
            } else {
                copy(
                    guardianBusy = false,
                    guardianError = when (error) {
                        "invalid_phone" -> R.string.guardian_err_phone
                        "own_phone" -> R.string.guardian_err_own
                        "rate_limited" -> R.string.guardian_err_rate
                        else -> R.string.guardian_err_network
                    }
                )
            }
        }
    }

    /** L'adulte a-t-il confirmé ? (bouton « Vérifier » de l'écran d'attente) */
    fun checkGuardian() = viewModelScope.launch {
        edit { copy(guardianBusy = true) }
        val confirmed = guardians.refresh()
        edit {
            copy(
                guardianBusy = false,
                guardian = if (confirmed) GuardianStatus.CONFIRMED else guardian,
                guardianError = if (confirmed) null else R.string.guardian_not_yet
            )
        }
    }

    fun simulateGuardian() = viewModelScope.launch {
        // Builds de test : même effet qu'une vraie confirmation (contact ajouté, protection débloquée).
        if (account.pendingGuardian() == null) account.requestGuardian(_ui.value.guardianPhone, _ui.value.relationship)
        guardians.confirmLocally()
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
    Step.LOGIN -> Step.WELCOME
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
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.slogan), style = PxType.bodyBold, color = Cr.colors.onLime)
                Text(stringResource(R.string.disclaimer_short), style = PxType.caption, color = Cr.colors.onLime)
                Spacer(Modifier.height(8.dp))
                PxButton(stringResource(R.string.login_have_account), { vm.goTo(Step.LOGIN) }, variant = PxVariant.DARK)
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
            Step.LOGIN -> Form(R.string.login_title, back, vm) { LoginStep(vm) { vm.goTo(Step.RETENTION) } }
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
                SummaryStatus(vm)
                SosGuide()
                Note(PixelIcon.LOCK, stringResource(R.string.summary_privacy))
                Note(PixelIcon.ALERT, stringResource(R.string.disclaimer_full), warn = true)
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
        Note(
            PixelIcon.ALERT,
            stringResource(
                R.string.profile_under_12,
                LocalContext.current.getSystemService(android.telephony.TelephonyManager::class.java).let {
                    com.cerclerouge.domain.EmergencyNumbers.resolve(null, it?.simCountryIso, it?.networkCountryIso).primary
                }
            ),
            warn = true
        )
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
private fun ColumnScope.GuardianStep(ui: OnboardingUi, vm: OnboardingViewModel, onSos: () -> Unit) {
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
            ui.guardianError?.let { Note(PixelIcon.ALERT, stringResource(it), warn = true) }
            PxButton(
                stringResource(R.string.guardian_send),
                { vm.requestGuardian() },
                icon = PixelIcon.ARROW_RIGHT,
                enabled = !ui.guardianBusy && ui.guardianPhone.count(Char::isDigit) in 8..15
            )
        }
        GuardianStatus.PENDING -> {
            Orb(OrbState.SEARCHING, 72.dp, c.warn, Modifier.align(Alignment.CenterHorizontally))
            Note(PixelIcon.CLOCK, stringResource(R.string.guardian_waiting), warn = true)
            Text(stringResource(R.string.guardian_waiting_sos), style = PxType.body, color = c.muted)
            SosBar(onTrigger = onSos, onTap = {})
            ui.guardianError?.let { Note(PixelIcon.CLOCK, stringResource(it), warn = true) }
            PxButton(stringResource(R.string.guardian_check), {
                vm.checkGuardian()
            }, variant = PxVariant.OUTLINE, enabled = !ui.guardianBusy)
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
        Triple(Retention.ON_CLOSURE, R.string.retention_closure, R.string.retention_closure_ex),
        Triple(Retention.MANUAL, R.string.retention_manual, R.string.retention_manual_ex)
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
    if (ui.retention == Retention.MANUAL) {
        PxPanel(Modifier.fillMaxWidth(), border = c.warn) {
            Text(stringResource(R.string.retention_manual_warning), style = PxType.caption)
        }
    }
    // Q23 : avis sur l'enregistrement (autres voix possibles, valeur en justice non garantie, interrupteur au coffre-fort).
    PxPanel(Modifier.fillMaxWidth(), border = c.warn) {
        Text(stringResource(R.string.retention_recording_notice), style = PxType.caption)
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

/**
 * « J'ai déjà un compte » (1.19) : numéro vérifié + code SMS. Le profil revient, les contacts aussi si leur sauvegarde était
 * activée. Les preuves de l'ancien téléphone ne sont pas transférées. On reprend ensuite à la durée de conservation (locale).
 */
@Composable
private fun LoginStep(vm: OnboardingViewModel, onRestored: () -> Unit) {
    val c = Cr.colors
    val s by vm.login.collectAsState()
    var number by remember { mutableStateOf("+509") }
    var code by remember { mutableStateOf("") }
    Intro(R.string.login_title, R.string.login_body)
    s.message?.let { Text(stringResource(it), style = PxType.label, color = c.warn) }
    val pending = s.pending
    if (pending == null) {
        androidx.compose.material3.OutlinedTextField(
            number,
            { number = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' }.take(20) },
            label = { Text(stringResource(R.string.settings_phone_label)) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        PxButton(stringResource(R.string.settings_phone_send), { vm.requestLogin(number) }, enabled = !s.busy && number.length >= 8)
    } else {
        Text(stringResource(R.string.settings_phone_code_for, pending), style = PxType.body)
        androidx.compose.material3.OutlinedTextField(
            code,
            { code = it.filter(Char::isDigit).take(8) },
            label = { Text(stringResource(R.string.settings_phone_code_label)) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        PxButton(stringResource(R.string.settings_phone_verify), {
            vm.verifyLogin(code, onRestored)
        }, enabled = !s.busy && code.length >= 6)
        PxButton(stringResource(R.string.action_cancel), { vm.cancelLogin() }, variant = PxVariant.OUTLINE)
    }
    Note(PixelIcon.LOCK, stringResource(R.string.login_privacy))
}

/**
 * Résumé « actif / inactif » (1.20, R-ONB-07) : ce qui marchera tout de suite et ce qui manque, sans bloquer. L'app reste
 * utilisable en partie sans autorisation : le SOS et les SMS marchent sans micro ; l'écoute marche sans position, etc.
 */
@Composable
private fun SummaryStatus(vm: OnboardingViewModel) {
    val c = Cr.colors
    val context = LocalContext.current
    var summary by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    LaunchedEffect(Unit) { summary = vm.summary() }
    fun granted(p: String) = context.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val rows = listOf(
        R.string.summary_mic to granted(android.Manifest.permission.RECORD_AUDIO),
        R.string.summary_location to (
            granted(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
                granted(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            ),
        R.string.summary_notifications to androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled(),
        R.string.summary_pin to (summary?.first == true),
        R.string.summary_contacts to ((summary?.second ?: 0) > 0)
    )
    PxPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { (label, on) ->
                Row(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PixelIconView(if (on) PixelIcon.CHECK else PixelIcon.CROSS, 16.dp, if (on) c.lime else c.warn)
                    Text(stringResource(label), style = PxType.body, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(if (on) R.string.summary_active else R.string.summary_inactive).uppercase(),
                        style = PxType.label,
                        color = if (on) c.lime else c.warn
                    )
                }
            }
        }
    }
    Text(stringResource(R.string.summary_partial), style = PxType.caption, color = c.muted)
}

/** Guide « Urgence SOS d'Android » (5 pressions sur le bouton marche/arrêt) : complément du SOS de l'app, en un geste. */
@Composable
private fun SosGuide() {
    val context = LocalContext.current
    Note(PixelIcon.PHONE, stringResource(R.string.sos_guide))
    PxButton(stringResource(R.string.sos_guide_open), {
        val safety = Intent("android.intent.action.SAFETY_CENTER")
        runCatching { context.startActivity(safety) }.recoverCatching {
            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
    }, variant = PxVariant.OUTLINE)
}
