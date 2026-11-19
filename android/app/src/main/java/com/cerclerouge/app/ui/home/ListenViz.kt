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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerclerouge.app.ui.components.V
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

/** Ambiance pour la carte « Veilleur » : voix et musique (YAMNet, 0 à 1), vitesse de marche (km/h). Null : inconnu. */
data class WatcherStats(val voice: Float? = null, val music: Float? = null, val speedKmh: Float? = null)

/**
 * Carte « Écoute » de l'accueil (maquette : 146 de haut, titre 17, « ● en direct » vert, orbe 78, « Rien n'est enregistré »).
 * Ce ne sont que des niveaux : aucun son n'est gardé ni envoyé (R-AE-01). Toucher ouvre l'écran « Niveau sonore ».
 */
@Composable
internal fun ListenCard(activity: ListenActivity, status: AudioStatus, armed: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = Cr.colors
    val stage = stageText(activity, status, armed)
    val live = armed && status != AudioStatus.ERROR && status != AudioStatus.STOPPED
    val level = activity.levels.lastOrNull() ?: 0f
    val open = stringResource(R.string.viz_open_a11y, stage)
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.clip(shape).background(c.raised).border(1.dp, c.ink.copy(alpha = 0.04f), shape)
            .clickable(role = Role.Button, onClickLabel = open, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clearAndSetSemantics { contentDescription = stage }
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_listen_title), style = V.text(17.sp, FontWeight.Medium), color = c.ink, modifier = Modifier.weight(1f))
            Text(
                stringResource(if (live) R.string.v_live_dot else R.string.home_paused),
                style = V.text(12.sp, FontWeight.SemiBold),
                color = if (live) c.calmText else c.muted,
                maxLines = 1
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp), contentAlignment = Alignment.Center) {
            VoiceOrb(active = live, size = 78.dp, modifier = Modifier.scale(1f + level * 0.12f))
        }
        Text(
            if (live) stringResource(R.string.home_nothing_recorded) else stringResource(R.string.v_mic_off),
            style = V.small,
            color = c.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Carte « Le veilleur » (maquette) : icône, titre, ligne de pensée violette en police terminal avec curseur, pastille du
 * niveau ; protection active : 4 statistiques réelles (voix, musique, marche, état) ; coupée : une phrase. Toucher ouvre
 * l'écran du veilleur.
 */
@Composable
internal fun WatcherCard(
    activity: ListenActivity,
    status: AudioStatus,
    armed: Boolean,
    decision: com.cerclerouge.alert.Decision?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    explanation: String? = null,
    stats: WatcherStats = WatcherStats()
) {
    val c = Cr.colors
    val stage = stageText(activity, status, armed)
    val engaged = decision?.engaged ?: com.cerclerouge.alert.RecommendedAction.NONE
    val (stateText, stateColor) = when {
        engaged >= com.cerclerouge.alert.RecommendedAction.ALERT_CONTACTS -> stringResource(R.string.home_stat_alert) to c.danger
        engaged >= com.cerclerouge.alert.RecommendedAction.VERIFY -> stringResource(R.string.home_stat_watch) to c.warn
        else -> stringResource(R.string.home_stat_calm) to c.forest
    }
    val thought = explanation ?: events(activity.events, armed).lastOrNull() ?: stage
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(shape).background(c.raised).border(1.dp, c.ink.copy(alpha = 0.04f), shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassIcon(R.drawable.glass_terminal, 38.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(stringResource(R.string.home_watcher), style = V.text(17.sp, FontWeight.Medium), color = c.ink)
                if (armed) {
                    val blink = com.cerclerouge.app.ui.components.stepTick(500) % 2 == 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("◆ $thought", style = V.mono, color = c.thought, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Box(Modifier.padding(start = 3.dp).width(6.dp).height(12.dp).background(if (blink) c.thought else Color.Transparent))
                    }
                } else {
                    Text(stringResource(R.string.home_watcher_off), style = V.small, color = c.muted)
                }
            }
            val (chipBg, chipFg) = if (armed) Color(0xFFE8F1FE) to Color(0xFF1F6FE5) else Color(0xFFECEFF3) to c.muted
            com.cerclerouge.app.ui.components.Chip(
                if (armed) stringResource(R.string.home_watcher_level, engaged.ordinal) else stringResource(R.string.home_watcher_idle),
                chipBg,
                chipFg
            )
            PixelIconView(PixelIcon.ARROW_LEFT, 20.dp, c.muted, Modifier.scale(-1f, 1f), strokeWidth = 2f)
        }
        com.cerclerouge.app.ui.components.Divider()
        if (armed) {
            val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
            fun pct(v: Float?) = v?.let { "${(it * 100).toInt()} %" } ?: "—"
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp).clearAndSetSemantics { contentDescription = "$stage · $stateText" }) {
                Stat(R.drawable.glass_volume, pct(stats.voice), stringResource(R.string.v_stat_voice), c.ink, Modifier.weight(1f))
                Stat(R.drawable.glass_chart, pct(stats.music), stringResource(R.string.v_stat_music), c.ink, Modifier.weight(1f))
                Stat(
                    R.drawable.glass_heart,
                    stats.speedKmh?.let { String.format(locale, "%.0f km/h", it) } ?: "—",
                    stringResource(R.string.v_stat_walk),
                    c.ink,
                    Modifier.weight(1f)
                )
                Stat(if (stateColor == c.forest) R.drawable.glass_check else R.drawable.glass_warning, stateText, stringResource(R.string.home_stat_state), stateColor, Modifier.weight(1f))
            }
        } else {
            Text(stringResource(R.string.v_watcher_sleep), style = V.body, color = Color(0xFF566B80).takeIf { c.surface == Color(0xFFEDEDF0) } ?: c.muted, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun Stat(icon: Int, value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        GlassIcon(icon, 30.dp)
        Text(value, style = V.text(15.sp, FontWeight.SemiBold), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        Text(label, style = V.small, color = Cr.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
