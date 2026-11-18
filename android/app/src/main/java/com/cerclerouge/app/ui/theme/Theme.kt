package com.cerclerouge.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cerclerouge.app.R

/**
 * Jetons du design « Verre » (docs/design/systeme-verre.md) : fond gris uni, cartes blanches, encre ardoise,
 * bleu #2F86F6 pour l'action, rouge réservé au logo, au SOS et à l'alerte.
 * Les noms des champs viennent du design précédent et sont gardés pour ne pas toucher à chaque écran :
 * `lime` = couleur d'action et d'état « protégé » (bleu), `raised` = carte, `surface` = fond d'écran.
 */
@Immutable
data class CrColors(
    /** Fond le plus profond (derrière les feuilles). */
    val void: Color,
    /** Fond des écrans. */
    val surface: Color,
    /** Intérieur des cartes. */
    val raised: Color,
    /** Fond des boutons gris et des pastilles. */
    val forestDeep: Color,
    /** Vert « calme », « accepté ». */
    val forest: Color,
    val forestShade: Color,
    /** Couleur d'action et d'état « protégé » (bleu). */
    val lime: Color,
    val glow: Color,
    /** Texte posé sur la couleur d'action, le rouge ou l'ambre. */
    val onLime: Color,
    val ink: Color,
    val muted: Color,
    /** Séparateurs et liserés des cartes (décoratifs). */
    val line: Color,
    /** Bordure des contrôles cliquables (≥ 3:1 sur le fond et les cartes). */
    val lineControl: Color,
    val grid: Color,
    /** SOS et alertes. */
    val danger: Color,
    /** Vérification, attente, hors ligne. */
    val warn: Color,
    val scrim: Color,
    /** Violet de la « pensée » du veilleur (journal). */
    val thought: Color = Color(0xFF6D28D9),
    /** Lime des bonnes nouvelles (« calme »), en fond clair. */
    val calmTint: Color = Color(0xFFECF8E1),
    val calmText: Color = Color(0xFF3F6212)
)

/** Clair « Verre » : le design de référence (défaut). */
val Verre = CrColors(
    void = Color(0xFFE4E6EA),
    surface = Color(0xFFEDEDF0),
    raised = Color(0xFFFFFFFF),
    forestDeep = Color(0xFFECEFF3),
    forest = Color(0xFF15803D),
    forestShade = Color(0xFF0F5F2D),
    lime = Color(0xFF2F86F6),
    glow = Color(0x332F86F6),
    onLime = Color(0xFFFFFFFF),
    ink = Color(0xFF1F2A33),
    muted = Color(0xFF566B80),
    line = Color(0xFFE4E9EE),
    lineControl = Color(0xFF7D8E9F),
    grid = Color(0x00000000),
    danger = Color(0xFFD91F36),
    warn = Color(0xFFB26B00),
    scrim = Color(0x991F2A33)
)

/** Sombre « Verre » (nuit) : mêmes rôles, fonds ardoise, bleu éclairci pour garder ≥ 4,5:1. */
val VerreNight = CrColors(
    void = Color(0xFF0C0F12),
    surface = Color(0xFF13171B),
    raised = Color(0xFF1D2329),
    forestDeep = Color(0xFF283038),
    forest = Color(0xFF4ADE80),
    forestShade = Color(0xFF166534),
    lime = Color(0xFF5AA2FF),
    glow = Color(0x445AA2FF),
    onLime = Color(0xFF0C1218),
    ink = Color(0xFFE9EDF1),
    muted = Color(0xFFA3B1BE),
    line = Color(0xFF2C343C),
    lineControl = Color(0xFF7D8E9F),
    grid = Color(0x00000000),
    danger = Color(0xFFFF6B7A),
    warn = Color(0xFFFFC85A),
    scrim = Color(0xCC0C0F12),
    thought = Color(0xFFB79CFF),
    calmTint = Color(0xFF1E3313),
    calmText = Color(0xFFBEF264)
)

/** Contraste élevé (1.20) : noir pur, texte blanc, bordures blanches, couleurs vives (≥ 7:1 sur noir). */
val VerreHighContrast = CrColors(
    void = Color(0xFF000000),
    surface = Color(0xFF000000),
    raised = Color(0xFF000000),
    forestDeep = Color(0xFF1A1A1A),
    forest = Color(0xFF00E676),
    forestShade = Color(0xFF00430F),
    lime = Color(0xFF82B8FF),
    glow = Color(0x6682B8FF),
    onLime = Color(0xFF000000),
    ink = Color(0xFFFFFFFF),
    muted = Color(0xFFE0E0E0),
    line = Color(0xFFFFFFFF),
    lineControl = Color(0xFFFFFFFF),
    grid = Color(0x00000000),
    danger = Color(0xFFFF8A80),
    warn = Color(0xFFFFE066),
    scrim = Color(0xE6000000),
    thought = Color(0xFFD0BCFF),
    calmTint = Color(0xFF000000),
    calmText = Color(0xFFC6FF3D)
)

/** Choix d'apparence (Réglages › Apparence). CLAIR par défaut : c'est le design de référence « Verre ». */
enum class ThemeMode { DARK, LIGHT, SYSTEM, HIGH_CONTRAST }

/** Tout le texte : Inter (jumelle libre de SF Pro, la police des captures de référence). */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** Journal du veilleur (style terminal) : JetBrains Mono. */
val Code = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium)
)

/** Anciens noms gardés pour les écrans : tout est désormais en Inter. */
val Pixel = Inter
val Digit = Inter
val Mono = Inter

object PxType {
    /** Grand titre (onboarding). */
    val poster =
        TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 29.sp, lineHeight = 35.sp, letterSpacing = (-0.02).em)

    /** Grand titre d'écran. */
    val display =
        TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.015).em)

    /** Titre de carte et d'en-tête. */
    val title =
        TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em)
    val brand = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp)
    val digits = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 96.sp, lineHeight = 100.sp)

    /** Chiffres clés : Inter gras, chiffres à chasse fixe. */
    val count =
        TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, fontFeatureSettings = "tnum")

    /** Texte courant : Inter 15 sp. */
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyBold = body.copy(fontWeight = FontWeight.SemiBold)

    /** Libellés de section (13 sp, en capitales dans les écrans). */
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.03.em)
    val caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val button = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp)

    /** Journal (terminal). */
    val code = TextStyle(fontFamily = Code, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp)
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

/** Formes Material (champs de texte, menus, dialogues) arrondies comme les cartes « Verre ». */
private val CrShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

val LocalCrColors = staticCompositionLocalOf { Verre }

/** Thème de l'app : clair « Verre » (référence), sombre, celui du système, ou contraste élevé (1.20). */
@Composable
fun CercleRougeTheme(mode: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme())
    val t = when {
        mode == ThemeMode.HIGH_CONTRAST -> VerreHighContrast
        dark -> VerreNight
        else -> Verre
    }
    val base = if (dark || mode == ThemeMode.HIGH_CONTRAST) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = t.lime,
        onPrimary = t.onLime,
        background = t.surface,
        onBackground = t.ink,
        surface = t.raised,
        onSurface = t.ink,
        surfaceVariant = t.forestDeep,
        onSurfaceVariant = t.muted,
        outline = t.lineControl,
        error = t.danger
    )
    CompositionLocalProvider(LocalCrColors provides t) {
        MaterialTheme(colorScheme = scheme, typography = CrTypography, shapes = CrShapes) {
            CompositionLocalProvider(LocalContentColor provides t.ink, content = content)
        }
    }
}

object Cr {
    val colors: CrColors @Composable get() = LocalCrColors.current
}
