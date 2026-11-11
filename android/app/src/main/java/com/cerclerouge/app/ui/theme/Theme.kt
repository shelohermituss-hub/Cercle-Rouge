package com.cerclerouge.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cerclerouge.app.R

/**
 * Tokens du design « Pixel Lime » (maquettes Cercle Rouge v3, canevas « Cercle Rouge — Maquettes »).
 * Fonds noir-gris neutres (comme la référence « Pixel VPN »), lime comme seule couleur d'action.
 * Contrastes : voir docs/design/README.md (v3).
 */
@Immutable
data class CrColors(
    val void: Color,
    /** Fond des écrans. */
    val surface: Color,
    /** Intérieur des panneaux crénelés. */
    val raised: Color,
    val forestDeep: Color,
    val forest: Color,
    /** Vert sombre de l'escalier des affiches lime. */
    val forestShade: Color,
    /** Couleur d'action et d'état « protégé ». */
    val lime: Color,
    val glow: Color,
    /** Texte posé sur lime, rouge ou jaune. */
    val onLime: Color,
    val ink: Color,
    val muted: Color,
    /** Bordure décorative des panneaux (2,6:1 : jamais seule pour un contrôle). */
    val line: Color,
    /** Bordure des contrôles cliquables (≥ 3:1 sur surface et raised). */
    val lineControl: Color,
    val grid: Color,
    /** Texte et voyants d'alerte (corail, lisible sur fond sombre). */
    val danger: Color,
    /** Boutons SOS : rouge franc, texte blanc (4,6:1). */
    val sos: Color,
    val onSos: Color,
    /** Vérification, attente, hors ligne. */
    val warn: Color,
    val scrim: Color
)

val PixelLime = CrColors(
    void = Color(0xFF0A0B0C),
    surface = Color(0xFF121416),
    raised = Color(0xFF1B1E21),
    forestDeep = Color(0xFF2A2E33),
    forest = Color(0xFF009443),
    forestShade = Color(0xFF00430F),
    lime = Color(0xFFA0E705),
    glow = Color(0x66A0E705),
    onLime = Color(0xFF0E1012),
    ink = Color(0xFFE6E8EA),
    muted = Color(0xFF9BA1A8),
    line = Color(0xFF3A3F45),
    lineControl = Color(0xFF6B7279),
    grid = Color(0x1F6B7279),
    danger = Color(0xFFFF6B5A),
    sos = Color(0xFFE5202B),
    onSos = Color(0xFFFFFFFF),
    warn = Color(0xFFFFD23F),
    scrim = Color(0xCC0A0B0C)
)

/** Titres en pixels (toujours en capitales). */
val Pixel = FontFamily(Font(R.font.pixelify_sans_bold, FontWeight.Bold))

/** Tout le reste : Space Mono. */
val Mono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold)
)

object PxType {
    /** Grand titre d'affiche (onboarding). */
    val poster = TextStyle(fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 50.sp)
    val display = TextStyle(fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp)
    val title = TextStyle(fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp)
    val brand = TextStyle(fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 0.04.em)
    val digits = TextStyle(fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 96.sp, lineHeight = 100.sp)
    val body = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp)
    val bodyBold = body.copy(fontWeight = FontWeight.Bold, lineHeight = 20.sp)

    /** Étiquettes en capitales (LB du design). */
    val label = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.12.em)
    val caption =
        TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.04.em)
    val button = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.08.em)
}

private val CrTypography = Typography(
    headlineSmall = PxType.title,
    titleLarge = PxType.display,
    titleMedium = PxType.title,
    bodyLarge = PxType.body,
    bodyMedium = PxType.body,
    bodySmall = PxType.caption,
    labelLarge = PxType.button
)

val LocalCrColors = staticCompositionLocalOf { PixelLime }

/** Le design « Pixel Lime » n'existe qu'en sombre : un seul thème, quel que soit le réglage du système. */
@Composable
fun CercleRougeTheme(content: @Composable () -> Unit) {
    val t = PixelLime
    val scheme = darkColorScheme(
        primary = t.lime,
        onPrimary = t.onLime,
        background = t.surface,
        onBackground = t.ink,
        surface = t.raised,
        onSurface = t.ink,
        surfaceVariant = t.raised,
        onSurfaceVariant = t.muted,
        outline = t.lineControl,
        error = t.danger
    )
    CompositionLocalProvider(LocalCrColors provides t) {
        MaterialTheme(colorScheme = scheme, typography = CrTypography) {
            CompositionLocalProvider(LocalContentColor provides t.ink, content = content)
        }
    }
}

object Cr {
    val colors: CrColors @Composable get() = LocalCrColors.current
}
