package com.cerclerouge.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cerclerouge.app.R
import kotlin.math.PI
import kotlin.math.sin

/** Icône en verre (assets du porteur, docs/design/systeme-verre.md). Décorative : le texte voisin porte le sens. */
@Composable
fun GlassIcon(@DrawableRes res: Int, size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(res), contentDescription = null, modifier = modifier.size(size))
}

/** Icône en verre la plus proche d'une icône au trait (illustrations de l'onboarding, lignes de liste). */
@DrawableRes
fun glassFor(icon: PixelIcon): Int = when (icon) {
    PixelIcon.SHIELD -> R.drawable.glass_shield
    PixelIcon.MIC -> R.drawable.glass_volume
    PixelIcon.USERS -> R.drawable.glass_proches
    PixelIcon.ARROW_RIGHT -> R.drawable.glass_trajet
    PixelIcon.ALERT -> R.drawable.glass_warning
    PixelIcon.HOME -> R.drawable.glass_home
    PixelIcon.PIN -> R.drawable.glass_pin
    PixelIcon.GEAR -> R.drawable.glass_reglages
    PixelIcon.CHECK -> R.drawable.glass_check
    PixelIcon.PHONE -> R.drawable.glass_firstaid
    PixelIcon.LOCK -> R.drawable.glass_lock
    PixelIcon.CLOCK -> R.drawable.glass_clock
    PixelIcon.STAR -> R.drawable.glass_heart
    else -> R.drawable.glass_terminal
}

private val Grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Bouclier 3D violet (carte du haut de l'accueil) : flotte doucement ; grisé quand la protection est coupée. */
@Composable
fun Shield3D(on: Boolean, height: Dp, modifier: Modifier = Modifier) {
    val t = loopProgress(3200, enabled = on)
    val lift = (sin(t * 2 * PI) * 4).dp
    Image(
        painterResource(R.drawable.cr_shield),
        contentDescription = null,
        colorFilter = if (on) null else Grayscale,
        modifier = modifier
            .size(height * (300f / 360f), height)
            .offset(y = -lift)
            .alpha(if (on) 1f else 0.5f)
    )
}

/**
 * Orbe « assistant vocal » (fichier du porteur) : trois images clés en fondu, respiration et rotation lente, petite ombre.
 * Grise et immobile quand l'écoute est coupée. Décorative pour TalkBack.
 */
@Composable
fun VoiceOrb(active: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val turn = loopProgress(24_000, active) * 360f
    val breath = loopProgress(2600, active)
    val fade = loopProgress(6000, active)
    val k = 1f + 0.06f * sin(breath * 2 * PI).toFloat()

    // Fondu : l'image 2 culmine au tiers du cycle, l'image 3 aux deux tiers.
    fun bump(center: Float): Float {
        val d = kotlin.math.abs(fade - center).coerceAtMost(1f - kotlin.math.abs(fade - center))
        return (1f - d * 4f).coerceIn(0f, 1f)
    }
    Box(
        modifier
            .size(size)
            .scale(if (active) k else 1f)
            .shadow(size / 9, CircleShape, ambientColor = Color(0x551F2A33), spotColor = Color(0x551F2A33))
            .clip(CircleShape)
            .rotate(turn)
    ) {
        val img = Modifier.size(size)
        if (!active) {
            Image(painterResource(R.drawable.cr_orb_1), null, img.alpha(0.5f), colorFilter = Grayscale)
        } else {
            Image(painterResource(R.drawable.cr_orb_1), null, img)
            Image(painterResource(R.drawable.cr_orb_2), null, img.alpha(bump(1f / 3f)))
            Image(painterResource(R.drawable.cr_orb_3), null, img.alpha(bump(2f / 3f)))
        }
    }
}

/**
 * Bouton rond du milieu de la barre du bas : active ou coupe la protection. Blanc et rouge (couleurs du logo), sans noir.
 * Actif : symbole animé et halo rouge qui pulse. Coupé : symbole rose pâle immobile.
 */
@Composable
fun ProtectionButton(armed: Boolean, description: String, state: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val halo = loopProgress(2400, armed)
    Box(
        modifier
            .size(80.dp)
            .drawBehind {
                if (armed) {
                    val r = size.minDimension / 2
                    drawCircle(LogoRed.copy(alpha = 0.12f), r + 7.dp.toPx())
                    drawCircle(LogoRed.copy(alpha = 0.5f * (1f - halo)), r * (1f + 0.3f * halo), style = Stroke(3.dp.toPx()))
                }
            }
            .shadow(14.dp, CircleShape, ambientColor = Color(0x59E01E1E), spotColor = Color(0x59E01E1E))
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, Color(0xFFF0F2F5), CircleShape)
            .semantics {
                role = Role.Switch
                contentDescription = description
                stateDescription = state
            }
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        if (armed) {
            LogoMark(48.dp, animated = true)
        } else {
            LogoMark(48.dp, color = Color(0xFFF3B9BB), deep = Color(0xFFE9A3A6))
        }
    }
}
