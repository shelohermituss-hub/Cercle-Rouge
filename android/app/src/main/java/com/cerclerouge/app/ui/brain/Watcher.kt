package com.cerclerouge.app.ui.brain

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerclerouge.alert.Decision
import com.cerclerouge.alert.RecommendedAction
import com.cerclerouge.app.R
import com.cerclerouge.app.ui.components.BackHeader
import com.cerclerouge.app.ui.components.Divider
import com.cerclerouge.app.ui.components.GlassIcon
import com.cerclerouge.app.ui.components.InkButton
import com.cerclerouge.app.ui.components.Marks
import com.cerclerouge.app.ui.components.MonoBox
import com.cerclerouge.app.ui.components.MonoLine
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.SectionLabel
import com.cerclerouge.app.ui.components.V
import com.cerclerouge.app.ui.components.VCard
import com.cerclerouge.app.ui.home.WatcherStats
import com.cerclerouge.app.ui.theme.Cr
import java.util.Locale

/**
 * « Le veilleur » (maquette VerreVeilleur) : état en une phrase, ce qu'il entend (voix, musique, marche, cri), ce qu'il pense
 * (raisonnement en lignes de terminal), les derniers échanges, et le bouton « Parler au veilleur ». Toutes les valeurs sont
 * réelles ; aucun son n'est gardé.
 */
@Composable
fun WatcherScreen(home: com.cerclerouge.app.ui.home.HomeViewModel, talk: TalkViewModel, onBack: () -> Unit, onTalk: () -> Unit, onJournal: () -> Unit) {
    val decision by home.brain.decision.collectAsState()
    val thought by home.brain.thought.collectAsState()
    val ambience by home.audio.ambience.collectAsState()
    val speed by home.location.speed.collectAsState()
    val state by home.controller.state.collectAsState()
    val lines by talk.lines.collectAsState()
    WatcherContent(
        armed = state.protection.isArmed || state.protection.isAlert,
        decision = decision,
        thought = thought?.explanation,
        stats = WatcherStats(ambience?.first, ambience?.second, speed?.let { it * 3.6f }),
        lines = lines.takeLast(2),
        onBack = onBack,
        onTalk = onTalk,
        onJournal = onJournal
    )
}

@Composable
internal fun WatcherContent(
    armed: Boolean,
    decision: Decision?,
    thought: String?,
    stats: WatcherStats,
    lines: List<TalkLine>,
    onBack: () -> Unit,
    onTalk: () -> Unit,
    onJournal: () -> Unit
) {
    val c = Cr.colors
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    val engaged = decision?.engaged ?: RecommendedAction.NONE
    val (bannerText, bannerBg, bannerFg) = when {
        !armed -> Triple(stringResource(R.string.v_watcher_sleeping), Color(0xFFECEFF3), c.muted)
        engaged >= RecommendedAction.ALERT_CONTACTS -> Triple(stringResource(R.string.v_watcher_alert), Color(0xFFFDE3E4), Color(0xFF9E0F15))
        engaged >= RecommendedAction.VERIFY -> Triple(stringResource(R.string.v_watcher_watch), Color(0xFFFFF0C7), Color(0xFF5C3D00))
        else -> Triple(stringResource(R.string.v_watcher_calm), c.calmTint, c.calmText)
    }
    fun pct(v: Float?) = v?.let { "${(it * 100).toInt()} %" } ?: "—"
    val scream = decision?.a?.toFloat()
    Column(Modifier.fillMaxSize().background(c.surface)) {
        BackHeader(stringResource(R.string.home_watcher), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bannerBg).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassIcon(R.drawable.glass_check, 26.dp)
                Text(bannerText, style = V.text(15.sp, FontWeight.SemiBold), color = bannerFg)
            }
            SectionLabel(stringResource(R.string.v_what_i_hear), if (armed) stringResource(R.string.v_live) else stringResource(R.string.home_paused))
            VCard(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                Heard(R.drawable.glass_volume, stringResource(R.string.v_voice_far), stringResource(R.string.v_voice_far_sub), pct(stats.voice), c.ink)
                Divider()
                Heard(R.drawable.glass_chart, stringResource(R.string.v_stat_music), stringResource(R.string.v_music_sub), pct(stats.music), c.ink)
                Divider()
                Heard(
                    R.drawable.glass_heart,
                    stringResource(R.string.v_your_walk),
                    stringResource(R.string.v_walk_sub),
                    stats.speedKmh?.let { String.format(locale, "%.0f km/h", it) } ?: "—",
                    c.ink
                )
                Divider()
                Heard(
                    R.drawable.glass_warning,
                    stringResource(R.string.v_scream),
                    stringResource(if ((scream ?: 0f) >= 0.5f) R.string.v_heard else R.string.v_not_heard),
                    pct(scream),
                    if ((scream ?: 0f) >= 0.5f) c.danger else c.forest
                )
            }
            SectionLabel(stringResource(R.string.v_what_i_think))
            MonoBox(
                buildList {
                    thought?.let { add(MonoLine("◆", Marks.thought, it)) }
                    decision?.let {
                        add(MonoLine("◆", Marks.thought, stringResource(R.string.v_think_risk, (it.risk * 100).toInt(), it.signals)))
                    }
                    add(
                        MonoLine(
                            "✓",
                            Marks.next,
                            if (armed) stringResource(R.string.v_think_level, engaged.ordinal) else stringResource(R.string.v_think_off)
                        )
                    )
                }
            )
            if (lines.isNotEmpty()) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    lines.forEach { Bubble(it) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(c.raised.copy(alpha = 0.6f)).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val more = stringResource(R.string.v_more_journal)
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFE4E8EE)).clickable(role = Role.Button, onClick = onJournal).semantics { contentDescription = more },
                contentAlignment = Alignment.Center
            ) { Text("•••", style = V.text(16.sp, FontWeight.Bold), color = c.ink) }
            InkButton(stringResource(R.string.talk_open), onTalk, Modifier.weight(1f), height = 56.dp, icon = PixelIcon.MIC)
        }
    }
}

@Composable
private fun Heard(icon: Int, title: String, sub: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassIcon(icon, 32.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = V.text(17.sp, FontWeight.Medium), color = Cr.colors.ink)
            Text(sub, style = V.small, color = Cr.colors.muted)
        }
        Text(value, style = V.text(18.sp, FontWeight.Medium), color = valueColor)
    }
}

/** Bulle de conversation (bleue pour toi, blanche pour le veilleur) : partagée avec « Parler au veilleur ». */
@Composable
internal fun Bubble(line: TalkLine, content: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (line.mine) Alignment.CenterEnd else Alignment.CenterStart) {
        if (line.mine) {
            Text(
                line.text,
                style = V.text(15.sp, line = 21.sp),
                color = Color.White,
                modifier = Modifier.padding(start = 60.dp).clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)).background(Color(0xFF2F86F6)).padding(horizontal = 14.dp, vertical = 10.dp)
            )
        } else {
            Column(
                Modifier.padding(end = 30.dp).clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)).background(Cr.colors.raised).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(line.text, style = V.text(15.sp, line = 21.sp), color = Cr.colors.ink)
                content?.invoke()
            }
        }
    }
}
