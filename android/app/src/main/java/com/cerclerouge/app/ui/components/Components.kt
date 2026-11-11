package com.cerclerouge.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.cerclerouge.app.R
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.valentinerutto.orbmotion.orbs.AnimatedThinkingOrb
import com.valentinerutto.orbmotion.orbs.OrbState
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.hypot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Mouvement
// ---------------------------------------------------------------------------

/** « Réduire les animations » du système (échelle des animations à 0). */
@Composable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Compteur qui avance d'un pas toutes les `periodMs` ; figé à 0 si « réduire les animations ». */
@Composable
fun stepTick(periodMs: Long, enabled: Boolean = true): Int {
    val still = reduceMotion() || !enabled
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(still, periodMs) {
        if (still) return@LaunchedEffect
        while (true) {
            delay(periodMs)
            tick++
        }
    }
    return tick
}

/** Couleur ramenée en niveaux de gris (veille « noir et blanc »). */
fun Color.mono(): Color {
    val g = 0.299f * red + 0.587f * green + 0.114f * blue
    return Color(g, g, g, alpha)
}

/** Quadrillage léger du fond (24 dp). */
fun Modifier.gridBackground(color: Color): Modifier = drawBehind {
    val step = 24.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
}

// ---------------------------------------------------------------------------
// Icônes (Lucide, ISC)
// ---------------------------------------------------------------------------

enum class CrIcon(val rows: String) {
    SHIELD("########/#......#/#......#/#......#/.#....#./.#....#./..#..#../...##..."),
    POWER("...##.../.#.##.#./#..##..#/#......#/#......#/.#....#./..####../........"),
    MIC("..####../..#..#../..#..#../#.#..#.#/#..##..#/.#....#./..####../...##..."),
    PIN("..####../.#....#./.#.##.#./.#.##.#./.#....#./..#..#../...##.../........"),
    USERS("..####../..#..#../..####../...##.../.######./##....##/#......#/#......#"),
    ALERT("...##.../..#..#../..#..#../.#.##.#./.#.##.#./#......#/#..##..#/########"),
    HOME("...##.../..#..#../.#....#./##....##/.#....#./.#.##.#./.#.##.#./.######."),
    MAP("##...##./#.###..#/#.#.#..#/#.#.#..#/#.#.#..#/#.#.#..#/#..###.#/.##...##"),
    ROUTE("##....../##....../.#....../.######./......#./......#./......##/......##"),
    HISTORY("..####../.#....#./#..#...#/#..#...#/#..###.#/##.....#/###...#./..####.."),
    EXPAND("###..###/#......#/#......#/......../......../#......#/#......#/###..###"),
    CLOSE("#......#/.#....#./..#..#../...##.../...##.../..#..#../.#....#./#......#"),
    BACK("....#.../...#..../..#...../.#######/..#...../...#..../....#.../........"),
    CHECK("......../.......#/......##/#....##./##..##../.####.../..##..../........"),
    PHONE(".######./.#....#./.#....#./.#....#./.#....#./.#....#./.######./...##..."),
    LOCK("..####../.#....#./.#....#./########/##....##/##.##.##/##....##/########"),
    CLOCK("..####../.#....#./#..#...#/#..#...#/#..###.#/#......#/.#....#./..####.."),
    NEXT("......../...#..../....#.../#######./....#.../...#..../......../........")
}

/** Icône 8 × 8 en pixels, décorative (le texte voisin porte le sens). */
@Composable
fun Ico(icon: CrIcon, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    val rows = remember(icon) { icon.rows.split('/') }
    Canvas(modifier.size(size)) {
        val cell = this.size.width / 8
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch -> if (ch == '#') drawRect(tint, Offset(x * cell, y * cell), Size(cell, cell)) }
        }
    }
}

/** Angles crénelés (marches de 4 dp) des panneaux et boutons pixel. */
class NotchShape(private val step: Dp = 4.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { step.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2)
        val p = Path().apply {
            moveTo(0f, s)
            lineTo(s, s)
            lineTo(s, 0f)
            lineTo(size.width - s, 0f)
            lineTo(size.width - s, s)
            lineTo(size.width, s)
            lineTo(size.width, size.height - s)
            lineTo(size.width - s, size.height - s)
            lineTo(size.width - s, size.height)
            lineTo(s, size.height)
            lineTo(s, size.height - s)
            lineTo(0f, size.height - s)
            close()
        }
        return Outline.Generic(p)
    }
}

val Notch = NotchShape()

/** Cercle en escalier sur une grille de `cells` cases (disques pixel). */
fun pixelCircle(cells: Int): Shape = GenericShape { size, _ ->
    val cell = size.width / cells
    val r = cells / 2f
    for (row in 0 until cells) {
        val cols = (0 until cells).filter { col -> hypot(col + 0.5f - r, row + 0.5f - r) <= r + 0.15f }
        if (cols.isNotEmpty()) addRect(Rect(cols.first() * cell, row * cell, (cols.last() + 1) * cell, (row + 1) * cell))
    }
}

/** Cadre pixel : bordure crénelée de 2 dp (`border`) et intérieur (`fill`). */
fun Modifier.pixelFrame(border: Color, fill: Brush): Modifier = clip(Notch).background(border).padding(2.dp).clip(Notch).background(fill)

fun Modifier.pixelFrame(border: Color, fill: Color): Modifier = pixelFrame(border, SolidColor(fill))

// ---------------------------------------------------------------------------
// Cadres
// ---------------------------------------------------------------------------

/** Panneau pixel : dégradé vert sombre, bordure crénelée de 2 dp. */
@Composable
fun DashPanel(
    modifier: Modifier = Modifier,
    edge: Color = Cr.colors.edge,
    padding: PaddingValues = PaddingValues(12.dp),
    fill: Brush? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val c = Cr.colors
    Box(
        modifier
            .pixelFrame(edge, fill ?: Brush.verticalGradient(listOf(c.panelTop, c.panelBottom)))
            .padding(padding),
        content = content
    )
}

/** Crochets « [ … ] » autour d'un contenu (barre d'horloge, météo des assets). */
@Composable
fun Brackets(modifier: Modifier = Modifier, color: Color = Cr.colors.green, content: @Composable () -> Unit) {
    Box(
        modifier
            .drawBehind {
                val w = 2.dp.toPx()
                val l = 8.dp.toPx()
                drawLine(color, Offset(0f, 0f), Offset(0f, size.height), w)
                drawLine(color, Offset(0f, 0f), Offset(l, 0f), w)
                drawLine(color, Offset(0f, size.height), Offset(l, size.height), w)
                drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), w)
                drawLine(color, Offset(size.width, 0f), Offset(size.width - l, 0f), w)
                drawLine(color, Offset(size.width, size.height), Offset(size.width - l, size.height), w)
            }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) { content() }
}

/** Bandeau de section : fond vert, carré marqueur, languette en biais en bas à gauche. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, accent: Color = Cr.colors.green, trailing: @Composable () -> Unit = {}) {
    val c = Cr.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Brush.horizontalGradient(listOf(c.banner, c.banner.copy(alpha = 0.4f))))
            .drawBehind {
                val w = 1.dp.toPx()
                drawLine(accent, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
                // Languette en biais (repère des bandeaux des assets).
                val tab = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(0f, size.height - 5.dp.toPx())
                    lineTo(48.dp.toPx(), size.height - 5.dp.toPx())
                    lineTo(56.dp.toPx(), size.height)
                    close()
                }
                drawPath(tab, accent)
            }
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(10.dp).background(accent))
        Spacer(Modifier.width(12.dp))
        Text(title.uppercase(), style = PxType.banner, color = c.ink, modifier = Modifier.weight(1f))
        trailing()
        Spacer(Modifier.width(10.dp))
    }
}

/** Titre de graphique « ◀ titre ▶ ». */
@Composable
fun TagTitle(title: String, modifier: Modifier = Modifier) {
    val c = Cr.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(6.dp, 14.dp)) {
            drawPath(
                Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(0f, size.height / 2)
                    lineTo(size.width, size.height)
                    close()
                },
                c.green
            )
        }
        Text(title, style = PxType.bodyStrong, color = c.ink)
        Canvas(Modifier.size(6.dp, 14.dp)) {
            drawPath(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height / 2)
                    lineTo(0f, size.height)
                    close()
                },
                c.green
            )
        }
    }
}

/** Titre d'écran « » TITRE « » (chevrons or, asset « 首页 »). */
@Composable
fun ChevronTitle(title: String, modifier: Modifier = Modifier, color: Color = Cr.colors.gold) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("»", style = PxType.title, color = color)
        Text(title.uppercase(), style = PxType.title, color = Cr.colors.ink, modifier = Modifier.semantics { heading() })
        Text("«", style = PxType.title, color = color)
    }
}

/** Barre d'horloge « [ 22:14:03 ¦ 2026.11.11 ¦ MER. ] », mise à jour chaque seconde. */
@Composable
fun ClockBar(modifier: Modifier = Modifier, color: Color = Cr.colors.green) {
    val locale = LocalConfiguration.current.locales[0]
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = ZonedDateTime.now()
        }
    }
    val time = remember(now) { now.format(DateTimeFormatter.ofPattern("HH:mm:ss")) }
    val date = remember(now) { now.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) }
    val day = remember(now, locale) { now.format(DateTimeFormatter.ofPattern("EEE", locale)).uppercase(locale) }
    Brackets(modifier, color) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(time, style = PxType.clock)
            Text("¦", style = PxType.clock, color = Cr.colors.muted)
            Text(date, style = PxType.clock)
            Text("¦", style = PxType.clock, color = Cr.colors.muted)
            Text(day, style = PxType.banner, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// Données
// ---------------------------------------------------------------------------

/** Chiffre en dégradé menthe (gros chiffres condensés des assets). */
@Composable
fun Digits(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = PxType.digits, active: Boolean = true) {
    val c = Cr.colors
    val brush = if (active) Brush.verticalGradient(listOf(c.mintLight, c.mint)) else Brush.verticalGradient(listOf(c.ink, c.muted))
    Text(text, style = style.copy(brush = brush), modifier = modifier)
}

/** Tuile KPI : icône, étiquette, valeur en chiffres condensés et unité (tuiles « 事项总量 4,432 件 »). */
@Composable
fun KpiTile(
    icon: CrIcon,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    active: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val c = Cr.colors
    val accent = if (active) c.green else c.muted
    Row(
        modifier
            .clip(Notch)
            .background(Brush.horizontalGradient(listOf(c.panelTop, c.panelBottom.copy(alpha = 0.3f))))
            .drawBehind { drawLine(accent, Offset(0f, 0f), Offset(0f, size.height), 3.dp.toPx()) }
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Ico(icon, 30.dp, if (active) c.mint else c.muted)
        Column {
            Text(label.uppercase(), style = PxType.label, color = c.ink)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Digits(value, active = active)
                if (unit.isNotEmpty()) Text(unit, style = PxType.label, color = c.muted, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

/** Voyant rond de 8 dp, qui clignote (1 s) quand `blink`. */
@Composable
fun Led(color: Color, blink: Boolean, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val on = stepTick(500, enabled = blink) % 2 == 0
    Box(modifier.size(size).background(if (on) color else color.copy(alpha = 0.25f)))
}

/** Égaliseur de 5 barres : l'écoute est active (aucun contenu audio n'est affiché). */
@Composable
fun EqBars(color: Color = Cr.colors.mint) {
    val t = stepTick(160)
    Row(Modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(3, 1, 4, 2, 5).forEachIndexed { i, base ->
            val h = (base + t + i * 2) % 5 + 1
            Box(Modifier.width(3.dp).height((h * 3).dp).background(color))
        }
    }
}

// ---------------------------------------------------------------------------
// Boutons et navigation
// ---------------------------------------------------------------------------

enum class DashVariant { PRIMARY, DANGER, OUTLINE, DARK_DANGER }

/** Bouton : 56 dp (72 dp pour les actions critiques), légèrement décalé à l'appui. */
@Composable
fun DashButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: DashVariant = DashVariant.PRIMARY,
    icon: CrIcon? = null,
    tall: Boolean = false,
    enabled: Boolean = true
) {
    val c = Cr.colors
    val (fill, border, ink) = when (variant) {
        DashVariant.PRIMARY -> Triple(Brush.verticalGradient(listOf(c.mint, c.green)), c.green, c.onAccent)
        DashVariant.DANGER -> Triple(Brush.verticalGradient(listOf(c.sos, c.sos)), c.sos, c.onSos)
        DashVariant.OUTLINE -> Triple(Brush.verticalGradient(listOf(c.panelTop, c.panelBottom)), c.lineControl, c.ink)
        DashVariant.DARK_DANGER -> Triple(Brush.verticalGradient(listOf(c.bg, c.bg)), c.danger, c.danger)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .height(if (tall) 72.dp else 56.dp)
            .offset { if (pressed) IntOffset(0, 2.dp.roundToPx()) else IntOffset.Zero }
            .pixelFrame(if (enabled) border else c.line, if (enabled) fill else SolidColor(c.panelBottom))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Ico(it, 22.dp, if (enabled) ink else c.muted)
            Spacer(Modifier.width(10.dp))
        }
        Text(text.uppercase(), style = PxType.button, color = if (enabled) ink else c.muted, textAlign = TextAlign.Center)
    }
}

/**
 * SOS : appui long de 1,5 s (remplissage), puis vibration et compte à rebours (R-HOME-01) ; appui court → écran SOS.
 * TalkBack : l'action « activer » déclenche directement. `tile` : version moniteur de l'accueil.
 */
@Composable
fun SosBar(onTrigger: () -> Unit, onTap: () -> Unit, modifier: Modifier = Modifier, tile: Boolean = false) {
    val c = Cr.colors
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val a11y = stringResource(R.string.sos_a11y)
    Box(
        (if (tile) modifier else modifier.fillMaxWidth().height(72.dp))
            .clip(Notch)
            .background(c.sos)
            .semantics {
                role = Role.Button
                contentDescription = a11y
                onClick {
                    onTrigger()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    var fired = false
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(1500, easing = LinearEasing))
                        fired = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTrigger()
                    }
                    waitForUpOrCancellation()
                    if (!fired) {
                        job.cancel()
                        if (progress.value < 0.12f) onTap()
                        scope.launch { progress.snapTo(0f) }
                    }
                }
            }
    ) {
        if (tile) {
            Box(
                Modifier.align(
                    Alignment.BottomStart
                ).fillMaxWidth().fillMaxHeight(progress.value).background(Color.Black.copy(alpha = 0.3f))
            )
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Ico(CrIcon.ALERT, 30.dp, c.onSos)
                Text(
                    stringResource(R.string.sos),
                    style = PxType.display.copy(fontSize = PxType.display.fontSize * 1.5f),
                    color = c.onSos
                )
                Text(stringResource(R.string.sos_hint_two_lines), style = PxType.label, color = c.onSos, textAlign = TextAlign.Center)
            }
        } else {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(Color.Black.copy(alpha = 0.3f)))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Ico(CrIcon.ALERT, 28.dp, c.onSos)
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(R.string.sos),
                    style = PxType.display.copy(fontSize = PxType.display.fontSize * 1.3f),
                    color = c.onSos
                )
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.sos_hint_two_lines), style = PxType.label, color = c.onSos)
            }
        }
    }
}

/** En-tête d'écran : retour (48 dp) et titre à chevrons. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?) {
    val back = stringResource(R.string.back)
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(
                Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = back },
                contentAlignment = Alignment.Center
            ) { Ico(CrIcon.BACK, 24.dp, Cr.colors.ink) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Spacer(Modifier.width(4.dp))
        ChevronTitle(title)
    }
}

data class NavItem(val label: String, val icon: CrIcon, val onClick: () -> Unit)

/** Barre de navigation : 4 onglets, l'onglet courant en vert avec un trait lumineux au-dessus. */
@Composable
fun DashNavBar(items: List<NavItem>, selected: Int) {
    val c = Cr.colors
    Row(Modifier.fillMaxWidth().height(64.dp).pixelFrame(c.line, c.screen)) {
        items.forEachIndexed { i, item ->
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(role = Role.Tab, onClick = item.onClick)
                    .semantics { this.selected = active },
                contentAlignment = Alignment.Center
            ) {
                if (active) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(0.6f)
                            .height(3.dp)
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, c.mint, Color.Transparent)))
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Ico(item.icon, 22.dp, if (active) c.mint else c.muted)
                    Text(item.label.uppercase(), style = PxType.label, color = if (active) c.mint else c.muted)
                }
            }
        }
    }
}

/** Case à cocher carrée de 24 dp (zone tactile : toute la ligne). */
@Composable
fun DashCheck(checked: Boolean) {
    val c = Cr.colors
    Box(
        Modifier.size(24.dp).background(if (checked) c.green else c.screen).border(2.dp, if (checked) c.green else c.lineControl),
        contentAlignment = Alignment.Center
    ) { if (checked) Ico(CrIcon.CHECK, 18.dp, c.onAccent) }
}

/** Puce de filtre (Tout / Alertes…). */
@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Cr.colors
    Box(
        modifier
            .heightIn(min = 48.dp)
            .pixelFrame(if (selected) c.green else c.lineControl, if (selected) c.green else c.screen)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text.uppercase(), style = PxType.label, color = if (selected) c.onAccent else c.ink) }
}

/** Pavé PIN (touches de 72 × 56 dp). Le code n'est jamais affiché en clair. */
@Composable
fun PinPad(value: String, onChange: (String) -> Unit, maxLength: Int = 8) {
    val c = Cr.colors
    val delete = stringResource(R.string.pin_delete)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(16.dp)) {
            repeat(maxOf(4, value.length)) { i ->
                Box(Modifier.size(12.dp).background(if (i < value.length) c.mint else c.line))
            }
        }
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(72.dp, 56.dp))
                    } else {
                        Box(
                            Modifier
                                .size(72.dp, 56.dp)
                                .pixelFrame(c.lineControl, Brush.verticalGradient(listOf(c.panelTop, c.panelBottom)))
                                .clickable(role = Role.Button) {
                                    if (key == "⌫") {
                                        onChange(value.dropLast(1))
                                    } else if (value.length < maxLength) {
                                        onChange(value + key)
                                    }
                                }
                                .semantics { if (key == "⌫") contentDescription = delete },
                            contentAlignment = Alignment.Center
                        ) {
                            if (key == "⌫") Ico(CrIcon.BACK, 20.dp, c.ink) else Digits(key, style = PxType.title)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Orbes à points (OrbMotion, MIT)
// ---------------------------------------------------------------------------

/**
 * Orbe à points (bibliothèque OrbMotion) : activité en cours (envoi, attente, démarrage).
 * Décorative pour TalkBack ; figée si « réduire les animations ». La bibliothèque dessine en pixels.
 */
@Composable
fun Orb(state: OrbState, size: Dp, color: Color, modifier: Modifier = Modifier, speed: Float = 1f) {
    val px = with(LocalDensity.current) { size.toPx() }
    AnimatedThinkingOrb(
        modifier = modifier.requiredSize(size).clearAndSetSemantics {},
        state = state,
        size = px,
        speed = if (reduceMotion()) 0f else speed,
        color = color
    )
}
