package com.cerclerouge.app.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.cerclerouge.app.security.SecureScreen
import com.cerclerouge.app.security.SecuritySettings
import com.cerclerouge.app.server.ServerSync
import com.cerclerouge.app.ui.components.FacePhase
import com.cerclerouge.app.ui.components.FaceUnlock
import com.cerclerouge.app.ui.components.GlassIcon
import com.cerclerouge.app.ui.components.LogoMark
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PxButton
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.PxVariant
import com.cerclerouge.app.ui.components.Shield3D
import com.cerclerouge.app.ui.components.SosBar
import com.cerclerouge.app.ui.components.V
import com.cerclerouge.app.ui.components.cappedScale
import com.cerclerouge.app.ui.components.heroCard
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.cerclerouge.contacts.ContactStatus
import com.cerclerouge.privacy.log.EventLog
import com.cerclerouge.privacy.pin.PinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val brain: com.cerclerouge.app.protection.RiskBrain,
    val listen: com.cerclerouge.app.protection.ListenSettings,
    val device: com.cerclerouge.app.device.DeviceMonitor,
    val history: com.cerclerouge.app.history.HistoryRepository,
    val emergency: com.cerclerouge.app.settings.EmergencyNumberRepository,
    private val guardians: GuardianSync,
    val location: com.cerclerouge.app.location.LocationTracker,
    val trips: com.cerclerouge.app.trip.TripController
) : ViewModel() {
    init {
        viewModelScope.launch {
            contacts.load()
            listen.load()
            emergency.load()
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
    onHistory: () -> Unit = {},
    sosRequested: Boolean = false,
    onSosConsumed: () -> Unit = {},
    armRequested: Boolean = false,
    onArmConsumed: () -> Unit = {},
    disarmRequested: Boolean = false,
    onDisarmConsumed: () -> Unit = {},
    onTalk: () -> Unit = {},
    onLevel: () -> Unit = {}
) {
    val c = Cr.colors
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val state by vm.controller.state.collectAsState()
    val book by vm.contacts.book.collectAsState()
    val activity by vm.audio.activity.collectAsState()
    val audioStatus by vm.audio.status.collectAsState()
    val decision by vm.brain.decision.collectAsState()
    val thought by vm.brain.thought.collectAsState()
    val device by vm.device.status.collectAsState()
    val listenPrefs by vm.listen.prefs.collectAsState()
    val ambience by vm.audio.ambience.collectAsState()
    val speed by vm.location.speed.collectAsState()
    val tripUi by vm.trips.trip.collectAsState()
    // « Depuis : 21 h 04 » : heure d'activation de la protection (gardée tant que l'écran vit).
    var armedSince by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.protection.isArmed || state.protection.isAlert) {
        armedSince = if (state.protection.isArmed || state.protection.isAlert) armedSince ?: System.currentTimeMillis() else null
    }
    var lastAlert by remember { mutableStateOf<com.cerclerouge.privacy.db.HistoryEntity?>(null) }
    var lastTrip by remember { mutableStateOf<com.cerclerouge.privacy.db.HistoryEntity?>(null) }
    LaunchedEffect(state.protection, state.alert?.id) {
        lastAlert = vm.history.last(com.cerclerouge.app.history.HistoryRepository.ALERT)
        lastTrip = vm.history.last(com.cerclerouge.app.history.HistoryRepository.TRIP)
    }
    val contactsReady = book.contacts.count { it.status == ContactStatus.ACCEPTED }
    var sos by remember { mutableStateOf(SosStep.NONE) }
    var pinFor by remember { mutableStateOf<Guarded?>(null) }
    // Biométrie autorisée pour désarmer ; pour « Je vais bien » (annuler une alerte) seulement si l'utilisateur l'a choisi.
    var bioAllowed by remember { mutableStateOf(emptyMap<Guarded, Boolean>()) }
    LaunchedEffect(pinFor) {
        bioAllowed = mapOf(Guarded.DISARM to vm.security.biometric(), Guarded.IM_SAFE to vm.security.biometricForAlerts())
    }
    val soon = stringResource(R.string.home_soon)

    val emergencyCountry by vm.emergency.country.collectAsState()

    // R-HOME-07 : toujours le composeur, jamais d'appel direct ; numéro du pays (1.18).
    fun dial114() = context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${emergencyCountry.primary}".toUri()))

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

    // 1.20 : avant la demande d'Android, on explique pourquoi (micro, position) ; refuser n'empêche pas d'armer en partie.
    var rationale by remember { mutableStateOf(false) }
    fun missingPermissions() = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION)
        .any { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    fun toggleProtection() {
        if (state.protection == com.cerclerouge.domain.ProtectionState.OFF && missingPermissions() && !rationale) {
            rationale = true
            return
        }
        rationale = false
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

    // Armer depuis la tuile ou le widget : une seule fois, et seulement si la protection est coupée (désarmer demande le PIN).
    LaunchedEffect(armRequested) {
        if (armRequested) {
            if (state.protection == com.cerclerouge.domain.ProtectionState.OFF) toggleProtection()
            onArmConsumed()
        }
    }
    // Couper depuis un autre onglet : le code est demandé ici, comme pour le bouton de l'accueil.
    LaunchedEffect(disarmRequested) {
        if (disarmRequested) {
            if (state.protection.isArmed) guarded(Guarded.DISARM)
            onDisarmConsumed()
        }
    }
    Box(Modifier.fillMaxSize().background(c.surface).safeDrawingPadding()) {
        when {
            state.protection.isAlert && listenPrefs.discreet -> DiscreetScreen(emergencyCountry.primary, onSafe = {
                guarded(Guarded.IM_SAFE)
            }, onCall = ::dial114)
            state.protection.isAlert -> EmergencyScreen(vm, state, emergencyCountry.primary, onSafe = {
                guarded(Guarded.IM_SAFE)
            }, onCall = ::dial114)
            sos == SosStep.HOLD -> SosHoldScreen(
                onBack = { sos = SosStep.NONE },
                onTrigger = { sos = SosStep.COUNTDOWN },
                names = book.contacts.filter {
                    it.status == ContactStatus.ACCEPTED
                }.sortedBy { it.position }.map { it.name.substringBefore(' ') }.take(3),
                number = emergencyCountry.primary,
                onCall = ::dial114
            )
            sos == SosStep.COUNTDOWN -> CountdownScreen(
                names = book.contacts.filter {
                    it.status == ContactStatus.ACCEPTED
                }.sortedBy { it.position }.map { it.name.substringBefore(' ') }.take(3),
                onCancel = { sos = SosStep.NONE },
                onDone = {
                    sos = SosStep.NONE
                    vm.controller.send { Input.Sos(it) }
                }
            )
            else -> HomeConsole(
                state = state,
                onToggle = ::toggleProtection,
                contactsReady = contactsReady,
                onSosTrigger = { sos = SosStep.COUNTDOWN },
                onSosTap = { sos = SosStep.HOLD },
                onTab = { tab ->
                    when (tab) {
                        com.cerclerouge.app.ui.components.Tab.HOME -> Unit
                        com.cerclerouge.app.ui.components.Tab.TRIP -> onTrip()
                        com.cerclerouge.app.ui.components.Tab.CONTACTS -> onContacts()
                        com.cerclerouge.app.ui.components.Tab.SETTINGS -> onSettings()
                    }
                },
                map = { armed, showMe, modifier -> LiveMap(armed, showMe, modifier, coveredBottom = 28.dp) },
                activity = activity,
                audioStatus = audioStatus,
                decision = decision,
                explanation = thought?.explanation,
                device = device,
                since = armedSince?.let { com.cerclerouge.app.ui.history.formatTime(it) },
                lastOut = lastTrip?.let { com.cerclerouge.app.ui.history.formatWhen(it.startedAtMillis) },
                trip = tripUi?.let { t ->
                    HomeTrip(
                        t.position?.let { com.cerclerouge.location.Geo.distance(it, t.plan.destination) / 1000.0 },
                        java.time.Duration.between(java.time.Instant.now(), t.plan.plannedArrival).toMinutes().coerceAtLeast(0)
                    )
                },
                stats = WatcherStats(ambience?.first, ambience?.second, speed?.let { it * 3.6f }),
                onListen = onLevel,
                onWatcher = onTalk,
                onProfile = onSettings,
                onHistory = onHistory
            )
        }
    }

    if (rationale) {
        Dialog(onDismissRequest = { rationale = false }) {
            PxPanel(fill = Cr.colors.raised, radius = 32.dp, padding = PaddingValues(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.perm_title), style = PxType.title)
                    Text(stringResource(R.string.perm_mic), style = PxType.body)
                    Text(stringResource(R.string.perm_location), style = PxType.body)
                    Text(stringResource(R.string.perm_partial), style = PxType.caption, color = Cr.colors.muted)
                    PxButton(stringResource(R.string.action_continue), { toggleProtection() }, icon = PixelIcon.ARROW_RIGHT)
                    PxButton(stringResource(R.string.action_cancel), { rationale = false }, variant = PxVariant.OUTLINE)
                }
            }
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

/** Trajet en cours affiché sur la carte de l'accueil (« Trajet en cours · 3,2 km · 14 min »). */
data class HomeTrip(val distanceKm: Double?, val minutesLeft: Long?)

/**
 * Accueil (maquettes « Accueil · protection active / coupée ») : logo et profil, grande carte d'état avec le bouclier et le SOS
 * fondu dans le bas, « En direct » avec l'écoute et la carte, la carte du veilleur, la barre du bas et son bouton central.
 */
@Composable
internal fun HomeConsole(
    state: EngineState,
    onToggle: () -> Unit,
    contactsReady: Int,
    onSosTrigger: () -> Unit,
    onSosTap: () -> Unit,
    onTab: (com.cerclerouge.app.ui.components.Tab) -> Unit,
    map: @Composable (armed: Boolean, showMe: Boolean, modifier: Modifier) -> Unit,
    activity: ListenActivity = ListenActivity(),
    audioStatus: AudioStatus = AudioStatus.STOPPED,
    decision: com.cerclerouge.alert.Decision? = null,
    explanation: String? = null,
    device: com.cerclerouge.sensor.DeviceStatus = com.cerclerouge.sensor.DeviceStatus(),
    since: String? = null,
    lastOut: String? = null,
    trip: HomeTrip? = null,
    stats: WatcherStats = WatcherStats(),
    onListen: () -> Unit = {},
    onWatcher: () -> Unit = {},
    onProfile: () -> Unit = {},
    onHistory: () -> Unit = {}
) {
    val c = Cr.colors
    val armed = state.protection.isArmed
    // P4 : la position n'est lue que protection active et autorisation accordée.
    val showMe = armed && state.locationGranted
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale >= LARGE_FONT
    val profile = stringResource(R.string.v_profile)
    Box(Modifier.fillMaxSize().background(c.surface)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
            // En-tête : logo (190 × 36) et bouton profil rond blanc de 48.
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark(height = 36.dp)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(
                        48.dp
                    ).clip(CircleShape).background(c.raised).clickable(role = Role.Button, onClick = onProfile).semantics {
                        contentDescription =
                            profile
                    },
                    contentAlignment = Alignment.Center
                ) { GlassIcon(R.drawable.glass_user, 30.dp) }
            }
            HeroCard(armed, contactsReady, device, since, lastOut, onHistory, onSosTrigger, onSosTap)
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.home_live).uppercase(), style = V.section, color = c.muted, modifier = Modifier.weight(1f))
                Text(
                    if (armed) {
                        listOf(state.micGranted, state.locationGranted, true).count {
                            it
                        }.let { n -> pluralStringResource(R.plurals.home_sensors, n, n) }
                    } else {
                        stringResource(R.string.home_paused)
                    },
                    style = V.body,
                    color = Color(0xFF2C3A47).takeIf { c.surface == Color(0xFFEDEDF0) } ?: c.ink
                )
            }
            val h = if (large) 200.dp else 146.dp
            val listen = @Composable { m: Modifier -> ListenCard(activity, audioStatus, armed, onListen, m) }
            val mapCard = @Composable { m: Modifier ->
                MapCard(armed, showMe, map, trip, { onTab(com.cerclerouge.app.ui.components.Tab.TRIP) }, m)
            }
            if (large) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listen(Modifier.fillMaxWidth().height(h))
                    mapCard(Modifier.fillMaxWidth().height(h))
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listen(Modifier.weight(1f).height(h))
                    mapCard(Modifier.weight(1f).height(h))
                }
            }
            WatcherCard(
                activity,
                audioStatus,
                armed,
                decision,
                onWatcher,
                Modifier.padding(top = 12.dp),
                explanation = explanation,
                stats = stats
            )
        }
        if (!armed) {
            // Bulle « Touche ici pour activer la protection », pointe vers le bouton central.
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 118.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.home_off_hint),
                    style = V.text(14.sp, FontWeight.Medium, 19.sp),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(
                        max = 258.dp
                    ).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1F2A33)).padding(horizontal = 16.dp, vertical = 10.dp)
                )
                Box(
                    Modifier.size(14.dp, 8.dp).drawBehind {
                        drawPath(
                            androidx.compose.ui.graphics.Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width / 2, size.height)
                                close()
                            },
                            Color(0xFF1F2A33)
                        )
                    }
                )
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) {
            com.cerclerouge.app.ui.components.MainNav(com.cerclerouge.app.ui.components.Tab.HOME, onTab, armed, onToggle)
        }
    }
}

/**
 * Grande carte d'état (maquette : 200 de haut, rayon 34, dégradé blanc, bouclier 93 × 112 à droite, SOS de 54 fondu en bas).
 * Toucher le texte ouvre l'historique.
 */
@Composable
private fun HeroCard(
    armed: Boolean,
    contactsReady: Int,
    device: com.cerclerouge.sensor.DeviceStatus,
    since: String?,
    lastOut: String?,
    onHistory: () -> Unit,
    onSosTrigger: () -> Unit,
    onSosTap: () -> Unit
) {
    val c = Cr.colors
    val issue = when {
        !device.gpsEnabled -> stringResource(R.string.status_gps_off)
        device.network == com.cerclerouge.sensor.NetworkKind.NONE -> stringResource(R.string.status_net_none)
        device.lowBattery -> device.batteryPct?.let { stringResource(R.string.status_battery, it) }
        else -> null
    }
    val never = stringResource(R.string.status_never)
    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().heightIn(min = 200.dp).heroCard()) {
        Shield3D(on = armed, height = 112.dp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 22.dp))
        Column(
            Modifier.padding(start = 20.dp, top = 18.dp, bottom = 66.dp).width(210.dp).clickable(role = Role.Button, onClick = onHistory),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(if (armed) R.string.home_protected_title else R.string.home_off_title),
                style = V.text(21.sp, FontWeight.SemiBold, 26.sp, -0.014f),
                color = c.ink,
                modifier = Modifier.semantics { heading() }
            )
            com.cerclerouge.app.ui.components.Bullets(
                buildList {
                    if (armed) {
                        add(stringResource(R.string.v_since) to (since ?: "—"))
                    } else {
                        add(
                            stringResource(R.string.v_last_out) to (lastOut ?: never)
                        )
                    }
                    add(stringResource(R.string.v_ready) to contactsReady.toString())
                }
            )
            issue?.let { Text("·  $it", style = V.text(15.sp, line = 22.sp), color = c.warn) }
        }
        SosBar(
            onTrigger = onSosTrigger,
            onTap = onSosTap,
            modifier = Modifier.align(Alignment.BottomCenter),
            shape = RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp),
            minHeight = 54.dp
        )
    }
}

/** Logo officiel (image du porteur) ; en thème sombre, « Cercle » passe en clair pour rester lisible. */
@Composable
internal fun Wordmark(modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 34.dp) {
    val c = Cr.colors
    if (c.raised.luminance() > 0.5f) {
        Image(
            painterResource(R.drawable.cr_wordmark),
            contentDescription = stringResource(R.string.app_name),
            modifier = modifier.height(height)
        )
    } else {
        Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            com.cerclerouge.app.ui.components.LogoMark(height)
            Spacer(Modifier.width(height * 0.3f))
            Text("Cercle ", style = PxType.title.copy(fontSize = PxType.title.fontSize * (height.value / 30f)), color = c.ink)
            Text(
                "Rouge",
                style = PxType.title.copy(
                    fontSize = PxType.title.fontSize * (height.value / 30f),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = com.cerclerouge.app.ui.components.LogoRed
            )
        }
    }
}

/** Carte « Carte » de l'accueil : la carte en direct (claire), la pastille du trajet ou de la dernière position. */
@Composable
private fun MapCard(
    armed: Boolean,
    showMe: Boolean,
    map: @Composable (armed: Boolean, showMe: Boolean, modifier: Modifier) -> Unit,
    trip: HomeTrip?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Cr.colors
    val shape = RoundedCornerShape(22.dp)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: java.util.Locale.ROOT
    Box(
        modifier.clip(
            shape
        ).background(c.raised).border(1.dp, c.ink.copy(alpha = 0.04f), shape).clickable(role = Role.Button, onClick = onOpen)
    ) {
        map(armed, showMe, Modifier.matchParentSize())
        Text(
            MapStyle.ATTRIBUTION,
            style = cappedScale(V.text(9.sp)),
            color = c.muted,
            maxLines = 1,
            modifier = Modifier.align(
                Alignment.TopStart
            ).padding(6.dp).background(c.raised.copy(alpha = 0.8f), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp, vertical = 1.dp)
        )
        Column(
            Modifier.align(
                Alignment.BottomCenter
            ).fillMaxWidth().padding(
                8.dp
            ).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.92f)).padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Text(
                stringResource(
                    when {
                        trip != null -> R.string.v_trip_running
                        showMe -> R.string.home_map_title
                        else -> R.string.v_last_position
                    }
                ),
                style = V.text(12.sp),
                color = Color(0xFF566B80)
            )
            Text(
                when {
                    trip != null -> listOfNotNull(
                        trip.distanceKm?.let { String.format(locale, "%.1f km", it) },
                        trip.minutesLeft?.let { "$it min" }
                    ).joinToString(" · ").ifEmpty { "—" }
                    showMe -> stringResource(R.string.home_map_live)
                    else -> stringResource(R.string.home_map_last)
                },
                style = V.text(14.sp, FontWeight.SemiBold),
                color = Color(0xFF1F2A33),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

private const val LARGE_FONT = 1.5f

/**
 * Animation « Face ID » de l'alerte en cours (fichier « Biometric Smart Animations » du porteur) :
 * visage → cercle qui tourne → coche, en boucle. Figée sur la coche si « réduire les animations ».
 */
@Composable
internal fun FaceIdLoop(size: androidx.compose.ui.unit.Dp) {
    val still = com.cerclerouge.app.ui.components.reduceMotion()
    var phase by remember { mutableStateOf(if (still) FacePhase.SUCCESS else FacePhase.IDLE) }
    LaunchedEffect(still) {
        if (!still) {
            while (true) {
                phase = FacePhase.IDLE
                delay(1200)
                phase = FacePhase.SCANNING
                delay(1700)
                phase = FacePhase.SUCCESS
                delay(1800)
            }
        }
    }
    FaceUnlock(phase, size)
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

internal fun RejectReason.message(): Int = when (this) {
    RejectReason.CONSENT_REQUIRED -> R.string.reject_consent
    RejectReason.NO_SENSOR -> R.string.reject_sensor
    RejectReason.PIN_REQUIRED -> R.string.reject_pin
    RejectReason.PIN_INVALID -> R.string.pin_wrong
    RejectReason.LOCATION_REQUIRED -> R.string.reject_location
}

/**
 * Écran d'une alerte en mode discret (R-HOME-03) : aucun mot « alerte », « danger » ni « urgence », aucun son, aspect d'une
 * simple application de suivi. Deux gestes seulement : terminer (PIN/biométrie, comme « Je vais bien ») et appeler le 114.
 */
@Composable
internal fun DiscreetScreen(number: String, onSafe: () -> Unit, onCall: () -> Unit) {
    SecureScreen()
    val c = Cr.colors
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PxPanel(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.service_text_armed), style = PxType.display, color = c.muted)
        }
        Spacer(Modifier.weight(1f))
        PxButton(stringResource(R.string.discreet_finish), onSafe, variant = PxVariant.OUTLINE)
        PxButton(stringResource(R.string.discreet_call, number), onCall, variant = PxVariant.DARK)
    }
}
