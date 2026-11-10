package com.cerclerouge.app.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.cerclerouge.alert.Auth
import com.cerclerouge.alert.EngineState
import com.cerclerouge.alert.Input
import com.cerclerouge.alert.RejectReason
import com.cerclerouge.app.R
import com.cerclerouge.app.protection.ProtectionController
import com.cerclerouge.app.protection.UiEvent
import com.cerclerouge.app.ui.components.EqBars
import com.cerclerouge.app.ui.components.Led
import com.cerclerouge.app.ui.components.NavItem
import com.cerclerouge.app.ui.components.PinPad
import com.cerclerouge.app.ui.components.PixelDisc
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PixelIconView
import com.cerclerouge.app.ui.components.PxButton
import com.cerclerouge.app.ui.components.PxNavBar
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.PxVariant
import com.cerclerouge.app.ui.components.SosBar
import com.cerclerouge.app.ui.components.TopBar
import com.cerclerouge.app.ui.components.pixelGrid
import com.cerclerouge.app.ui.journal.humanize
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.cerclerouge.privacy.log.ChainedEntry
import com.cerclerouge.privacy.log.EventLog
import com.cerclerouge.privacy.pin.PinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(val controller: ProtectionController, private val pins: PinRepository, val log: EventLog) :
    ViewModel() {
    suspend fun pinConfigured() = pins.isConfigured()
}

/** Ce que l'utilisateur doit confirmer par PIN avant de l'envoyer au moteur. */
private enum class Guarded { DISARM, IM_SAFE }

/** Sous-écrans du parcours SOS (maquettes « SOS · appui long » et « SOS · compte à rebours »). */
private enum class SosStep { NONE, HOLD, COUNTDOWN }

/** Contacts de confiance : la gestion arrive au Sprint 2. */
private const val CONTACTS_READY = 0

/**
 * Accueil « Pixel Lime » (maquettes Accueil · veille / actif / écoute) et parcours SOS.
 * L'écran d'urgence remplace l'accueil tant qu'une alerte est ouverte.
 */
@Composable
fun HomeScreen(vm: HomeViewModel, onJournal: () -> Unit) {
    val c = Cr.colors
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val state by vm.controller.state.collectAsState()
    var sos by remember { mutableStateOf(SosStep.NONE) }
    var pinFor by remember { mutableStateOf<Guarded?>(null) }
    val soon = stringResource(R.string.home_soon)

    fun dial114() = context.startActivity(Intent(Intent.ACTION_DIAL, "tel:114".toUri())) // R-HOME-07 : toujours le composeur

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast(context, resources.getString(R.string.mic_needed))
        vm.controller.send { Input.PermissionsChanged(it, mic = granted, location = state.locationGranted) }
        vm.controller.send { Input.Arm(it) }
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
            micLauncher.launch(Manifest.permission.RECORD_AUDIO) // demandé en contexte (R-PLAY-02)
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
                onSosTrigger = { sos = SosStep.COUNTDOWN },
                onSosTap = { sos = SosStep.HOLD },
                nav = listOf(
                    NavItem(stringResource(R.string.nav_home), PixelIcon.HOME) {},
                    NavItem(stringResource(R.string.nav_map), PixelIcon.PIN) { toast(context, soon) },
                    NavItem(stringResource(R.string.nav_trip), PixelIcon.ARROW_RIGHT) { toast(context, soon) },
                    NavItem(stringResource(R.string.nav_log), PixelIcon.CLOCK, onJournal)
                )
            )
        }
    }

    pinFor?.let { action ->
        PinDialog(
            onDismiss = { pinFor = null },
            onPin = { pin ->
                pinFor = null
                scope.launch {
                    val auth = vm.controller.authFor(pin)
                    vm.controller.send { if (action == Guarded.DISARM) Input.Disarm(it, auth) else Input.ImSafe(it, auth) }
                    if (auth == Auth.FAILED) toast(context, resources.getString(R.string.pin_wrong))
                }
            }
        )
    }
}

@Composable
internal fun HomeConsole(
    state: EngineState,
    onToggle: () -> Unit,
    onSoon: () -> Unit,
    onSosTrigger: () -> Unit,
    onSosTap: () -> Unit,
    nav: List<NavItem>
) {
    val c = Cr.colors
    val armed = state.protection.isArmed
    val listening = armed && state.micGranted
    val discA11y = stringResource(if (armed) R.string.home_disc_a11y_on else R.string.home_disc_a11y_off)
    // Coupée : tout est en noir et blanc. Active : la couleur revient (carte, disque, satellites).
    val accent = if (armed) c.lime else c.muted
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PixelIconView(PixelIcon.SHIELD, 24.dp, accent)
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
            HaitiMap(
                landColor = if (armed) c.lime.copy(alpha = 0.18f) else c.ink.copy(alpha = 0.08f),
                coastColor = if (armed) c.lime.copy(alpha = 0.6f) else c.ink.copy(alpha = 0.26f),
                seaColor = c.line,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.3f)
            )
            Text(
                stringResource(R.string.home_map_caption).uppercase(),
                style = PxType.caption,
                color = c.muted,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(336.dp, 288.dp), contentAlignment = Alignment.Center) {
                    // Losange qui relie le disque aux quatre fonctions (repris de la référence « connect »).
                    Canvas(Modifier.size(260.dp)) {
                        val w = size.width
                        val stroke = 2.dp.toPx()
                        val col = if (armed) c.lime.copy(alpha = 0.5f) else c.line
                        val pts = listOf(Offset(w / 2, 0f), Offset(w, w / 2), Offset(w / 2, w), Offset(0f, w / 2))
                        pts.forEachIndexed { i, p -> drawLine(col, p, pts[(i + 1) % 4], stroke) }
                        pts.forEach { drawRect(col, it - Offset(4.dp.toPx(), 4.dp.toPx()), Size(8.dp.toPx(), 8.dp.toPx())) }
                    }
                    PixelDisc(
                        size = 144.dp,
                        cells = 18,
                        fill = if (armed) c.lime else c.ink,
                        glow = if (armed) c.lime else null,
                        orbit = armed,
                        modifier = Modifier.semantics {
                            role = Role.Switch
                            contentDescription = discA11y
                            onClick {
                                onToggle()
                                true
                            }
                        }.clickable(onClick = onToggle)
                    ) {
                        PixelIconView(PixelIcon.POWER, 32.dp, c.onLime)
                        Text(stringResource(if (armed) R.string.home_on else R.string.home_off), style = PxType.display, color = c.onLime)
                    }
                    Satellite(
                        PixelIcon.MIC,
                        stringResource(R.string.home_row_listen),
                        stringResource(if (listening) R.string.sat_on else R.string.sat_off),
                        armed,
                        onToggle,
                        Modifier.align(Alignment.TopStart)
                    ) { if (listening) EqBars() else Led(c.line, blink = false) }
                    Satellite(
                        PixelIcon.PIN,
                        stringResource(R.string.home_row_position),
                        stringResource(R.string.sat_position_off),
                        armed,
                        onSoon,
                        Modifier.align(Alignment.TopEnd)
                    )
                    Satellite(
                        PixelIcon.USERS,
                        stringResource(R.string.home_row_contacts),
                        stringResource(R.string.sat_contacts, CONTACTS_READY),
                        armed,
                        onSoon,
                        Modifier.align(Alignment.BottomStart)
                    )
                    Satellite(
                        PixelIcon.ARROW_RIGHT,
                        stringResource(R.string.home_row_trip),
                        stringResource(R.string.sat_trip_none),
                        armed,
                        onSoon,
                        Modifier.align(Alignment.BottomEnd)
                    )
                }
                Text(stringResource(if (armed) R.string.home_protected else R.string.home_standby), style = PxType.label, color = accent)
                Text(
                    stringResource(if (listening) R.string.home_listen_on else R.string.home_tap_activate).uppercase(),
                    style = PxType.caption,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp)
                )
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SosBar(onTrigger = onSosTrigger, onTap = onSosTap)
            PxNavBar(nav, selected = 0)
        }
    }
}

/** Fonction autour du disque : icône, étiquette, valeur courte ; grise quand la protection est coupée. */
@Composable
private fun Satellite(
    icon: PixelIcon,
    label: String,
    value: String,
    armed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    val c = Cr.colors
    PxPanel(
        modifier.width(112.dp).clickable(role = Role.Button, onClick = onClick),
        border = if (armed) c.lime else c.lineControl,
        padding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelIconView(icon, 20.dp, if (armed) c.lime else c.muted)
                Spacer(Modifier.weight(1f))
                trailing()
            }
            Text(label.uppercase(), style = PxType.label, color = c.muted, maxLines = 1)
            Text(value.uppercase(), style = PxType.label, color = if (armed) c.ink else c.muted, maxLines = 1)
        }
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
            Text(
                "$seconds",
                style = PxType.digits,
                color = c.danger,
                modifier = Modifier.padding(top = 8.dp).semantics {
                    liveRegion = LiveRegionMode.Assertive
                    contentDescription = a11y
                }
            )
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
    val c = Cr.colors
    val resources = LocalResources.current
    var lines by remember { mutableStateOf(emptyList<ChainedEntry>()) }
    LaunchedEffect(state) { lines = vm.log.latest(4) }
    val time = remember { DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PxPanel(Modifier.fillMaxWidth(), border = c.danger) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Led(c.danger, blink = true, size = 12.dp)
                Column {
                    Text(stringResource(R.string.urgent_sent).uppercase(), style = PxType.display, color = c.danger)
                    state.alert?.let { Text(time.format(it.openedAt), style = PxType.caption, color = c.muted) }
                }
            }
        }
        Text(stringResource(R.string.urgent_contacts).uppercase(), style = PxType.label, color = c.muted)
        PxPanel(Modifier.fillMaxWidth(), border = c.warn, padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PixelIconView(PixelIcon.USERS, 24.dp, c.warn)
                Text(stringResource(R.string.emergency_no_contacts), style = PxType.body)
            }
        }
        PxPanel(Modifier.fillMaxWidth(), fill = c.surface, padding = PaddingValues(8.dp)) {
            Column {
                Text(
                    stringResource(R.string.nav_log).uppercase(),
                    style = PxType.label,
                    color = c.muted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                lines.forEach { e ->
                    Text(
                        "${time.format(Instant.ofEpochMilli(e.utcMillis))} ... ${resources.getString(humanize(e))}".uppercase(),
                        style = PxType.caption
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PxButton(stringResource(R.string.urgent_safe), onSafe, icon = PixelIcon.CHECK, tall = true)
        PxButton(stringResource(R.string.home_sat_call), onCall, variant = PxVariant.DARK_DANGER, icon = PixelIcon.PHONE)
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onPin: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        PxPanel(border = Cr.colors.lime, fill = Cr.colors.surface, padding = PaddingValues(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.pin_enter).uppercase(), style = PxType.title)
                PinPad(pin, { v ->
                    pin = v
                    if (v.length == 4) onPin(v)
                }, maxLength = 4)
                PxButton(stringResource(R.string.action_cancel), onDismiss, variant = PxVariant.OUTLINE)
            }
        }
    }
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

private fun RejectReason.message(): Int = when (this) {
    RejectReason.CONSENT_REQUIRED -> R.string.reject_consent
    RejectReason.NO_SENSOR -> R.string.reject_sensor
    RejectReason.PIN_REQUIRED -> R.string.reject_pin
    RejectReason.PIN_INVALID -> R.string.pin_wrong
    RejectReason.LOCATION_REQUIRED -> R.string.reject_location
}
