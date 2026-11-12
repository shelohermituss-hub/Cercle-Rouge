package com.cerclerouge.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cerclerouge.app.R
import com.cerclerouge.app.audio.AudioStatus
import com.cerclerouge.app.audio.ListenActivity
import com.cerclerouge.app.audio.ListenEvent
import com.cerclerouge.app.audio.ListenEventKind
import com.cerclerouge.app.audio.ListenStage
import com.cerclerouge.app.ui.components.GradientOrb
import com.cerclerouge.app.ui.components.Led
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.TERM_CYAN
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import java.util.Locale

/** Violet clair du visualiseur de l'IA (lisible sur fond sombre, distinct du lime de la carte). */
internal val THOUGHT_VIOLET = Color(0xFFB794F6)

/**
 * Deux visualiseurs sous la carte (S5, accueil) : l'écoute en direct (niveau réel du micro) et la « réflexion » de l'IA
 * (étapes réelles de l'analyse : voix détectée, comparaison à la phrase secrète, résultat). Ce ne sont que des niveaux et
 * des étapes : aucun son n'est gardé ni envoyé (R-AE-01). Toucher l'un ou l'autre ouvre les réglages de l'écoute.
 */
@Composable
internal fun ListenPanels(
    activity: ListenActivity,
    status: AudioStatus,
    armed: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stage = stageText(activity, status, armed)
    val open = stringResource(R.string.viz_open_a11y, stage)
    Row(modifier.fillMaxWidth().height(PANEL_HEIGHT), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LivePanel(
            activity,
            armed,
            stage,
            Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClickLabel = open, onClick = onOpen)
        )
        ThoughtPanel(
            activity,
            armed,
            stage,
            Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClickLabel = open, onClick = onOpen)
        )
    }
}

private val PANEL_HEIGHT = 172.dp

@Composable
private fun stageText(activity: ListenActivity, status: AudioStatus, armed: Boolean): String = when {
    !armed -> stringResource(R.string.viz_off)
    status == AudioStatus.PAUSED_LOW_POWER -> stringResource(R.string.viz_paused_low)
    status == AudioStatus.PAUSED_MIC_BUSY -> stringResource(R.string.viz_paused_busy)
    status == AudioStatus.ERROR -> stringResource(R.string.viz_mic_error)
    else -> when (activity.stage) {
        ListenStage.IDLE -> stringResource(R.string.viz_stage_idle)
        ListenStage.LISTENING -> stringResource(R.string.viz_stage_listening)
        ListenStage.VOICE -> stringResource(R.string.viz_stage_voice)
        ListenStage.RESULT ->
            stringResource(
                if (activity.events.lastOrNull()?.kind == ListenEventKind.HIT) R.string.viz_stage_hit else R.string.viz_stage_miss
            )
    }
}

@Composable
private fun PanelTitle(text: String, tint: Color, on: Boolean) {
    val c = Cr.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Led(if (on) tint else c.line, blink = on, size = 6.dp)
        Text(text.uppercase(), style = PxType.label, color = if (on) tint else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Écoute en temps réel : 40 barres, une par 100 ms, la plus récente à droite. */
@Composable
private fun LivePanel(activity: ListenActivity, armed: Boolean, stage: String, modifier: Modifier = Modifier) {
    val c = Cr.colors
    val live = armed && activity.levels.isNotEmpty()
    val tint = if (live) TERM_CYAN else c.muted
    PxPanel(
        modifier,
        border = if (live) TERM_CYAN else c.lineControl,
        fill = c.surface,
        padding = PaddingValues(10.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = stage
            },
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PanelTitle(stringResource(R.string.viz_live_title), tint, live)
            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                val n = LEVELS
                val slot = size.width / n
                val barW = slot * 0.62f
                val levels = activity.levels
                for (i in 0 until n) {
                    val idx = levels.size - n + i
                    val v = if (live && idx >= 0) levels[idx] else 0f
                    val h = maxOf(2.dp.toPx(), v * size.height)
                    val color = if (v > 0.02f) tint.copy(alpha = 0.45f + 0.55f * v) else c.line
                    drawRect(color, Offset(i * slot, (size.height - h) / 2f), Size(barW, h))
                }
            }
            Text(stage, style = PxType.bodyBold, color = if (live) c.ink else c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.viz_levels_only), style = PxType.caption, color = c.muted, maxLines = 2)
        }
    }
}

private const val LEVELS = 28

/** « Réflexion » de l'IA : orbe qui respire avec la voix, et le fil des dernières étapes de l'analyse (style terminal). */
@Composable
private fun ThoughtPanel(activity: ListenActivity, armed: Boolean, stage: String, modifier: Modifier = Modifier) {
    val c = Cr.colors
    val on = armed && activity.stage != ListenStage.IDLE
    val level = activity.levels.lastOrNull() ?: 0f
    PxPanel(
        modifier,
        border = if (on) THOUGHT_VIOLET else c.lineControl,
        fill = c.surface,
        padding = PaddingValues(10.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = stage
            },
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PanelTitle(stringResource(R.string.viz_ai_title), if (on) THOUGHT_VIOLET else c.muted, on)
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradientOrb(active = on, size = 48.dp, modifier = Modifier.scale(1f + level * 0.18f))
                Spacer(Modifier.width(8.dp))
                Text(stage, style = PxType.caption, color = if (on) c.ink else c.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            val lines = events(activity.events, armed)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                lines.forEachIndexed { i, line ->
                    val newest = i == lines.lastIndex
                    Text(
                        "> $line",
                        style = PxType.caption,
                        color = if (newest && on) THOUGHT_VIOLET else c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun events(events: List<ListenEvent>, armed: Boolean): List<String> {
    if (!armed) return emptyList()
    if (events.isEmpty()) return listOf(stringResource(R.string.viz_waiting))
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    return events.flatMap { e ->
        when (e.kind) {
            ListenEventKind.VOICE -> listOf(stringResource(R.string.viz_ev_voice))
            ListenEventKind.HIT, ListenEventKind.MISS -> listOf(
                stringResource(
                    R.string.viz_ev_compare,
                    String.format(locale, "%.1f", e.distance ?: 0.0),
                    String.format(locale, "%.1f", e.threshold ?: 0.0)
                ),
                stringResource(if (e.kind == ListenEventKind.HIT) R.string.viz_ev_hit else R.string.viz_ev_miss)
            )
        }
    }.takeLast(MAX_LINES)
}

private const val MAX_LINES = 3
