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
 * Tokens du design v4 « tableau de bord » (assets Data Visualization, AgentPrism, Biometric fournis par le porteur du projet).
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

/** Titres, bandeaux et chiffres : Barlow Condensed. */
val Condensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold)
)

/** Texte courant : Barlow. */
val Sans = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold)
)

/** Terminal, horodatages : JetBrains Mono. */
val Mono = FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal))

object DashType {
    /** Grand titre (onboarding). */
    val hero = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp)
    val display = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp)
    val title =
        TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = 0.04.em)
    val banner =
        TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 0.08.em)
    val digits = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 34.sp)
    val digitsXL = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 120.sp, lineHeight = 120.sp)
    val clock =
        TextStyle(fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 0.06.em)
    val body = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
    val label =
        TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.08.em)
    val caption = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val button =
        TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 0.08.em)
    val mono = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
}

private val CrTypography = Typography(
    headlineSmall = DashType.title,
    titleLarge = DashType.display,
    titleMedium = DashType.title,
    bodyLarge = DashType.body,
    bodyMedium = DashType.body,
    bodySmall = DashType.caption,
    labelLarge = DashType.button
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
