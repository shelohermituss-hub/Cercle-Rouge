package com.cerclerouge.app.ui.watch

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerclerouge.app.R
import com.cerclerouge.app.server.ServerSync
import com.cerclerouge.app.server.Watched
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PxButton
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.PxVariant
import com.cerclerouge.app.ui.components.TopBar
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class WatchUi(val items: List<Watched> = emptyList(), val loaded: Boolean = false, val unreachable: Boolean = false)

@HiltViewModel
class WatchViewModel @Inject constructor(private val server: ServerSync) : ViewModel() {
    private val _ui = MutableStateFlow(WatchUi())
    val ui: StateFlow<WatchUi> = _ui

    fun refresh() {
        viewModelScope.launch {
            val items = server.watched()
            _ui.value = if (items == null) _ui.value.copy(loaded = true, unreachable = true) else WatchUi(items, loaded = true)
        }
    }
}

/**
 * « Ceux qui m'ont choisi » (S3.09) : pour un proche inscrit (numéro vérifié), les alertes de niveau 2 ou plus et les trajets en
 * cours de ceux dont il a accepté l'invitation, avec dernière position, batterie et réseau. Rafraîchi toutes les 15 s tant que
 * l'écran est ouvert ; aucun historique. Pas de notification poussée (ADR-0026) : le SMS reste le canal qui réveille.
 */
@Composable
fun WatchScreen(vm: WatchViewModel, onBack: () -> Unit) {
    val c = Cr.colors
    val ui by vm.ui.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            vm.refresh()
            delay(15_000)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.watch_title), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.watch_intro), style = PxType.body, color = c.muted)
            if (ui.unreachable) {
                Text(
                    stringResource(R.string.watch_unreachable),
                    style = PxType.label,
                    color = c.warn,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            if (ui.loaded && ui.items.isEmpty() && !ui.unreachable) {
                PxPanel(Modifier.fillMaxWidth()) { Text(stringResource(R.string.watch_none), style = PxType.body) }
            }
            ui.items.forEach { WatchCard(it) }
        }
    }
}

@Composable
private fun WatchCard(w: Watched) {
    val c = Cr.colors
    val context = LocalContext.current
    val alert = w.kind == "alert"
    val danger = alert || w.status == "lost"
    PxPanel(Modifier.fillMaxWidth(), border = if (danger) c.danger else c.lime) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(
                    when {
                        alert -> R.string.watch_alert
                        w.status == "lost" -> R.string.watch_trip_lost
                        else -> R.string.watch_trip
                    },
                    w.name
                ).uppercase(),
                style = PxType.title,
                color = if (danger) c.danger else c.lime
            )
            Text(
                stringResource(R.string.watch_since, HHMM.format(w.since.atZone(ZoneId.systemDefault()))),
                style = PxType.caption,
                color = c.muted
            )
            w.plannedArrival?.let {
                Text(
                    stringResource(R.string.watch_arrival, HHMM.format(it.atZone(ZoneId.systemDefault()))),
                    style = PxType.caption,
                    color = c.muted
                )
            }
            val device = listOfNotNull(
                w.battery?.let { stringResource(R.string.watch_battery, it) },
                w.online?.let { stringResource(if (it) R.string.watch_online else R.string.watch_offline) }
            )
            if (device.isNotEmpty()) Text(device.joinToString(" · "), style = PxType.bodyBold)
            if (w.lat != null && w.lon != null) {
                Text(stringResource(R.string.watch_position, ago(w.positionAt)), style = PxType.caption, color = c.muted)
                PxButton(stringResource(R.string.watch_open_map), {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "geo:${w.lat},${w.lon}?q=${w.lat},${w.lon}".toUri()))
                }, variant = PxVariant.OUTLINE, icon = PixelIcon.PIN)
            } else {
                Text(stringResource(R.string.watch_no_position), style = PxType.caption, color = c.muted)
            }
        }
    }
}

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun ago(at: Instant?): String {
    if (at == null) return "?"
    val s = Duration.between(at, Instant.now()).seconds.coerceAtLeast(0)
    return when {
        s < 90 -> stringResource(R.string.watch_seconds, s.toInt())
        else -> stringResource(R.string.watch_minutes, (s / 60).toInt())
    }
}
