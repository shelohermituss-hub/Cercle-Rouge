package com.cerclerouge.app.ui.onboarding

import android.Manifest
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerclerouge.app.BuildConfig
import com.cerclerouge.app.R
import com.cerclerouge.app.account.AccountRepository
import com.cerclerouge.app.account.GuardianStatus
import com.cerclerouge.app.account.GuardianSync
import com.cerclerouge.app.contacts.ContactsRepository
import com.cerclerouge.app.ui.components.Avatar
import com.cerclerouge.app.ui.components.BottomActions
import com.cerclerouge.app.ui.components.Chip
import com.cerclerouge.app.ui.components.DEFAULT_ROUTE
import com.cerclerouge.app.ui.components.DashedAdd
import com.cerclerouge.app.ui.components.Divider
import com.cerclerouge.app.ui.components.GlassIcon
import com.cerclerouge.app.ui.components.InfoNote
import com.cerclerouge.app.ui.components.InkButton
import com.cerclerouge.app.ui.components.Keypad
import com.cerclerouge.app.ui.components.Marks
import com.cerclerouge.app.ui.components.MonoBox
import com.cerclerouge.app.ui.components.MonoLine
import com.cerclerouge.app.ui.components.ONB_ROUTE
import com.cerclerouge.app.ui.components.PageDots
import com.cerclerouge.app.ui.components.PhoneMock
import com.cerclerouge.app.ui.components.PillChoice
import com.cerclerouge.app.ui.components.PinDots
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PixelIconView
import com.cerclerouge.app.ui.components.SectionLabel
import com.cerclerouge.app.ui.components.Shield3D
import com.cerclerouge.app.ui.components.SketchMap
import com.cerclerouge.app.ui.components.SosBar
import com.cerclerouge.app.ui.components.StepBar
import com.cerclerouge.app.ui.components.TextLink
import com.cerclerouge.app.ui.components.ToggleRow
import com.cerclerouge.app.ui.components.Tone
import com.cerclerouge.app.ui.components.V
import com.cerclerouge.app.ui.components.VCard
import com.cerclerouge.app.ui.components.VField
import com.cerclerouge.app.ui.components.VoiceOrb
import com.cerclerouge.app.ui.components.heroCard
import com.cerclerouge.app.ui.components.screenBg
import com.cerclerouge.app.ui.theme.Cr
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

enum class Step { WELCOME, PERMISSIONS, LOGIN, PROFILE, GUARDIAN, PIN, PHRASE, FINISH }

data class LoginUi(val pending: String? = null, val busy: Boolean = false, val message: Int? = null)

data class OnboardingUi(
    val step: Step = Step.WELCOME,
    /** Affiche de l'onboarding (0 à 3). */
    val page: Int = 0,
    val contactName: String = "",
    val contactPhone: String = "+509",
    val contactError: Int? = null,
    val contacts: Int = 0,
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

/** Actions de l'onboarding (le ViewModel ; une version factice pour les captures d'écran). */
interface OnbActions {
    val ui: StateFlow<OnboardingUi>
    val login: StateFlow<LoginUi>
    val book: StateFlow<com.cerclerouge.contacts.ContactBook>
    fun edit(change: OnboardingUi.() -> OnboardingUi)
    fun goTo(step: Step)
    fun saveProfile(): Any?
    fun requestGuardian(): Any?
    fun checkGuardian(): Any?
    fun simulateGuardian(): Any?
    fun chooseRetention(r: Retention): Any?
    fun pinDigit(value: String)
    fun recordConsent(scope: ConsentScope, granted: Boolean): Any?
    fun finish(onDone: () -> Unit): Any?
    fun addFirstContact(): Any?
    fun refreshContacts(): Any?
    fun requestLogin(raw: String): Any?
    fun verifyLogin(code: String, onRestored: () -> Unit): Any?
    fun cancelLogin(): Any?
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
) : ViewModel(), OnbActions {
    /** Connexion sur un nouveau téléphone (1.19) : numéro en attente de code, message, travail en cours. */
    private val _login = MutableStateFlow(LoginUi())
    override val login: StateFlow<LoginUi> = _login

    override fun requestLogin(raw: String) = viewModelScope.launch {
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

    override fun verifyLogin(code: String, onRestored: () -> Unit) = viewModelScope.launch {
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

    override fun cancelLogin() = _login.update { LoginUi() }

    /** Résumé final (1.20) : PIN posé, nombre de proches. Les autorisations sont lues par l'écran. */
    suspend fun summary(): Pair<Boolean, Int> {
        contacts.load()
        return pins.isConfigured() to contacts.book.value.contacts.size
    }

    private val _ui = MutableStateFlow(OnboardingUi())
    override val ui: StateFlow<OnboardingUi> = _ui

    override fun edit(change: OnboardingUi.() -> OnboardingUi) = _ui.update { it.change() }

    override fun goTo(step: Step) = edit { copy(step = step) }

    override fun saveProfile() = viewModelScope.launch {
        val u = _ui.value
        val band = u.band ?: return@launch
        account.saveProfile(u.name, u.year.toInt(), u.month.toInt(), u.adultAttested && band == AgeBand.ADULT)
        goTo(if (band.needsGuardian) Step.GUARDIAN else Step.PIN)
    }

    /** Envoie la demande au serveur, qui prévient l'adulte par SMS (S1.17, S1.22). */
    override fun requestGuardian() = viewModelScope.launch {
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
    override fun checkGuardian() = viewModelScope.launch {
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

    override fun simulateGuardian() = viewModelScope.launch {
        // Builds de test : même effet qu'une vraie confirmation (contact ajouté, protection débloquée).
        if (account.pendingGuardian() == null) account.requestGuardian(_ui.value.guardianPhone, _ui.value.relationship)
        guardians.confirmLocally()
        edit { copy(guardian = GuardianStatus.CONFIRMED) }
    }

    override fun chooseRetention(r: Retention) = viewModelScope.launch {
        settings.setRetention(r)
        edit { copy(retention = r) }
    }

    override fun pinDigit(value: String) {
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
                    edit { copy(pin = "", pinFirst = null, step = Step.PHRASE) }
                }
            }
        }
    }

    override fun recordConsent(scope: ConsentScope, granted: Boolean) = viewModelScope.launch {
        consents.record(Consent(scope, granted, TEXT_VERSION, Locale.getDefault().language, clock.instant()))
    }

    /** Premier proche (VerreSetup5) : invitation envoyée ensuite par le carnet (il doit accepter). */
    override fun addFirstContact() = viewModelScope.launch {
        val u = _ui.value
        when (val r = contacts.add(u.contactName.trim(), u.contactPhone)) {
            com.cerclerouge.app.contacts.AddOutcome.Added -> edit { copy(contactName = "", contactPhone = "+509", contactError = null) }
            is com.cerclerouge.app.contacts.AddOutcome.InvalidPhone -> edit { copy(contactError = R.string.settings_phone_invalid) }
            is com.cerclerouge.app.contacts.AddOutcome.Refused -> edit { copy(contactError = R.string.v_contact_refused) }
        }
        refreshContacts()
    }

    override fun refreshContacts() = viewModelScope.launch {
        contacts.load()
        val n = contacts.book.value.contacts.size
        edit { copy(contacts = n) }
    }

    /** Noms des proches déjà dans le carnet (adulte référent compris), pour la dernière étape. */
    override val book: StateFlow<com.cerclerouge.contacts.ContactBook> get() = contacts.book

    override fun finish(onDone: () -> Unit) = viewModelScope.launch {
        recordConsent(ConsentScope.PRIVACY_POLICY, true).join()
        settings.setOnboardingDone()
        onDone()
    }

    private companion object {
        const val TEXT_VERSION = "2026-11-v1"
    }
}

/** Étape précédente (retour système et flèche). */
private fun Step.previous(ui: OnboardingUi): Step? = when (this) {
    Step.WELCOME -> null
    Step.PERMISSIONS -> Step.WELCOME
    Step.LOGIN, Step.PROFILE -> Step.PERMISSIONS
    Step.GUARDIAN -> Step.PROFILE
    Step.PIN -> if (ui.band?.needsGuardian == true) Step.GUARDIAN else Step.PROFILE
    Step.PHRASE -> Step.PIN
    Step.FINISH -> Step.PHRASE
}

/** Numéro de l'étape de configuration (barre en haut, 1 à 5). */
private fun Step.setupIndex(): Int = when (this) {
    Step.PROFILE -> 1
    Step.GUARDIAN -> 2
    Step.PIN -> 3
    Step.PHRASE -> 4
    else -> 5
}

@Composable
fun OnboardingScreen(vm: OnboardingViewModel, listen: com.cerclerouge.app.ui.listen.ListenViewModel, onSos: () -> Unit, onDone: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val back = ui.step.previous(ui)
    when {
        ui.step == Step.WELCOME && ui.page > 0 -> BackHandler { vm.edit { copy(page = page - 1) } }
        back != null -> BackHandler { vm.goTo(back) }
    }
    Box(Modifier.fillMaxSize().background(screenBg(onboarding = true)).safeDrawingPadding()) {
        when (ui.step) {
            Step.WELCOME -> Posters(ui.page, onSos, onNext = {
                if (ui.page < 3) vm.edit { copy(page = page + 1) } else vm.goTo(Step.PERMISSIONS)
            }, onSkip = { vm.goTo(Step.PERMISSIONS) })
            Step.PERMISSIONS -> PermissionsScreen(vm) { vm.goTo(Step.PROFILE) }
            Step.LOGIN -> SetupPage(null, stringResource(R.string.login_title), stringResource(R.string.login_body), { vm.goTo(Step.PROFILE) }) {
                LoginStep(vm) { vm.goTo(Step.PIN) }
            }
            Step.PROFILE -> ProfileStep(ui, vm)
            Step.GUARDIAN -> GuardianStep(ui, vm, onSos)
            Step.PIN -> PinStep(ui, vm)
            Step.PHRASE -> PhraseStep(listen, { vm.goTo(Step.PIN) }) { vm.goTo(Step.FINISH) }
            Step.FINISH -> FinishStep(ui, vm, onDone)
        }
    }
}

// ---------------------------------------------------------------- Affiches (VerreOnb1 à 4)

@Composable
internal fun Posters(page: Int, onSos: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    val titles = listOf(R.string.v_onb1_title, R.string.v_onb2_title, R.string.v_onb3_title, R.string.v_onb4_title)
    val bodies = listOf(R.string.v_onb1_body, R.string.v_onb2_body, R.string.v_onb3_body, R.string.v_onb4_body)
    // Mise en page des maquettes : « Passer » en haut, téléphone 300 × 460 à 80 du haut, titre à 570, points à 118 du bas.
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(80.dp).padding(top = 18.dp, end = 18.dp), contentAlignment = Alignment.TopEnd) {
            Text(
                stringResource(R.string.v_skip),
                style = V.text(16.sp, FontWeight.Medium),
                color = Cr.colors.muted,
                modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onSkip).padding(horizontal = 6.dp, vertical = 11.dp)
            )
        }
        Box(Modifier.fillMaxWidth().height(460.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedContent(
                page,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (
                        androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(280)) { it / 5 * dir } +
                            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(280))
                        ) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160))
                },
                label = "poster"
            ) { p ->
                PhoneMock {
                    when (p) {
                        0 -> MockHome(onSos)
                        1 -> MockListen()
                        2 -> MockTrip()
                        else -> MockWatcher()
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(titles[page]), style = V.poster, color = Cr.colors.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(stringResource(bodies[page]), style = V.lead, color = Cr.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.weight(1f))
        PageDots(page, 4, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 18.dp))
        BottomActions(Modifier.padding(top = 0.dp)) { InkButton(stringResource(if (page < 3) R.string.action_continue else R.string.v_start), onNext) }
    }
}

/** Affiche 1 : l'accueil en miniature. Le SOS de l'illustration est un vrai SOS (R-ONB-06 : il marche dès le premier écran). */
@Composable
private fun MockHome(onSos: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 26.dp)) {
        com.cerclerouge.app.ui.home.Wordmark(height = 28.dp)
        Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(150.dp).heroCard(28.dp)) {
            Column(Modifier.padding(14.dp).width(140.dp)) {
                Text(stringResource(R.string.v_protected_f), style = V.text(16.sp, FontWeight.SemiBold), color = Cr.colors.ink)
                Text(stringResource(R.string.v_onb1_mock_since), style = V.text(12.sp, line = 17.sp), color = Cr.colors.muted, modifier = Modifier.padding(top = 4.dp))
            }
            Shield3D(true, 76.dp, Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 16.dp))
            SosBar(onTrigger = onSos, onTap = {}, modifier = Modifier.align(Alignment.BottomCenter), shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp), minHeight = 40.dp, hint = false, labelSize = 15.sp)
        }
        Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(110.dp).clip(RoundedCornerShape(20.dp)).background(Color.White), contentAlignment = Alignment.Center) { VoiceOrb(true, 78.dp) }
            SketchMap(Modifier.weight(1f).height(110.dp).clip(RoundedCornerShape(20.dp)), route = DEFAULT_ROUTE.dropLast(1), designW = 129f, designH = 110f, roadWidth = 5f, routeWidth = 1.4f, me = null)
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            com.cerclerouge.app.ui.components.MainNav(com.cerclerouge.app.ui.components.Tab.HOME, {}, armed = true, onToggle = {}, insets = false)
        }
    }
}

@Composable
private fun MockListen() {
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 26.dp)) {
        Text(stringResource(R.string.v_listen), style = V.text(22.sp, FontWeight.SemiBold), color = Cr.colors.ink)
        Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 18.dp), contentAlignment = Alignment.Center) { VoiceOrb(true, 170.dp) }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 14.dp)) {
            MockStatRow(R.drawable.glass_volume, stringResource(R.string.v_voice_far), stringResource(R.string.v_no_scream), "62 %", Cr.colors.ink)
            Divider()
            MockStatRow(R.drawable.glass_lock, stringResource(R.string.v_recorded), stringResource(R.string.v_nothing), "0 s", Cr.colors.forest)
        }
    }
}

@Composable
private fun MockStatRow(icon: Int, title: String, sub: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassIcon(icon, 34.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = V.text(17.sp, FontWeight.Medium), color = Cr.colors.ink)
            Text(sub, style = V.small, color = Cr.colors.muted)
        }
        Text(value, style = V.text(18.sp, FontWeight.Medium), color = valueColor)
    }
}

@Composable
private fun MockTrip() {
    Box(Modifier.fillMaxSize()) {
        SketchMap(Modifier.matchParentSize(), route = ONB_ROUTE, designW = 300f, designH = 460f, roadWidth = 8f, routeWidth = 2.2f)
        GlassIcon(R.drawable.glass_home, 34.dp, Modifier.padding(start = 181.dp, top = 338.dp))
        Row(
            Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassIcon(R.drawable.glass_trajet, 30.dp)
            Column {
                Text(stringResource(R.string.v_onb3_mock_title), style = V.bodyStrong, color = Cr.colors.ink)
                Text(stringResource(R.string.v_onb3_mock_sub), style = V.small, color = Cr.colors.muted)
            }
        }
    }
}

@Composable
private fun MockWatcher() {
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassIcon(R.drawable.glass_terminal, 36.dp)
            Text(stringResource(R.string.home_watcher), style = V.text(22.sp, FontWeight.SemiBold), color = Cr.colors.ink)
        }
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(16.dp)).background(Cr.colors.calmTint).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassIcon(R.drawable.glass_check, 26.dp)
            Text(stringResource(R.string.v_all_calm), style = V.bodyStrong, color = Cr.colors.calmText)
        }
        MonoBox(
            listOf(
                MonoLine("▸", Marks.seen, stringResource(R.string.v_mock_l1)),
                MonoLine("▸", Marks.seen, stringResource(R.string.v_mock_l2)),
                MonoLine("◆", Marks.thought, stringResource(R.string.v_mock_l3)),
                MonoLine("✓", Marks.next, stringResource(R.string.v_mock_l4))
            ),
            Modifier.padding(top = 12.dp),
            side = 0.dp
        )
        Text(
            stringResource(R.string.v_mock_q),
            style = V.text(14.sp, line = 19.sp),
            color = Color.White,
            modifier = Modifier.padding(top = 12.dp, start = 40.dp).clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)).background(Color(0xFF2F86F6)).padding(10.dp, 10.dp)
        )
        Text(
            stringResource(R.string.v_mock_a),
            style = V.text(14.sp, line = 19.sp),
            color = Cr.colors.ink,
            modifier = Modifier.padding(top = 8.dp, end = 30.dp).clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)).background(Color.White).padding(10.dp, 10.dp)
        )
    }
}

// ---------------------------------------------------------------- Autorisations (VerrePermissions)

private data class Perm(val icon: Int, val title: Int, val body: Int, val perms: List<String>)

@Composable
internal fun PermissionsScreen(vm: OnbActions, onNext: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    fun granted(p: String) = context.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val notif = if (android.os.Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
    val items = listOf(
        Perm(R.drawable.glass_volume, R.string.v_perm_mic, R.string.v_perm_mic_body, listOf(Manifest.permission.RECORD_AUDIO)),
        Perm(R.drawable.glass_pin, R.string.v_perm_loc, R.string.v_perm_loc_body, listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
        Perm(R.drawable.glass_bell, R.string.v_perm_notif, R.string.v_perm_notif_body, notif),
        Perm(R.drawable.glass_chat, R.string.v_perm_sms, R.string.v_perm_sms_body, listOf(Manifest.permission.SEND_SMS))
    )
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        result[Manifest.permission.RECORD_AUDIO]?.let { vm.recordConsent(ConsentScope.MICROPHONE, it) }
        result[Manifest.permission.ACCESS_FINE_LOCATION]?.let { vm.recordConsent(ConsentScope.LOCATION, it) }
        if (notif.isNotEmpty()) result[notif[0]]?.let { vm.recordConsent(ConsentScope.NOTIFICATIONS, it) }
        tick++
    }
    val pm = context.getSystemService(android.os.PowerManager::class.java)
    @Suppress("UNUSED_EXPRESSION") tick
    val batteryOk = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    val missingCore = !granted(Manifest.permission.RECORD_AUDIO) || !(granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION))
    val allOn = items.all { it.perms.all(::granted) } && batteryOk
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 96.dp)) {
            Text(stringResource(R.string.v_perm_title), style = V.text(29.sp, FontWeight.Bold, 35.sp, -0.021f), color = Cr.colors.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
            Text(stringResource(R.string.v_perm_body), style = V.lead, color = Cr.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp))
            Column(Modifier.padding(top = 8.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { item ->
                    val on = item.perms.isEmpty() || item.perms.any(::granted)
                    PermRow(item.icon, stringResource(item.title), stringResource(item.body), on) { if (!on) launcher.launch(item.perms.toTypedArray()) }
                }
                PermRow(R.drawable.glass_battery, stringResource(R.string.v_perm_battery), stringResource(R.string.v_perm_battery_body), batteryOk) {
                    if (!batteryOk) {
                        runCatching {
                            context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                        tick++
                    }
                }
            }
            if (missingCore) {
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelIconView(PixelIcon.ALERT, 18.dp, Cr.colors.danger, strokeWidth = 2f)
                    Text(stringResource(R.string.v_perm_needed), style = V.text(15.sp, line = 21.sp), color = Cr.colors.danger)
                }
            }
        }
        BottomActions {
            if (allOn) {
                InkButton(stringResource(R.string.action_continue), onNext)
            } else {
                InkButton(stringResource(R.string.v_perm_all), {
                    val all = items.flatMap { it.perms }.filterNot(::granted)
                    if (all.isEmpty()) onNext() else launcher.launch(all.toTypedArray())
                })
                TextLink(stringResource(R.string.action_later), onNext)
            }
        }
    }
}

@Composable
private fun PermRow(icon: Int, title: String, body: String, on: Boolean, onTurnOn: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(on, role = Role.Switch) { if (it) onTurnOn() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFECEEF1)), contentAlignment = Alignment.Center) { GlassIcon(icon, 30.dp) }
        Column(Modifier.weight(1f)) {
            Text(title, style = V.text(17.sp, FontWeight.SemiBold), color = Cr.colors.ink)
            Text(body, style = V.small.copy(lineHeight = 19.sp), color = Cr.colors.muted)
        }
        androidx.compose.material3.Switch(
            checked = on,
            onCheckedChange = null,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = Cr.colors.lime,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFA9B6C3),
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

// ---------------------------------------------------------------- Configuration (VerreSetup1 à 5)

/** Cadre d'une étape : retour, « configuration · n/5 », barre d'étapes, titre 29 px et texte 17 px, contenu, actions en bas. */
@Composable
private fun SetupPage(
    step: Int?,
    title: String,
    body: String,
    onBack: (() -> Unit)?,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val backLabel = stringResource(R.string.back)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).then(if (onBack != null) Modifier.clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = backLabel } else Modifier), contentAlignment = Alignment.Center) {
                if (onBack != null) PixelIconView(PixelIcon.ARROW_LEFT, 24.dp, Cr.colors.ink, strokeWidth = 2.2f)
            }
            Text(
                if (step != null) stringResource(R.string.v_setup_step, step) else "",
                style = V.mono,
                color = Cr.colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(48.dp))
        }
        if (step != null) StepBar(step, 5)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp)) {
                Text(title, style = V.poster, color = Cr.colors.ink, modifier = Modifier.semantics { heading() })
                Text(body, style = V.lead, color = Cr.colors.muted, modifier = Modifier.padding(top = 10.dp))
            }
            Column(Modifier.padding(top = 24.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
        }
        actions?.let { BottomActions(content = it) }
    }
}

@Composable
internal fun ProfileStep(ui: OnboardingUi, vm: OnbActions) {
    val band = ui.band
    SetupPage(
        1,
        stringResource(R.string.v_setup1_title),
        stringResource(R.string.v_setup1_body),
        { vm.goTo(Step.PERMISSIONS) },
        actions = {
            InkButton(stringResource(R.string.action_continue), { vm.saveProfile() }, enabled = ui.name.isNotBlank() && band != null && band != AgeBand.UNDER_12 && ui.holderAttested)
            TextLink(stringResource(R.string.login_have_account), { vm.goTo(Step.LOGIN) })
        }
    ) {
        VField(stringResource(R.string.v_first_name), ui.name, { v -> vm.edit { copy(name = v.take(40)) } })
        Column {
            Text(stringResource(R.string.v_birth_date), style = V.text(14.sp, FontWeight.SemiBold), color = Cr.colors.ink, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VField(
                    null,
                    ui.month,
                    { v -> vm.edit { copy(month = v.filter(Char::isDigit).take(2)) } },
                    Modifier.weight(1f),
                    placeholder = stringResource(R.string.profile_month),
                    side = 0.dp,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                VField(
                    null,
                    ui.year,
                    { v -> vm.edit { copy(year = v.filter(Char::isDigit).take(4)) } },
                    Modifier.weight(1f),
                    placeholder = stringResource(R.string.profile_year),
                    side = 0.dp,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Text(stringResource(R.string.v_birth_hint), style = V.small, color = Cr.colors.muted, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp))
        }
        when (band) {
            AgeBand.UNDER_12 -> InfoNote(
                stringResource(
                    R.string.profile_under_12,
                    LocalContext.current.getSystemService(android.telephony.TelephonyManager::class.java).let {
                        com.cerclerouge.domain.EmergencyNumbers.resolve(null, it?.simCountryIso, it?.networkCountryIso).primary
                    }
                ),
                R.drawable.glass_warning,
                warn = true
            )
            null -> Unit
            else -> if (band.needsGuardian) InfoNote(stringResource(R.string.v_setup1_minor)) else Unit
        }
        if (band != null && band != AgeBand.UNDER_12) {
            VCard(padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp)) {
                if (band == AgeBand.ADULT) {
                    ToggleRow(stringResource(R.string.profile_adult), null, ui.adultAttested) { vm.edit { copy(adultAttested = it) } }
                    Divider()
                }
                // R-ONB-08 : anti-stalkerware, la personne protégée est le détenteur du téléphone.
                ToggleRow(stringResource(R.string.profile_holder), null, ui.holderAttested) { vm.edit { copy(holderAttested = it) } }
            }
        }
    }
}

@Composable
internal fun GuardianStep(ui: OnboardingUi, vm: OnbActions, onSos: () -> Unit) {
    val ready = ui.guardian != GuardianStatus.NONE
    SetupPage(
        2,
        stringResource(R.string.v_setup2_title),
        stringResource(R.string.v_setup2_body),
        { vm.goTo(Step.PROFILE) },
        actions = {
            when (ui.guardian) {
                GuardianStatus.NONE -> InkButton(
                    stringResource(R.string.guardian_send),
                    { vm.requestGuardian() },
                    enabled = !ui.guardianBusy && ui.guardianPhone.count(Char::isDigit) in 8..15
                )
                GuardianStatus.PENDING -> {
                    InkButton(stringResource(R.string.action_continue), { vm.goTo(Step.PIN) })
                    TextLink(stringResource(R.string.guardian_check), { vm.checkGuardian() })
                }
                GuardianStatus.CONFIRMED -> InkButton(stringResource(R.string.action_continue), { vm.goTo(Step.PIN) })
            }
        }
    ) {
        if (!ready) {
            VField(
                stringResource(R.string.guardian_phone),
                ui.guardianPhone,
                { v -> vm.edit { copy(guardianPhone = v.take(20)) } },
                hint = stringResource(R.string.v_guardian_hint),
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            Column {
                Text(stringResource(R.string.v_link), style = V.text(14.sp, FontWeight.SemiBold), color = Cr.colors.ink, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
                PillChoice(
                    listOf("parent" to stringResource(R.string.relation_parent), "tuteur" to stringResource(R.string.relation_tutor), "autre" to stringResource(R.string.relation_other)),
                    ui.relationship,
                    { vm.edit { copy(relationship = it) } }
                )
            }
        } else {
            VCard(padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(stringResource(R.string.relation_parent), 0)
                    Column(Modifier.weight(1f)) {
                        Text(ui.guardianPhone, style = V.rowStrong, color = Cr.colors.ink)
                        Text(
                            stringResource(if (ui.guardian == GuardianStatus.CONFIRMED) R.string.v_guardian_confirmed else R.string.v_guardian_waiting),
                            style = V.mono,
                            color = if (ui.guardian == GuardianStatus.CONFIRMED) Marks.done else Marks.wait
                        )
                    }
                    Chip(stringResource(R.string.v_referent), Tone.Ink)
                }
            }
            if (ui.guardian == GuardianStatus.PENDING) {
                InfoNote(stringResource(R.string.guardian_waiting_sos), R.drawable.glass_clock)
                SosBar(onTrigger = onSos, onTap = {}, modifier = Modifier.padding(horizontal = 16.dp), minHeight = 54.dp)
                TextLink(stringResource(R.string.guardian_change), { vm.edit { copy(guardian = GuardianStatus.NONE) } })
                if (BuildConfig.DEBUG) TextLink(stringResource(R.string.guardian_simulate), { vm.simulateGuardian() })
            }
        }
        ui.guardianError?.let { InfoNote(stringResource(it), R.drawable.glass_warning, warn = true) }
    }
}

@Composable
internal fun PinStep(ui: OnboardingUi, vm: OnbActions) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            SetupPage(
                3,
                stringResource(if (ui.pinFirst == null) R.string.v_setup3_title else R.string.pin_confirm),
                stringResource(R.string.v_setup3_body),
                { vm.goTo(if (ui.band?.needsGuardian == true) Step.GUARDIAN else Step.PROFILE) }
            ) {
                PinDots(ui.pin.length, 4, Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
                Text(
                    ui.pinError?.let { stringResource(it) } ?: stringResource(R.string.v_pin_hint),
                    style = V.text(14.sp),
                    color = if (ui.pinError != null) Cr.colors.danger else Cr.colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Keypad(
            { d -> if (ui.pin.length < 4) vm.pinDigit(ui.pin + d) },
            { vm.edit { copy(pin = pin.dropLast(1)) } },
            Modifier.padding(bottom = 28.dp)
        )
    }
}

/** Étape 4 : phrase secrète, facultative (3 répétitions, empreinte seule, R-AUD-09). */
@Composable
private fun PhraseStep(vm: com.cerclerouge.app.ui.listen.ListenViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val ui by vm.ui.collectAsState()
    PhraseContent(ui, vm.granted, { vm.recordNext() }, onBack, onNext)
}

@Composable
internal fun PhraseContent(ui: com.cerclerouge.app.ui.listen.ListenUi, granted: Boolean, onRecord: () -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    val recording = ui.step is com.cerclerouge.app.ui.listen.Step.Recording
    val done = ui.enrolled && ui.keywordTarget == null && ui.samples == 0
    SetupPage(
        4,
        stringResource(R.string.v_setup4_title),
        stringResource(R.string.v_setup4_body),
        onBack,
        actions = {
            if (done) {
                InkButton(stringResource(R.string.action_continue), onNext)
            } else {
                InkButton(
                    stringResource(if (recording) R.string.v_phrase_listening else R.string.v_phrase_say),
                    onRecord,
                    enabled = !recording && granted,
                    icon = PixelIcon.MIC
                )
                TextLink(stringResource(R.string.action_later), onNext)
            }
        }
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().heroCard().padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            VoiceOrb(true, 130.dp)
            Text(
                if (done) stringResource(R.string.v_phrase_done) else stringResource(R.string.v_phrase_rep, (ui.samples + 1).coerceAtMost(3)),
                style = V.text(17.sp, FontWeight.SemiBold),
                color = Cr.colors.ink
            )
            Text(stringResource(R.string.v_phrase_mono), style = V.mono, color = Marks.seen)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i -> Box(Modifier.width(36.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (done || i < ui.samples) Cr.colors.lime else Color(0xFFC9D2DB))) }
            }
            (ui.step as? com.cerclerouge.app.ui.listen.Step.Message)?.let { m ->
                Text(stringResource(m.text), style = V.small, color = if (m.ok) Cr.colors.forest else Cr.colors.danger, textAlign = TextAlign.Center)
            }
        }
        Text(stringResource(R.string.v_phrase_tip), style = V.text(14.sp, line = 20.sp), color = Cr.colors.muted, modifier = Modifier.padding(horizontal = 22.dp))
    }
}

/** Étape 5 : proches, accords et durée de conservation (ADR-0017 : rien de présélectionné). */
@Composable
internal fun FinishStep(ui: OnboardingUi, vm: OnbActions, onDone: () -> Unit) {
    val book by vm.book.collectAsState()
    LaunchedEffect(Unit) { vm.refreshContacts() }
    var listenOk by remember { mutableStateOf(true) }
    var locOk by remember { mutableStateOf(true) }
    SetupPage(
        5,
        stringResource(R.string.v_setup5_title),
        stringResource(if (book.contacts.isEmpty()) R.string.v_setup5_body_none else R.string.v_setup5_body, (5 - book.contacts.size).coerceAtLeast(0)),
        { vm.goTo(Step.PHRASE) },
        actions = {
            InkButton(stringResource(R.string.v_finish), {
                vm.recordConsent(ConsentScope.MICROPHONE, listenOk)
                vm.recordConsent(ConsentScope.LOCATION, locOk)
                vm.finish(onDone)
            }, enabled = ui.retention != null)
        }
    ) {
        if (book.contacts.isNotEmpty()) {
            VCard {
                book.contacts.sortedBy { it.position }.forEachIndexed { i, ct ->
                    if (i > 0) Divider(Modifier.padding(horizontal = 14.dp))
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(ct.name, i)
                        Column(Modifier.weight(1f)) {
                            Text(ct.name, style = V.rowStrong, color = Cr.colors.ink)
                            Text(stringResource(R.string.v_invite_sms), style = V.mono, color = Marks.wait)
                        }
                        if (ct.isGuardian) Chip(stringResource(R.string.v_referent), Tone.Ink)
                    }
                }
            }
        }
        if (book.contacts.size < 5) {
            VField(stringResource(R.string.v_first_name), ui.contactName, { v -> vm.edit { copy(contactName = v.take(40)) } })
            VField(
                stringResource(R.string.v_number),
                ui.contactPhone,
                { v -> vm.edit { copy(contactPhone = v.take(20), contactError = null) } },
                error = ui.contactError?.let { stringResource(it) },
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            DashedAdd(stringResource(R.string.v_add_contact_places, 5 - book.contacts.size), { vm.addFirstContact() }, enabled = ui.contactName.isNotBlank() && ui.contactPhone.count(Char::isDigit) >= 8)
        }
        Column {
            SectionLabel(stringResource(R.string.v_agreements))
            VCard(padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp)) {
                ToggleRow(stringResource(R.string.v_agree_listen), stringResource(R.string.v_agree_listen_sub), listenOk) { listenOk = it }
                Divider()
                ToggleRow(stringResource(R.string.v_agree_loc), stringResource(R.string.v_agree_loc_sub), locOk) { locOk = it }
            }
        }
        Column {
            SectionLabel(stringResource(R.string.v_retention))
            PillChoice(
                listOf(
                    Retention.HOURS_24 to stringResource(R.string.retention_24h),
                    Retention.DAYS_7 to stringResource(R.string.retention_7d),
                    Retention.DAYS_30 to stringResource(R.string.retention_30d),
                    Retention.ON_CLOSURE to stringResource(R.string.retention_closure),
                    Retention.MANUAL to stringResource(R.string.retention_manual)
                ),
                ui.retention,
                { vm.chooseRetention(it) }
            )
            Text(stringResource(R.string.retention_recording_notice), style = V.small, color = Cr.colors.muted, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp))
            if (ui.retention == Retention.MANUAL) {
                Text(stringResource(R.string.retention_manual_warning), style = V.small, color = Cr.colors.warn, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp))
            }
        }
        Text(stringResource(R.string.v_change_later), style = V.small, color = Cr.colors.muted, modifier = Modifier.padding(horizontal = 22.dp))
    }
}

/**
 * « J'ai déjà un compte » (1.19) : numéro vérifié + code SMS. Le profil revient, les contacts aussi si leur sauvegarde était
 * activée. Les preuves de l'ancien téléphone ne sont pas transférées.
 */
@Composable
private fun ColumnScope.LoginStep(vm: OnbActions, onRestored: () -> Unit) {
    val s by vm.login.collectAsState()
    var number by remember { mutableStateOf("+509") }
    var code by remember { mutableStateOf("") }
    s.message?.let { InfoNote(stringResource(it), R.drawable.glass_warning, warn = true) }
    val pending = s.pending
    if (pending == null) {
        VField(stringResource(R.string.settings_phone_label), number, { number = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' }.take(20) }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone))
        Box(Modifier.padding(horizontal = 18.dp)) { InkButton(stringResource(R.string.settings_phone_send), { vm.requestLogin(number) }, enabled = !s.busy && number.length >= 8) }
    } else {
        VField(
            stringResource(R.string.settings_phone_code_label),
            code,
            { code = it.filter(Char::isDigit).take(8) },
            hint = stringResource(R.string.settings_phone_code_for, pending),
            keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InkButton(stringResource(R.string.settings_phone_verify), { vm.verifyLogin(code, onRestored) }, enabled = !s.busy && code.length >= 6)
            TextLink(stringResource(R.string.action_cancel), { vm.cancelLogin() })
        }
    }
    InfoNote(stringResource(R.string.login_privacy), R.drawable.glass_lock)
}
