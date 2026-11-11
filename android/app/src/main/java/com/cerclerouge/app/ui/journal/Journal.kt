package com.cerclerouge.app.ui.journal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.cerclerouge.app.R
import com.cerclerouge.app.ui.components.BarChart
import com.cerclerouge.app.ui.components.CountUp
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PixelIconView
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.RadarChart
import com.cerclerouge.app.ui.components.TERM_BG
import com.cerclerouge.app.ui.components.TERM_BLUE
import com.cerclerouge.app.ui.components.TERM_CYAN
import com.cerclerouge.app.ui.components.TERM_ORANGE
import com.cerclerouge.app.ui.components.TERM_RED
import com.cerclerouge.app.ui.components.TERM_TEAL
import com.cerclerouge.app.ui.components.TERM_VIOLET
import com.cerclerouge.app.ui.components.TopBar
import com.cerclerouge.app.ui.components.TraceLine
import com.cerclerouge.app.ui.components.TraceRow
import com.cerclerouge.app.ui.components.TraceStatus
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.cerclerouge.privacy.log.ChainedEntry
import com.cerclerouge.privacy.log.EventLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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

private fun isAlert(e: ChainedEntry) = humanize(e).let {
    it == R.string.log_alert_sos ||
        it == R.string.log_dispatch ||
        it == R.string.log_check
}

/** Lettre, couleur et état d'une ligne de terminal selon le type d'événement. */
private fun style(label: Int): Triple<Char, Color, TraceStatus> = when (label) {
    R.string.log_alert_sos -> Triple('A', TERM_RED, TraceStatus.ERROR)
    R.string.log_dispatch -> Triple('E', TERM_ORANGE, TraceStatus.WARN)
    R.string.log_check -> Triple('V', Color(0xFFFFD23F), TraceStatus.WARN)
    R.string.log_alert_closed -> Triple('C', TERM_CYAN, TraceStatus.OK)
    R.string.log_protection_on -> Triple('P', TERM_TEAL, TraceStatus.OK)
    else -> Triple('O', TERM_BLUE, TraceStatus.NONE)
}

private fun span(ms: Long): String {
    val m = ms / 60_000
    return when {
        m < 1 -> "${ms / 1000}s"
        m < 60 -> "${m}min"
        else -> "${m / 60}h${"%02d".format(m % 60)}"
    }
}

/**
 * Lignes du terminal (style AgentPrism) : une ligne racine par jour, puis les événements du jour.
 * Les sessions de protection ont une barre de la durée réelle jusqu'à la désactivation suivante.
 */
fun traceRows(entries: List<ChainedEntry>, resources: Resources): List<TraceRow> {
    val zone = ZoneId.systemDefault()
    val hms = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)
    val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault())
    val today = LocalDate.now(zone)
    val sorted = entries.filter { humanize(it) != R.string.log_other }.sortedBy { it.utcMillis }
    val rows = mutableListOf<TraceRow>()
    sorted.groupBy { Instant.ofEpochMilli(it.utcMillis).atZone(zone).toLocalDate() }.toSortedMap(reverseOrder()).forEach { (day, list) ->
        val first = list.first().utcMillis
        val last = list.last().utcMillis
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        rows += TraceRow(
            key = -day.toEpochDay(),
            level = 0,
            letter = 'J',
            color = TERM_VIOLET,
            title = if (day == today) resources.getString(R.string.journal_today) else day.format(dayFmt),
            tag = resources.getQuantityString(R.plurals.journal_events, list.size, list.size),
            start = (first - dayStart) / 86_400_000f,
            length = ((last - first) / 86_400_000f).coerceAtLeast(0.02f),
            duration = span(last - first),
            status = if (list.any { humanize(it) == R.string.log_alert_sos }) TraceStatus.ERROR else TraceStatus.OK
        )
        list.asReversed().forEach { e ->
            val label = humanize(e)
            val (letter, color, status) = style(label)
            val end = if (label == R.string.log_protection_on) {
                sorted.firstOrNull { it.utcMillis > e.utcMillis && humanize(it) == R.string.log_protection_off }?.utcMillis
            } else {
                null
            }
            rows += TraceRow(
                key = e.seq,
                level = 1,
                letter = letter,
                color = color,
                title = resources.getString(label),
                tag = e.type,
                chips = listOf(hms.format(Instant.ofEpochMilli(e.utcMillis))),
                start = (e.utcMillis - dayStart) / 86_400_000f,
                length = end?.let { (it - e.utcMillis) / 86_400_000f } ?: 0f,
                duration = end?.let { span(it - e.utcMillis) } ?: "—",
                status = status
            )
        }
    }
    return rows
}

private fun granted(context: Context, permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** Journal : chiffres clés, histogramme 7 jours, radar de préparation, terminal des événements (P7, R-PE-03). */
@Composable
fun JournalScreen(log: EventLog, onBack: () -> Unit) {
    var entries by remember { mutableStateOf(emptyList<ChainedEntry>()) }
    var broken by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        // Les entrées « engines » et les transitions sans changement d'état ne parlent pas à l'utilisateur.
        entries = log.latest(500).filter { it.type != "engines" && humanize(it) != R.string.log_other }
        broken = log.firstBreak()
    }
    JournalContent(entries, broken, onBack)
}

/** Contenu du journal, séparé du chargement pour pouvoir être testé avec des entrées fabriquées. */
@Composable
internal fun JournalContent(entries: List<ChainedEntry>, broken: Long?, onBack: () -> Unit) {
    val c = Cr.colors
    val context = LocalContext.current
    val resources = LocalResources.current
    var alertsOnly by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val days = remember { (6 downTo 0).map { LocalDate.now(zone).minusDays(it.toLong()) } }
    fun countPerDay(match: (ChainedEntry) -> Boolean) = days.map { d ->
        entries.count { match(it) && Instant.ofEpochMilli(it.utcMillis).atZone(zone).toLocalDate() == d }
    }
    val activations = countPerDay { humanize(it) == R.string.log_protection_on }
    val alerts = countPerDay { humanize(it) == R.string.log_alert_sos }
    val locale = LocalConfiguration.current.locales[0]
    val dayLabels = days.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(3) }
    val readiness = listOf(
        if (granted(context, Manifest.permission.RECORD_AUDIO)) 1f else 0.08f,
        if (granted(context, Manifest.permission.ACCESS_FINE_LOCATION)) 1f else 0.08f,
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) 1f else 0.08f,
        0.08f, // contacts : gestion au Sprint 2
        if (broken == null) 1f else 0.08f
    )
    val rows = remember(entries, alertsOnly) { traceRows(entries.filter { !alertsOnly || isAlert(it) }, resources) }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TopBar(stringResource(R.string.journal_title), onBack) }
        item {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val status = broken?.let { stringResource(R.string.journal_broken, it.toInt()) } ?: stringResource(R.string.journal_intact)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelIconView(if (broken == null) PixelIcon.LOCK else PixelIcon.ALERT, 16.dp, if (broken == null) c.lime else c.danger)
                    Text(status.uppercase(), style = PxType.label, color = if (broken == null) c.lime else c.danger)
                }
                Text(stringResource(R.string.journal_week).uppercase(), style = PxType.label, color = c.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(stringResource(R.string.journal_kpi_on), activations.sum(), Modifier.weight(1f))
                    Stat(stringResource(R.string.journal_kpi_alerts), alerts.sum(), Modifier.weight(1f))
                }
                PxPanel(
                    Modifier.fillMaxWidth().semantics {
                        contentDescription = resources.getString(R.string.journal_chart)
                    },
                    fill = c.surface
                ) {
                    BarChart(dayLabels, listOf(activations, alerts), listOf(c.lime, c.warn))
                }
                Text(stringResource(R.string.journal_ready).uppercase(), style = PxType.label, color = c.muted)
                PxPanel(Modifier.fillMaxWidth(), fill = c.surface) {
                    RadarChart(
                        listOf(
                            stringResource(R.string.ready_mic),
                            stringResource(R.string.ready_location),
                            stringResource(R.string.ready_notif),
                            stringResource(R.string.ready_contacts),
                            stringResource(R.string.ready_log)
                        ),
                        readiness,
                        c.lime,
                        Modifier.align(Alignment.Center)
                    )
                }
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
            }
        }
        // Terminal (référence AgentPrism).
        itemsIndexed(rows, key = { _, r -> r.key }) { i, row ->
            val last = i == rows.lastIndex || rows[i + 1].level == 0
            TraceLine(row, last, Modifier.background(TERM_BG).padding(horizontal = 12.dp))
        }
        item {
            Text(
                stringResource(R.string.journal_body),
                style = PxType.caption,
                color = c.muted,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
        }
    }
}

/** Chiffre clé du journal (libellé + nombre qui monte). */
@Composable
private fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    val c = Cr.colors
    PxPanel(modifier, padding = PaddingValues(12.dp)) {
        Column {
            Text(label.uppercase(), style = PxType.label, color = c.muted)
            CountUp(value)
            Text(pluralStringResource(R.plurals.journal_events, value, value), style = PxType.caption, color = c.muted)
        }
    }
}
