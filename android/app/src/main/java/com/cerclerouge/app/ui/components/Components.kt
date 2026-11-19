package com.cerclerouge.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.cerclerouge.app.R
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.valentinerutto.orbmotion.orbs.AnimatedThinkingOrb
import com.valentinerutto.orbmotion.orbs.OrbState
import kotlin.math.hypot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Rouges officiels de la charte (« Charte graphique & identité visuelle ») : Signal vif au cœur, Grenat à l'extérieur. */
val LogoRed = Color(0xFFE01E1E)
val LogoDeep = Color(0xFFA01515)

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

/** Horloge à pas fixes. Retourne 0 si animations réduites. */
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

/** Valeur 0 → 1 qui boucle (animation continue), figée à `still` si animations réduites. */
@Composable
fun loopProgress(periodMs: Int, enabled: Boolean = true, still: Float = 0f): Float {
    if (reduceMotion() || !enabled) return still
    val t = rememberInfiniteTransition(label = "loop")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "p")
    return v
}

// ---------------------------------------------------------------------------
// Formes (anciennes formes « pixel » gardées pour compatibilité)
// ---------------------------------------------------------------------------

/** Coins crénelés (design précédent) ; plus utilisé par les composants « Verre ». */
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

/** Cercle en escalier (design précédent). */
fun pixelCircle(cells: Int): Shape = GenericShape { size, _ ->
    val cell = size.width / cells
    val r = cells / 2f
    for (row in 0 until cells) {
        val cols = (0 until cells).filter { col -> hypot(col + 0.5f - r, row + 0.5f - r) <= r + 0.15f }
        if (cols.isNotEmpty()) addRect(Rect(cols.first() * cell, row * cell, (cols.last() + 1) * cell, (row + 1) * cell))
    }
}

/** Le fond quadrillé du design précédent est retiré : le fond « Verre » est uni. */
@Suppress("UnusedParameter")
fun Modifier.pixelGrid(color: Color): Modifier = this

// ---------------------------------------------------------------------------
// Cartes et boutons
// ---------------------------------------------------------------------------

/**
 * Carte « Verre » : blanche, rayon 22, liseré à peine visible. Une `border` autre que le liseré par défaut
 * (état actif, alerte) devient un contour de 1,5 dp de cette couleur.
 */
@Composable
fun PxPanel(
    modifier: Modifier = Modifier,
    border: Color = Cr.colors.line,
    fill: Color = Cr.colors.raised,
    padding: PaddingValues = PaddingValues(14.dp),
    radius: Dp = 22.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val c = Cr.colors
    val shape = RoundedCornerShape(radius)
    val stroke = if (border == c.line) BorderStroke(1.dp, c.line) else BorderStroke(1.5.dp, border)
    Box(
        modifier
            .clip(shape)
            .background(fill)
            .border(stroke, shape)
            .padding(padding),
        content = content
    )
}

enum class PxVariant { LIME, DANGER, DARK, DARK_DANGER, OUTLINE }

/**
 * Bouton pilule (référence « Verre ») : 52 dp (60 dp pour les actions critiques).
 * LIME = principal (encre), DANGER = rouge, DARK = gris, DARK_DANGER = contour rouge, OUTLINE = contour encre.
 */
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(50)
    val ink = when (variant) {
        PxVariant.LIME -> c.raised
        PxVariant.DANGER -> Color.White
        PxVariant.DARK, PxVariant.OUTLINE -> c.ink
        PxVariant.DARK_DANGER -> c.danger
    }
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = if (tall) 60.dp else 52.dp)
        .scale(if (pressed && enabled) 0.97f else 1f)
        .clip(shape)
    val filled = when (variant) {
        PxVariant.LIME -> base.background(c.ink)
        PxVariant.DANGER -> base.background(Brush.verticalGradient(listOf(LogoRed, LogoDeep)))
        PxVariant.DARK -> base.background(c.forestDeep)
        PxVariant.DARK_DANGER -> base.border(2.dp, c.danger, shape)
        PxVariant.OUTLINE -> base.border(2.dp, c.ink, shape)
    }
    Row(
        filled
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .then(if (enabled) Modifier else Modifier.background(c.surface.copy(alpha = 0.5f))),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            PixelIconView(it, 20.dp, if (enabled) ink else c.muted)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = PxType.button, color = if (enabled) ink else c.muted, textAlign = TextAlign.Center)
    }
}

/** Dégradé rouge du SOS. */
val SosGradient = Brush.verticalGradient(listOf(LogoRed, LogoDeep))

/**
 * Bouton SOS : appui long de 1,5 s (le bouton se remplit), puis vibration et compte à rebours (R-HOME-01).
 * Un appui court ouvre l'écran SOS. TalkBack : l'action « activer » déclenche directement.
 * `shape` permet de le fondre dans le bas d'une carte (coins du bas seulement).
 */
@Composable
fun SosBar(
    onTrigger: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    minHeight: Dp = 60.dp,
    hint: Boolean = true,
    labelSize: androidx.compose.ui.unit.TextUnit = 18.sp
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val a11y = stringResource(R.string.sos_a11y)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(SosGradient)
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
        contentAlignment = Alignment.Center
    ) {
        // Remplissage pendant l'appui (plus sombre), de gauche à droite.
        Box(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(Color.Black.copy(alpha = 0.22f)))
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.sos),
                style = V.text(labelSize, FontWeight.Bold, spacing = 0.055f),
                color = Color.White
            )
            if (hint) Spacer(Modifier.width(10.dp))
            if (hint) Text(stringResource(R.string.sos_hint_hold), style = PxType.caption, color = Color(0xFFFFE1E3))
        }
    }
}

// ---------------------------------------------------------------------------
// Disque, indicateurs
// ---------------------------------------------------------------------------

/** Disque plein (SOS) avec halo qui pulse quand `glow`. */
@Composable
fun PixelDisc(
    size: Dp,
    @Suppress("UnusedParameter") cells: Int,
    fill: Color,
    modifier: Modifier = Modifier,
    glow: Color? = null,
    @Suppress("UnusedParameter") orbit: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val pulse = loopProgress(1800, enabled = glow != null)
    Box(modifier.size(size + 32.dp), contentAlignment = Alignment.Center) {
        if (glow != null) {
            Canvas(Modifier.size(size + 32.dp)) {
                val r = this.size.minDimension / 2
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.35f), Color.Transparent), radius = r))
                drawCircle(glow.copy(alpha = 0.5f * (1f - pulse)), radius = r * (0.78f + 0.22f * pulse), style = Stroke(3.dp.toPx()))
            }
        }
        Column(
            Modifier.size(size).shadow(16.dp, CircleShape, spotColor = fill).clip(CircleShape).background(fill),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/** Voyant rond de 8 dp, qui clignote (1 s) quand `blink`. */
@Composable
fun Led(color: Color, blink: Boolean, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val on = stepTick(500, enabled = blink) % 2 == 0
    Box(modifier.size(size).clip(CircleShape).background(if (on) color else Color.Transparent))
}

// ---------------------------------------------------------------------------
// Icônes au trait (24 × 24, trait 1,9) — l'énumération garde les noms du design précédent
// ---------------------------------------------------------------------------

enum class PixelIcon(val path: String) {
    SHIELD("M12 3l8 3v6c0 4.5-3.4 8.4-8 9-4.6-.6-8-4.5-8-9V6z M8.5 12l2.5 2.5 4.5-5"),
    POWER("M12 3v8 M6.3 7.2a8 8 0 1 0 11.4 0"),
    MIC("M9 5a3 3 0 0 1 6 0v6a3 3 0 0 1-6 0z M19 10v1a7 7 0 0 1-14 0v-1 M12 18v4"),
    USERS("M9 11.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z M2.5 20a6.5 6.5 0 0 1 13 0 M16 4.5a3.5 3.5 0 0 1 0 7 M18.5 20a6.5 6.5 0 0 0-3-5.5"),
    ARROW_RIGHT("M5 12h14 M13 6l6 6-6 6"),
    ALERT("M12 3.5l9.2 16.5H2.8z M12 10v4.2 M12 17.4v.1"),
    HOME("M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"),
    PIN("M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z M12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z"),
    GEAR(
        "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M12 2.5v2.5 M12 19v2.5 M4.6 4.6l1.8 1.8 M17.6 17.6l1.8 1.8 M2.5 12H5 M19 12h2.5 " +
            "M4.6 19.4l1.8-1.8 M17.6 6.4l1.8-1.8"
    ),
    ARROW_LEFT("M15 18l-6-6 6-6"),
    CHECK("M5 12.5l4.5 4.5L19 7.5"),
    PHONE(
        "M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7" +
            "c.1.9.4 1.8.7 2.7a2 2 0 0 1-.5 2.1L8 9.8a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.7.7a2 2 0 0 1 1.7 2z"
    ),
    CROSS("M6 6l12 12 M18 6L6 18"),
    LOCK("M6 10h12a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z M8 10V7a4 4 0 0 1 8 0v3"),
    CLOCK("M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 7v5l3 2"),
    UP("M12 19V5 M6 11l6-6 6 6"),
    DOWN("M12 5v14 M6 13l6 6 6-6"),
    STAR("M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z"),
    PLUS("M12 5v14 M5 12h14"),
    BACKSPACE("M21 5H9l-6 7 6 7h12a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1z M17 9l-6 6 M11 9l6 6"),
    ROUTE(
        "M6 21.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z M18 7.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z M8.5 19H15a3.5 3.5 0 0 0 0-7H9a3.5 3.5 0 0 1 0-7h6.5"
    ),
    COG(
        "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1" +
            "a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3" +
            "l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1" +
            "a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9" +
            "a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1" +
            "a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"
    ),
    SEND("M22 2L11 13 M22 2l-7 20-4-9-9-4z"),
    FINGER("M7 11V8a5 5 0 0 1 10 0v3 M12 14v3 M5 11h14v9H5z")
}

/** Icône au trait (décorative : le libellé voisin porte le sens pour TalkBack). */
@Composable
fun PixelIconView(icon: PixelIcon, size: Dp, color: Color, modifier: Modifier = Modifier, strokeWidth: Float = 1.9f) {
    val path = remember(icon) { PathParser().parsePathString(icon.path).toPath() }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// ---------------------------------------------------------------------------
// Lignes, en-têtes, navigation
// ---------------------------------------------------------------------------

/** Ligne de carte : icône, libellé gris, valeur, indicateur à droite. */
@Composable
fun StatusRow(icon: PixelIcon, label: String, value: String, active: Boolean = false, trailing: @Composable RowScope.() -> Unit = {}) {
    val c = Cr.colors
    PxPanel(
        Modifier.fillMaxWidth(),
        border = if (active) c.lime else c.line,
        padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PixelIconView(icon, 24.dp, if (active) c.lime else c.ink)
            Column(Modifier.weight(1f)) {
                Text(label, style = PxType.caption, color = c.muted)
                Text(value, style = PxType.bodyBold)
            }
            trailing()
        }
    }
}

/** En-tête d'écran : retour (48 dp, chevron) et titre. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?) {
    val back = stringResource(R.string.back)
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 6.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = back },
                contentAlignment = Alignment.Center
            ) { PixelIconView(PixelIcon.ARROW_LEFT, 26.dp, Cr.colors.ink, strokeWidth = 2.2f) }
        } else {
            Spacer(Modifier.width(10.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = PxType.display, modifier = Modifier.semantics { heading() })
    }
}

/**
 * Texte court et non essentiel (onglets, légendes de carte) : suit la police d'Android jusqu'à 130 % seulement, pour ne pas
 * couper un mot ni masquer la carte à 200 %. Le texte courant, lui, suit jusqu'à 200 % (R-UI). TalkBack lit tout.
 */
@Composable
fun cappedScale(style: androidx.compose.ui.text.TextStyle, max: Float = 1.3f): androidx.compose.ui.text.TextStyle {
    val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    if (scale <= max) return style
    val k = max / scale
    return style.copy(
        fontSize = if (style.fontSize.isSpecified) style.fontSize * k else style.fontSize,
        lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * k else style.lineHeight
    )
}

data class NavItem(val label: String, val icon: PixelIcon, val onClick: () -> Unit)

/**
 * Barre du bas « Verre » : blanche, icônes au trait, onglet courant en encre et gras.
 * `center` : bouton rond du milieu (activation de la protection), qui dépasse au-dessus de la barre.
 */
@Composable
fun PxNavBar(items: List<NavItem>, selected: Int, center: (@Composable () -> Unit)? = null, insets: Boolean = true) {
    val c = Cr.colors
    val half = items.size / 2
    Box(Modifier.fillMaxWidth()) {
        // Maquette : barre blanche pleine largeur, 86 de haut, 5 colonnes, onglets de 64 alignés en haut (marge 6).
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.raised)
                .then(if (insets) Modifier.navigationBarsPadding() else Modifier)
                .heightIn(min = 86.dp)
                .padding(start = 4.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            items.forEachIndexed { i, item ->
                if (center != null && i == half) Spacer(Modifier.weight(1f))
                val active = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Tab, onClick = item.onClick)
                        .semantics { if (active) stateDescription = "sélectionné" }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                ) {
                    PixelIconView(
                        item.icon,
                        26.dp,
                        if (active) {
                            c.ink
                        } else {
                            Color(0xFF93A5B6).takeIf { c.surface == Color(0xFFEDEDF0) }
                                ?: c.lineControl
                        },
                        strokeWidth = if (active) 2.4f else 1.6f
                    )
                    Text(
                        item.label,
                        style = cappedScale(V.text(13.sp, if (active) FontWeight.Bold else FontWeight.Normal)),
                        color = if (active) c.ink else c.muted,
                        maxLines = 1
                    )
                }
            }
        }
        if (center != null) Box(Modifier.align(Alignment.TopCenter).offset(y = (-24).dp)) { center() }
    }
}

/** Case à cocher arrondie de 26 dp (zone tactile : toute la ligne). */
@Composable
fun PxCheck(checked: Boolean) {
    val c = Cr.colors
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) c.lime else c.raised)
            .border(if (checked) 0.dp else 2.dp, c.lineControl, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) { if (checked) PixelIconView(PixelIcon.CHECK, 18.dp, c.onLime, strokeWidth = 2.8f) }
}

/**
 * Écran d'onboarding « Verre » : grande illustration dans une carte, titre, texte, points d'étape et bouton sombre.
 */
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
    Column(Modifier.fillMaxSize().background(c.surface)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (onSkip != null) {
                Box(
                    Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onSkip)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.action_skip),
                        style = PxType.button.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        color = c.muted
                    )
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(220.dp)
                    .shadow(24.dp, RoundedCornerShape(44.dp), ambientColor = Color(0x331F2A33), spotColor = Color(0x331F2A33))
                    .clip(RoundedCornerShape(44.dp))
                    .background(Brush.verticalGradient(listOf(c.raised, c.surface.copy(alpha = 0.6f)))),
                contentAlignment = Alignment.Center
            ) { PosterArt(icon) }
            Spacer(Modifier.height(32.dp))
            Text(
                title,
                style = PxType.poster,
                color = c.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(12.dp))
            Text(body, style = PxType.body.copy(fontSize = PxType.body.fontSize * 1.1f), color = c.muted, textAlign = TextAlign.Center)
            extra()
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 18.dp).semantics { stateDescription = "$step / $total" },
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            repeat(total) { i -> Box(Modifier.size(9.dp).clip(CircleShape).background(if (i == step - 1) c.ink else c.lineControl)) }
        }
        PxButton(action, onAction, Modifier.padding(start = 18.dp, end = 18.dp, bottom = 24.dp), tall = true)
    }
}

/** Illustration d'une affiche : bouclier 3D pour le bouclier, icône en verre sinon. */
@Composable
private fun PosterArt(icon: PixelIcon) {
    when (icon) {
        PixelIcon.SHIELD -> Shield3D(on = true, height = 150.dp)
        else -> GlassIcon(glassFor(icon), 132.dp)
    }
}

/** Pavé PIN : touches rondes blanches de 72 dp. Le code n'est jamais affiché en clair. */
@Composable
fun PinPad(value: String, onChange: (String) -> Unit, maxLength: Int = 8) {
    val c = Cr.colors
    val delete = stringResource(R.string.pin_delete)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(16.dp)) {
            repeat(maxOf(4, value.length)) { i ->
                Box(Modifier.size(13.dp).clip(CircleShape).background(if (i < value.length) c.lime else c.line))
            }
        }
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(68.dp))
                    } else {
                        Box(
                            Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(if (key == "⌫") Color.Transparent else c.forestDeep)
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
                            if (key == "⌫") {
                                PixelIconView(PixelIcon.ARROW_LEFT, 24.dp, c.ink, strokeWidth = 2.2f)
                            } else {
                                Text(key, style = PxType.display)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Orbe à points (bibliothèque OrbMotion) : activité en cours (envoi, attente, démarrage).
 * Décorative pour TalkBack ; figée si « réduire les animations ».
 */
@Composable
fun Orb(state: OrbState, size: Dp, color: Color, modifier: Modifier = Modifier, speed: Float = 1f) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.toPx() }
    AnimatedThinkingOrb(
        modifier = modifier.requiredSize(size).clearAndSetSemantics {},
        state = state,
        size = px,
        speed = if (reduceMotion()) 0f else speed,
        color = color
    )
}

/** Courbe des animations du logo (charte, `cercle-rouge-motion-reveal.svg`). */
private val BrandEase = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

private fun phase(t: Float, from: Float, to: Float) = BrandEase.transform(((t - from) / (to - from)).coerceIn(0f, 1f))

/**
 * Emblème officiel, construit sur la grille 100 × 100 de la charte : arc protecteur extérieur (r 39, trait 8,5, Grenat),
 * arc de veille (r 25,5, trait 7,5, Signal), noyau (r 10,5, Signal). Les deux arcs restent **ouverts vers la droite**
 * (partage sélectif) : on ne les fait jamais tourner. `reveal` (0 → 1) rejoue l'apparition de la charte : le noyau, puis
 * l'arc de veille, puis l'arc protecteur. `animated` : le noyau bat (bouton d'activation). Décoratif pour TalkBack.
 */
@Composable
fun LogoMark(
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = LogoRed,
    deep: Color = if (color == LogoRed) LogoDeep else color,
    animated: Boolean = false,
    reveal: Float = 1f
) {
    val beat = loopProgress(2400, animated)
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val k = this.size.minDimension / 100f
        val c = Offset(this.size.width / 2, this.size.height / 2)
        fun arc(r: Float, w: Float, start: Float, sweep: Float, col: Color, t: Float) {
            if (t <= 0f) return
            drawArc(
                col,
                start,
                sweep * t,
                false,
                Offset(c.x - r * k, c.y - r * k),
                Size(2 * r * k, 2 * r * k),
                style = Stroke(w * k, cap = StrokeCap.Round)
            )
        }
        // Tracé depuis l'extrémité haute, dans le sens inverse des aiguilles d'une montre (comme le SVG de la charte).
        arc(39f, 8.5f, -40.4f, -279.2f, deep, phase(reveal, 0.31f, 0.82f))
        arc(25.5f, 7.5f, -46.1f, -267.8f, color, phase(reveal, 0.165f, 0.65f))
        val dot = phase(reveal, 0f, 0.26f)
        if (animated && beat < 0.25f) drawCircle(color.copy(alpha = 0.35f * (1f - beat / 0.25f)), (10.5f + 9f * beat / 0.25f) * k, c)
        if (dot > 0f) drawCircle(color, 10.5f * k * dot, c)
    }
}

/** Avancée des mots du logo pendant l'apparition (« Cercle » puis « Rouge »), 0 → 1. */
fun wordReveal(reveal: Float, second: Boolean) = if (second) phase(reveal, 0.66f, 0.96f) else phase(reveal, 0.56f, 0.89f)
