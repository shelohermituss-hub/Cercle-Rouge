package com.cerclerouge.app.ui.components

import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
                    cubicTo(p(designW * 0.2f, designH * 0.84f).x, p(0f, designH * 0.84f).y, p(designW * 0.3f, designH).x, size.height, p(designW * 0.34f, designH + 5).x, size.height + 5f)
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
                        cubicTo(p(designW * 0.3f, designH * 0.27f).x, p(0f, designH * 0.27f).y, p(designW * 0.6f, designH * 0.2f).x, p(0f, designH * 0.2f).y, p(designW + 10, designH * 0.12f).x, p(0f, designH * 0.12f).y)
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

/** Démarrage (maquette VerreSplash) : fond blanc, logo animé de 200, logo officiel 240 × 45, barre de chargement et mention. */
@Composable
fun Splash() {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        androidx.compose.foundation.layout.Column(
            Modifier.align(androidx.compose.ui.Alignment.Center),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(22.dp)
        ) {
            LogoGlossy(200.dp)
            com.cerclerouge.app.ui.home.Wordmark(height = 45.dp)
        }
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


/**
 * Logo en relief du démarrage (maquette VerreSplash, 200 × 200) : deux anneaux en dégradé rouge qui tournent en sens
 * contraire (26 s et 17 s), reflets blancs, quatre points, cœur rouge brillant qui bat, ombre portée.
 */
@Composable
fun LogoGlossy(size: Dp, animated: Boolean = true) {
    val outerTurn = loopProgress(26_000, animated) * 360f
    val innerTurn = -loopProgress(17_000, animated) * 360f
    val beat = loopProgress(2600, animated)
    Canvas(Modifier.size(size)) {
        val k = this.size.minDimension / 200f
        fun o(x: Float, y: Float) = Offset(x * k, y * k)
        // Ombre au sol.
        drawOval(
            Brush.radialGradient(listOf(Color(0x381F2A33), Color(0x001F2A33)), o(104f, 182f), 64f * k),
            topLeft = o(40f, 172f),
            size = androidx.compose.ui.geometry.Size(128f * k, 20f * k)
        )
        val c = o(100f, 100f)
        val outer = Brush.linearGradient(listOf(Color(0xFFF04A52), Color(0xFF8E0E14)), o(28f, 28f), o(172f, 172f))
        val inner = Brush.linearGradient(listOf(Color(0xFFFF7A80), Color(0xFFD3141C)), o(55f, 55f), o(145f, 145f))
        fun ring(r: Float, w: Float, start: Float, sweep: Float, brush: Brush, dots: List<Pair<Float, Float>>, dotR: Float, shine: Float, turn: Float) {
            rotate(turn, c) {
                drawArc(brush, start, sweep, false, o(100f - r, 100f - r), androidx.compose.ui.geometry.Size(2 * r * k, 2 * r * k), style = Stroke(w * k, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                dots.forEach { (x, y) -> drawCircle(brush, dotR * k, o(x, y)) }
                val rr = r - 2f
                drawArc(Color.White.copy(alpha = shine), 222f, 74f, false, o(100f - rr, 100f - rr), androidx.compose.ui.geometry.Size(2 * rr * k, 2 * rr * k), style = Stroke(4f * k, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }
        ring(72f, 18f, 40f, 280f, outer, listOf(164.2f to 132.7f, 164.2f to 67.3f), 8f, 0.55f, outerTurn)
        ring(45f, 17f, 46f, 268f, inner, listOf(140.4f to 119.7f, 140.4f to 80.3f), 7.5f, 0.6f, innerTurn)
        // Cœur : halo qui bat, sphère en dégradé et reflet.
        val b = (beat * 5f).coerceAtMost(1f)
        drawCircle(Color(0xFFE31B23).copy(alpha = 0.35f * (1f - b)), 19f * k * (1f + 0.6f * b), c)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8A90), Color(0xFFC5121A)), o(90f, 90f), 22f * k), 16f * k, c)
        drawCircle(Color.White.copy(alpha = 0.7f), 5f * k, o(95f, 94f))
    }
}
