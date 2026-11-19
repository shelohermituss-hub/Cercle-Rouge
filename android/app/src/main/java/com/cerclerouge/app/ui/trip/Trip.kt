package com.cerclerouge.app.ui.trip

import com.cerclerouge.app.ui.components.V
import com.cerclerouge.app.ui.components.GlassIcon
import com.cerclerouge.app.ui.components.heroCard
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerclerouge.app.R
import com.cerclerouge.app.protection.ProtectionController
import com.cerclerouge.app.protection.UiEvent
import com.cerclerouge.app.security.SecuritySettings
import com.cerclerouge.app.server.TripWatch
import com.cerclerouge.app.server.WatchStatus
import com.cerclerouge.app.trip.AddressSearch
import com.cerclerouge.app.trip.ModeThresholds
import com.cerclerouge.app.trip.Place
import com.cerclerouge.app.trip.PlacesRepository
import com.cerclerouge.app.trip.RouteLine
import com.cerclerouge.app.trip.RouteService
import com.cerclerouge.app.trip.TripController
import com.cerclerouge.app.trip.TripDraft
import com.cerclerouge.app.trip.TripPrefs
import com.cerclerouge.app.trip.TripUi
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PixelIconView
import com.cerclerouge.app.ui.components.PxButton
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.PxVariant
import com.cerclerouge.app.ui.components.TopBar
import com.cerclerouge.app.ui.home.LiveMap
import com.cerclerouge.app.ui.home.PinDialog
import com.cerclerouge.app.ui.home.message
import com.cerclerouge.app.ui.settings.Toggle
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.cerclerouge.location.Geo
import com.cerclerouge.location.GeoPoint
import com.cerclerouge.location.TransportMode
import com.cerclerouge.location.TripPlan
import com.cerclerouge.privacy.pin.PinRepository
import com.cerclerouge.privacy.pin.PinResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Préparation d'un trajet : rien n'est démarré tant que l'utilisateur n'a pas confirmé avec son PIN. */
data class PlannerUi(
    val origin: GeoPoint? = null,
    val originLoading: Boolean = true,
    val destination: Place? = null,
    val mode: TransportMode = TransportMode.WALK,
    val minutes: Int = 15,
    val favorites: List<Place> = emptyList(),
    val results: List<Place>? = null,
    val searching: Boolean = false,
    val searchAllowed: Boolean = false,
    val route: RouteLine? = null,
    val routing: Boolean = false,
    val routeAllowed: Boolean = false,
    /** Seuils du mode choisi (réglables dans les bornes du mode). */
    val thresholds: ModeThresholds? = null,
    /** Proches ayant accepté (identifiant, prénom) et ceux choisis pour les trajets (vide : les 3 premiers). */
    val contacts: List<Pair<String, String>> = emptyList(),
    val recipients: Set<String> = emptySet()
)

@HiltViewModel
class TripViewModel @Inject constructor(
    private val trips: TripController,
    private val places: PlacesRepository,
    private val search: AddressSearch,
    private val pins: PinRepository,
    private val security: SecuritySettings,
    private val watch: TripWatch,
    private val routes: RouteService,
    private val prefs: TripPrefs,
    private val contactsRepo: com.cerclerouge.app.contacts.ContactsRepository,
    val controller: ProtectionController,
    private val clock: Clock,
    val device: com.cerclerouge.app.device.DeviceMonitor
) : ViewModel() {
    val trip: StateFlow<TripUi?> = trips.trip
    val watchEnabled: StateFlow<Boolean> = watch.enabled
    val watchStatus: StateFlow<WatchStatus> = watch.status
    private val _ui = MutableStateFlow(PlannerUi())
    val ui: StateFlow<PlannerUi> = _ui
    val searchAvailable: Boolean get() = search.available

    init {
        viewModelScope.launch {
            _ui.update { it.copy(favorites = places.favorites(), searchAllowed = places.searchAllowed(), routeAllowed = routes.allowed()) }
            contactsRepo.load()
            val accepted = contactsRepo.book.value.contacts
                .filter { it.status == com.cerclerouge.contacts.ContactStatus.ACCEPTED }.sortedBy { it.position }
            val ids = accepted.map { it.id }.toSet()
            _ui.update {
                it.copy(
                    thresholds = prefs.thresholds(it.mode),
                    contacts = accepted.map { c -> c.id to c.name.substringBefore(' ') },
                    recipients = prefs.recipients().intersect(ids)
                )
            }
            val origin = trips.currentPosition()
            _ui.update { it.copy(origin = origin, originLoading = false) }
            reestimate()
            reroute()
        }
    }

    /** Règle un seuil du mode choisi, gardé pour les prochains trajets ; les bornes du mode s'appliquent. */
    fun adjust(stopDelta: Int = 0, offRouteDelta: Int = 0) = viewModelScope.launch {
        val s = _ui.value
        val t = s.thresholds ?: prefs.thresholds(s.mode)
        val saved = prefs.setThresholds(s.mode, ModeThresholds(t.stopMinutes + stopDelta, t.offRouteMeters + offRouteDelta))
        _ui.update { it.copy(thresholds = saved) }
    }

    /** Coche ou décoche un proche pour les trajets (3 au plus, comme la surveillance serveur). */
    fun toggleRecipient(id: String) = viewModelScope.launch {
        val now = _ui.value.recipients
        val next = if (id in now) {
            now - id
        } else if (now.size < MAX_RECIPIENTS) {
            now + id
        } else {
            now
        }
        prefs.setRecipients(next)
        _ui.update { it.copy(recipients = next) }
    }

    fun choose(place: Place) {
        _ui.update { it.copy(destination = place, results = null) }
        reestimate()
        reroute()
    }

    fun mode(mode: TransportMode) {
        _ui.update { it.copy(mode = mode, thresholds = null) }
        viewModelScope.launch { _ui.update { it.copy(thresholds = prefs.thresholds(mode)) } }
        reestimate()
        reroute()
    }

    private var routeJob: Job? = null

    /** Trace tout de suite la ligne droite (locale), puis la route par les rues si l'utilisateur l'a autorisée. */
    private fun reroute() {
        val s = _ui.value
        val o = s.origin ?: return
        val d = s.destination ?: return
        routeJob?.cancel()
        _ui.update { it.copy(route = routes.straight(o, d.point, s.mode), routing = s.routeAllowed) }
        routeJob = viewModelScope.launch {
            val line = routes.route(o, d.point, s.mode)
            _ui.update { it.copy(route = line, routing = false) }
        }
    }

    /** Accord donné : la route par les rues est calculée aussitôt. */
    fun allowRoute() = viewModelScope.launch {
        routes.allow()
        _ui.update { it.copy(routeAllowed = true) }
        reroute()
    }

    /** Tracé du trajet en cours : celui calculé à la planification, sinon la ligne droite. */
    fun activeRoute(plan: com.cerclerouge.location.TripPlan): RouteLine = routes.cachedOrStraight(plan.origin, plan.destination, plan.mode)

    fun shift(delta: Int) = _ui.update { it.copy(minutes = (it.minutes + delta).coerceIn(MIN_MINUTES, MAX_MINUTES)) }

    fun allowSearch() = viewModelScope.launch {
        places.allowSearch()
        _ui.update { it.copy(searchAllowed = true) }
    }

    fun search(query: String) = viewModelScope.launch {
        _ui.update { it.copy(searching = true) }
        val found = search.search(query)
        _ui.update { it.copy(searching = false, results = found) }
    }

    fun saveFavorite(name: String) = viewModelScope.launch {
        val dest = _ui.value.destination ?: return@launch
        places.save(Place(name, dest.point))
        _ui.update { it.copy(favorites = places.favorites()) }
    }

    fun removeFavorite(name: String) = viewModelScope.launch {
        places.remove(name)
        _ui.update { it.copy(favorites = places.favorites()) }
    }

    suspend fun pinOk(pin: String) = pins.verify(pin) == PinResult.Ok

    /** S7.02 : empreinte ou visage à la place du PIN, si l'utilisateur l'a activé. */
    suspend fun biometric() = security.biometric()

    suspend fun pinConfigured() = pins.isConfigured()

    /** S4.07 : la surveillance serveur demande un numéro vérifié. */
    suspend fun watchAvailable() = watch.available()

    fun setWatch(on: Boolean) = viewModelScope.launch { watch.setEnabled(on) }

    /** Faux si la position de départ manque : l'écran l'explique. */
    fun start(pin: String?, biometricVerified: Boolean = false): Boolean {
        val s = _ui.value
        val origin = s.origin ?: return false
        val dest = s.destination ?: return false
        val arrival = clock.instant().plus(Duration.ofMinutes(s.minutes.toLong()))
        // Le corridor suit la route par les rues si elle a été calculée pour ce départ, cette arrivée et ce mode.
        val road = s.route?.takeIf { it.road && it.origin == origin && it.destination == dest.point && it.mode == s.mode }?.points.orEmpty()
        viewModelScope.launch {
            trips.start(origin, TripDraft(dest.point, s.mode, arrival, route = road, thresholds = s.thresholds), pin, biometricVerified)
        }
        return true
    }

    fun refreshOrigin() = viewModelScope.launch {
        _ui.update { it.copy(originLoading = true) }
        val origin = trips.currentPosition()
        _ui.update { it.copy(origin = origin, originLoading = false) }
        reestimate()
        reroute()
    }

    fun stop(minutes: Int) = viewModelScope.launch { trips.declareStop(minutes) }

    fun arrived(pin: String?, biometricVerified: Boolean = false) = viewModelScope.launch { trips.arrived(pin, biometricVerified) }

    /** Durée proposée : ligne droite à la vitesse moyenne du mode, arrondie aux 5 min supérieures ; toujours modifiable. */
    private fun reestimate() = _ui.update { s ->
        val o = s.origin
        val d = s.destination ?: return@update s
        if (o == null) return@update s
        val raw = TripPlan.estimatedDuration(o, d.point, s.mode).toMinutes().toInt()
        s.copy(minutes = ((raw + 4) / 5 * 5).coerceIn(MIN_MINUTES, MAX_MINUTES))
    }

    private companion object {
        const val MIN_MINUTES = 5
        const val MAX_MINUTES = 240
        const val MAX_RECIPIENTS = 3
    }
}

private val TransportMode.label: Int
    get() = when (this) {
        TransportMode.WALK -> R.string.trip_mode_walk
        TransportMode.CAR -> R.string.trip_mode_car
        TransportMode.TAP_TAP -> R.string.trip_mode_taptap
        TransportMode.TAXI -> R.string.trip_mode_taxi
        TransportMode.MOTO -> R.string.trip_mode_moto
        TransportMode.BUS -> R.string.trip_mode_bus
        TransportMode.TRAIN -> R.string.trip_mode_train
    }

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun Instant.hhmm(): String = HHMM.format(atZone(ZoneId.systemDefault()))

private fun km(meters: Double): String = String.format(Locale.ROOT, "%.1f", meters / 1000)

/**
 * Écran Trajet (S4.05, S4.06) : choix de la destination (carte, favoris, recherche opt-in), du mode et de l'heure d'arrivée,
 * puis suivi du trajet en cours (« Je fais un arrêt », « Je suis arrivé·e » avec PIN).
 */
@Composable
fun TripScreen(vm: TripViewModel, onBack: () -> Unit) {
    val trip by vm.trip.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        vm.controller.events.collect { e ->
            if (e is UiEvent.Rejected) Toast.makeText(context, resources.getString(e.reason.message()), Toast.LENGTH_LONG).show()
        }
    }
    val active = trip
    if (active != null) {
        ActiveTrip(vm, active)
    } else {
        Column(Modifier.fillMaxSize().background(Cr.colors.surface)) {
            com.cerclerouge.app.ui.components.TabHeader(stringResource(R.string.trip_title), stringResource(R.string.trip_intro))
            Planner(vm)
        }
    }
}

@Composable
private fun Planner(vm: TripViewModel) {
    val c = Cr.colors
    val s by vm.ui.collectAsState()
    val context = LocalContext.current
    val pointLabel = stringResource(R.string.trip_point)
    val noPosition = stringResource(R.string.trip_no_position)
    var askPin by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var routeConsent by remember { mutableStateOf(false) }
    var lastPin by remember { mutableStateOf<String?>(null) }
    var bioOk by remember { mutableStateOf(false) }
    var bio by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { bio = vm.biometric() }

    Box(Modifier.fillMaxWidth().height(240.dp)) {
        LiveMap(
            armed = true,
            showMe = false,
            modifier = Modifier.fillMaxSize(),
            coveredBottom = 0.dp,
            coveredTop = 0.dp,
            coveredEnd = 0.dp,
            destination = s.destination?.point,
            origin = s.origin,
            route = s.route?.points.orEmpty(),
            routeIsRoad = s.route?.road ?: true,
            onTap = { vm.choose(Place(pointLabel, it)) }
        )
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.trip_intro), style = PxType.body, color = c.muted)
        SearchBox(vm, s, onConsent = { consent = true })
        Favorites(vm, s.favorites)
        DestinationPanel(s, onSave = { naming = true })
        if (s.destination != null) RouteStatus(s, onAsk = { routeConsent = true })
        Text(stringResource(R.string.trip_mode).uppercase(), style = PxType.label, color = c.muted)
        ModePicker(s.mode, vm::mode)
        s.thresholds?.let { ThresholdPicker(s.mode, it, vm::adjust) }
        ArrivalPicker(s.minutes, vm::shift)
        if (s.contacts.isNotEmpty()) RecipientPicker(s.contacts, s.recipients, vm::toggleRecipient)
        WatchChoice(vm)
        if (!s.originLoading && s.origin == null) {
            Text(noPosition, style = PxType.label, color = c.warn, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            PxButton(stringResource(R.string.action_retry), { vm.refreshOrigin() }, variant = PxVariant.OUTLINE)
        }
        PxButton(
            stringResource(R.string.trip_start),
            {
                if (s.origin == null) {
                    Toast.makeText(context, noPosition, Toast.LENGTH_LONG).show()
                } else {
                    askPin = true
                }
            },
            icon = PixelIcon.ARROW_RIGHT,
            tall = true,
            enabled = s.destination != null && !s.originLoading
        )
        Text(stringResource(R.string.trip_privacy), style = PxType.caption, color = c.muted)
    }

    if (askPin) {
        PinDialog(
            onDismiss = { askPin = false },
            verify = { p -> vm.pinOk(p).also { ok -> if (ok) lastPin = p } },
            onDone = {
                askPin = false
                vm.start(lastPin, bioOk)
                lastPin = null
                bioOk = false
            },
            onBiometric = if (bio) {
                { bioOk = true }
            } else {
                null
            }
        )
    }
    if (naming) NameDialog(onDismiss = { naming = false }) { name -> vm.saveFavorite(name).also { naming = false } }
    if (consent) ConsentDialog(onDismiss = { consent = false }) { vm.allowSearch().also { consent = false } }
    if (routeConsent) {
        ConsentDialog(
            onDismiss = { routeConsent = false },
            title = R.string.trip_route_consent_title,
            text = R.string.trip_route_consent_text,
            ok = R.string.trip_route_consent_ok
        ) { vm.allowRoute().also { routeConsent = false } }
    }
}

/** État du tracé de la route : ligne droite locale, ou route par les rues après accord. */
@Composable
private fun RouteStatus(s: PlannerUi, onAsk: () -> Unit) {
    val c = Cr.colors
    val road = s.route?.road == true
    val text = when {
        s.routing -> R.string.trip_route_loading
        road -> R.string.trip_route_road
        s.routeAllowed -> R.string.trip_route_failed
        else -> R.string.trip_route_straight
    }
    Text(
        stringResource(text),
        style = PxType.caption,
        color = if (road) c.warn else c.muted,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
    if (!s.routeAllowed) {
        PxButton(
            stringResource(R.string.trip_route_trace),
            onAsk,
            variant = PxVariant.OUTLINE,
            icon = PixelIcon.ARROW_RIGHT
        )
    }
}

@Composable
private fun SearchBox(vm: TripViewModel, s: PlannerUi, onConsent: () -> Unit) {
    val c = Cr.colors
    var query by remember { mutableStateOf("") }
    if (!vm.searchAvailable) {
        Text(stringResource(R.string.trip_search_unavailable), style = PxType.caption, color = c.muted)
        return
    }
    val run = { if (s.searchAllowed) vm.search(query) else onConsent() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(120) },
            label = { Text(stringResource(R.string.trip_search_hint).uppercase(), style = PxType.label) },
            singleLine = true,
            textStyle = PxType.bodyBold,
            shape = RectangleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { run() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.lime,
                unfocusedBorderColor = c.lineControl,
                focusedLabelColor = c.lime,
                unfocusedLabelColor = c.muted,
                cursorColor = c.lime,
                focusedContainerColor = c.raised,
                unfocusedContainerColor = c.raised
            ),
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.size(width = 120.dp, height = 56.dp)) {
            PxButton(stringResource(R.string.trip_search), { run() }, variant = PxVariant.OUTLINE, enabled = query.trim().length >= 3)
        }
    }
    if (s.searching) Text(stringResource(R.string.trip_searching), style = PxType.caption, color = c.muted)
    s.results?.let { results ->
        if (results.isEmpty()) {
            Text(stringResource(R.string.trip_search_none), style = PxType.caption, color = c.warn)
        }
        results.forEach { place ->
            Row(
                Modifier.fillMaxWidth().heightIn(
                    min = 48.dp
                ).clip(
                    androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                ).background(
                    c.raised
                ).border(1.dp, c.line, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).clickable(role = Role.Button) {
                    vm.choose(place)
                }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PixelIconView(PixelIcon.PIN, 16.dp, c.lime)
                Text(place.name, style = PxType.body)
            }
        }
    }
}

@Composable
private fun Favorites(vm: TripViewModel, favorites: List<Place>) {
    val c = Cr.colors
    Text(stringResource(R.string.trip_favorites).uppercase(), style = PxType.label, color = c.muted)
    if (favorites.isEmpty()) {
        Text(stringResource(R.string.trip_favorites_empty), style = PxType.caption, color = c.muted)
        return
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        favorites.forEach { f ->
            Row(
                Modifier.clip(
                    androidx.compose.foundation.shape.RoundedCornerShape(50)
                ).background(c.raised).border(1.5.dp, c.lineControl, androidx.compose.foundation.shape.RoundedCornerShape(50)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { vm.choose(f) }.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PixelIconView(PixelIcon.STAR, 14.dp, c.lime)
                    Text(f.name, style = PxType.bodyBold)
                }
                val remove = stringResource(R.string.trip_favorite_remove, f.name)
                Box(
                    Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = remove) { vm.removeFavorite(f.name) }
                        .semantics { this.contentDescription = remove },
                    contentAlignment = Alignment.Center
                ) { PixelIconView(PixelIcon.CROSS, 12.dp, c.muted) }
            }
        }
    }
}

@Composable
private fun DestinationPanel(s: PlannerUi, onSave: () -> Unit) {
    val c = Cr.colors
    PxPanel(Modifier.fillMaxWidth(), border = if (s.destination != null) c.lime else c.line) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.trip_destination).uppercase(), style = PxType.label, color = c.muted)
            val dest = s.destination
            if (dest == null) {
                Text(stringResource(R.string.trip_destination_none), style = PxType.body)
            } else {
                Text(dest.name, style = PxType.bodyBold)
                s.origin?.let { Text(stringResource(R.string.trip_distance, km(Geo.distance(it, dest.point))), style = PxType.caption) }
                PxButton(stringResource(R.string.trip_favorite_add), onSave, variant = PxVariant.OUTLINE, icon = PixelIcon.STAR)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModePicker(selected: TransportMode, onPick: (TransportMode) -> Unit) {
    val c = Cr.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TransportMode.entries.forEach { m ->
            val on = m == selected
            Box(
                Modifier.heightIn(
                    min = 48.dp
                ).clip(
                    androidx.compose.foundation.shape.RoundedCornerShape(50)
                ).background(
                    if (on) c.glow else c.raised
                ).border(1.5.dp, if (on) c.lime else c.lineControl, androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .clickable(role = Role.RadioButton) { onPick(m) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(m.label), style = PxType.button, color = if (on) c.lime else c.ink) }
        }
    }
}

/** Seuils du mode (PDF 9.2) : écart toléré autour de la route et arrêt toléré, réglables dans les bornes du mode. */
@Composable
private fun ThresholdPicker(mode: TransportMode, t: ModeThresholds, onAdjust: (Int, Int) -> Unit) {
    val c = Cr.colors
    Text(stringResource(R.string.trip_thresholds).uppercase(), style = PxType.label, color = c.muted)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepButton("−", stringResource(R.string.trip_less)) { onAdjust(0, -OFF_ROUTE_STEP) }
        Text(
            stringResource(R.string.trip_threshold_off, t.offRouteMeters, mode.offRouteRange.first, mode.offRouteRange.last),
            style = PxType.body,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }
        )
        StepButton("+", stringResource(R.string.trip_more)) { onAdjust(0, OFF_ROUTE_STEP) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepButton("−", stringResource(R.string.trip_less)) { onAdjust(-STOP_STEP, 0) }
        Text(
            stringResource(R.string.trip_threshold_stop, t.stopMinutes, mode.stopRange.first, mode.stopRange.last),
            style = PxType.body,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }
        )
        StepButton("+", stringResource(R.string.trip_more)) { onAdjust(STOP_STEP, 0) }
    }
}

private const val OFF_ROUTE_STEP = 100
private const val STOP_STEP = 5

/** Proches à prévenir pour les trajets (PDF 5.4) : 3 au plus ; aucun coché = les 3 premiers ayant accepté. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipientPicker(contacts: List<Pair<String, String>>, chosen: Set<String>, onToggle: (String) -> Unit) {
    val c = Cr.colors
    Text(stringResource(R.string.trip_recipients).uppercase(), style = PxType.label, color = c.muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        contacts.forEach { (id, name) ->
            val on = id in chosen
            Box(
                Modifier.heightIn(
                    min = 48.dp
                ).clip(
                    androidx.compose.foundation.shape.RoundedCornerShape(50)
                ).background(
                    if (on) c.glow else c.raised
                ).border(1.5.dp, if (on) c.lime else c.lineControl, androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .clickable(role = Role.Checkbox) { onToggle(id) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text(name, style = PxType.button, color = if (on) c.lime else c.ink) }
        }
    }
    Text(
        stringResource(if (chosen.isEmpty()) R.string.trip_recipients_default else R.string.trip_recipients_ex),
        style = PxType.caption,
        color = c.muted
    )
}

@Composable
private fun ArrivalPicker(minutes: Int, onShift: (Int) -> Unit) {
    val c = Cr.colors
    val at = remember(minutes) { Instant.now(Clock.systemDefaultZone()).plusSeconds(minutes * 60L).hhmm() }
    Text(stringResource(R.string.trip_arrival).uppercase(), style = PxType.label, color = c.muted)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepButton("−5", stringResource(R.string.trip_less)) { onShift(-5) }
        Text(
            stringResource(R.string.trip_arrival_value, at, minutes),
            style = PxType.bodyBold,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }
        )
        StepButton("+5", stringResource(R.string.trip_more)) { onShift(5) }
    }
}

@Composable
private fun StepButton(text: String, description: String, onClick: () -> Unit) {
    val c = Cr.colors
    Box(
        Modifier.size(
            48.dp
        ).clip(
            androidx.compose.foundation.shape.RoundedCornerShape(50)
        ).background(
            c.raised
        ).border(
            1.5.dp,
            c.lineControl,
            androidx.compose.foundation.shape.RoundedCornerShape(50)
        ).clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { this.contentDescription = description },
        contentAlignment = Alignment.Center
    ) { Text(text, style = PxType.button) }
}

/** Ce que montre la carte flottante du trajet en cours (maquette VerreTrajet). */
data class TripCard(
    val followers: List<String>,
    val minutesLeft: Int,
    val distanceKm: Double?,
    val arrival: String,
    val progress: Float,
    val deviationM: Int?,
    val marginMin: Int?,
    val battery: Int?,
    val stopUntil: String?,
    val arrived: Boolean,
    val watch: String?
)

@Composable
private fun ActiveTrip(vm: TripViewModel, t: TripUi) {
    var askPin by remember { mutableStateOf(false) }
    var lastPin by remember { mutableStateOf<String?>(null) }
    var bioOk by remember { mutableStateOf(false) }
    var bio by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { bio = vm.biometric() }
    val route = remember(t.plan) { vm.activeRoute(t.plan) }
    val planner by vm.ui.collectAsState()
    val device by vm.device.status.collectAsState()
    val watch by vm.watchStatus.collectAsState()
    val total = Geo.distance(t.plan.origin, t.plan.destination).coerceAtLeast(1.0)
    val remaining = t.position?.let { Geo.distance(it, t.plan.destination) }
    val left = minutesLeft(t.plan.plannedArrival)
    val card = TripCard(
        followers = planner.contacts.filter { it.first in planner.recipients }.map { it.second.substringBefore(' ') },
        minutesLeft = left,
        distanceKm = remaining?.let { it / 1000.0 },
        arrival = t.plan.plannedArrival.hhmm(),
        progress = remaining?.let { (1.0 - it / total).toFloat().coerceIn(0f, 1f) } ?: 0f,
        deviationM = t.position?.let { p -> route.points.takeIf { it.size >= 2 }?.let { Geo.distanceToPath(p, it).toInt() } },
        marginMin = remaining?.let { (left - it / t.plan.mode.speedMps / 60).toInt() },
        battery = device.batteryPct,
        stopUntil = t.stopUntil?.hhmm(),
        arrived = t.arrivalDetected,
        watch = (watch as? WatchStatus.On)?.let { stringResource(R.string.trip_watch_on, it.recipients) }
    )
    TripActive(
        card,
        map = { m ->
            LiveMap(
                armed = true,
                showMe = true,
                modifier = m,
                coveredBottom = 300.dp,
                coveredTop = 0.dp,
                coveredEnd = 0.dp,
                destination = t.plan.destination,
                route = route.points,
                routeIsRoad = route.road
            )
        },
        onMore = { vm.stop(15) },
        onArrived = { askPin = true }
    )
    if (askPin) {
        PinDialog(
            onDismiss = { askPin = false },
            verify = { p -> vm.pinOk(p).also { ok -> if (ok) lastPin = p } },
            onDone = {
                askPin = false
                vm.arrived(lastPin, bioOk)
                lastPin = null
                bioOk = false
            },
            onBiometric = if (bio) {
                { bioOk = true }
            } else {
                null
            }
        )
    }
}

/**
 * Trajet en cours (maquette) : carte plein écran, carte blanche flottante en bas — destination et proches qui suivent,
 * minutes restantes, distance et heure d'arrivée, progression, 4 mesures (écart, marge, suivent, batterie), « + 15 min »
 * (arrêt déclaré) et « Je suis arrivé·e » (code demandé).
 */
@Composable
internal fun TripActive(card: TripCard, map: @Composable (Modifier) -> Unit, onMore: () -> Unit, onArrived: () -> Unit) {
    val c = Cr.colors
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: Locale.ROOT
    Box(Modifier.fillMaxSize().background(c.surface)) {
        map(Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 52.dp).fillMaxWidth()
                .heroCard(28.dp)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
        ) {
            if (card.arrived) {
                Text(
                    stringResource(R.string.trip_arrival_detected),
                    style = V.text(14.sp, FontWeight.SemiBold),
                    color = c.forest,
                    modifier = Modifier.padding(bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassIcon(R.drawable.glass_trajet, 30.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.v_trip_running), style = V.text(17.sp, FontWeight.Medium), color = c.ink)
                    Text(
                        if (card.followers.isEmpty()) stringResource(R.string.v_followed_none) else stringResource(R.string.v_followed_by, card.followers.joinToString(", ")),
                        style = V.small,
                        color = c.muted,
                        maxLines = 1
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${card.minutesLeft} min", style = V.text(22.sp, FontWeight.Bold), color = c.ink)
                    Text(
                        listOfNotNull(card.distanceKm?.let { String.format(locale, "%.1f km", it) }, card.arrival).joinToString(" · "),
                        style = V.small,
                        color = c.muted
                    )
                }
            }
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFD6DEE6))) {
                Box(Modifier.fillMaxWidth(card.progress).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF2F86F6)))
            }
            card.stopUntil?.let { Text(stringResource(R.string.trip_stop_until, it), style = V.small, color = c.warn, modifier = Modifier.padding(top = 8.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                TripStat(R.drawable.glass_pin, card.deviationM?.let { "$it m" } ?: "—", stringResource(R.string.v_deviation), Modifier.weight(1f))
                TripStat(R.drawable.glass_clock, card.marginMin?.let { if (it >= 0) "+$it min" else "$it min" } ?: "—", stringResource(R.string.v_margin), Modifier.weight(1f))
                TripStat(R.drawable.glass_proches, card.followers.size.toString(), stringResource(R.string.v_following), Modifier.weight(1f))
                TripStat(R.drawable.glass_battery, card.battery?.let { "$it %" } ?: "—", stringResource(R.string.v_battery), Modifier.weight(1f))
            }
            card.watch?.let { Text(it, style = V.small, color = c.muted, modifier = Modifier.padding(top = 8.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).heightIn(min = 50.dp).clip(RoundedCornerShape(25.dp)).background(Color(0xFFE4E8EE)).clickable(role = Role.Button, onClick = onMore),
                    contentAlignment = Alignment.Center
                ) { Text(stringResource(R.string.v_plus_15), style = V.text(16.sp, FontWeight.SemiBold), color = c.ink) }
                com.cerclerouge.app.ui.components.OutlineButton(stringResource(R.string.trip_arrived), onArrived, Modifier.weight(1f), height = 50.dp)
            }
        }
    }
}

@Composable
private fun TripStat(icon: Int, value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        GlassIcon(icon, 26.dp)
        Text(value, style = V.text(15.sp, FontWeight.SemiBold), color = Cr.colors.ink, modifier = Modifier.padding(top = 2.dp))
        Text(label, style = V.small, color = Cr.colors.muted)
    }
}

/** S4.07 : opt-in « prévenir mes proches si mon téléphone se tait », possible seulement avec un numéro vérifié. */
@Composable
private fun WatchChoice(vm: TripViewModel) {
    val c = Cr.colors
    val on by vm.watchEnabled.collectAsState()
    var available by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { available = vm.watchAvailable() }
    when (available) {
        true -> Toggle(R.string.trip_watch, R.string.trip_watch_ex, on) { vm.setWatch(it) }
        false -> Text(stringResource(R.string.trip_watch_need_phone), style = PxType.caption, color = c.muted)
        null -> Unit
    }
}

@Composable
private fun WatchLine(vm: TripViewModel) {
    val c = Cr.colors
    val status by vm.watchStatus.collectAsState()
    val (text, color) = when (val st = status) {
        WatchStatus.Off -> return
        WatchStatus.Starting -> stringResource(R.string.trip_watch_starting) to c.muted
        is WatchStatus.On -> stringResource(R.string.trip_watch_on, st.recipients) to c.lime
        is WatchStatus.Failed ->
            if (st.code == "no_recipients") {
                stringResource(R.string.trip_watch_no_recipients) to c.warn
            } else {
                stringResource(R.string.trip_watch_failed, st.code) to c.warn
            }
    }
    Text(text, style = PxType.caption, color = color, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

private fun minutesLeft(arrival: Instant): Int = Duration.between(Instant.now(), arrival).toMinutes().toInt().coerceAtLeast(0)

@Composable
private fun NameDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = Cr.colors
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        PxPanel(border = c.lime, fill = c.surface, padding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.trip_favorite_add), style = PxType.title)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(PlacesRepository.MAX_NAME) },
                    label = { Text(stringResource(R.string.trip_favorite_name).uppercase(), style = PxType.label) },
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
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { PxButton(stringResource(R.string.action_cancel), onDismiss, variant = PxVariant.OUTLINE) }
                    Column(Modifier.weight(1f)) {
                        PxButton(stringResource(R.string.trip_save), { onSave(name) }, enabled = name.isNotBlank())
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentDialog(
    onDismiss: () -> Unit,
    title: Int = R.string.trip_consent_title,
    text: Int = R.string.trip_consent_text,
    ok: Int = R.string.trip_consent_ok,
    onAccept: () -> Unit
) {
    val c = Cr.colors
    Dialog(onDismissRequest = onDismiss) {
        PxPanel(border = c.lime, fill = c.surface, padding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(title), style = PxType.title)
                Text(stringResource(text), style = PxType.body)
                Spacer(Modifier.size(4.dp))
                PxButton(stringResource(ok), onAccept)
                PxButton(stringResource(R.string.action_cancel), onDismiss, variant = PxVariant.OUTLINE)
            }
        }
    }
}
