package com.cerclerouge.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerclerouge.app.R
import com.cerclerouge.app.history.HistoryRepository
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.privacy.db.HistoryEntity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

fun formatWhen(millis: Long): String = WHEN.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** Heure seule, « 21 h 04 » en français (format court de la langue sinon). */
fun formatTime(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return if (java.util.Locale.getDefault().language == "fr") {
        "%d h %02d".format(t.hour, t.minute)
    } else {
        java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).format(t)
    }
}

/** Libellé du motif d'une alerte (Reason) ou du mode d'un trajet ; texte brut si inconnu (anciennes données). */
@Composable
fun reasonLabel(e: HistoryEntity): String = reasonLabel(e.reason)

@Composable
fun reasonLabel(reason: String?): String = stringResource(
    when (reason) {
        "SOS" -> R.string.reason_sos
        "DISTRESS_SCREAM" -> R.string.reason_scream
        "VIOLENCE_SOUND" -> R.string.reason_violence
        "DISTRESS_KEYWORD" -> R.string.reason_keyword
        "TRIP_DEVIATION" -> R.string.reason_deviation
        "PROLONGED_STOP" -> R.string.reason_stop
        "CONTACT_LOST" -> R.string.reason_lost
        "ARRIVAL_OVERDUE" -> R.string.reason_overdue
        "WALK" -> R.string.trip_mode_walk
        "MOTO" -> R.string.trip_mode_moto
        "TAP_TAP" -> R.string.trip_mode_taptap
        "TAXI" -> R.string.trip_mode_taxi
        "CAR" -> R.string.trip_mode_car
        "BUS" -> R.string.trip_mode_bus
        "TRAIN" -> R.string.trip_mode_train
        else -> R.string.history_unknown
    }
)

@Composable
fun outcomeLabel(e: HistoryEntity): String = stringResource(
    when {
        e.endedAtMillis == null -> R.string.outcome_open
        e.outcome == HistoryRepository.SAFE -> R.string.outcome_safe
        e.outcome == HistoryRepository.ARRIVED -> R.string.outcome_arrived
        e.outcome == HistoryRepository.STOPPED -> R.string.outcome_stopped
        else -> R.string.outcome_ended
    }
)

/** Historique des alertes et des trajets (PDF 16.1) : résumés sans coordonnées, effaçables. */
@Composable
fun HistoryScreen(repo: HistoryRepository, onBack: () -> Unit) {
    var entries by remember { mutableStateOf(emptyList<HistoryEntity>()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { entries = repo.latest() }
    HistoryContent(entries, onBack, onClear = {
        scope.launch { repo.clear() }
        entries = emptyList()
    })
}

/**
 * Historique (maquette VerreHistorique) : cartes par trajet ou alerte, regroupées « cette semaine » / « plus tôt »,
 * type, motif, quand, durée et issue en pastille. Résumés sans coordonnées, effaçables.
 */
@Composable
internal fun HistoryContent(entries: List<HistoryEntity>, onBack: () -> Unit, onClear: () -> Unit) {
    val c = Cr.colors
    var confirm by remember { mutableStateOf(false) }
    val weekAgo = System.currentTimeMillis() - 7 * 24 * 3_600_000L
    val (recent, older) = entries.partition { it.startedAtMillis >= weekAgo }
    Column(Modifier.fillMaxSize().background(c.surface)) {
        com.cerclerouge.app.ui.components.BackHeader(stringResource(R.string.history_title), onBack, stringResource(R.string.v_history_sub))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.history_empty),
                    style = com.cerclerouge.app.ui.components.V.body,
                    color = c.muted,
                    modifier = Modifier.padding(20.dp)
                )
            }
            listOf(R.string.v_this_week to recent, R.string.v_earlier to older).forEach { (title, list) ->
                if (list.isNotEmpty()) {
                    com.cerclerouge.app.ui.components.SectionLabel(stringResource(title))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { list.forEach { HistoryRow(it) } }
                }
            }
            if (entries.isNotEmpty()) {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp)) {
                    if (confirm) {
                        com.cerclerouge.app.ui.components.RedButton(stringResource(R.string.history_clear_confirm), {
                            onClear().also {
                                confirm =
                                    false
                            }
                        })
                    } else {
                        com.cerclerouge.app.ui.components.DangerOutlineButton(stringResource(R.string.history_clear), { confirm = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(e: HistoryEntity) {
    val c = Cr.colors
    val alert = e.kind == HistoryRepository.ALERT
    val minutes = e.endedAtMillis?.let { Duration.ofMillis(it - e.startedAtMillis).toMinutes().coerceAtLeast(0) }
    val tone = when {
        e.endedAtMillis == null -> com.cerclerouge.app.ui.components.Tone.Alert
        e.outcome == HistoryRepository.ARRIVED -> com.cerclerouge.app.ui.components.Tone.Calm
        e.outcome == HistoryRepository.SAFE -> com.cerclerouge.app.ui.components.Tone.Info
        else -> com.cerclerouge.app.ui.components.Tone.Mute
    }
    com.cerclerouge.app.ui.components.VCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.cerclerouge.app.ui.components.GlassIcon(if (alert) R.drawable.glass_warning else R.drawable.glass_trajet, 36.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(if (alert) R.string.v_kind_alert else R.string.v_kind_trip).uppercase(),
                    style = com.cerclerouge.app.ui.components.V.text(13.sp, spacing = 0.02f),
                    color = Color(0xFF66788A)
                )
                Text(reasonLabel(e), style = com.cerclerouge.app.ui.components.V.text(16.sp, FontWeight.SemiBold), color = c.ink)
                Text(
                    listOfNotNull(
                        formatWhen(e.startedAtMillis),
                        minutes?.let { stringResource(R.string.history_minutes, it.toInt()) },
                        e.level?.let { stringResource(R.string.history_level, it) }
                    ).joinToString(" · "),
                    style = com.cerclerouge.app.ui.components.V.small,
                    color = c.muted
                )
            }
            com.cerclerouge.app.ui.components.Chip(outcomeLabel(e).replaceFirstChar { it.titlecase() }, tone)
        }
    }
}
