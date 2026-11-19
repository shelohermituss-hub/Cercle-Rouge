package com.cerclerouge.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Plan clair dessiné des maquettes (« Verre ») : fond #EEF0F3, rues fines inclinées, trois grands axes blancs, eau, trajet
 * bleu en pointillés qui avance, et la position (point bleu, halo). Sert d'illustration (onboarding) et de repli quand la
 * vraie carte n'est pas disponible. Les coordonnées sont celles de la maquette (`w` × `h` px), mises à l'échelle.
 */
@Composable
fun SketchMap(
    modifier: Modifier,
    route: List<Offset> = DEFAULT_ROUTE,
    me: Offset? = route.firstOrNull(),
    animate: Boolean = true,
    designW: Float = 175f,
    designH: Float = 146f,
    roadWidth: Float = 6f,
    routeWidth: Float = 1.8f
) {
    val march = loopProgress(1400, animate)
    val ping = loopProgress(2000, animate && me != null)
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val sx = size.width / designW
            val sy = size.height / designH
            fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
            drawRect(Color(0xFFEEF0F3))
            // Eau, en bas à gauche.
            drawPath(
                Path().apply {
                    moveTo(0f, designH * 0.86f * sy)
                    cubicTo(
                        p(designW * 0.2f, designH * 0.84f).x,
                        p(0f, designH * 0.84f).y,
                        p(designW * 0.3f, designH).x,
                        size.height,
                        p(
                            designW * 0.34f,
                            designH + 5
                        ).x,
                        size.height + 5f
                    )
                    lineTo(0f, size.height + 5f)
                    close()
                },
                Color(0xFFD6E6F2)
            )
            // Petites rues.
            val step = 21f
            var x = -20f
            while (x < designW + 80f) {
                drawLine(Color(0xFFDCE1E6), p(x, -10f), p(x + 18f, designH + 10f), 1.2f * sx)
                x += step
            }
            var y = 0f
            while (y < designH + 60f) {
                drawLine(Color(0xFFDCE1E6), p(-10f, y), p(designW + 10f, y - 10f), 1.2f * sx)
                y += 18f
            }
            // Grands axes : liseré gris puis blanc.
            fun axes(color: Color, w: Float) {
                drawPath(
                    Path().apply {
                        moveTo(p(-10f, designH * 0.30f).x, p(-10f, designH * 0.30f).y)
                        cubicTo(
                            p(designW * 0.3f, designH * 0.27f).x,
                            p(0f, designH * 0.27f).y,
                            p(designW * 0.6f, designH * 0.2f).x,
                            p(
                                0f,
                                designH * 0.2f
                            ).y,
                            p(designW + 10, designH * 0.12f).x,
                            p(0f, designH * 0.12f).y
                        )
                    },
                    color,
                    style = Stroke(w * sx)
                )
                drawLine(color, p(designW * 0.62f, -10f), p(designW * 0.78f, designH + 10f), w * sx)
                drawLine(color, p(-10f, designH * 0.78f), p(designW + 10f, designH * 0.66f), w * sx)
            }
            axes(Color(0xFFD0D6DC), roadWidth)
            axes(Color.White, roadWidth * 0.66f)
            if (route.size >= 2) {
                val path = Path().apply {
                    moveTo(route[0].x * sx, route[0].y * sy)
                    route.drop(1).forEach { lineTo(it.x * sx, it.y * sy) }
                }
                drawPath(
                    path,
                    Color(0xFF2F86F6),
                    style = Stroke(
                        routeWidth * sx,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * sx, 4f * sx), -22f * sx * march)
                    )
                )
                route.dropLast(1).forEach { drawStop(it.x * sx, it.y * sy, 3.1f * sx) }
            }
            me?.let { m ->
                val c = Offset(m.x * sx, m.y * sy)
                drawCircle(Brush.radialGradient(listOf(Color(0x662F86F6), Color(0x002F86F6)), c, 35f * sx), 35f * sx, c)
                drawCircle(Color(0x992F86F6), 9f * sx * (0.5f + 2.1f * ping), c, alpha = 1f - ping, style = Stroke(2f * sx))
                drawCircle(Color.White, 11f * sx, c)
                drawCircle(Color(0xFF7DB3FA), 8f * sx, c)
            }
        }
    }
}

private fun DrawScope.drawStop(x: Float, y: Float, r: Float) {
    drawCircle(Color.White, r + 1.5f, Offset(x, y))
    drawCircle(Color(0xFF2F86F6), r, Offset(x, y))
}

/** Trajet de la carte de l'accueil (maquette 175 × 146). */
val DEFAULT_ROUTE = listOf(Offset(38f, 61f), Offset(74f, 44f), Offset(112f, 53f), Offset(94f, 76f), Offset(116f, 117f))

/** Trajet de l'onboarding 3 (maquette 300 × 460). */
val ONB_ROUTE = listOf(Offset(66f, 193f), Offset(126f, 138f), Offset(192f, 166f), Offset(162f, 239f), Offset(198f, 368f))

/** Trajet de l'écran « Trajet en cours » (maquette 390 × 760). */
val TRIP_ROUTE = listOf(Offset(86f, 311f), Offset(164f, 244f), Offset(250f, 278f), Offset(211f, 367f), Offset(258f, 520f))

/**
 * « Téléphone » de l'onboarding : carte 300 × 460, rayon 44, dégradé blanc → #F1F4F7, ombre longue, marges 26 / 16.
 */
@Composable
fun PhoneMock(modifier: Modifier = Modifier, width: Dp = 300.dp, height: Dp = 460.dp, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(44.dp)
    Box(
        modifier
            .size(width, height)
            .shadow(24.dp, shape, ambientColor = Color(0x591F2A33), spotColor = Color(0x591F2A33))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F4F7))))
            .border(1.dp, Color.White, shape),
        content = content
    )
}

/** Point de position seul (repère « maison » sur les plans). */
@Composable
fun MapPin(x: Dp, y: Dp, size: Dp = 22.dp) {
    Box(
        Modifier.offset(x, y).size(size).drawBehind { drawCircle(Color(0x552F86F6), this.size.minDimension) }.clip(CircleShape)
            .background(Color(0xFF7DB3FA)).border(3.dp, Color.White, CircleShape)
    )
}

/**
 * Démarrage : fond blanc, apparition officielle du logo (charte, `cercle-rouge-motion-reveal.svg`) : le noyau, l'arc de
 * veille, l'arc protecteur, puis « Cercle » et « Rouge ». Joué une fois (1,5 s) ; logo complet d'emblée si les animations
 * sont réduites. Barre de chargement et mention de la maquette VerreSplash.
 */
@Composable
fun Splash() {
    val still = reduceMotion()
    val reveal = remember { androidx.compose.animation.core.Animatable(if (still) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        reveal.animateTo(1f, androidx.compose.animation.core.tween(1530, easing = androidx.compose.animation.core.LinearEasing))
    }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        com.cerclerouge.app.ui.home.Wordmark(Modifier.align(androidx.compose.ui.Alignment.Center), height = 56.dp, reveal = reveal.value)
        val p = loopProgress(1600)
        androidx.compose.foundation.layout.Column(
            Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 60.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(120.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD3DDE6))) {
                Box(Modifier.size(120.dp * (0.2f + 0.8f * p), 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF2F86F6)))
            }
            androidx.compose.material3.Text(
                androidx.compose.ui.res.stringResource(com.cerclerouge.app.R.string.v_splash_ready),
                style = V.mono,
                color = Color(0xFF66788A)
            )
        }
    }
}
