package com.cerclerouge.app.ui.history

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cerclerouge.app.R
import com.cerclerouge.app.history.HistoryRepository
import com.cerclerouge.app.ui.components.PxButton
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.PxVariant
import com.cerclerouge.app.ui.components.TopBar
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.cerclerouge.privacy.db.HistoryEntity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

fun formatWhen(millis: Long): String = WHEN.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** Libellé du motif d'une alerte (Reason) ou du mode d'un trajet ; texte brut si inconnu (anciennes données). */
@Composable
fun reasonLabel(e: HistoryEntity): String = stringResource(
    when (e.reason) {
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

/** Contenu séparé du chargement pour pouvoir être testé avec des lignes fabriquées. */
@Composable
internal fun HistoryContent(entries: List<HistoryEntity>, onBack: () -> Unit, onClear: () -> Unit) {
    val c = Cr.colors
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.history_title), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.history_intro), style = PxType.body, color = c.muted)
            if (entries.isEmpty()) {
                PxPanel(Modifier.fillMaxWidth()) { Text(stringResource(R.string.history_empty), style = PxType.body) }
            }
            entries.forEach { e -> HistoryRow(e) }
            if (entries.isNotEmpty()) {
                PxButton(
                    stringResource(if (confirm) R.string.history_clear_confirm else R.string.history_clear),
                    { if (confirm) onClear().also { confirm = false } else confirm = true },
                    variant = if (confirm) PxVariant.DANGER else PxVariant.OUTLINE
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(e: HistoryEntity) {
    val c = Cr.colors
    val alert = e.kind == HistoryRepository.ALERT
    PxPanel(Modifier.fillMaxWidth(), border = if (alert) c.danger else c.lineControl) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(if (alert) R.string.history_alert else R.string.history_trip, reasonLabel(e)).uppercase(),
                style = PxType.label,
                color = if (alert) c.danger else c.lime
            )
            val minutes = e.endedAtMillis?.let { Duration.ofMillis(it - e.startedAtMillis).toMinutes().coerceAtLeast(0) }
            Text(
                listOfNotNull(
                    formatWhen(e.startedAtMillis),
                    minutes?.let { stringResource(R.string.history_minutes, it.toInt()) },
                    e.level?.let { stringResource(R.string.history_level, it) },
                    outcomeLabel(e)
                ).joinToString(" · "),
                style = PxType.caption,
                color = c.muted
            )
        }
    }
}
