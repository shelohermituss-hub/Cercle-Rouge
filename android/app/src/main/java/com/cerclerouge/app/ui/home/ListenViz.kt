package com.cerclerouge.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cerclerouge.app.R
import com.cerclerouge.app.audio.AudioStatus
import com.cerclerouge.app.audio.ListenActivity
import com.cerclerouge.app.audio.ListenEvent
import com.cerclerouge.app.audio.ListenEventKind
import com.cerclerouge.app.audio.ListenStage
import com.cerclerouge.app.ui.components.GlassIcon
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PixelIconView
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.VoiceOrb
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import java.util.Locale

/** Violet de la « pensée » du veilleur (journal), pour le thème clair. */
internal val THOUGHT_VIOLET = Color(0xFF6D28D9)

/**
 * Visualiseur « Écoute » de l'accueil : orbe vocale (vivante quand le micro écoute), état de l'écoute.
 * Ce ne sont que des niveaux : aucun son n'est gardé ni envoyé (R-AE-01). Toucher ouvre les réglages de l'écoute.
 */
@Composable
internal fun ListenCard(activity: ListenActivity, status: AudioStatus, armed: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = Cr.colors
    val stage = stageText(activity, status, armed)
    val live = armed && status != AudioStatus.ERROR && status != AudioStatus.STOPPED
    val level = activity.levels.lastOrNull() ?: 0f
    val open = stringResource(R.string.viz_open_a11y, stage)
    PxPanel(
        modifier.clickable(role = Role.Button, onClickLabel = open, onClick = onOpen),
        padding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = stage }, horizontalAlignment = Alignment.Start) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_listen_title),
                    style = PxType.bodyBold.copy(fontSize = PxType.body.fontSize * 1.1f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(if (live) R.string.home_listen_live else R.string.home_paused),
                    style = PxType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = if (live) c.calmText else c.muted,
                    maxLines = 1
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                VoiceOrb(active = live, size = 76.dp, modifier = Modifier.scale(1f + level * 0.15f))
            }
            Text(
                if (live) stringResource(R.string.home_nothing_recorded) else stage,
                style = PxType.caption,
                color = c.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Visualiseur « Le veilleur » : chiffres réels du cerveau (niveau du micro, risque fusionné, signaux indépendants, état)
 * et sa dernière « pensée » (étapes réelles de l'analyse), en police terminal. Toucher ouvre le journal.
 */
@Composable
internal fun WatcherCard(
    activity: ListenActivity,
    status: AudioStatus,
    armed: Boolean,
    decision: com.cerclerouge.alert.Decision?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Cr.colors
    val stage = stageText(activity, status, armed)
    val engaged = decision?.engaged ?: com.cerclerouge.alert.RecommendedAction.NONE
    val (stateText, stateColor) = when {
        !armed -> stringResource(R.string.home_watcher_idle) to c.muted
        engaged >= com.cerclerouge.alert.RecommendedAction.ALERT_CONTACTS -> stringResource(R.string.home_stat_alert) to c.danger
        engaged >= com.cerclerouge.alert.RecommendedAction.VERIFY -> stringResource(R.string.home_stat_watch) to c.warn
        else -> stringResource(R.string.home_stat_calm) to c.forest
    }
    val level = ((activity.levels.lastOrNull() ?: 0f) * 100).toInt()
    val lines = events(activity.events, armed)
    PxPanel(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen),
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassIcon(R.drawable.glass_terminal, 38.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_watcher), style = PxType.bodyBold.copy(fontSize = PxType.body.fontSize * 1.1f))
                    Text(
                        stringResource(if (armed) R.string.home_watcher_on else R.string.home_watcher_off),
                        style = PxType.caption,
                        color = c.muted
                    )
                }
                Text(
                    if (armed) stringResource(R.string.home_watcher_level, engaged.ordinal) else stringResource(R.string.home_watcher_idle),
                    style = PxType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = if (armed) c.lime else c.muted,
                    modifier = Modifier.background(
                        if (armed) c.glow else c.forestDeep,
                        RoundedCornerShape(50)
                    ).padding(horizontal = 10.dp, vertical = 4.dp)
                )
                PixelIconView(PixelIcon.ARROW_LEFT, 18.dp, c.lineControl, Modifier.scale(-1f, 1f))
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(c.line))
            if (armed) {
                Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$stage · $stateText" }) {
                    Stat(R.drawable.glass_volume, "$level %", stringResource(R.string.home_stat_sound), c.ink, Modifier.weight(1f))
                    Stat(
                        R.drawable.glass_chart,
                        decision?.let { "${(it.risk * 100).toInt()} %" } ?: "—",
                        stringResource(R.string.home_stat_level),
                        c.ink,
                        Modifier.weight(1f)
                    )
                    Stat(
                        R.drawable.glass_heart,
                        "${decision?.signals ?: 0}",
                        stringResource(R.string.home_stat_motion),
                        c.ink,
                        Modifier.weight(1f)
                    )
                    Stat(
                        if (stateColor == c.forest) R.drawable.glass_check else R.drawable.glass_warning,
                        stateText,
                        stringResource(R.string.home_stat_state),
                        stateColor,
                        Modifier.weight(1f)
                    )
                }
                val last = lines.lastOrNull() ?: stage
                val blink = com.cerclerouge.app.ui.components.stepTick(530) % 2 == 0
                Text(
                    "◆ $last" + if (blink) " ▍" else "  ",
                    style = PxType.code,
                    color = c.thought,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp)
                )
            } else {
                Text(stringResource(R.string.home_watcher_sleep), style = PxType.body, color = c.muted)
            }
        }
    }
}

@Composable
private fun Stat(icon: Int, value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        GlassIcon(icon, 30.dp)
        Text(value, style = PxType.bodyBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = PxType.caption, color = Cr.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

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
    }.takeLast(MAX_LINES - 1)
}

private const val MAX_LINES = 3
