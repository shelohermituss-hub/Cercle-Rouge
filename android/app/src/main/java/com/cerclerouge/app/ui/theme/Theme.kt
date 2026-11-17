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
    /** SOS et alertes. */
    val danger: Color,
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
    warn = Color(0xFFFFD23F),
    scrim = Color(0xCC0A0B0C)
)

/**
 * Thème clair (1.20, R-UI) : fonds blanc cassé ; le lime, illisible sur blanc, devient un vert profond (5,9:1 sur blanc) pour le
 * texte et les bordures, le rouge et le jaune sont assombris pour garder ≥ 4,5:1.
 */
val PixelLight = CrColors(
    void = Color(0xFFE6E9E3),
    surface = Color(0xFFF5F6F2),
    raised = Color(0xFFFFFFFF),
    forestDeep = Color(0xFFDDE2D8),
    forest = Color(0xFF007A37),
    forestShade = Color(0xFF00430F),
    lime = Color(0xFF2F6B00),
    glow = Color(0x332F6B00),
    onLime = Color(0xFFFFFFFF),
    ink = Color(0xFF121416),
    muted = Color(0xFF4C535A),
    line = Color(0xFFC6CBD0),
    lineControl = Color(0xFF6B7279),
    grid = Color(0x146B7279),
    danger = Color(0xFFB3261E),
    warn = Color(0xFF7A5C00),
    scrim = Color(0xCCF5F6F2)
)

/** Contraste élevé (1.20) : noir pur, texte blanc, bordures blanches, couleurs vives (≥ 7:1 sur noir). */
val PixelHighContrast = CrColors(
    void = Color(0xFF000000),
    surface = Color(0xFF000000),
    raised = Color(0xFF000000),
    forestDeep = Color(0xFF1A1A1A),
    forest = Color(0xFF00C853),
    forestShade = Color(0xFF00430F),
    lime = Color(0xFFC6FF3D),
    glow = Color(0x66C6FF3D),
    onLime = Color(0xFF000000),
    ink = Color(0xFFFFFFFF),
    muted = Color(0xFFE0E0E0),
    line = Color(0xFFFFFFFF),
    lineControl = Color(0xFFFFFFFF),
    grid = Color(0x00000000),
    danger = Color(0xFFFF8A80),
    warn = Color(0xFFFFE066),
    scrim = Color(0xE6000000)
)

/** Choix d'apparence (Réglages › Apparence). SOMBRE par défaut : c'est le design de référence. */
enum class ThemeMode { DARK, LIGHT, SYSTEM, HIGH_CONTRAST }

/** Titres et chiffres en pixels : Silkscreen (lettres nettes, 5 et S distincts ; toujours en capitales). */
val Pixel = FontFamily(Font(R.font.silkscreen_bold, FontWeight.Bold))
val Digit = Pixel

/** Tout le reste : Space Mono. */
val Mono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold)
)

object PxType {
    /** Grand titre d'affiche (onboarding). */
    val poster = TextStyle(fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp)
    val display = TextStyle(fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp)
    val title = TextStyle(fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp)
    val brand = TextStyle(fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp)
    val digits = TextStyle(fontFamily = Digit, fontWeight = FontWeight.Bold, fontSize = 96.sp, lineHeight = 100.sp)

    /** Chiffres clés (journal) : même police que le compte à rebours. */
    val count = TextStyle(fontFamily = Digit, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp)

    /** Texte courant : Space Mono 15 sp, interligne généreux. */
    val body = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp)
    val bodyBold = body.copy(fontWeight = FontWeight.Bold, lineHeight = 22.sp)

    /** Étiquettes en capitales : 13 sp, espacement réduit (lisible, pas de pixel). */
    val label = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.06.em)
    val caption =
        TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.02.em)
    val button = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.06.em)
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

/** Thème de l'app : sombre (référence), clair, celui du système, ou contraste élevé (1.20). */
@Composable
fun CercleRougeTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val light = mode == ThemeMode.LIGHT || (mode == ThemeMode.SYSTEM && !androidx.compose.foundation.isSystemInDarkTheme())
    val t = when {
        mode == ThemeMode.HIGH_CONTRAST -> PixelHighContrast
        light -> PixelLight
        else -> PixelLime
    }
    val base = if (light) androidx.compose.material3.lightColorScheme() else darkColorScheme()
    val scheme = base.copy(
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
