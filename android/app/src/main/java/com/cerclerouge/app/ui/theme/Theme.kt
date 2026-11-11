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
 * Tokens du design « Pixel » v5 : style pixel (polices, angles crénelés, icônes 8×8) avec les couleurs du tableau de bord v4.
 * Fond noir-gris neutre, panneaux verts sombres, vert d'état, chiffres menthe, or pour les valeurs mises en avant.
 * Contrastes : voir docs/design/README.md (v4).
 */
@Immutable
data class CrColors(
    /** Fond des écrans (noir-gris). */
    val bg: Color,
    /** Écran des moniteurs et des cartes, plus sombre que le fond. */
    val screen: Color,
    /** Panneaux : dégradé de `panelTop` vers `panelBottom`. */
    val panelTop: Color,
    val panelBottom: Color,
    /** Bandeaux de section. */
    val banner: Color,
    /** Bordure verte des panneaux et bandeaux. */
    val edge: Color,
    /** Vert d'état et d'action (barres, icônes, bouton principal). */
    val green: Color,
    /** Chiffres et icônes en dégradé : du clair (`mintLight`) au vert (`mint`). */
    val mint: Color,
    val mintLight: Color,
    /** Valeurs mises en avant, chevrons de titre. */
    val gold: Color,
    /** Texte posé sur vert ou or. */
    val onAccent: Color,
    val ink: Color,
    val muted: Color,
    /** Traits décoratifs (grilles, séparateurs) : jamais seuls pour un contrôle. */
    val line: Color,
    /** Bordure des contrôles cliquables (≥ 3:1). */
    val lineControl: Color,
    /** Texte et voyants d'alerte (corail, lisible sur fond sombre). */
    val danger: Color,
    /** Boutons SOS : rouge franc, texte blanc (4,6:1). */
    val sos: Color,
    val onSos: Color,
    /** Terminal (style AgentPrism). */
    val terminal: Color,
    val chip: Color,
    val violet: Color,
    val cyan: Color,
    val teal: Color,
    val orange: Color,
    val blue: Color,
    val scrim: Color
)

val Dashboard = CrColors(
    bg = Color(0xFF0E1011),
    screen = Color(0xFF070908),
    panelTop = Color(0xFF0D2416),
    panelBottom = Color(0xFF09130D),
    banner = Color(0xFF062A12),
    edge = Color(0xFF1A6B3A),
    green = Color(0xFF1DB46C),
    mint = Color(0xFF4BE38C),
    mintLight = Color(0xFFD6FFB4),
    gold = Color(0xFFF2B226),
    onAccent = Color(0xFF06140C),
    ink = Color(0xFFE9F2EC),
    muted = Color(0xFF93A89B),
    line = Color(0xFF26352C),
    lineControl = Color(0xFF5E8A6F),
    danger = Color(0xFFFF6B5A),
    sos = Color(0xFFE5202B),
    onSos = Color(0xFFFFFFFF),
    terminal = Color(0xFF070B14),
    chip = Color(0xFF1A2233),
    violet = Color(0xFF6366F1),
    cyan = Color(0xFF22C3EE),
    teal = Color(0xFF14C8A8),
    orange = Color(0xFFFF6A1A),
    blue = Color(0xFF1AA3F5),
    scrim = Color(0xCC050706)
)

/** Titres, bandeaux et chiffres : Pixelify Sans (en capitales). */
val Pixel = FontFamily(Font(R.font.pixelify_sans_bold, FontWeight.Bold))

/** Chiffres : Silkscreen (5 et S bien distincts, contrairement à Pixelify : vital pour le compte à rebours). */
val Digit = FontFamily(Font(R.font.silkscreen_bold, FontWeight.Bold))

/** Texte, terminal et horodatages : Space Mono. */
val Mono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold)
)

/** Typographie pixel : Pixelify Sans pour ce qui se lit de loin, Space Mono pour le reste. */
object PxType {
    /** Grand titre (onboarding). */
    val hero = TextStyle(fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 42.sp)
    val display = TextStyle(fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp)
    val title = TextStyle(fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 0.04.em)
    val banner = TextStyle(fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.08.em)
    val digits = TextStyle(fontFamily = Digit, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp)
    val digitsXL = TextStyle(fontFamily = Digit, fontWeight = FontWeight.Bold, fontSize = 104.sp, lineHeight = 108.sp)
    val clock = TextStyle(fontFamily = Digit, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.02.em)
    val body = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp)
    val bodyStrong = body.copy(fontWeight = FontWeight.Bold, lineHeight = 20.sp)
    val label = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.1.em)
    val caption = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp)
    val button = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.08.em)
    val mono = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp)
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

val LocalCrColors = staticCompositionLocalOf { Dashboard }

/** Un seul thème sombre, quel que soit le réglage du système. */
@Composable
fun CercleRougeTheme(content: @Composable () -> Unit) {
    val t = Dashboard
    val scheme = darkColorScheme(
        primary = t.green,
        onPrimary = t.onAccent,
        background = t.bg,
        onBackground = t.ink,
        surface = t.panelBottom,
        onSurface = t.ink,
        surfaceVariant = t.panelTop,
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
