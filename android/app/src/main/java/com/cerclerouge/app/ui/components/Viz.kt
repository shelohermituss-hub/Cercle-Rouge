package com.cerclerouge.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.DashType
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Orbe dégradée : écoute IA (image de référence rose / orange / bleu)
// ---------------------------------------------------------------------------

private val ORB_PINK = Color(0xFFFF3EA5)
private val ORB_ORANGE = Color(0xFFFFB347)
private val ORB_RED = Color(0xFFFF1E50)
private val ORB_PURPLE = Color(0xFF7B2FF7)
private val ORB_CREAM = Color(0xFFFFF2DC)
private val ORB_GLOW_RED = Color(0xFFB0283A)
private val ORB_GLOW_BLUE = Color(0xFF2846C8)

/**
 * Sphère lumineuse qui « respire » pendant l'écoute IA. `active` faux : même sphère en noir et blanc, immobile.
 * Aucun contenu audio n'est utilisé : l'animation signale seulement que l'écoute tourne (P1).
 */
@Composable
fun GradientOrb(active: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val still = reduceMotion() || !active
    val transition = rememberInfiniteTransition(label = "orb")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "spin")
    val breath by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val a = if (still) 35f else spin
        val b = if (still) 0.5f else breath
        drawOrb(active, a, b)
    }
}

private fun DrawScope.drawOrb(active: Boolean, angle: Float, breath: Float) {
    fun col(c: Color) = if (active) c else c.mono()
    val center = Offset(size.width / 2, size.height / 2)
    val r = size.minDimension * 0.34f * (0.97f + 0.06f * breath)
    // Halo : rouge à gauche, bleu à droite.
    drawCircle(
        Brush.radialGradient(
            listOf(col(ORB_GLOW_RED).copy(alpha = 0.55f), Color.Transparent),
            center - Offset(r * 0.5f, 0f),
            r * 1.5f
        ),
        r * 1.5f,
        center - Offset(r * 0.5f, 0f)
    )
    drawCircle(
        Brush.radialGradient(
            listOf(col(ORB_GLOW_BLUE).copy(alpha = 0.55f), Color.Transparent),
            center + Offset(r * 0.5f, 0f),
            r * 1.5f
        ),
        r * 1.5f,
        center + Offset(r * 0.5f, 0f)
    )
    val sphere = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
    clipPath(sphere) {
        drawCircle(col(Color(0xFFE8318C)), r, center)
        fun blob(color: Color, deg: Float, dist: Float, radius: Float) {
            val rad = Math.toRadians(deg.toDouble())
            val p = center + Offset((cos(rad) * dist * r).toFloat(), (sin(rad) * dist * r).toFloat())
            drawCircle(Brush.radialGradient(listOf(col(color), col(color).copy(alpha = 0f)), p, radius * r), radius * r, p)
        }
        blob(ORB_PINK, angle + 200f, 0.45f, 0.9f)
        blob(ORB_PURPLE, angle + 300f, 0.7f, 0.6f)
        blob(ORB_RED, angle + 120f, 0.65f, 0.6f)
        blob(ORB_ORANGE, angle + 20f, 0.4f, 0.75f)
        blob(ORB_CREAM, angle + 330f, 0.08f, 0.45f + 0.1f * breath)
        // Reflet du haut.
        drawOval(col(Color(0xFFFF9A8A)).copy(alpha = 0.55f), center + Offset(-r * 0.25f, -r * 0.95f), Size(r * 0.5f, r * 0.14f))
    }
    drawCircle(Color.White.copy(alpha = 0.22f), r, center, style = Stroke(r * 0.03f))
}

// ---------------------------------------------------------------------------
// Biométrie (animation « Face Unlock » : visage → cercle → validation)
// ---------------------------------------------------------------------------

enum class FacePhase { IDLE, SCANNING, SUCCESS, ERROR }

/** Icône animée d'authentification : visage au repos, cercle qui tourne pendant la vérification, coche ou croix. */
@Composable
fun FaceUnlock(phase: FacePhase, size: Dp, modifier: Modifier = Modifier) {
    val c = Cr.colors
    val color = when (phase) {
        FacePhase.ERROR -> c.danger
        FacePhase.SUCCESS -> c.mint
        else -> c.blue
    }
    val still = reduceMotion()
    val transition = rememberInfiniteTransition(label = "face")
    val sweep by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1_100, easing = LinearEasing)), label = "sweep")
    val look by transition.animateFloat(
        -1f,
        1f,
        infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "look"
    )
    val draw = remember(phase) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(phase) {
        if (!still && (phase == FacePhase.SUCCESS || phase == FacePhase.ERROR)) {
            draw.snapTo(0f)
            draw.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        }
    }
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val r = this.size.minDimension / 2 - 3.dp.toPx()
        val o = Offset(this.size.width / 2, this.size.height / 2)
        val w = 3.dp.toPx()
        when (phase) {
            FacePhase.IDLE -> drawFace(o, r, color, w, if (still) 0f else look)
            FacePhase.SCANNING -> {
                drawCircle(color.copy(alpha = 0.25f), r, o, style = Stroke(w))
                drawArc(
                    color,
                    if (still) 0f else sweep,
                    90f,
                    false,
                    o - Offset(r, r),
                    Size(r * 2, r * 2),
                    style = Stroke(w, cap = StrokeCap.Round)
                )
            }
            FacePhase.SUCCESS -> {
                drawCircle(color, r, o, style = Stroke(w))
                val p = Path().apply {
                    moveTo(o.x - r * 0.4f, o.y)
                    lineTo(o.x - r * 0.1f, o.y + r * 0.3f)
                    lineTo(o.x + r * 0.45f, o.y - r * 0.3f)
                }
                drawPartialPath(p, draw.value, color, w)
            }
            FacePhase.ERROR -> {
                drawCircle(color, r, o, style = Stroke(w))
                val d = r * 0.35f * draw.value
                drawLine(color, o - Offset(d, d), o + Offset(d, d), w, StrokeCap.Round)
                drawLine(color, o + Offset(d, -d), o + Offset(-d, d), w, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.drawFace(o: Offset, r: Float, color: Color, w: Float, look: Float) {
    drawCircle(color, r, o, style = Stroke(w))
    val dx = look * r * 0.06f
    drawCircle(color, w * 0.9f, o + Offset(-r * 0.32f + dx, -r * 0.22f))
    drawCircle(color, w * 0.9f, o + Offset(r * 0.32f + dx, -r * 0.22f))
    val nose = Path().apply {
        moveTo(o.x + r * 0.04f + dx, o.y - r * 0.12f)
        lineTo(o.x + r * 0.04f + dx, o.y + r * 0.12f)
        lineTo(o.x - r * 0.08f + dx, o.y + r * 0.12f)
    }
    drawPath(nose, color, style = Stroke(w * 0.8f, cap = StrokeCap.Round))
    drawArc(color, 30f, 120f, false, o - Offset(r * 0.42f, r * 0.2f), Size(r * 0.84f, r * 0.7f), style = Stroke(w, cap = StrokeCap.Round))
}

/** Trace un chemin jusqu'à `progress` (0..1) en le découpant en segments. */
private fun DrawScope.drawPartialPath(path: Path, progress: Float, color: Color, width: Float) {
    val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
    val out = Path()
    measure.getSegment(0f, measure.length * progress, out, true)
    drawPath(out, color, style = Stroke(width, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

// ---------------------------------------------------------------------------
// Terminal « trace tree » (AgentPrism)
// ---------------------------------------------------------------------------

enum class TraceStatus { OK, WARN, ERROR, NONE }

/**
 * Ligne de terminal : niveau d'arborescence, pastille lettre, titre, étiquette colorée, puces (heure…),
 * barre de chronologie (`start` et `length` en fraction de la période) et durée.
 */
data class TraceRow(
    val key: Long,
    val level: Int,
    val letter: Char,
    val color: Color,
    val title: String,
    val tag: String? = null,
    val chips: List<String> = emptyList(),
    val start: Float = 0f,
    val length: Float = 0f,
    val duration: String = "",
    val status: TraceStatus = TraceStatus.NONE
)

/** Une ligne du terminal ; les lignes enfants (niveau > 0) sont reliées par des traits d'arborescence. */
@Composable
fun TraceLine(row: TraceRow, last: Boolean, modifier: Modifier = Modifier) {
    val c = Cr.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row.level > 0) {
            Spacer(Modifier.width(((row.level - 1) * 16).dp))
            Canvas(Modifier.width(18.dp).height(52.dp)) {
                val x = 7.dp.toPx()
                val stroke = 1.dp.toPx()
                drawLine(c.lineControl, Offset(x, 0f), Offset(x, if (last) size.height / 2 else size.height), stroke)
                drawLine(c.lineControl, Offset(x, size.height / 2), Offset(size.width, size.height / 2), stroke)
            }
        }
        Box(Modifier.size(22.dp).clip(CircleShape).background(row.color), contentAlignment = Alignment.Center) {
            Text(row.letter.toString(), style = DashType.label.copy(letterSpacing = DashType.label.letterSpacing * 0), color = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(row.title, style = DashType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.tag?.let {
                    Text(
                        it,
                        style = DashType.mono,
                        color = row.color,
                        maxLines = 1,
                        modifier = Modifier.background(
                            row.color.copy(alpha = 0.16f),
                            RoundedCornerShape(4.dp)
                        ).padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                row.chips.forEach {
                    Text(
                        it,
                        style = DashType.mono,
                        color = c.muted,
                        maxLines = 1,
                        modifier = Modifier.border(1.dp, c.chip, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier
                    .width(64.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.chip)
                    .drawBehind {
                        val x = size.width * row.start.coerceIn(0f, 1f)
                        val w = (size.width * row.length).coerceIn(4.dp.toPx(), size.width - x)
                        drawRoundRect(
                            row.color,
                            Offset(x, 0f),
                            Size(w, size.height),
                            androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                        )
                    }
            )
            Text(row.duration, style = DashType.mono, color = c.muted, modifier = Modifier.widthIn(min = 40.dp))
        }
        Spacer(Modifier.width(8.dp))
        val dot = when (row.status) {
            TraceStatus.OK -> c.green
            TraceStatus.WARN -> c.gold
            TraceStatus.ERROR -> c.danger
            TraceStatus.NONE -> c.chip
        }
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
    }
}

// ---------------------------------------------------------------------------
// Graphiques (axes et grilles des assets « Visualization Axis »)
// ---------------------------------------------------------------------------

/** Histogramme à grille pointillée et axes gradués ; une couleur par série, barres côte à côte. */
@Composable
fun BarChart(labels: List<String>, series: List<List<Int>>, colors: List<Color>, modifier: Modifier = Modifier) {
    val c = Cr.colors
    val measurer = rememberTextMeasurer()
    val axisStyle = DashType.mono.copy(color = c.muted)
    val max = (series.flatten().maxOrNull() ?: 0).coerceAtLeast(4)
    val top = ((max + 3) / 4) * 4
    Canvas(modifier.fillMaxWidth().height(160.dp).clearAndSetSemantics {}) {
        val left = 28.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val plotW = size.width - left
        for (i in 0..4) {
            val y = bottom - bottom * i / 4f * 0.92f
            drawLine(
                c.line,
                Offset(left, y),
                Offset(size.width, y),
                1.dp.toPx(),
                pathEffect = if (i ==
                    0
                ) {
                    null
                } else {
                    PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                }
            )
            val t = measurer.measure("${top * i / 4}", axisStyle)
            drawText(t, topLeft = Offset(left - t.size.width - 6.dp.toPx(), y - t.size.height / 2))
        }
        val slot = plotW / labels.size
        val barW = (slot * 0.5f / series.size).coerceAtMost(14.dp.toPx())
        labels.forEachIndexed { i, label ->
            val cx = left + slot * (i + 0.5f)
            series.forEachIndexed { s, values ->
                val v = values.getOrElse(i) { 0 }
                val h = bottom * 0.92f * v / top
                val x = cx - barW * series.size / 2 + s * barW
                drawRect(
                    Brush.verticalGradient(listOf(colors[s], colors[s].copy(alpha = 0.55f)), bottom - h, bottom),
                    Offset(x, bottom - h),
                    Size(
                        barW - 2.dp.toPx(),
                        h
                    )
                )
            }
            val t = measurer.measure(label, axisStyle)
            drawText(t, topLeft = Offset(cx - t.size.width / 2, bottom + 4.dp.toPx()))
        }
    }
}

/** Radar (toile d'araignée) : valeurs 0..1 par axe, grille polygonale comme l'asset « radar ». */
@Composable
fun RadarChart(labels: List<String>, values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    val c = Cr.colors
    val measurer = rememberTextMeasurer()
    val style: TextStyle = DashType.label.copy(color = c.muted)
    Canvas(modifier.size(260.dp, 210.dp).clearAndSetSemantics {}) {
        val o = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 28.dp.toPx()
        val n = labels.size
        fun pt(i: Int, f: Float): Offset {
            val a = Math.toRadians(-90.0 + 360.0 * i / n)
            return o + Offset((cos(a) * r * f).toFloat(), (sin(a) * r * f).toFloat())
        }
        for (ring in 1..3) {
            val p = Path().apply {
                moveTo(pt(0, ring / 3f).x, pt(0, ring / 3f).y)
                for (i in 1 until n) lineTo(pt(i, ring / 3f).x, pt(i, ring / 3f).y)
                close()
            }
            drawPath(p, c.lineControl.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
        }
        for (i in 0 until n) drawLine(c.lineControl.copy(alpha = 0.6f), o, pt(i, 1f), 1.dp.toPx())
        val shape = Path().apply {
            moveTo(pt(0, values[0]).x, pt(0, values[0]).y)
            for (i in 1 until n) lineTo(pt(i, values[i]).x, pt(i, values[i]).y)
            close()
        }
        drawPath(shape, color.copy(alpha = 0.25f))
        drawPath(shape, color, style = Stroke(2.dp.toPx()))
        labels.forEachIndexed { i, label ->
            val t = measurer.measure(label.uppercase(), style)
            val p = pt(i, 1.22f)
            drawText(t, topLeft = Offset(p.x - t.size.width / 2, p.y - t.size.height / 2))
        }
    }
}

// ---------------------------------------------------------------------------
// Animations de texte
// ---------------------------------------------------------------------------

/** Nombre qui monte de 0 à `value` (chiffres des tableaux de bord) ; direct si « réduire les animations ». */
@Composable
fun CountUp(value: Int, modifier: Modifier = Modifier, style: TextStyle = DashType.digits, active: Boolean = true) {
    val still = reduceMotion()
    val anim = remember { Animatable(if (still) value.toFloat() else 0f) }
    LaunchedEffect(value) {
        if (still) anim.snapTo(value.toFloat()) else anim.animateTo(value.toFloat(), tween(800, easing = FastOutSlowInEasing))
    }
    Digits("%,d".format(anim.value.toInt()), modifier, style, active)
}

/** Texte tapé caractère par caractère (lignes du terminal) ; affiché d'un coup si « réduire les animations ». */
@Composable
fun Typewriter(text: String, modifier: Modifier = Modifier, style: TextStyle = DashType.mono, color: Color = Cr.colors.ink) {
    val still = reduceMotion()
    var shown by remember(text) { mutableIntStateOf(if (still) text.length else 0) }
    LaunchedEffect(text) {
        while (shown < text.length) {
            delay(18)
            shown++
        }
    }
    Box(modifier) {
        // Le texte complet, invisible, réserve la place et sert à TalkBack.
        Text(text, style = style, color = Color.Transparent)
        Text(text.take(shown), style = style, color = color, modifier = Modifier.clearAndSetSemantics {})
    }
}
