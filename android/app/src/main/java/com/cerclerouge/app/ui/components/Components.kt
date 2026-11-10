package com.cerclerouge.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cerclerouge.app.R
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import kotlin.math.hypot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Mouvement
// ---------------------------------------------------------------------------

/** Vrai si l'utilisateur a demandé de réduire les animations (R-UI-05) : tout devient statique. */
@Composable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Horloge à pas fixes (le design n'anime que par paliers, comme `steps()` en CSS). Retourne 0 si animations réduites. */
@Composable
fun stepTick(periodMs: Long, enabled: Boolean = true): Int {
    val still = reduceMotion() || !enabled
    val tick by produceState(0, still, periodMs) {
        if (still) return@produceState
        while (true) {
            delay(periodMs)
            value++
        }
    }
    return if (still) 0 else tick
}

// ---------------------------------------------------------------------------
// Formes
// ---------------------------------------------------------------------------

/** Coins crénelés en pixels réels, à partir d'une taille en dp. */
fun notch(px: Float): Shape = GenericShape { size, _ ->
    val s = px.coerceAtMost(minOf(size.width, size.height) / 2)
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

/** Cercle en escalier sur une grille de `cells` cases (disques `--c11`, `--c17`, `--c25` du design). */
fun pixelCircle(cells: Int): Shape = GenericShape { size, _ ->
    val cell = size.width / cells
    val r = cells / 2f
    for (row in 0 until cells) {
        val cols = (0 until cells).filter { col -> hypot(col + 0.5f - r, row + 0.5f - r) <= r + 0.15f }
        if (cols.isNotEmpty()) addRect(Rect(cols.first() * cell, row * cell, (cols.last() + 1) * cell, (row + 1) * cell))
    }
}

@Composable
private fun notchShape(): Shape {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.toPx() }
    return remember(px) { notch(px) }
}

/** Fond quadrillé de 24 dp (fond des écrans « pl »). */
fun Modifier.pixelGrid(color: Color): Modifier = drawBehind {
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
// Panneaux et boutons
// ---------------------------------------------------------------------------

/** Panneau crénelé : bordure de 2 dp (`border`) et intérieur (`fill`). */
@Composable
fun PxPanel(
    modifier: Modifier = Modifier,
    border: Color = Cr.colors.line,
    fill: Color = Cr.colors.raised,
    padding: PaddingValues = PaddingValues(12.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = notchShape()
    Box(
        modifier
            .clip(shape)
            .background(border)
            .padding(2.dp)
            .clip(shape)
            .background(fill)
            .padding(padding),
        content = content
    )
}

enum class PxVariant { LIME, DANGER, DARK, DARK_DANGER, OUTLINE }

/** Bouton du design : 56 dp (72 dp pour les actions critiques), décalé de 2 dp à l'appui. */
@Composable
fun PxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PxVariant = PxVariant.LIME,
    icon: PixelIcon? = null,
    tall: Boolean = false,
    enabled: Boolean = true
) {
    val c = Cr.colors
    val (border, fill, ink) = when (variant) {
        PxVariant.LIME -> Triple(c.lime, c.lime, c.onLime)
        PxVariant.DANGER -> Triple(c.danger, c.danger, c.onLime)
        PxVariant.DARK -> Triple(c.lineControl, c.surface, c.ink)
        PxVariant.DARK_DANGER -> Triple(c.danger, c.surface, c.danger)
        PxVariant.OUTLINE -> Triple(c.lineControl, c.raised, c.ink)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    PxPanel(
        modifier
            .fillMaxWidth()
            .height(if (tall) 72.dp else 56.dp)
            .offset { if (pressed) IntOffset(2.dp.roundToPx(), 2.dp.roundToPx()) else IntOffset.Zero }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        border = if (enabled) border else c.line,
        fill = if (enabled) fill else c.raised,
        padding = PaddingValues(horizontal = 12.dp)
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                PixelIconView(it, 24.dp, if (enabled) ink else c.muted)
                Spacer(Modifier.width(10.dp))
            }
            Text(text.uppercase(), style = PxType.button, color = if (enabled) ink else c.muted, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Barre SOS de l'accueil : appui long de 1,5 s (la barre se remplit), puis vibration et compte à rebours (R-HOME-01).
 * Un appui court ouvre l'écran SOS. TalkBack : l'action « activer » déclenche directement.
 */
@Composable
fun SosBar(onTrigger: () -> Unit, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val c = Cr.colors
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val a11y = stringResource(R.string.sos_a11y)
    PxPanel(
        modifier
            .fillMaxWidth()
            .height(72.dp)
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
            },
        border = c.danger,
        fill = c.danger,
        padding = PaddingValues(0.dp)
    ) {
        // Remplissage par paliers de 1/12, comme les 12 carrés de l'écran SOS.
        val steps = (progress.value * 12).toInt() / 12f
        Box(Modifier.fillMaxHeight().fillMaxWidth(steps).background(c.onLime.copy(alpha = 0.25f)))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            PixelIconView(PixelIcon.ALERT, 28.dp, c.onLime)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.sos), style = PxType.display.copy(fontSize = PxType.display.fontSize * 1.28f), color = c.onLime)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.sos_hint_two_lines), style = PxType.label, color = c.onLime)
        }
    }
}

// ---------------------------------------------------------------------------
// Disque, orbite, indicateurs
// ---------------------------------------------------------------------------

/** Disque en pixels (ON/OFF de l'accueil, SOS) avec halo qui pulse par paliers quand `glow`. */
@Composable
fun PixelDisc(
    size: Dp,
    cells: Int,
    fill: Color,
    modifier: Modifier = Modifier,
    glow: Color? = null,
    orbit: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Cr.colors
    val pulse = stepTick(500, enabled = glow != null) % 6
    val shape = remember(cells) { pixelCircle(cells) }
    Box(modifier.size(size + 32.dp), contentAlignment = Alignment.Center) {
        if (glow != null) {
            val strength = 0.35f + 0.1f * (if (pulse < 3) pulse else 6 - pulse)
            Canvas(Modifier.size(size + 32.dp)) {
                drawCircle(
                    Brush.radialGradient(listOf(glow.copy(alpha = strength), Color.Transparent), radius = this.size.minDimension / 2)
                )
            }
        }
        if (orbit) {
            val turn = stepTick(1000) % 8
            Box(Modifier.size(size + 32.dp).rotate(turn * 45f)) {
                val dot = Modifier.size(6.dp).background(c.lime)
                Box(dot.align(Alignment.TopCenter))
                Box(dot.align(Alignment.CenterEnd))
                Box(dot.align(Alignment.BottomCenter))
                Box(dot.align(Alignment.CenterStart))
            }
        }
        Column(
            Modifier.size(size).clip(shape).background(fill),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/** Voyant carré de 8 dp, qui clignote (1 s) quand `blink`. */
@Composable
fun Led(color: Color, blink: Boolean, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val on = stepTick(500, enabled = blink) % 2 == 0
    Box(modifier.size(size).background(if (on) color else Color.Transparent))
}

/** Segments « x / total » (contacts prêts). */
@Composable
fun Segments(filled: Int, total: Int, color: Color = Cr.colors.lime) {
    val c = Cr.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics { stateDescription = "$filled / $total" }) {
        repeat(total) { i ->
            val on = i < filled
            Box(
                Modifier
                    .size(16.dp)
                    .background(if (on) color else Color.Transparent)
                    .then(
                        if (on) {
                            Modifier
                        } else {
                            Modifier.drawBehind {
                                drawRect(c.line, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                            }
                        }
                    )
            )
        }
    }
}

/** Égaliseur de 5 barres : l'écoute est active (aucun contenu audio n'est affiché). */
@Composable
fun EqBars() {
    val c = Cr.colors
    val t = stepTick(300)
    val levels = listOf(0.2f, 1f, 0.45f, 0.8f)
    Row(Modifier.height(24.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        repeat(5) { i -> Box(Modifier.width(6.dp).fillMaxHeight(levels[(t + i * 3) % 4]).background(c.lime)) }
    }
}

// ---------------------------------------------------------------------------
// Icônes en pixels (grilles 8 × 8 des maquettes)
// ---------------------------------------------------------------------------

enum class PixelIcon(val rows: String) {
    SHIELD("########/#......#/#......#/#......#/.#....#./.#....#./..#..#../...##..."),
    POWER("...##.../.#.##.#./#..##..#/#......#/#......#/.#....#./..####../........"),
    MIC("..####../..#..#../..#..#../#.#..#.#/#..##..#/.#....#./..####../...##..."),
    USERS("..####../..#..#../..####../...##.../.######./##....##/#......#/#......#"),
    ARROW_RIGHT("......../...#..../....#.../#######./....#.../...#..../......../........"),
    ALERT("...##.../..#..#../..#..#../.#.##.#./.#.##.#./#......#/#..##..#/########"),
    HOME("...##.../..#..#../.#....#./##....##/.#....#./.#.##.#./.#.##.#./.######."),
    PIN("..####../.#....#./.#.##.#./.#.##.#./.#....#./..#..#../...##.../........"),
    GEAR("...##.../#.####.#/.##..##./###..###/###..###/.##..##./#.####.#/...##..."),
    ARROW_LEFT("....#.../...#..../..#...../.#######/..#...../...#..../....#.../........"),
    CHECK("......../.......#/......##/#....##./##..##../.####.../..##..../........"),
    PHONE(".######./.#....#./.#....#./.#....#./.#....#./.#....#./.######./...##..."),
    CROSS("#......#/.#....#./..#..#../...##.../...##.../..#..#../.#....#./#......#"),
    LOCK("..####../.#....#./.#....#./########/##....##/##.##.##/##....##/########"),
    CLOCK("..####../.#....#./#..#...#/#..#...#/#..###.#/#......#/.#....#./..####..")
}

/** Icône décorative en pixels : le libellé voisin porte le sens (TalkBack). */
@Composable
fun PixelIconView(icon: PixelIcon, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val rows = remember(icon) { icon.rows.split('/') }
    Canvas(modifier.size(size)) {
        val cell = this.size.width / 8
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch -> if (ch == '#') drawRect(color, Offset(x * cell, y * cell), Size(cell, cell)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Lignes, en-têtes, navigation
// ---------------------------------------------------------------------------

/** Ligne de panneau : icône, étiquette grise, valeur en gras, indicateur à droite (lignes de l'accueil). */
@Composable
fun StatusRow(icon: PixelIcon, label: String, value: String, active: Boolean = false, trailing: @Composable RowScope.() -> Unit = {}) {
    val c = Cr.colors
    PxPanel(
        Modifier.fillMaxWidth(),
        border = if (active) c.lime else c.line,
        padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PixelIconView(icon, 24.dp, c.lime)
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), style = PxType.label, color = c.muted)
                Text(value.uppercase(), style = PxType.bodyBold)
            }
            trailing()
        }
    }
}

/** En-tête d'écran : retour (48 dp) et titre en pixels. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?) {
    val back = stringResource(R.string.back)
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = back },
                contentAlignment = Alignment.Center
            ) { PixelIconView(PixelIcon.ARROW_LEFT, 24.dp, Cr.colors.ink) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), style = PxType.title, modifier = Modifier.semantics { heading() })
    }
}

data class NavItem(val label: String, val icon: PixelIcon, val onClick: () -> Unit)

/** Barre de navigation : 4 onglets, l'onglet courant en lime avec un trait au-dessus. */
@Composable
fun PxNavBar(items: List<NavItem>, selected: Int) {
    val c = Cr.colors
    PxPanel(Modifier.fillMaxWidth().height(68.dp), fill = c.surface, padding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { i, item ->
                val active = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Tab, onClick = item.onClick)
                        .semantics { if (active) stateDescription = "sélectionné" },
                    contentAlignment = Alignment.Center
                ) {
                    if (active) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth(0.6f).height(4.dp).background(c.lime))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        PixelIconView(item.icon, 20.dp, if (active) c.lime else c.muted)
                        Text(item.label.uppercase(), style = PxType.label, color = if (active) c.lime else c.muted)
                    }
                }
            }
        }
    }
}

/** Ligne de journal « heure ······ événement ». */
@Composable
fun LeaderLine(time: String, text: String, color: Color) {
    val c = Cr.colors
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(time, style = PxType.caption, color = c.muted)
        Canvas(Modifier.weight(1f).height(10.dp)) {
            var x = 0f
            val dot = 2.dp.toPx()
            while (x < size.width) {
                drawRect(c.line, Offset(x, size.height - dot), Size(dot, dot))
                x += dot * 2
            }
        }
        Text(text.uppercase(), style = PxType.label, color = color, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 220.dp))
    }
}

/** Case à cocher carrée de 28 dp (zone tactile : toute la ligne). */
@Composable
fun PxCheck(checked: Boolean) {
    val c = Cr.colors
    Box(
        Modifier
            .size(28.dp)
            .background(if (checked) c.lime else c.surface)
            .drawBehind { if (!checked) drawRect(c.lineControl, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())) },
        contentAlignment = Alignment.Center
    ) { if (checked) PixelIconView(PixelIcon.CHECK, 20.dp, c.onLime) }
}

/** Écran d'affiche lime de l'onboarding : progression, titre géant, escalier en bas, bouton sombre. */
@Composable
fun Poster(
    step: Int,
    total: Int,
    icon: PixelIcon,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    onSkip: (() -> Unit)?,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    val c = Cr.colors
    Box(Modifier.fillMaxSize().background(c.lime)) {
        // Escalier décoratif (blocs forêt / forêt profonde).
        listOf(0, 60, 120, 180, 240, 300).forEachIndexed { i, left ->
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth(1f - left / 360f)
                    .height((24 * (i + 1)).dp)
                    .background(if (i % 2 == 0) c.forest else c.forestDeep)
            )
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f).semantics {
                        stateDescription =
                            "$step / $total"
                    }
                ) {
                    repeat(total) { i ->
                        Box(
                            Modifier
                                .size(24.dp, 12.dp)
                                .background(if (i < step) c.onLime else Color.Transparent)
                                .drawBehind { drawRect(c.onLime, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())) }
                        )
                    }
                }
                if (onSkip != null) {
                    Box(
                        Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onSkip).padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.action_skip).uppercase(), style = PxType.label, color = c.onLime)
                    }
                }
            }
            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                PxPanel(Modifier.width(96.dp), fill = c.surface) {
                    PixelIconView(icon, 48.dp, c.lime, Modifier.align(Alignment.Center))
                }
                Spacer(Modifier.height(24.dp))
                Text(title.uppercase(), style = PxType.poster, color = c.onLime, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(16.dp))
                Text(body, style = PxType.bodyBold, color = c.onLime)
                extra()
            }
            Spacer(Modifier.weight(1f))
            PxButton(action, onAction, Modifier.padding(24.dp), variant = PxVariant.DARK, icon = PixelIcon.ARROW_RIGHT)
        }
    }
}

/** Pavé PIN en touches crénelées de 64 dp. Le code n'est jamais affiché en clair. */
@Composable
fun PinPad(value: String, onChange: (String) -> Unit, maxLength: Int = 8) {
    val c = Cr.colors
    val delete = stringResource(R.string.pin_delete)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(16.dp)) {
            repeat(maxOf(4, value.length)) { i -> Box(Modifier.size(14.dp).background(if (i < value.length) c.lime else c.line)) }
        }
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(72.dp, 56.dp))
                    } else {
                        PxPanel(
                            Modifier
                                .size(72.dp, 56.dp)
                                .clickable(role = Role.Button) {
                                    if (key == "⌫") {
                                        onChange(value.dropLast(1))
                                    } else if (value.length < maxLength) {
                                        onChange(value + key)
                                    }
                                }
                                .semantics { if (key == "⌫") contentDescription = delete },
                            border = c.lineControl,
                            padding = PaddingValues(0.dp)
                        ) {
                            if (key == "⌫") {
                                PixelIconView(PixelIcon.ARROW_LEFT, 20.dp, c.ink, Modifier.align(Alignment.Center))
                            } else {
                                Text(key, style = PxType.title, modifier = Modifier.align(Alignment.Center))
                            }
                        }
                    }
                }
            }
        }
    }
}
