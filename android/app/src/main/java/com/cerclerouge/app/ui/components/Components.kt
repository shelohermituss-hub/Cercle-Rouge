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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerclerouge.app.R
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.Display
import com.cerclerouge.app.ui.theme.HudType
import kotlinx.coroutines.launch

/** Vrai si l'utilisateur a demandé de réduire les animations (R-UI-05). */
@Composable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Bouton SOS : appui long de 1,5 s avec anneau de progression `brand-rouge`, puis vibration (R-HOME-01).
 * Relâcher avant la fin annule. TalkBack : l'action « activer » déclenche directement (l'appui long n'est pas accessible).
 */
@Composable
fun SosButton(onTrigger: () -> Unit, modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val c = Cr.colors
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val a11y = stringResource(R.string.sos_a11y)
    Box(
        modifier
            .size(size + 16.dp)
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
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(1500, easing = LinearEasing))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTrigger()
                    }
                    waitForUpOrCancellation()
                    if (progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(180)) }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size + 16.dp)) {
            val stroke = 4.dp.toPx()
            drawArc(c.lineGrid, 0f, 360f, false, style = Stroke(stroke))
            drawArc(
                c.brandRouge,
                -90f,
                360f * progress.value,
                false,
                style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(c.brandRouge),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.sos),
                color = c.onBrand,
                fontFamily = Display,
                fontSize = if (size >= 120.dp) 28.sp else 22.sp,
                letterSpacing = 1.5.sp
            )
        }
    }
}

enum class ChipTone { OFF, PROTECTED, VIGILANCE, ALERT }

/** Puce d'état : couleur + point + libellé, jamais la couleur seule. */
@Composable
fun StatusChip(text: String, tone: ChipTone, pulse: Boolean = false) {
    val c = Cr.colors
    val color = when (tone) {
        ChipTone.OFF -> c.inactif
        ChipTone.PROTECTED -> c.protege
        ChipTone.VIGILANCE -> c.vigilance
        ChipTone.ALERT -> c.alerte
    }
    val alpha = if (pulse && !reduceMotion()) {
        val t = rememberInfiniteTransition(label = "pulse")
        val a by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
        a
    } else {
        1f
    }
    Row(
        Modifier
            .border(BorderStroke(1.dp, color), RoundedCornerShape(8.dp))
            .background(c.bgSurface, RoundedCornerShape(8.dp))
            .heightIn(min = 36.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha))
        )
        Text(text.uppercase(), style = HudType.label, color = color)
    }
}

/** Radar d'accueil : anneaux fins ; balayage uniquement quand l'écoute est active (docs/design). */
@Composable
fun Radar(active: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val c = Cr.colors
    val sweep = if (active && !reduceMotion()) {
        val t = rememberInfiniteTransition(label = "radar")
        val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "angle")
        angle
    } else {
        null
    }
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        listOf(1f, 0.75f, 0.55f).forEachIndexed { i, f ->
            val color = if (active && i == 1) c.protege.copy(alpha = 0.45f) else c.lineGrid
            drawCircle(color, radius = r * f - 1f, style = Stroke(1.dp.toPx()))
        }
        if (sweep != null) drawArc(c.protege.copy(alpha = 0.12f), sweep - 90f, 60f, useCenter = true)
    }
}

/** Bouton rond « satellite » autour du radar, 48 dp, avec libellé. */
@Composable
fun Satellite(label: String, highlighted: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val c = Cr.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(112.dp)) {
        OutlinedButton(
            onClick = onClick,
            shape = CircleShape,
            modifier = Modifier.size(48.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            border = BorderStroke(1.dp, if (highlighted) c.protege else c.lineControl),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = c.bgSurface,
                contentColor = if (highlighted) c.protege else c.textPrimary
            )
        ) { icon() }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Cr.colors.protege
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Cr.colors.onState)
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Bouton SOS rectangulaire pour les écrans sans radar (onboarding, attente) : toujours en `brand-rouge`. */
@Composable
fun SosBarButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Cr.colors.brandRouge, contentColor = Cr.colors.onBrand)
    ) { Text(stringResource(R.string.sos), fontFamily = Display, fontSize = 20.sp, letterSpacing = 1.5.sp) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Cr.colors.lineControl),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cr.colors.textPrimary)
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Pavé PIN de 4 à 8 chiffres. Le code n'est jamais affiché en clair. */
@Composable
fun PinPad(value: String, onChange: (String) -> Unit, maxLength: Int = 8) {
    val c = Cr.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(16.dp)) {
            repeat(maxOf(4, value.length)) { i ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (i < value.length) c.textPrimary else c.lineBorder)
                )
            }
        }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))
        val delete = stringResource(R.string.pin_delete)
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(72.dp))
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (key == "⌫") {
                                    onChange(value.dropLast(1))
                                } else if (value.length < maxLength) {
                                    onChange(value + key)
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .size(72.dp)
                                .semantics { if (key == "⌫") contentDescription = delete },
                            border = BorderStroke(1.dp, c.lineControl),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary)
                        ) { Text(key, style = HudType.md) }
                    }
                }
            }
        }
    }
}
