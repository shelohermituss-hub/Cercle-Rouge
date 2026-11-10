package com.cerclerouge.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cerclerouge.app.R

/** Tokens du design system « Cercle Rouge » (contrastes vérifiés dans les deux thèmes). */
@Immutable
data class CrColors(
    val bgBase: Color,
    val bgSurface: Color,
    val bgElevated: Color,
    val lineGrid: Color,
    val lineBorder: Color,
    val lineControl: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val brandRouge: Color,
    val onBrand: Color,
    val protege: Color,
    val vigilance: Color,
    val alerte: Color,
    val inactif: Color,
    val onState: Color,
    val focusRing: Color,
    val scrim: Color
)

val DarkTokens = CrColors(
    bgBase = Color(0xFF0B0F14),
    bgSurface = Color(0xFF121821),
    bgElevated = Color(0xFF18212C),
    lineGrid = Color(0xFF1E2A36),
    lineBorder = Color(0xFF2A3746),
    lineControl = Color(0xFF5E7086),
    textPrimary = Color(0xFFE6EDF3),
    textSecondary = Color(0xFF8B9AAB),
    textDisabled = Color(0xFF4A5868),
    brandRouge = Color(0xFFD91E36),
    onBrand = Color(0xFFFFFFFF),
    protege = Color(0xFF2DD4BF),
    vigilance = Color(0xFFF5A524),
    alerte = Color(0xFFFF3B4E),
    inactif = Color(0xFF7A8A9C),
    onState = Color(0xFF0B0F14),
    focusRing = Color(0xFF7DD3FC),
    scrim = Color(0xB805080C)
)

val LightTokens = CrColors(
    bgBase = Color(0xFFF5F7FA),
    bgSurface = Color(0xFFFFFFFF),
    bgElevated = Color(0xFFEEF2F6),
    lineGrid = Color(0xFFE2E8F0),
    lineBorder = Color(0xFFCBD5E1),
    lineControl = Color(0xFF7B8A9D),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF4B5869),
    textDisabled = Color(0xFF94A3B8),
    brandRouge = Color(0xFFC8102E),
    onBrand = Color(0xFFFFFFFF),
    protege = Color(0xFF0F766E),
    vigilance = Color(0xFFA15C07),
    alerte = Color(0xFFB4142B),
    inactif = Color(0xFF5B6878),
    onState = Color(0xFFFFFFFF),
    focusRing = Color(0xFF0369A1),
    scrim = Color(0x800F172A)
)

val Display = FontFamily(Font(R.font.space_grotesk_semibold, FontWeight.SemiBold))
val Sans = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)
val Mono = FontFamily(Font(R.font.jetbrains_mono_medium, FontWeight.Medium))

/** Styles HUD en JetBrains Mono, chiffres tabulaires. */
object HudType {
    val xl =
        TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 64.sp, lineHeight = 64.sp, fontFeatureSettings = "tnum")
    val md =
        TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum")
    val label = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.04.em,
        fontFeatureSettings = "tnum"
    )
}

private val CrTypography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
)

val LocalCrColors = staticCompositionLocalOf { DarkTokens }

/** Thème sombre par défaut (docs/design), clair si le système le demande. */
@Composable
fun CercleRougeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val t = if (dark) DarkTokens else LightTokens
    val scheme = if (dark) {
        darkColorScheme(
            primary = t.protege,
            onPrimary = t.onState,
            background = t.bgBase,
            onBackground = t.textPrimary,
            surface = t.bgSurface,
            onSurface = t.textPrimary,
            surfaceVariant = t.bgElevated,
            onSurfaceVariant = t.textSecondary,
            outline = t.lineControl,
            error = t.alerte
        )
    } else {
        lightColorScheme(
            primary = t.protege,
            onPrimary = t.onState,
            background = t.bgBase,
            onBackground = t.textPrimary,
            surface = t.bgSurface,
            onSurface = t.textPrimary,
            surfaceVariant = t.bgElevated,
            onSurfaceVariant = t.textSecondary,
            outline = t.lineControl,
            error = t.alerte
        )
    }
    CompositionLocalProvider(LocalCrColors provides t) {
        MaterialTheme(colorScheme = scheme, typography = CrTypography) {
            // Couleur de texte par défaut : sans elle, un Text sans couleur explicite serait noir sur fond sombre.
            CompositionLocalProvider(LocalContentColor provides t.textPrimary, content = content)
        }
    }
}

object Cr {
    val colors: CrColors @Composable get() = LocalCrColors.current
}
