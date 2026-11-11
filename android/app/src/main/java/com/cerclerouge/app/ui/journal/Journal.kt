package com.cerclerouge.app.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cerclerouge.app.R
import com.cerclerouge.app.ui.components.LeaderLine
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.TopBar
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.cerclerouge.privacy.log.ChainedEntry
import com.cerclerouge.privacy.log.EventLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Traduit une entrée technique du journal (« OFF → EMERGENCY_MODE : Sos ») en libellé lisible. */
fun humanize(e: ChainedEntry): Int {
    val p = e.payload
    return when {
        e.type == "dispatch" -> R.string.log_dispatch
        "EMERGENCY_MODE :" in p || p.endsWith("→ EMERGENCY_MODE : Sos") -> R.string.log_alert_sos
        "→ ALERT_PENDING" in p -> R.string.log_check
        p.startsWith("EMERGENCY_MODE →") || p.startsWith("ALERT_SENT →") || p.startsWith("ALERT_PENDING →") -> R.string.log_alert_closed
        "→ ARMED" in p || "→ TRIP_ACTIVE" in p -> R.string.log_protection_on
        "→ OFF" in p -> R.string.log_protection_off
        else -> R.string.log_other
    }
}

private fun isAlert(e: ChainedEntry) = e.type == "dispatch" || "ALERT" in e.payload || "EMERGENCY" in e.payload

/** Maquette « Journal » : lignes « heure ······ événement », filtre, vérification de la chaîne (P7, R-PE-03). */
@Composable
fun JournalScreen(log: EventLog, onBack: () -> Unit) {
    val c = Cr.colors
    var entries by remember { mutableStateOf(emptyList<ChainedEntry>()) }
    var broken by remember { mutableStateOf<Long?>(null) }
    var alertsOnly by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Les entrées « engines » et les transitions sans changement d'état ne parlent pas à l'utilisateur.
        entries = log.latest(300).filter { it.type != "engines" && humanize(it) != R.string.log_other }
        broken = log.firstBreak()
    }
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.journal_title), onBack)
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to R.string.log_filter_all, true to R.string.log_filter_alerts).forEach { (value, label) ->
                    val on = alertsOnly == value
                    PxPanel(
                        Modifier
                            .weight(1f)
                            .semantics { selected = on }
                            .clickable(role = Role.Tab) { alertsOnly = value },
                        border = if (on) c.lime else c.lineControl,
                        fill = if (on) c.lime else c.raised,
                        padding = PaddingValues(10.dp)
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
            val status = broken?.let { stringResource(R.string.journal_broken, it.toInt()) } ?: stringResource(R.string.journal_intact)
            Text(status.uppercase(), style = PxType.label, color = if (broken == null) c.lime else c.danger)
            PxPanel(Modifier.fillMaxWidth().weight(1f, fill = false), fill = c.surface) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(entries.filter { !alertsOnly || isAlert(it) }, key = { it.seq }) { e ->
                        val label = humanize(e)
                        val color = when (label) {
                            R.string.log_alert_sos -> c.danger
                            R.string.log_check, R.string.log_dispatch -> c.warn
                            R.string.log_protection_on, R.string.log_alert_closed -> c.lime
                            else -> c.muted
                        }
                        LeaderLine(fmt.format(Instant.ofEpochMilli(e.utcMillis)), stringResource(label), color)
                    }
                }
            }
            Text(
                stringResource(R.string.journal_body),
                style = PxType.caption,
                color = c.muted,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}
