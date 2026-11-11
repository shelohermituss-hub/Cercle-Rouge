package com.cerclerouge.app.ui.journal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.cerclerouge.app.R
import com.cerclerouge.app.ui.components.BarChart
import com.cerclerouge.app.ui.components.Brackets
import com.cerclerouge.app.ui.components.CountUp
import com.cerclerouge.app.ui.components.CrIcon
import com.cerclerouge.app.ui.components.FilterChip
import com.cerclerouge.app.ui.components.Ico
import com.cerclerouge.app.ui.components.RadarChart
import com.cerclerouge.app.ui.components.SectionHeader
import com.cerclerouge.app.ui.components.TagTitle
import com.cerclerouge.app.ui.components.TopBar
import com.cerclerouge.app.ui.components.TraceLine
import com.cerclerouge.app.ui.components.TraceRow
import com.cerclerouge.app.ui.components.TraceStatus
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.CrColors
import com.cerclerouge.app.ui.theme.Dashboard
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
private fun style(label: Int, c: CrColors): Triple<Char, Color, TraceStatus> = when (label) {
    R.string.log_alert_sos -> Triple('A', c.sos, TraceStatus.ERROR)
    R.string.log_dispatch -> Triple('E', c.orange, TraceStatus.WARN)
    R.string.log_check -> Triple('V', c.gold, TraceStatus.WARN)
    R.string.log_alert_closed -> Triple('C', c.cyan, TraceStatus.OK)
    R.string.log_protection_on -> Triple('P', c.teal, TraceStatus.OK)
    else -> Triple('O', c.blue, TraceStatus.NONE)
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
fun traceRows(entries: List<ChainedEntry>, resources: Resources, c: CrColors = Dashboard): List<TraceRow> {
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
            color = c.violet,
            title = if (day == today) resources.getString(R.string.journal_today) else day.format(dayFmt),
            tag = resources.getQuantityString(R.plurals.journal_events, list.size, list.size),
            start = (first - dayStart) / 86_400_000f,
            length = ((last - first) / 86_400_000f).coerceAtLeast(0.02f),
            duration = span(last - first),
            status = if (list.any { humanize(it) == R.string.log_alert_sos }) TraceStatus.ERROR else TraceStatus.OK
        )
        list.asReversed().forEach { e ->
            val label = humanize(e)
            val (letter, color, status) = style(label, c)
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
    val c = Cr.colors
    val context = LocalContext.current
    val resources = LocalResources.current
    var entries by remember { mutableStateOf(emptyList<ChainedEntry>()) }
    var broken by remember { mutableStateOf<Long?>(null) }
    var alertsOnly by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entries = log.latest(500).filter { it.type != "engines" && humanize(it) != R.string.log_other }
        broken = log.firstBreak()
    }
    val zone = ZoneId.systemDefault()
    val days = remember { (6 downTo 0).map { LocalDate.now(zone).minusDays(it.toLong()) } }
    fun countPerDay(label: (ChainedEntry) -> Boolean) = days.map { d ->
        entries.count { label(it) && Instant.ofEpochMilli(it.utcMillis).atZone(zone).toLocalDate() == d }
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

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TopBar(stringResource(R.string.journal_title), onBack) }
        item {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val status = broken?.let { stringResource(R.string.journal_broken, it.toInt()) } ?: stringResource(R.string.journal_intact)
                Brackets(color = if (broken == null) c.green else c.danger) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ico(if (broken == null) CrIcon.LOCK else CrIcon.ALERT, 16.dp, if (broken == null) c.mint else c.danger)
                        Text("  " + status.uppercase(), style = PxType.label, color = if (broken == null) c.mint else c.danger)
                    }
                }
                SectionHeader(stringResource(R.string.journal_week))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(stringResource(R.string.journal_kpi_on), activations.sum(), c.green, Modifier.weight(1f))
                    Stat(stringResource(R.string.journal_kpi_alerts), alerts.sum(), c.gold, Modifier.weight(1f))
                }
                TagTitle(stringResource(R.string.journal_chart), Modifier.align(Alignment.CenterHorizontally))
                Box(Modifier.semantics { contentDescription = resources.getString(R.string.journal_chart) }) {
                    BarChart(dayLabels, listOf(activations, alerts), listOf(c.green, c.gold))
                }
                TagTitle(stringResource(R.string.journal_ready), Modifier.align(Alignment.CenterHorizontally))
                RadarChart(
                    listOf(
                        stringResource(R.string.ready_mic),
                        stringResource(R.string.ready_location),
                        stringResource(R.string.ready_notif),
                        stringResource(R.string.ready_contacts),
                        stringResource(R.string.ready_log)
                    ),
                    readiness,
                    c.mint,
                    Modifier.align(Alignment.CenterHorizontally)
                )
                SectionHeader(stringResource(R.string.journal_title))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(stringResource(R.string.log_filter_all), !alertsOnly, { alertsOnly = false })
                    FilterChip(stringResource(R.string.log_filter_alerts), alertsOnly, { alertsOnly = true })
                }
            }
        }
        itemsIndexed(rows, key = { _, r -> r.key }) { i, row ->
            val last = i == rows.lastIndex || rows[i + 1].level == 0
            TraceLine(row, last, Modifier.background(c.terminal).padding(horizontal = 12.dp))
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
private fun Stat(label: String, value: Int, accent: Color, modifier: Modifier = Modifier) {
    val c = Cr.colors
    Column(
        modifier
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(c.panelTop, c.panelBottom.copy(alpha = 0.3f))))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(end = 6.dp).background(accent).padding(4.dp))
            Text(label.uppercase(), style = PxType.label, color = c.ink)
        }
        CountUp(value)
        Text(pluralStringResource(R.plurals.journal_events, value, value), style = PxType.caption, color = c.muted)
    }
}
