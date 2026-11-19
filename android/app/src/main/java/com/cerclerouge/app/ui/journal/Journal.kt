package com.cerclerouge.app.ui.journal

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerclerouge.app.R
import com.cerclerouge.app.ui.components.BackHeader
import com.cerclerouge.app.ui.components.CountUp
import com.cerclerouge.app.ui.components.Marks
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.SectionLabel
import com.cerclerouge.app.ui.components.TERM_BLUE
import com.cerclerouge.app.ui.components.TERM_CYAN
import com.cerclerouge.app.ui.components.TERM_ORANGE
import com.cerclerouge.app.ui.components.TERM_RED
import com.cerclerouge.app.ui.components.TERM_TEAL
import com.cerclerouge.app.ui.components.TERM_VIOLET
import com.cerclerouge.app.ui.components.TextLink
import com.cerclerouge.app.ui.components.TraceRow
import com.cerclerouge.app.ui.components.TraceStatus
import com.cerclerouge.app.ui.components.VCard
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.cerclerouge.privacy.log.ChainedEntry
import com.cerclerouge.privacy.log.EventLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
fun JournalScreen(log: EventLog, onHistory: () -> Unit = {}, onBack: () -> Unit) {
    var entries by remember { mutableStateOf(emptyList<ChainedEntry>()) }
    var broken by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        // Les entrées « engines » et les transitions sans changement d'état ne parlent pas à l'utilisateur.
        entries = log.latest(500).filter { it.type != "engines" && humanize(it) != R.string.log_other }
        broken = log.firstBreak()
    }
    JournalContent(entries, broken, onBack, onHistory)
}

/** Contenu du journal, séparé du chargement pour pouvoir être testé avec des entrées fabriquées. */
/** Filtre du journal (maquette : Aujourd'hui · Alertes · Trajets). */
private enum class JournalFilter { TODAY, ALERTS, TRIPS }

/** Titre lisible d'une entrée technique (type du journal) quand elle n'a pas de libellé dédié. */
@Composable
private fun typeTitle(type: String): String = stringResource(
    when (type) {
        "risk" -> R.string.v_log_risk
        "brain" -> R.string.v_log_brain
        "audio" -> R.string.v_log_audio
        "sensor" -> R.string.v_log_sensor
        "trip" -> R.string.v_log_trip
        "listen" -> R.string.v_log_listen
        "evidence" -> R.string.v_log_evidence
        else -> R.string.log_other
    }
)

/**
 * Journal (maquette VerreJournal) : tout ce que le veilleur a vu et décidé, par jour, en lignes « heure · marque · texte »,
 * filtres en pilules et l'intégrité de la chaîne en bas. Rien n'est envoyé.
 */
@Composable
internal fun JournalContent(entries: List<ChainedEntry>, broken: Long?, onBack: () -> Unit, onHistory: () -> Unit = {}) {
    val c = Cr.colors
    var filter by remember { mutableStateOf(JournalFilter.TODAY) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val hhmm = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val shown = entries.filter { e ->
        when (filter) {
            JournalFilter.TODAY -> Instant.ofEpochMilli(e.utcMillis).atZone(zone).toLocalDate() == today
            JournalFilter.ALERTS -> isAlert(e) || humanize(e) == R.string.log_alert_closed
            JournalFilter.TRIPS -> e.type == "trip" || "TRIP_ACTIVE" in e.payload
        }
    }.sortedByDescending { it.seq }
    val byDay = shown.groupBy { Instant.ofEpochMilli(it.utcMillis).atZone(zone).toLocalDate() }
    LazyColumn(Modifier.fillMaxSize().background(c.surface)) {
        item { BackHeader(stringResource(R.string.journal_title), onBack, stringResource(R.string.v_journal_sub)) }
        item {
            com.cerclerouge.app.ui.components.PillChoice(
                listOf(
                    JournalFilter.TODAY to stringResource(R.string.v_today),
                    JournalFilter.ALERTS to stringResource(R.string.log_filter_alerts),
                    JournalFilter.TRIPS to stringResource(R.string.v_trips)
                ),
                filter,
                { filter = it },
                Modifier.padding(top = 16.dp)
            )
        }
        if (byDay.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.v_journal_empty),
                    style = com.cerclerouge.app.ui.components.V.body,
                    color = c.muted,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
        byDay.forEach { (day, list) ->
            item(key = "d$day") {
                SectionLabel(
                    when (day) {
                        today -> stringResource(R.string.v_today)
                        today.minusDays(1) -> stringResource(R.string.v_yesterday)
                        else -> day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", LocalConfiguration.current.locales[0] ?: Locale.ROOT))
                    }
                )
            }
            item(key = "c$day") {
                VCard(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                    list.forEach { e ->
                        val label = humanize(e)
                        val (mark, color) = when (label) {
                            R.string.log_alert_sos -> "!" to Color(0xFFA01515)
                            R.string.log_dispatch -> "→" to Marks.next
                            R.string.log_check, R.string.log_alert_closed, R.string.log_protection_on, R.string.log_protection_off ->
                                "✓" to
                                    Marks.done
                            else -> if (e.type == "risk" || e.type == "brain") "◆" to Marks.thought else "▸" to Marks.seen
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                hhmm.format(Instant.ofEpochMilli(e.utcMillis).atZone(zone)),
                                style = com.cerclerouge.app.ui.components.V.mono,
                                color = Color(0xFF66788A),
                                modifier = Modifier.width(46.dp).padding(top = 2.dp)
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    androidx.compose.ui.text.buildAnnotatedString {
                                        pushStyle(androidx.compose.ui.text.SpanStyle(color = color))
                                        append(mark)
                                        pop()
                                        append(" ")
                                        append(if (label != R.string.log_other) stringResource(label) else typeTitle(e.type))
                                    },
                                    style = com.cerclerouge.app.ui.components.V.text(15.sp),
                                    color = c.ink
                                )
                                if (label ==
                                    R.string.log_other
                                ) {
                                    Text(
                                        e.payload.take(80),
                                        style = com.cerclerouge.app.ui.components.V.small,
                                        color = c.muted,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                broken?.let { stringResource(R.string.journal_broken, it.toInt()) } ?: stringResource(R.string.v_journal_intact),
                style = com.cerclerouge.app.ui.components.V.mono,
                color = if (broken == null) Color(0xFF66788A) else c.danger,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 12.dp)
            )
        }
        item { TextLink(stringResource(R.string.history_open), onHistory, Modifier.padding(top = 8.dp, bottom = 24.dp), color = c.lime) }
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
