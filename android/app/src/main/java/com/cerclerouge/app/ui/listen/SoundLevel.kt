package com.cerclerouge.app.ui.listen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerclerouge.app.R
import com.cerclerouge.app.audio.AudioListener
import com.cerclerouge.app.audio.SoundHistory
import com.cerclerouge.app.ui.components.BackHeader
import com.cerclerouge.app.ui.components.GlassIcon
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PixelIconView
import com.cerclerouge.app.ui.components.SectionLabel
import com.cerclerouge.app.ui.components.V
import com.cerclerouge.app.ui.components.VCard
import com.cerclerouge.app.ui.theme.Cr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Onglet de l'écran « Niveau sonore ». */
enum class LevelTab { SOUND, MOTION, POSITION }

/** Un point d'une courbe : instant (ms) et valeur (0 à 1). */
typealias Sample = Pair<Long, Float>

/**
 * « Niveau sonore » (maquette VerreEcoute) : les 10 dernières minutes du son, des mouvements et de la position, en nombres
 * seulement (aucun son gardé). Bouton pour couper l'écoute 15 min (la protection reste active).
 */
@HiltViewModel
class SoundLevelViewModel @Inject constructor(
    audio: AudioListener,
    motion: com.cerclerouge.app.device.MotionSensors,
    location: com.cerclerouge.app.location.LocationTracker,
    val controller: com.cerclerouge.app.protection.ProtectionController,
    recorder: com.cerclerouge.app.evidence.EvidenceRecorder
) : ViewModel() {
    val history: StateFlow<SoundHistory> = audio.history
    val recording: StateFlow<String?> = recorder.recording
    private val _motion = MutableStateFlow<List<Sample>>(emptyList())
    val motion: StateFlow<List<Sample>> = _motion
    private val _speed = MutableStateFlow<List<Sample>>(emptyList())
    val speed: StateFlow<List<Sample>> = _speed

    init {
        viewModelScope.launch {
            motion.events.collect { e -> _motion.value = keep(_motion.value + (e.atMillis to e.strength.toFloat().coerceIn(0f, 1f))) }
        }
        viewModelScope.launch {
            location.speed.collect { v -> if (v != null) _speed.value = keep(_speed.value + (System.currentTimeMillis() to (v * 3.6f / MAX_KMH).coerceIn(0f, 1f))) }
        }
    }

    private fun keep(list: List<Sample>): List<Sample> = list.filter { it.first >= System.currentTimeMillis() - WINDOW_MS }

    private companion object {
        const val WINDOW_MS = 10 * 60_000L
        const val MAX_KMH = 60f
    }
}

@Composable
fun SoundLevelScreen(vm: SoundLevelViewModel, onBack: () -> Unit, onTalk: () -> Unit) {
    val history by vm.history.collectAsState()
    val motion by vm.motion.collectAsState()
    val speed by vm.speed.collectAsState()
    val recording by vm.recording.collectAsState()
    val state by vm.controller.state.collectAsState()
    var muted by remember { mutableStateOf(vm.controller.listeningMutedUntil != null) }
    SoundLevelContent(
        history,
        motion,
        speed,
        System.currentTimeMillis(),
        alerts = if (state.protection.isAlert) 1 else 0,
        recording = recording != null,
        muted = muted,
        onBack = onBack,
        onMute = {
            if (muted) vm.controller.unmuteListening() else vm.controller.muteListening(MUTE_MINUTES)
            muted = !muted
        },
        onTalk = onTalk
    )
}

private const val MUTE_MINUTES = 15L
private const val ATTENTION = 0.88f

@Composable
internal fun SoundLevelContent(
    history: SoundHistory,
    motion: List<Sample>,
    speed: List<Sample>,
    now: Long,
    alerts: Int,
    recording: Boolean,
    muted: Boolean,
    onBack: () -> Unit,
    onMute: () -> Unit,
    onTalk: () -> Unit,
    initialTab: LevelTab = LevelTab.SOUND
) {
    val c = Cr.colors
    var tab by remember { mutableStateOf(initialTab) }
    Column(Modifier.fillMaxSize().background(c.surface)) {
        BackHeader(stringResource(R.string.v_level_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Segmented(tab) { tab = it }
            Info(tab, history, motion, now)
            VCard(Modifier.padding(top = 14.dp), padding = PaddingValues(start = 10.dp, end = 14.dp, top = 14.dp, bottom = 10.dp)) {
                val samples = when (tab) {
                    LevelTab.SOUND -> history.peaks
                    LevelTab.MOTION -> motion
                    LevelTab.POSITION -> speed
                }
                LevelChart(samples, now, threshold = if (tab == LevelTab.SOUND) ATTENTION else null, Modifier.fillMaxWidth().height(280.dp))
            }
            SectionLabel(stringResource(R.string.v_last_10))
            VCard(padding = PaddingValues(vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Tile(R.drawable.glass_volume, history.voices.size.toString(), stringResource(R.string.v_stat_voice), c.ink, Modifier.weight(1f))
                    Tile(R.drawable.glass_warning, history.loud.size.toString(), stringResource(R.string.v_peak), if (history.loud.isEmpty()) c.ink else Color(0xFFB45309), Modifier.weight(1f))
                    Tile(R.drawable.glass_bell, alerts.toString(), stringResource(R.string.v_alert_count), if (alerts == 0) c.forest else c.danger, Modifier.weight(1f))
                    Tile(R.drawable.glass_lock, stringResource(if (recording) R.string.v_rec_on else R.string.v_rec_none), stringResource(R.string.v_recorded), c.ink, Modifier.weight(1f))
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(27.dp)).background(Color(0xFFE4E8EE)).clickable(role = Role.Button, onClick = onMute),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(if (muted) R.string.v_unmute else R.string.v_mute_15), style = V.text(16.sp, FontWeight.SemiBold), color = c.ink) }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 54.dp)
                    .shadow(10.dp, RoundedCornerShape(27.dp), ambientColor = Color(0x662F86F6), spotColor = Color(0x662F86F6))
                    .clip(RoundedCornerShape(27.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF4A98F8), Color(0xFF1F6FE5))))
                    .clickable(role = Role.Button, onClick = onTalk),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelIconView(PixelIcon.MIC, 22.dp, Color.White, strokeWidth = 2.2f)
                Text(stringResource(R.string.v_listen_with_me), style = V.text(17.sp, FontWeight.SemiBold), color = Color.White)
            }
        }
    }
}

/** Sélecteur à 3 segments (maquette : fond #D8DEE5, segment choisi blanc). */
@Composable
private fun Segmented(tab: LevelTab, onTab: (LevelTab) -> Unit) {
    val labels = listOf(LevelTab.SOUND to R.string.v_tab_sound, LevelTab.MOTION to R.string.v_tab_motion, LevelTab.POSITION to R.string.v_tab_position)
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFD8DEE5)).padding(4.dp)) {
        labels.forEach { (t, label) ->
            val on = t == tab
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(11.dp)).background(if (on) Color.White else Color.Transparent)
                    .clickable(role = Role.Tab) { onTab(t) }.semantics { selected = on },
                contentAlignment = Alignment.Center
            ) { Text(stringResource(label).uppercase(), style = V.text(13.sp, FontWeight.Medium, spacing = 0.02f), color = if (on) Cr.colors.ink else Cr.colors.muted) }
        }
    }
}

@Composable
private fun Info(tab: LevelTab, history: SoundHistory, motion: List<Sample>, now: Long) {
    val text = when (tab) {
        LevelTab.SOUND -> history.loud.lastOrNull()?.let { at ->
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(stringResource(R.string.v_loud_noise)) }
                append(" ")
                append(stringResource(R.string.v_loud_ago, ((now - at) / 60_000).coerceAtLeast(0)))
            }
        } ?: buildAnnotatedString { append(stringResource(R.string.v_no_loud)) }
        LevelTab.MOTION -> buildAnnotatedString { append(stringResource(if (motion.isEmpty()) R.string.v_no_motion else R.string.v_motion_seen, motion.size)) }
        LevelTab.POSITION -> buildAnnotatedString { append(stringResource(R.string.v_position_info)) }
    }
    VCard(Modifier.padding(top = 14.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PixelIconView(PixelIcon.ALERT, 22.dp, Cr.colors.muted, strokeWidth = 1.8f)
            Text(text, style = V.text(16.sp, line = 23.sp), color = Cr.colors.ink)
        }
    }
}

@Composable
private fun Tile(icon: Int, value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        GlassIcon(icon, 30.dp)
        Text(value, style = V.text(16.sp, FontWeight.SemiBold), color = color, modifier = Modifier.padding(top = 4.dp))
        Text(label, style = V.small, color = Cr.colors.muted)
    }
}

/**
 * Courbe des 10 dernières minutes (maquette) : axe 0 à 100, grille légère, courbe bleue lissée avec dégradé, seuil
 * d'attention rouge en pointillés. Décrite pour TalkBack par son maximum.
 */
@Composable
internal fun LevelChart(samples: List<Sample>, now: Long, threshold: Float?, modifier: Modifier) {
    val desc = stringResource(R.string.v_chart_a11y, ((samples.maxOfOrNull { it.second } ?: 0f) * 100).toInt())
    val attention = stringResource(R.string.v_attention)
    val ticks = listOf("−10 min", "−7", "−5", "−2", stringResource(R.string.v_now_short))
    val muted = Cr.colors.muted
    Canvas(modifier.semantics { contentDescription = desc }) {
        val left = 34.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val top = 8.dp.toPx()
        val w = size.width - left
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 11.sp.toPx()
            color = android.graphics.Color.rgb(0x66, 0x78, 0x8A)
        }
        for (k in 0..5) {
            val y = bottom - (bottom - top) * k / 5f
            drawLine(Color(0xFFE6EBF0), Offset(left, y), Offset(size.width, y), 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("${k * 20}", 4.dp.toPx(), y + 4.dp.toPx(), paint)
        }
        listOf(0f, 0.3f, 0.5f, 0.8f, 1f).forEachIndexed { i, f ->
            val label = ticks[i]
            val tw = paint.measureText(label)
            val x = (left + w * f - tw / 2).coerceIn(left - tw / 2, size.width - tw)
            drawContext.canvas.nativeCanvas.drawText(label, x, size.height - 4.dp.toPx(), paint)
        }
        threshold?.let { th ->
            val y = bottom - (bottom - top) * th
            drawLine(Color(0xFFE31B23), Offset(left, y), Offset(size.width, y), 1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            val red = android.graphics.Paint(paint).apply { color = android.graphics.Color.rgb(0xC8, 0x14, 0x1D) }
            drawContext.canvas.nativeCanvas.drawText(attention, size.width - red.measureText(attention), y - 5.dp.toPx(), red)
        }
        val from = now - 10 * 60_000L
        val pts = samples.sortedBy { it.first }.map { (t, v) -> Offset(left + w * ((t - from) / 600_000f).coerceIn(0f, 1f), bottom - (bottom - top) * v.coerceIn(0f, 1f)) }
        if (pts.size >= 2) {
            val line = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val a = pts[i - 1]
                    val b = pts[i]
                    val mx = (a.x + b.x) / 2
                    cubicTo(mx, a.y, mx, b.y, b.x, b.y)
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(pts.last().x, bottom)
                lineTo(pts.first().x, bottom)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(Color(0x592F86F6), Color(0x082F86F6)), top, bottom))
            drawPath(line, Color(0xFF2F86F6), style = Stroke(2.5.dp.toPx()))
        } else if (pts.size == 1) {
            drawCircle(Color(0xFF2F86F6), 4.dp.toPx(), pts[0])
        } else {
            val t = paint.apply { color = android.graphics.Color.rgb((muted.red * 255).toInt(), (muted.green * 255).toInt(), (muted.blue * 255).toInt()) }
            val msg = "—"
            drawContext.canvas.nativeCanvas.drawText(msg, left + w / 2 - t.measureText(msg) / 2, (top + bottom) / 2, t)
        }
    }
}
