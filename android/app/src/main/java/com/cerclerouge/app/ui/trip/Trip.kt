package com.cerclerouge.app.ui.trip

import android.widget.Toast
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
import com.cerclerouge.app.trip.Place
import com.cerclerouge.app.trip.PlacesRepository
import com.cerclerouge.app.trip.TripController
import com.cerclerouge.app.trip.TripDraft
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
    val searchAllowed: Boolean = false
)

@HiltViewModel
class TripViewModel @Inject constructor(
    private val trips: TripController,
    private val places: PlacesRepository,
    private val search: AddressSearch,
    private val pins: PinRepository,
    private val security: SecuritySettings,
    private val watch: TripWatch,
    val controller: ProtectionController,
    private val clock: Clock
) : ViewModel() {
    val trip: StateFlow<TripUi?> = trips.trip
    val watchEnabled: StateFlow<Boolean> = watch.enabled
    val watchStatus: StateFlow<WatchStatus> = watch.status
    private val _ui = MutableStateFlow(PlannerUi())
    val ui: StateFlow<PlannerUi> = _ui
    val searchAvailable: Boolean get() = search.available

    init {
        viewModelScope.launch {
            _ui.update { it.copy(favorites = places.favorites(), searchAllowed = places.searchAllowed()) }
            val origin = trips.currentPosition()
            _ui.update { it.copy(origin = origin, originLoading = false) }
            reestimate()
        }
    }

    fun choose(place: Place) {
        _ui.update { it.copy(destination = place, results = null) }
        reestimate()
    }

    fun mode(mode: TransportMode) {
        _ui.update { it.copy(mode = mode) }
        reestimate()
    }

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
        viewModelScope.launch { trips.start(origin, TripDraft(dest.point, s.mode, arrival), pin, biometricVerified) }
        return true
    }

    fun refreshOrigin() = viewModelScope.launch {
        _ui.update { it.copy(originLoading = true) }
        val origin = trips.currentPosition()
        _ui.update { it.copy(origin = origin, originLoading = false) }
        reestimate()
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
    }
}

private val TransportMode.label: Int
    get() = when (this) {
        TransportMode.WALK -> R.string.trip_mode_walk
        TransportMode.CAR -> R.string.trip_mode_car
        TransportMode.TAP_TAP -> R.string.trip_mode_taptap
        TransportMode.TAXI -> R.string.trip_mode_taxi
        TransportMode.MOTO -> R.string.trip_mode_moto
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
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(if (trip != null) R.string.trip_active else R.string.trip_title), onBack)
        val active = trip
        if (active != null) ActiveTrip(vm, active) else Planner(vm)
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
        Text(stringResource(R.string.trip_mode).uppercase(), style = PxType.label, color = c.lime)
        ModePicker(s.mode, vm::mode)
        ArrivalPicker(s.minutes, vm::shift)
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
                Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, c.line).clickable(role = Role.Button) { vm.choose(place) }
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
    Text(stringResource(R.string.trip_favorites).uppercase(), style = PxType.label, color = c.lime)
    if (favorites.isEmpty()) {
        Text(stringResource(R.string.trip_favorites_empty), style = PxType.caption, color = c.muted)
        return
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        favorites.forEach { f ->
            Row(Modifier.border(2.dp, c.lineControl), verticalAlignment = Alignment.CenterVertically) {
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
                Modifier.heightIn(min = 48.dp).border(2.dp, if (on) c.lime else c.lineControl)
                    .clickable(role = Role.RadioButton) { onPick(m) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(m.label).uppercase(), style = PxType.button, color = if (on) c.lime else c.ink) }
        }
    }
}

@Composable
private fun ArrivalPicker(minutes: Int, onShift: (Int) -> Unit) {
    val c = Cr.colors
    val at = remember(minutes) { Instant.now(Clock.systemDefaultZone()).plusSeconds(minutes * 60L).hhmm() }
    Text(stringResource(R.string.trip_arrival).uppercase(), style = PxType.label, color = c.lime)
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
        Modifier.size(48.dp).border(2.dp, c.lineControl).clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { this.contentDescription = description },
        contentAlignment = Alignment.Center
    ) { Text(text, style = PxType.button) }
}

@Composable
private fun ActiveTrip(vm: TripViewModel, t: TripUi) {
    val c = Cr.colors
    var askPin by remember { mutableStateOf(false) }
    var stopChoice by remember { mutableStateOf(false) }
    var lastPin by remember { mutableStateOf<String?>(null) }
    var bioOk by remember { mutableStateOf(false) }
    var bio by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { bio = vm.biometric() }
    Box(Modifier.fillMaxWidth().height(260.dp)) {
        LiveMap(
            armed = true,
            showMe = true,
            modifier = Modifier.fillMaxSize(),
            coveredBottom = 0.dp,
            coveredTop = 0.dp,
            coveredEnd = 0.dp,
            destination = t.plan.destination
        )
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (t.arrivalDetected) {
            PxPanel(Modifier.fillMaxWidth(), border = c.lime) {
                Text(
                    stringResource(R.string.trip_arrival_detected),
                    style = PxType.bodyBold,
                    color = c.lime,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        PxPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(t.plan.mode.label).uppercase(), style = PxType.label, color = c.muted)
                t.position?.let {
                    Text(stringResource(R.string.trip_remaining, km(Geo.distance(it, t.plan.destination))), style = PxType.count)
                }
                Text(
                    stringResource(R.string.trip_arrival_value, t.plan.plannedArrival.hhmm(), minutesLeft(t.plan.plannedArrival)),
                    style = PxType.bodyBold
                )
                t.stopUntil?.let { Text(stringResource(R.string.trip_stop_until, it.hhmm()), style = PxType.body, color = c.warn) }
                Text(
                    stringResource(R.string.trip_incidents, t.incidents),
                    style = PxType.caption,
                    color = if (t.incidents >
                        0
                    ) {
                        c.warn
                    } else {
                        c.muted
                    }
                )
            }
        }
        WatchLine(vm)
        PxButton(stringResource(R.string.trip_arrived), { askPin = true }, icon = PixelIcon.CHECK, tall = true)
        PxButton(stringResource(R.string.trip_stop), { stopChoice = true }, variant = PxVariant.OUTLINE, icon = PixelIcon.CLOCK)
        Text(stringResource(R.string.trip_privacy), style = PxType.caption, color = c.muted)
    }
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
    if (stopChoice) {
        Dialog(onDismissRequest = { stopChoice = false }) {
            PxPanel(border = c.lime, fill = c.surface, padding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.trip_stop).uppercase(), style = PxType.title)
                    listOf(10, 20, 30, 60).forEach { m ->
                        PxButton(stringResource(R.string.trip_stop_minutes, m), {
                            vm.stop(m)
                            stopChoice = false
                        }, variant = PxVariant.OUTLINE)
                    }
                    PxButton(stringResource(R.string.action_cancel), { stopChoice = false }, variant = PxVariant.DARK)
                }
            }
        }
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
                Text(stringResource(R.string.trip_favorite_add).uppercase(), style = PxType.title)
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
private fun ConsentDialog(onDismiss: () -> Unit, onAccept: () -> Unit) {
    val c = Cr.colors
    Dialog(onDismissRequest = onDismiss) {
        PxPanel(border = c.lime, fill = c.surface, padding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.trip_consent_title).uppercase(), style = PxType.title)
                Text(stringResource(R.string.trip_consent_text), style = PxType.body)
                Spacer(Modifier.size(4.dp))
                PxButton(stringResource(R.string.trip_consent_ok), onAccept)
                PxButton(stringResource(R.string.action_cancel), onDismiss, variant = PxVariant.OUTLINE)
            }
        }
    }
}
