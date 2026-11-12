package com.cerclerouge.app.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerclerouge.alert.Auth
import com.cerclerouge.alert.EngineState
import com.cerclerouge.alert.Input
import com.cerclerouge.alert.RejectReason
import com.cerclerouge.app.R
import com.cerclerouge.app.account.GuardianSync
import com.cerclerouge.app.audio.AudioListener
import com.cerclerouge.app.audio.AudioStatus
import com.cerclerouge.app.audio.ListenActivity
import com.cerclerouge.app.contacts.ContactsRepository
import com.cerclerouge.app.protection.ProtectionController
import com.cerclerouge.app.protection.ProtectionService
import com.cerclerouge.app.protection.UiEvent
import com.cerclerouge.app.security.Biometric
import com.cerclerouge.app.security.SecureScreen
import com.cerclerouge.app.security.SecuritySettings
import com.cerclerouge.app.server.ServerSync
import com.cerclerouge.app.ui.components.FacePhase
import com.cerclerouge.app.ui.components.FaceUnlock
import com.cerclerouge.app.ui.components.Led
import com.cerclerouge.app.ui.components.LogoMark
import com.cerclerouge.app.ui.components.NavItem
import com.cerclerouge.app.ui.components.Orb
import com.cerclerouge.app.ui.components.PinPad
import com.cerclerouge.app.ui.components.PixelDisc
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PixelIconView
import com.cerclerouge.app.ui.components.PxButton
import com.cerclerouge.app.ui.components.PxNavBar
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.PxVariant
import com.cerclerouge.app.ui.components.SosBar
import com.cerclerouge.app.ui.components.TERM_BG
import com.cerclerouge.app.ui.components.TERM_CYAN
import com.cerclerouge.app.ui.components.TopBar
import com.cerclerouge.app.ui.components.TraceLine
import com.cerclerouge.app.ui.components.pixelGrid
import com.cerclerouge.app.ui.journal.traceRows
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.cerclerouge.contacts.ContactStatus
import com.cerclerouge.privacy.log.ChainedEntry
import com.cerclerouge.privacy.log.EventLog
import com.cerclerouge.privacy.pin.PinRepository
import com.valentinerutto.orbmotion.orbs.OrbState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    val controller: ProtectionController,
    private val pins: PinRepository,
    val log: EventLog,
    val contacts: ContactsRepository,
    val server: ServerSync,
    val security: SecuritySettings,
    val audio: AudioListener,
    private val guardians: GuardianSync
) : ViewModel() {
    init {
        viewModelScope.launch {
            contacts.load()
            contacts.applyServerStatuses(server.contactStatuses()) // accord des proches (accepté, refusé, STOP)
            guardians.refresh() // 12–15 ans : l'adulte référent a-t-il confirmé ?
        }
    }

    suspend fun pinConfigured() = pins.isConfigured()
}

/** Ce que l'utilisateur doit confirmer par PIN avant de l'envoyer au moteur. */
private enum class Guarded { DISARM, IM_SAFE }

/** Sous-écrans du parcours SOS (maquettes « SOS · appui long » et « SOS · compte à rebours »). */
private enum class SosStep { NONE, HOLD, COUNTDOWN }

/**
 * Accueil « Pixel Lime » (maquettes Accueil · veille / actif / écoute) et parcours SOS.
 * L'écran d'urgence remplace l'accueil tant qu'une alerte est ouverte.
 */
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onJournal: () -> Unit,
    onContacts: () -> Unit,
    onTrip: () -> Unit = {},
    onListen: () -> Unit = {},
    onVault: () -> Unit = {},
    onSettings: () -> Unit = {},
    sosRequested: Boolean = false,
    onSosConsumed: () -> Unit = {}
) {
    val c = Cr.colors
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val state by vm.controller.state.collectAsState()
    val book by vm.contacts.book.collectAsState()
    val activity by vm.audio.activity.collectAsState()
    val audioStatus by vm.audio.status.collectAsState()
    val contactsReady = book.contacts.count { it.status == ContactStatus.ACCEPTED }
    var sos by remember { mutableStateOf(SosStep.NONE) }
    var pinFor by remember { mutableStateOf<Guarded?>(null) }
    // Biométrie autorisée pour désarmer ; pour « Je vais bien » (annuler une alerte) seulement si l'utilisateur l'a choisi.
    var bioAllowed by remember { mutableStateOf(emptyMap<Guarded, Boolean>()) }
    LaunchedEffect(pinFor) {
        bioAllowed = mapOf(Guarded.DISARM to vm.security.biometric(), Guarded.IM_SAFE to vm.security.biometricForAlerts())
    }
    val soon = stringResource(R.string.home_soon)

    fun dial114() = context.startActivity(Intent(Intent.ACTION_DIAL, "tel:114".toUri())) // R-HOME-07 : toujours le composeur

    // Activation : micro (écoute) et position (point sur la carte, trajet) demandés ensemble, en contexte (R-PLAY-02).
    val armLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val mic = result[Manifest.permission.RECORD_AUDIO] == true
        val location = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!mic) toast(context, resources.getString(R.string.mic_needed))
        vm.controller.send { Input.PermissionsChanged(it, mic = mic, location = location) }
        vm.controller.send { Input.Arm(it) }
    }

    // SOS demandé par la tuile, le widget ou la notification : on passe directement au compte à rebours de 5 s, annulable.
    LaunchedEffect(sosRequested) {
        if (sosRequested) {
            sos = SosStep.COUNTDOWN
            onSosConsumed()
        }
    }
    // Le service de premier plan tourne tant que la protection n'est pas OFF (il s'arrête seul au retour à OFF).
    LaunchedEffect(state.protection) {
        if (state.protection != com.cerclerouge.domain.ProtectionState.OFF) ProtectionService.start(context)
    }
    LaunchedEffect(Unit) {
        val mic = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val loc = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        vm.controller.send { Input.PermissionsChanged(it, mic, loc) } // R-PE-01 : état réel des autorisations
    }
    LaunchedEffect(Unit) {
        vm.controller.events.collect { e ->
            when (e) {
                UiEvent.OpenEmergencyDialer -> dial114()
                is UiEvent.Rejected -> Toast.makeText(context, resources.getString(e.reason.message()), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun guarded(action: Guarded) = scope.launch {
        if (vm.pinConfigured()) {
            pinFor = action
        } else {
            vm.controller.send { if (action == Guarded.DISARM) Input.Disarm(it, Auth.NO_PIN) else Input.ImSafe(it, Auth.NO_PIN) }
        }
    }

    fun toggleProtection() {
        if (state.protection == com.cerclerouge.domain.ProtectionState.OFF) {
            armLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            guarded(Guarded.DISARM)
        }
    }

    Box(Modifier.fillMaxSize().background(c.surface).pixelGrid(c.grid).safeDrawingPadding()) {
        when {
            state.protection.isAlert -> EmergencyScreen(vm, state, onSafe = { guarded(Guarded.IM_SAFE) }, onCall = ::dial114)
            sos == SosStep.HOLD -> SosHoldScreen(onBack = { sos = SosStep.NONE }, onTrigger = { sos = SosStep.COUNTDOWN })
            sos == SosStep.COUNTDOWN -> CountdownScreen(
                onCancel = { sos = SosStep.NONE },
                onDone = {
                    sos = SosStep.NONE
                    vm.controller.send { Input.Sos(it) }
                }
            )
            else -> HomeConsole(
                state = state,
                onToggle = ::toggleProtection,
                onSoon = { toast(context, soon) },
                contactsReady = contactsReady,
                onContacts = onContacts,
                onTrip = onTrip,
                onListen = onListen,
                onSettings = onSettings,
                onSosTrigger = { sos = SosStep.COUNTDOWN },
                onSosTap = { sos = SosStep.HOLD },
                nav = listOf(
                    NavItem(stringResource(R.string.nav_home), PixelIcon.HOME) {},
                    NavItem(stringResource(R.string.nav_vault), PixelIcon.LOCK, onVault),
                    NavItem(stringResource(R.string.nav_trip), PixelIcon.ARROW_RIGHT, onTrip),
                    NavItem(stringResource(R.string.nav_log), PixelIcon.CLOCK, onJournal)
                ),
                map = { armed, showMe, modifier -> LiveMap(armed, showMe, modifier, coveredBottom = 28.dp) },
                activity = activity,
                audioStatus = audioStatus
            )
        }
    }

    pinFor?.let { action ->
        PinDialog(
            onDismiss = { pinFor = null },
            verify = { pin ->
                val auth = vm.controller.authFor(pin)
                vm.controller.send { if (action == Guarded.DISARM) Input.Disarm(it, auth) else Input.ImSafe(it, auth) }
                auth != Auth.FAILED
            },
            onDone = { pinFor = null },
            onBiometric = if (bioAllowed[action] == true) {
                {
                    vm.controller.send {
                        if (action ==
                            Guarded.DISARM
                        ) {
                            Input.Disarm(it, Auth.VERIFIED)
                        } else {
                            Input.ImSafe(it, Auth.VERIFIED)
                        }
                    }
                }
            } else {
                null
            }
        )
    }
}

@Composable
internal fun HomeConsole(
    state: EngineState,
    onToggle: () -> Unit,
    onSoon: () -> Unit,
    contactsReady: Int,
    onContacts: () -> Unit,
    onSosTrigger: () -> Unit,
    onSosTap: () -> Unit,
    nav: List<NavItem>,
    map: @Composable (armed: Boolean, showMe: Boolean, modifier: Modifier) -> Unit,
    onTrip: () -> Unit = onSoon,
    onListen: () -> Unit = onToggle,
    onSettings: () -> Unit = onSoon,
    activity: ListenActivity = ListenActivity(),
    audioStatus: AudioStatus = AudioStatus.STOPPED
) {
    val c = Cr.colors
    val armed = state.protection.isArmed
    // P4 : la position n'est lue que protection active et autorisation accordée.
    val showMe = armed && state.locationGranted
    val listening = armed && state.micGranted
    val trip = state.protection == com.cerclerouge.domain.ProtectionState.TRIP_ACTIVE
    val discA11y = stringResource(if (armed) R.string.home_disc_a11y_on else R.string.home_disc_a11y_off)
    // Coupée : tout est en noir et blanc. Active : la couleur revient (carte, disque, satellites).
    val accent = if (armed) c.lime else c.muted
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(26.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.app_name).uppercase(), style = PxType.brand, color = accent, modifier = Modifier.weight(1f))
            PxPanel(Modifier.width(56.dp), padding = PaddingValues(8.dp)) {
                Text(
                    LocalConfiguration.current.locales[0].language.uppercase(),
                    style = PxType.label,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Carte en haut, pleine largeur ; le losange et ses fonctions viennent par-dessus son bas (référence « connect »).
            map(armed, showMe, Modifier.matchParentSize())
            // Fondus en haut et en bas : légendes, losange et textes restent lisibles par-dessus la carte.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to c.surface.copy(alpha = 0.85f),
                        0.12f to Color.Transparent,
                        0.4f to Color.Transparent,
                        1f to c.surface.copy(alpha = 0.92f)
                    )
                )
            )
            // Légendes en haut à gauche (la colonne de boutons occupe la droite).
            Column(Modifier.padding(start = 12.dp, end = 76.dp)) {
                Text(
                    stringResource(if (showMe) R.string.home_map_caption_on else R.string.home_map_caption).uppercase(),
                    style = PxType.caption,
                    color = if (showMe) c.lime else c.muted
                )
                Text(MapStyle.ATTRIBUTION, style = PxType.caption.copy(fontSize = PxType.caption.fontSize * 0.85f), color = c.muted)
            }
            // Les boutons, empilés dans le coin supérieur droit : ON/OFF d'abord, puis une couleur par fonction.
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End
            ) {
                PowerButton(armed, discA11y, onToggle)
                Satellite(
                    PixelIcon.PIN,
                    stringResource(R.string.home_row_position),
                    stringResource(if (showMe) R.string.sat_position_on else R.string.sat_position_off),
                    armed,
                    onSoon,
                    tint = TERM_CYAN,
                    active = showMe
                )
                Satellite(
                    PixelIcon.USERS,
                    stringResource(R.string.home_row_contacts),
                    stringResource(R.string.sat_contacts, contactsReady),
                    armed,
                    onContacts,
                    tint = THOUGHT_VIOLET
                )
                Satellite(
                    PixelIcon.ARROW_RIGHT,
                    stringResource(R.string.home_row_trip),
                    stringResource(if (trip) R.string.sat_trip_active else R.string.sat_trip_none),
                    armed,
                    onTrip,
                    tint = c.warn,
                    active = trip
                )
                Satellite(
                    PixelIcon.GEAR,
                    stringResource(R.string.home_row_settings),
                    stringResource(R.string.sat_settings),
                    armed,
                    onSettings,
                    tint = c.ink
                )
            }
            // État de la protection, en bas de la carte.
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(if (armed) R.string.home_protected else R.string.home_standby), style = PxType.label, color = accent)
                Text(
                    stringResource(if (listening) R.string.home_listen_on else R.string.home_tap_activate).uppercase(),
                    style = PxType.caption,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp, start = 24.dp, end = 24.dp)
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListenPanels(activity, audioStatus, armed, onOpen = onListen)
            SosBar(onTrigger = onSosTrigger, onTap = onSosTap)
            PxNavBar(nav, selected = 0)
        }
    }
}

/** ON/OFF de la protection : le bouton principal, en haut à droite (rempli quand la protection est active). */
@Composable
private fun PowerButton(armed: Boolean, description: String, onToggle: () -> Unit) {
    val c = Cr.colors
    PxPanel(
        Modifier.size(56.dp).semantics {
            role = Role.Switch
            contentDescription = description
            onClick {
                onToggle()
                true
            }
        }.clickable(onClick = onToggle),
        border = if (armed) c.lime else c.ink,
        fill = if (armed) c.lime else c.surface,
        padding = PaddingValues(0.dp)
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            PixelIconView(PixelIcon.POWER, 22.dp, if (armed) c.onLime else c.ink)
            Text(
                stringResource(if (armed) R.string.home_on else R.string.home_off),
                style = PxType.label,
                color = if (armed) c.onLime else c.ink
            )
        }
    }
}

/**
 * Bouton carré de la colonne de droite (52 dp) : icône seule, voyant d'état dans le coin.
 * TalkBack lit l'étiquette et la valeur. Gris quand la protection est coupée.
 */
@Composable
private fun Satellite(
    icon: PixelIcon,
    label: String,
    value: String,
    armed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Cr.colors.lime,
    active: Boolean = false
) {
    val c = Cr.colors
    val color = if (armed) tint else c.muted
    PxPanel(
        modifier
            .size(52.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label : $value" }
            .clickable(role = Role.Button, onClick = onClick),
        border = if (armed) tint else c.lineControl,
        fill = c.surface,
        padding = PaddingValues(0.dp)
    ) {
        PixelIconView(icon, 24.dp, color, Modifier.align(Alignment.Center))
        Led(if (active) tint else c.line, blink = active, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp), size = 6.dp)
    }
}

/** Maquette « SOS · appui long » : 12 carrés s'allument autour du disque pendant 1,5 s. */
@Composable
internal fun SosHoldScreen(onBack: () -> Unit, onTrigger: () -> Unit) {
    val c = Cr.colors
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val a11y = stringResource(R.string.sos_a11y)
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.sos), onBack)
        Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                val lit = (progress.value * 12).toInt()
                repeat(12) { i ->
                    val angle = Math.toRadians(i * 30.0 - 90)
                    Box(
                        Modifier
                            .offset((130 * Math.cos(angle)).dp, (130 * Math.sin(angle)).dp)
                            .size(12.dp)
                            .background(c.danger.copy(alpha = if (i < lit) 1f else 0.2f))
                    )
                }
                PixelDisc(
                    size = 200.dp,
                    cells = 25,
                    fill = c.danger,
                    glow = c.danger,
                    modifier = Modifier
                        .semantics {
                            role = Role.Button
                            contentDescription = a11y
                            onClick {
                                onTrigger()
                                true
                            }
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                val job = scope.launch {
                                    progress.snapTo(0f)
                                    progress.animateTo(1f, tween(1500, easing = LinearEasing))
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTrigger()
                                }
                                waitForUpOrCancellation()
                                if (progress.value < 1f) {
                                    job.cancel()
                                    scope.launch { progress.snapTo(0f) }
                                }
                            }
                        }
                ) {
                    PixelIconView(PixelIcon.ALERT, 40.dp, c.onLime)
                    Text(
                        stringResource(R.string.sos),
                        style = PxType.display.copy(fontSize = PxType.display.fontSize * 1.7f),
                        color = c.onLime
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.sos_hold_title).uppercase(), style = PxType.display)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.sos_hold_body), style = PxType.body, color = c.muted, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        PxPanel(Modifier.fillMaxWidth().padding(12.dp), padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PixelIconView(PixelIcon.LOCK, 24.dp, c.lime)
                Text(stringResource(R.string.sos_hold_note), style = PxType.caption)
            }
        }
    }
}

/** Maquette « SOS · compte à rebours » : 5 s pour annuler, puis l'alerte part. */
@Composable
internal fun CountdownScreen(onCancel: () -> Unit, onDone: () -> Unit) {
    val c = Cr.colors
    var seconds by remember { mutableIntStateOf(5) }
    val a11y = pluralStringResource(R.plurals.countdown_a11y, seconds, seconds)
    BackHandler(onBack = onCancel)
    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1000)
            seconds--
        }
        onDone()
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.sos), null)
        Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.countdown_label).uppercase(), style = PxType.label, color = c.danger)
            Box(Modifier.padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Orb(OrbState.CONNECTING, 220.dp, c.danger)
                Text(
                    "$seconds",
                    style = PxType.digits,
                    color = c.danger,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Assertive
                        contentDescription = a11y
                    }
                )
            }
            Text(stringResource(R.string.countdown_seconds).uppercase(), style = PxType.display, modifier = Modifier.padding(top = 12.dp))
            Text(
                stringResource(R.string.countdown_body),
                style = PxType.body,
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        PxButton(stringResource(R.string.action_cancel), onCancel, Modifier.padding(12.dp), icon = PixelIcon.CROSS, tall = true)
    }
}

/** Maquette « Urgence » : bandeau d'alerte, contacts, journal, « Je suis en sécurité » et 114. */
@Composable
private fun EmergencyScreen(vm: HomeViewModel, state: EngineState, onSafe: () -> Unit, onCall: () -> Unit) {
    SecureScreen() // S7.04 : pas de capture de l'écran d'urgence
    val c = Cr.colors
    val resources = LocalResources.current
    var lines by remember { mutableStateOf(emptyList<ChainedEntry>()) }
    LaunchedEffect(state) { lines = vm.log.latest(6) }
    val time = remember { DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PxPanel(Modifier.fillMaxWidth(), border = c.danger) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Orb(OrbState.SEARCHING, 48.dp, c.danger)
                Column {
                    Text(stringResource(R.string.urgent_sent).uppercase(), style = PxType.display, color = c.danger)
                    state.alert?.let { Text(time.format(it.openedAt), style = PxType.caption, color = c.muted) }
                }
            }
        }
        Text(stringResource(R.string.urgent_contacts).uppercase(), style = PxType.label, color = c.muted)
        // Réponses des proches via la page de suivi (S3.10) ; à défaut, combien ont été prévenus.
        val book by vm.contacts.book.collectAsState()
        val feedback by vm.server.feedback.collectAsState()
        val replied = feedback.any { it.action != "VIEWED" }
        PxPanel(
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            border = if (replied) c.lime else c.warn,
            padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PixelIconView(if (replied) PixelIcon.CHECK else PixelIcon.USERS, 24.dp, if (replied) c.lime else c.warn)
                Column {
                    when {
                        book.contacts.isEmpty() -> Text(stringResource(R.string.emergency_no_contacts), style = PxType.body)
                        feedback.isEmpty() -> Text(
                            pluralStringResource(R.plurals.urgent_notified, book.contacts.size, book.contacts.size),
                            style = PxType.body
                        )
                        else -> feedback.sortedByDescending { it.at }.forEach { f ->
                            val who = f.label ?: stringResource(R.string.urgent_fb_someone)
                            val text = when (f.action) {
                                "TOOK_CONTACT" -> R.string.urgent_fb_took
                                "FALSE_ALARM" -> R.string.urgent_fb_false
                                "CALLED_EMERGENCY" -> R.string.urgent_fb_called
                                else -> R.string.urgent_fb_viewed
                            }
                            Text(stringResource(text, who), style = if (f.action == "VIEWED") PxType.body else PxType.bodyBold)
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.nav_log).uppercase(), style = PxType.label, color = c.muted)
        // Terminal du journal (référence AgentPrism).
        Column(Modifier.weight(1f).fillMaxWidth().background(TERM_BG).padding(horizontal = 8.dp).verticalScroll(rememberScrollState())) {
            val rows = traceRows(lines, resources)
            rows.forEachIndexed { i, row -> TraceLine(row, last = i == rows.lastIndex) }
        }
        PxButton(stringResource(R.string.urgent_safe), onSafe, icon = PixelIcon.CHECK, tall = true)
        PxButton(stringResource(R.string.home_sat_call), onCall, variant = PxVariant.DARK_DANGER, icon = PixelIcon.PHONE)
    }
}

/**
 * PIN avec l'animation biométrique : visage au repos, cercle qui tourne pendant la vérification, puis coche (fermeture)
 * ou croix (nouvel essai).
 */
@Composable
internal fun PinDialog(
    onDismiss: () -> Unit,
    verify: suspend (String) -> Boolean,
    onDone: () -> Unit,
    onBiometric: (suspend () -> Unit)? = null
) {
    val c = Cr.colors
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val bioTitle = stringResource(R.string.bio_title)
    val bioCancel = stringResource(R.string.bio_use_pin)
    var pin by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(FacePhase.IDLE) }
    Dialog(onDismissRequest = onDismiss) {
        PxPanel(border = c.lime, fill = c.surface, padding = PaddingValues(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FaceUnlock(phase, 64.dp)
                Text(stringResource(R.string.pin_enter).uppercase(), style = PxType.title)
                if (phase == FacePhase.ERROR) {
                    Text(
                        stringResource(R.string.pin_wrong),
                        style = PxType.label,
                        color = c.danger,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                PinPad(pin, { v ->
                    if (phase == FacePhase.SCANNING || phase == FacePhase.SUCCESS) return@PinPad
                    pin = v
                    if (phase == FacePhase.ERROR) phase = FacePhase.IDLE
                    if (v.length == 4) {
                        phase = FacePhase.SCANNING
                        scope.launch {
                            val ok = verify(v)
                            phase = if (ok) FacePhase.SUCCESS else FacePhase.ERROR
                            pin = ""
                            if (ok) {
                                delay(650)
                                onDone()
                            }
                        }
                    }
                }, maxLength = 4)
                // S7.02 : empreinte ou visage, seulement si l'utilisateur l'a activé (et pour une alerte, si autorisé).
                if (onBiometric != null && Biometric.available(activity)) {
                    PxButton(stringResource(R.string.bio_button), {
                        if (phase == FacePhase.SCANNING || phase == FacePhase.SUCCESS) return@PxButton
                        scope.launch {
                            if (Biometric.prompt(activity, bioTitle, bioCancel)) {
                                phase = FacePhase.SUCCESS
                                onBiometric()
                                delay(650)
                                onDone()
                            }
                        }
                    }, variant = PxVariant.OUTLINE, icon = PixelIcon.LOCK)
                }
                PxButton(stringResource(R.string.action_cancel), onDismiss, variant = PxVariant.OUTLINE)
            }
        }
    }
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

internal fun RejectReason.message(): Int = when (this) {
    RejectReason.CONSENT_REQUIRED -> R.string.reject_consent
    RejectReason.NO_SENSOR -> R.string.reject_sensor
    RejectReason.PIN_REQUIRED -> R.string.reject_pin
    RejectReason.PIN_INVALID -> R.string.pin_wrong
    RejectReason.LOCATION_REQUIRED -> R.string.reject_location
}
