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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.cerclerouge.alert.Auth
import com.cerclerouge.alert.EngineState
import com.cerclerouge.alert.Input
import com.cerclerouge.alert.RejectReason
import com.cerclerouge.app.R
import com.cerclerouge.app.protection.ProtectionController
import com.cerclerouge.app.protection.UiEvent
import com.cerclerouge.app.ui.components.Brackets
import com.cerclerouge.app.ui.components.ClockBar
import com.cerclerouge.app.ui.components.CrIcon
import com.cerclerouge.app.ui.components.DashButton
import com.cerclerouge.app.ui.components.DashNavBar
import com.cerclerouge.app.ui.components.DashPanel
import com.cerclerouge.app.ui.components.DashVariant
import com.cerclerouge.app.ui.components.Digits
import com.cerclerouge.app.ui.components.EqBars
import com.cerclerouge.app.ui.components.FacePhase
import com.cerclerouge.app.ui.components.FaceUnlock
import com.cerclerouge.app.ui.components.GradientOrb
import com.cerclerouge.app.ui.components.Ico
import com.cerclerouge.app.ui.components.KpiTile
import com.cerclerouge.app.ui.components.Led
import com.cerclerouge.app.ui.components.NavItem
import com.cerclerouge.app.ui.components.Orb
import com.cerclerouge.app.ui.components.PinPad
import com.cerclerouge.app.ui.components.SectionHeader
import com.cerclerouge.app.ui.components.SosBar
import com.cerclerouge.app.ui.components.TopBar
import com.cerclerouge.app.ui.components.TraceLine
import com.cerclerouge.app.ui.components.gridBackground
import com.cerclerouge.app.ui.components.pixelCircle
import com.cerclerouge.app.ui.components.pixelFrame
import com.cerclerouge.app.ui.journal.traceRows
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
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
class HomeViewModel @Inject constructor(val controller: ProtectionController, private val pins: PinRepository, val log: EventLog) :
    ViewModel() {
    suspend fun pinConfigured() = pins.isConfigured()
}

/** Ce que l'utilisateur doit confirmer par PIN avant de l'envoyer au moteur. */
private enum class Guarded { DISARM, IM_SAFE }

/** Sous-écrans du parcours SOS (appui long, compte à rebours). */
private enum class SosStep { NONE, HOLD, COUNTDOWN }

/** Contacts de confiance : la gestion arrive au Sprint 2. */
private const val CONTACTS_READY = 0

/**
 * Accueil « tableau de bord » : carte satellite en haut, quatre moniteurs en bas (écoute IA, position, contacts, SOS).
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
    var mapFull by remember { mutableStateOf(false) }
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

    Box(Modifier.fillMaxSize().background(c.bg).gridBackground(c.line.copy(alpha = 0.35f)).safeDrawingPadding()) {
        when {
            state.protection.isAlert -> EmergencyScreen(vm, state, onSafe = { guarded(Guarded.IM_SAFE) }, onCall = ::dial114)
            mapFull -> FullMap(state.protection.isArmed, state.protection.isArmed && state.locationGranted) { mapFull = false }
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
                onExpandMap = { mapFull = true },
                nav = listOf(
                    NavItem(stringResource(R.string.nav_home), CrIcon.HOME) {},
                    NavItem(stringResource(R.string.nav_map), CrIcon.MAP) { mapFull = true },
                    NavItem(stringResource(R.string.nav_trip), CrIcon.ROUTE) { toast(context, soon) },
                    NavItem(stringResource(R.string.nav_log), CrIcon.HISTORY, onJournal)
                ),
                map = { armed, showMe, modifier -> LiveMap(armed, showMe, modifier) }
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
            onDone = { pinFor = null }
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
    onExpandMap: () -> Unit,
    nav: List<NavItem>,
    map: @Composable (armed: Boolean, showMe: Boolean, modifier: Modifier) -> Unit
) {
    val c = Cr.colors
    val armed = state.protection.isArmed
    val listening = armed && state.micGranted
    // P4 : la position n'est lue que protection active et autorisation accordée.
    val showMe = armed && state.locationGranted
    // Veille : tout est en noir et blanc. Protection active : la couleur revient.
    val accent = if (armed) c.mint else c.muted
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(CrIcon.SHIELD, 24.dp, accent)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.app_name).uppercase(),
                style = PxType.title,
                color = if (armed) c.ink else c.muted,
                modifier = Modifier.weight(1f)
            )
            Brackets(color = c.lineControl) {
                Text(LocalConfiguration.current.locales[0].language.uppercase(), style = PxType.label)
            }
        }
        ClockBar(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp), color = if (armed) c.green else c.lineControl)
        // Moitié haute : carte satellite, agrandissable en plein écran.
        SectionHeader(
            stringResource(R.string.home_map_title),
            Modifier.padding(horizontal = 12.dp),
            accent = if (armed) c.green else c.lineControl
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp)
                .pixelFrame(if (armed) c.edge else c.line, c.screen)
        ) {
            map(armed, showMe, Modifier.matchParentSize())
            MapCaptions(showMe)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(48.dp)
                    .pixelFrame(c.lineControl, c.scrim)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.map_expand), onClick = onExpandMap),
                contentAlignment = Alignment.Center
            ) { Ico(CrIcon.EXPAND, 20.dp, c.ink) }
        }
        // Moitié basse : quatre moniteurs.
        Column(Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ListenMonitor(armed, listening, onToggle, Modifier.weight(1f).fillMaxHeight())
                Monitor("02", stringResource(R.string.home_row_position), armed, onSoon, Modifier.weight(1f).fillMaxHeight(), {
                    Led(if (showMe) c.mint else c.muted, blink = showMe)
                }) {
                    Ico(CrIcon.PIN, 30.dp, accent)
                    Text(
                        stringResource(if (showMe) R.string.sat_position_on else R.string.sat_position_off).uppercase(),
                        style = PxType.title,
                        color = if (armed) c.ink else c.muted
                    )
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Monitor("03", stringResource(R.string.home_row_contacts), armed, onSoon, Modifier.weight(1f).fillMaxHeight(), {
                    Led(c.muted, blink = false)
                }) {
                    Ico(CrIcon.USERS, 30.dp, accent)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Digits("$CONTACTS_READY", active = armed)
                        Text(" / 5", style = PxType.clock.copy(fontSize = PxType.title.fontSize), color = c.muted)
                    }
                }
                SosBar(onTrigger = onSosTrigger, onTap = onSosTap, modifier = Modifier.weight(1f).fillMaxHeight(), tile = true)
            }
        }
        DashNavBar(nav, selected = 0)
    }
}

/** Légendes de la carte : état de la position et attribution (obligatoire). */
@Composable
private fun BoxScope.MapCaptions(showMe: Boolean) {
    val c = Cr.colors
    Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to c.screen.copy(alpha = 0.85f), 0.3f to Color.Transparent)))
    Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.8f to Color.Transparent, 1f to c.screen.copy(alpha = 0.85f))))
    Text(
        stringResource(if (showMe) R.string.home_map_caption_on else R.string.home_map_caption).uppercase(),
        style = PxType.label,
        color = if (showMe) c.mint else c.muted,
        modifier = Modifier.padding(10.dp)
    )
    Text(
        MapStyle.ATTRIBUTION,
        style = PxType.caption.copy(fontSize = PxType.caption.fontSize * 0.85f),
        color = c.muted,
        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
    )
}

/** Moniteur 01 : l'orbe dégradée est le bouton qui active ou coupe la protection ; elle « respire » pendant l'écoute. */
@Composable
private fun ListenMonitor(armed: Boolean, listening: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val c = Cr.colors
    val a11y = stringResource(if (armed) R.string.home_disc_a11y_on else R.string.home_disc_a11y_off)
    Monitor(
        "01",
        stringResource(R.string.home_row_listen),
        armed,
        onToggle,
        modifier.semantics(mergeDescendants = true) {
            role = Role.Switch
            contentDescription = a11y
        },
        { if (listening) EqBars() else Led(c.muted, blink = false) }
    ) {
        Box(contentAlignment = Alignment.Center) {
            GradientOrb(active = armed, size = 92.dp)
            Text(
                stringResource(if (armed) R.string.home_on else R.string.home_off),
                style = PxType.title,
                color = Color.White
            )
        }
    }
}

/** Moniteur de l'accueil : bandeau « CH.0x · TITRE » + voyant, écran sombre à lignes de balayage. */
@Composable
private fun Monitor(
    channel: String,
    title: String,
    armed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    indicator: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Cr.colors
    Box(
        modifier
            .pixelFrame(if (armed) c.edge else c.line, c.screen)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val step = 3.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(c.ink.copy(alpha = 0.03f), Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
        }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(if (armed) c.banner else c.panelBottom, Color.Transparent)))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CH.$channel · ${title.uppercase()}",
                    style = PxType.label,
                    color = c.muted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                indicator()
            }
            Column(
                Modifier.fillMaxSize().padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                content = content
            )
        }
    }
}

/** Carte en plein écran : même carte, bouton pour revenir à l'accueil. */
@Composable
private fun FullMap(armed: Boolean, showMe: Boolean, onClose: () -> Unit) {
    val c = Cr.colors
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize()) {
        LiveMap(armed, showMe, Modifier.matchParentSize())
        MapCaptions(showMe)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(48.dp)
                .pixelFrame(c.lineControl, c.scrim)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.map_close), onClick = onClose),
            contentAlignment = Alignment.Center
        ) { Ico(CrIcon.CLOSE, 20.dp, c.ink) }
    }
}

/** SOS · appui long : 12 repères s'allument autour du disque pendant 1,5 s. */
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
        Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val lit = (progress.value * 12).toInt()
                    val o = Offset(size.width / 2, size.height / 2)
                    val r1 = size.minDimension / 2 - 6.dp.toPx()
                    val r2 = r1 - 18.dp.toPx()
                    repeat(12) { i ->
                        val a = Math.toRadians(i * 30.0 - 90)
                        val d = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                        val q = 12.dp.toPx()
                        drawRect(
                            if (i <
                                lit
                            ) {
                                c.sos
                            } else {
                                c.line
                            },
                            o + d * ((r1 + r2) / 2) - Offset(q / 2, q / 2),
                            androidx.compose.ui.geometry.Size(q, q)
                        )
                    }
                }
                Column(
                    Modifier
                        .size(200.dp)
                        .clip(remember { pixelCircle(25) })
                        .background(c.sos)
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
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Ico(CrIcon.ALERT, 40.dp, c.onSos)
                    Text(
                        stringResource(R.string.sos),
                        style = PxType.hero.copy(fontSize = PxType.hero.fontSize * 1.3f),
                        color = c.onSos
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.sos_hold_title).uppercase(), style = PxType.display)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.sos_hold_body), style = PxType.body, color = c.muted, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        DashPanel(Modifier.fillMaxWidth().padding(12.dp), padding = PaddingValues(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Ico(CrIcon.LOCK, 22.dp, c.mint)
                Text(stringResource(R.string.sos_hold_note), style = PxType.body)
            }
        }
    }
}

/** SOS · compte à rebours : 5 s pour annuler, puis l'alerte part. */
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
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Brackets(color = c.danger) {
                Text(stringResource(R.string.countdown_label).uppercase(), style = PxType.banner, color = c.danger)
            }
            Box(Modifier.padding(top = 12.dp), contentAlignment = Alignment.Center) {
                Orb(OrbState.CONNECTING, 230.dp, c.danger)
                Text(
                    "$seconds",
                    style = PxType.digitsXL,
                    color = c.danger,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Assertive
                        contentDescription = a11y
                    }
                )
            }
            Text(stringResource(R.string.countdown_seconds).uppercase(), style = PxType.display, modifier = Modifier.padding(top = 8.dp))
            Text(
                stringResource(R.string.countdown_body),
                style = PxType.body,
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        DashButton(stringResource(R.string.action_cancel), onCancel, Modifier.padding(12.dp), icon = CrIcon.CLOSE, tall = true)
    }
}

/** Urgence : bandeau d'alerte, contacts, terminal du journal, « Je suis en sécurité » et 114. */
@Composable
private fun EmergencyScreen(vm: HomeViewModel, state: EngineState, onSafe: () -> Unit, onCall: () -> Unit) {
    val c = Cr.colors
    val resources = LocalResources.current
    var lines by remember { mutableStateOf(emptyList<ChainedEntry>()) }
    LaunchedEffect(state) { lines = vm.log.latest(6) }
    val time = remember { DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DashPanel(
            Modifier.fillMaxWidth(),
            edge = c.danger,
            fill = Brush.verticalGradient(listOf(c.sos.copy(alpha = 0.25f), c.screen))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Orb(OrbState.SEARCHING, 48.dp, c.danger)
                Column {
                    Text(stringResource(R.string.urgent_sent).uppercase(), style = PxType.display, color = c.danger)
                    state.alert?.let { Text(time.format(it.openedAt), style = PxType.mono, color = c.muted) }
                }
            }
        }
        SectionHeader(stringResource(R.string.urgent_contacts), accent = c.gold)
        KpiTile(
            CrIcon.USERS,
            stringResource(R.string.emergency_no_contacts),
            "$CONTACTS_READY",
            Modifier.fillMaxWidth(),
            unit = "/ 5",
            active = false
        )
        SectionHeader(stringResource(R.string.nav_log))
        Column(Modifier.weight(1f).fillMaxWidth().background(c.terminal).padding(horizontal = 8.dp).verticalScroll(rememberScrollState())) {
            val rows = traceRows(lines, resources)
            rows.forEachIndexed { i, row -> TraceLine(row, last = i == rows.lastIndex) }
        }
        DashButton(stringResource(R.string.urgent_safe), onSafe, icon = CrIcon.CHECK, tall = true)
        DashButton(stringResource(R.string.home_sat_call), onCall, variant = DashVariant.DARK_DANGER, icon = CrIcon.PHONE)
    }
}

/**
 * PIN avec l'animation biométrique : visage au repos, cercle qui tourne pendant la vérification, puis coche (fermeture)
 * ou croix (nouvel essai).
 */
@Composable
private fun PinDialog(onDismiss: () -> Unit, verify: suspend (String) -> Boolean, onDone: () -> Unit) {
    val c = Cr.colors
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(FacePhase.IDLE) }
    val wrong = stringResource(R.string.pin_wrong)
    Dialog(onDismissRequest = onDismiss) {
        DashPanel(edge = c.edge, padding = PaddingValues(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FaceUnlock(phase, 72.dp)
                Text(stringResource(R.string.pin_enter).uppercase(), style = PxType.title)
                if (phase == FacePhase.ERROR) {
                    Text(
                        wrong,
                        style = PxType.label,
                        color = c.danger,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        }
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
                DashButton(stringResource(R.string.action_cancel), onDismiss, variant = DashVariant.OUTLINE)
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
