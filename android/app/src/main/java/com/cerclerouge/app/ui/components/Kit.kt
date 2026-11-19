package com.cerclerouge.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cerclerouge.app.R
import com.cerclerouge.app.ui.theme.Code
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.Inter

/**
 * Boîte à outils « Verre » : chaque composant reprend au pixel près un bloc des maquettes Claude Design
 * (`project/Verre*.dc.html`, 390 × 844). Les tailles en px des maquettes sont des dp ici, les tailles de texte des sp.
 */
object V {
    fun text(size: TextUnit, weight: FontWeight = FontWeight.Normal, line: TextUnit = TextUnit.Unspecified, spacing: Float = 0f) =
        TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size, lineHeight = line, letterSpacing = if (spacing == 0f) TextUnit.Unspecified else spacing.em)

    /** Grand titre d'onglet (Réglages, Proches) : 34 px, gras, -0,6 px. */
    val tabTitle = text(34.sp, FontWeight.Bold, 40.sp, -0.018f)

    /** Titre d'écran avec retour : 28 px. */
    val screenTitle = text(28.sp, FontWeight.Bold, 34.sp, -0.018f)

    /** Titre d'onboarding et de configuration : 29 px / 35. */
    val poster = text(29.sp, FontWeight.Bold, 35.sp, -0.021f)

    /** Titre de dialogue (alerte, vérification) : 27 px / 32, demi-gras. */
    val dialog = text(27.sp, FontWeight.SemiBold, 32.sp, -0.019f)

    val lead = text(17.sp, FontWeight.Normal, 24.sp)
    val row = text(17.sp)
    val rowStrong = text(17.sp, FontWeight.SemiBold)
    val body = text(15.sp, FontWeight.Normal, 22.sp)
    val bodyStrong = text(15.sp, FontWeight.SemiBold, 22.sp)
    val small = text(13.sp, FontWeight.Normal, 18.sp)
    val section = text(14.sp, FontWeight.Normal, 18.sp, 0.02f)
    val chip = text(12.sp, FontWeight.SemiBold)
    val mono = TextStyle(fontFamily = Code, fontSize = 12.sp, lineHeight = 18.sp)
}

/** Fond d'un écran « Verre » (gris #EDEDF0 ; onboarding et configuration #F4F6F9). */
@Composable
fun screenBg(onboarding: Boolean = false): Color = if (onboarding && Cr.colors.surface == Color(0xFFEDEDF0)) Color(0xFFF4F6F9) else Cr.colors.surface

/** En-tête d'un onglet : grand titre 34 px et sous-titre (padding 22 / 20). */
@Composable
fun TabHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
        Text(title, style = V.tabTitle, color = Cr.colors.ink, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = V.body, color = Cr.colors.muted, modifier = Modifier.padding(top = 4.dp)) }
    }
}

/** En-tête avec retour (48 dp, chevron) et titre 28 px. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, subtitle: String? = null, trailing: @Composable (RowScope.() -> Unit)? = null) {
    val back = stringResource(R.string.back)
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = back },
            contentAlignment = Alignment.Center
        ) { PixelIconView(PixelIcon.ARROW_LEFT, 24.dp, Cr.colors.ink, strokeWidth = 2.2f) }
        Column(Modifier.weight(1f).padding(start = 4.dp, top = 6.dp)) {
            Text(title, style = V.screenTitle, color = Cr.colors.ink, modifier = Modifier.semantics { heading() })
            subtitle?.let { Text(it, style = V.body, color = Cr.colors.muted, modifier = Modifier.padding(top = 4.dp)) }
        }
        trailing?.invoke(this)
    }
}

/** Libellé de section en capitales (14 px, #66788A), marge 20 / 20 / 8, avec un texte ou une action à droite. */
@Composable
fun SectionLabel(text: String, right: String? = null, onRight: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = V.section, color = Cr.colors.muted, modifier = Modifier.weight(1f).semantics { heading() })
        right?.let {
            val m = if (onRight != null) Modifier.clickable(role = Role.Button, onClick = onRight).padding(vertical = 12.dp) else Modifier
            Text(it, style = V.body, color = if (onRight != null) Cr.colors.lime else Cr.colors.ink, modifier = m)
        }
    }
}

/** Carte blanche « Verre » : rayon 22, liseré 4 %, ombre très légère, marge latérale 16. */
@Composable
fun VCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(0.dp), radius: Dp = 22.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .shadow(1.dp, shape, ambientColor = Color(0x081F2A33), spotColor = Color(0x081F2A33))
            .clip(shape)
            .background(Cr.colors.raised)
            .border(1.dp, Cr.colors.ink.copy(alpha = 0.04f), shape)
            .padding(padding),
        content = content
    )
}

/** Grande carte du haut (héros, dialogues) : rayon 34, dégradé blanc → #F4F6F8, ombre longue. */
fun Modifier.heroCard(radius: Dp = 34.dp, top: Color = Color.White, bottom: Color = Color(0xFFF4F6F8)): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(18.dp, shape, ambientColor = Color(0x471F2A33), spotColor = Color(0x471F2A33))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .border(1.dp, top, shape)
}

/** Ligne de liste des réglages : 56 de haut, icône en verre 32, texte 17 px, chevron. */
@Composable
fun ListRow(@DrawableRes icon: Int?, text: String, onClick: () -> Unit, value: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        icon?.let { GlassIcon(it, 32.dp) }
        Text(text, style = V.row, color = Cr.colors.ink, modifier = Modifier.weight(1f))
        value?.let { Text(it, style = V.body, color = Cr.colors.muted) }
        if (trailing != null) trailing() else PixelIconView(PixelIcon.ARROW_LEFT, 22.dp, Cr.colors.ink, Modifier.scale(-1f, 1f), strokeWidth = 2f)
    }
}

/** Bloc de lignes (réglages) : carte blanche rayon 22, marges internes 4 / 18. */
@Composable
fun RowGroup(content: @Composable ColumnScope.() -> Unit) =
    VCard(padding = PaddingValues(horizontal = 18.dp, vertical = 4.dp), content = content)

@Composable
fun Divider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(Cr.colors.line))

/** Bouton principal encre : 62 de haut, pilule, texte blanc 19 px (onboarding, configuration). */
@Composable
fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 62.dp, icon: PixelIcon? = null) =
    PillButton(text, onClick, modifier, Cr.colors.ink, Cr.colors.raised, enabled, height, if (height >= 60.dp) 19.sp else 16.sp, icon)

/** Bouton blanc ombré (54) : « Je suis en sécurité », actions secondaires. */
@Composable
fun WhiteButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 54.dp, icon: PixelIcon? = null) =
    PillButton(text, onClick, modifier, Cr.colors.raised, Cr.colors.ink, enabled, height, if (height >= 60.dp) 18.sp else 16.sp, icon, shadow = true)

/** Bouton rouge (dégradé du SOS) pour une action d'alerte. */
@Composable
fun RedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 54.dp) =
    PillButton(text, onClick, modifier, Color(0xFFD91F2A), Color.White, true, height, 16.sp, null, brush = Brush.verticalGradient(listOf(Color(0xFFF0333E), Color(0xFFC8141D))))

/** Bouton contour rouge (action destructrice : tout effacer). */
@Composable
fun DangerOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    PillButton(text, onClick, modifier, Color.Transparent, Color(0xFFA3161C), true, 54.dp, 16.sp, null, border = Color(0xFFC8141D))

/** Bouton contour encre (2 dp), 44 de haut : action proposée dans une carte. */
@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 44.dp) =
    PillButton(text, onClick, modifier, Color.Transparent, Cr.colors.ink, true, height, 15.sp, null, border = Cr.colors.ink)

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    bg: Color,
    fg: Color,
    enabled: Boolean,
    height: Dp,
    size: TextUnit,
    icon: PixelIcon?,
    shadow: Boolean = false,
    brush: Brush? = null,
    border: Color? = null
) {
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var m = modifier.fillMaxWidth().heightIn(min = height).scale(if (pressed && enabled) 0.97f else 1f)
    if (shadow) m = m.shadow(6.dp, shape, ambientColor = Color(0x241F2A33), spotColor = Color(0x241F2A33))
    m = m.clip(shape)
    m = if (brush != null) m.background(brush) else m.background(bg)
    if (border != null) m = m.border(2.dp, border, shape)
    Row(
        m.clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (enabled) Modifier else Modifier.background(Color.White.copy(alpha = 0.45f)))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { PixelIconView(it, 22.dp, fg, strokeWidth = 2.2f) }
        Text(text, style = V.text(size, FontWeight.SemiBold), color = fg, textAlign = TextAlign.Center)
    }
}

/** Pastille d'état (26 de haut, 12 px demi-gras). */
@Composable
fun Chip(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = 26.dp).clip(RoundedCornerShape(13.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(text, style = V.chip, color = fg, maxLines = 1)
    }
}

/** Couleurs des pastilles des maquettes. */
object Tone {
    val Ink = Color(0xFF1F2A33) to Color.White
    val Calm = Color(0xFFECF8E1) to Color(0xFF3F6212)
    val Wait = Color(0xFFFFF0C7) to Color(0xFF5C3D00)
    val Info = Color(0xFFE8F1FE) to Color(0xFF1F4E8C)
    val Mute = Color(0xFFF1F3F6) to Color(0xFF566B80)
    val Alert = Color(0xFFFDE3E4) to Color(0xFF9E0F15)
}

@Composable
fun Chip(text: String, tone: Pair<Color, Color>, modifier: Modifier = Modifier) = Chip(text, tone.first, tone.second, modifier)

/** Ligne de journal (terminal) : marque colorée + texte mono 12 px. */
data class MonoLine(val mark: String, val color: Color, val text: String)

object Marks {
    val seen = Color(0xFF3F6212)
    val thought = Color(0xFF6D28D9)
    val done = Color(0xFF15803D)
    val next = Color(0xFF1F6FE5)
    val wait = Color(0xFF8A5300)
}

/** Boîte blanche de lignes mono (alerte, proches, SOS envoyé). */
@Composable
fun MonoBox(lines: List<MonoLine>, modifier: Modifier = Modifier, side: Dp = 16.dp) {
    Column(
        modifier.padding(horizontal = side).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Cr.colors.raised).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        lines.forEach { l ->
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = l.color))
                    append(l.mark)
                    pop()
                    append(" ")
                    append(l.text)
                },
                style = V.mono,
                color = Color(0xFF2C3A47),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Champ de saisie : libellé 14 px demi-gras, champ blanc 58 de haut, rayon 18, bordure #D3DDE6, aide 13 px. */
@Composable
fun VField(
    label: String?,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    placeholder: String? = null,
    error: String? = null,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    side: Dp = 16.dp
) {
    Column(modifier.padding(horizontal = side).fillMaxWidth()) {
        label?.let { Text(it, style = V.text(14.sp, FontWeight.SemiBold), color = Cr.colors.ink, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)) }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).then(if (label == null && placeholder != null) Modifier.semantics { contentDescription = placeholder } else Modifier),
            textStyle = V.text(17.sp),
            singleLine = true,
            isError = error != null,
            placeholder = placeholder?.let { { Text(it, style = V.text(17.sp), color = Color(0xFF93A5B6)) } },
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = keyboard,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Cr.colors.raised,
                focusedContainerColor = Cr.colors.raised,
                unfocusedBorderColor = Color(0xFFD3DDE6),
                focusedBorderColor = Cr.colors.lime
            )
        )
        val under = error ?: hint
        under?.let { Text(it, style = V.small, color = if (error != null) Cr.colors.danger else Cr.colors.muted, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
    }
}

/** Interrupteur bleu des maquettes (50 × 30). */
@Composable
fun VToggle(checked: Boolean, onChange: (Boolean) -> Unit) = Switch(
    checked = checked,
    onCheckedChange = onChange,
    colors = SwitchDefaults.colors(
        checkedTrackColor = Cr.colors.lime,
        checkedThumbColor = Color.White,
        uncheckedTrackColor = Color(0xFFC9D2DB),
        uncheckedThumbColor = Color.White,
        uncheckedBorderColor = Color.Transparent
    )
)

/** Ligne d'accord : titre 16 px, sous-titre 13 px, interrupteur à droite. */
@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = V.text(16.sp, FontWeight.Medium), color = Cr.colors.ink)
            subtitle?.let { Text(it, style = V.small, color = Cr.colors.muted) }
        }
        VToggle(checked, onChange)
    }
}

/** Avatar rond avec l'initiale (46). */
@Composable
fun Avatar(name: String, index: Int, size: Dp = 46.dp) {
    val bg = AVATARS[index.mod(AVATARS.size)]
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), style = V.text((size.value * 0.39f).sp, FontWeight.Bold), color = Color(0xFF1F2A33))
    }
}

private val AVATARS = listOf(Color(0xFFCFE3FB), Color(0xFFDCF2C8), Color(0xFFFFE7B3), Color(0xFFF6D6F0), Color(0xFFD9E1FF))

/** Barre d'étapes de la configuration : 5 segments de 5 dp. */
@Composable
fun StepBar(step: Int, total: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.v_step_of, step, total)
    Row(modifier.padding(horizontal = 20.dp).fillMaxWidth().semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i -> Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (i < step) Cr.colors.ink else Color(0xFFC9D2DB))) }
    }
}

/** Points de pagination de l'onboarding (9 dp, écart 12). */
@Composable
fun PageDots(page: Int, total: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.v_step_of, page + 1, total)
    Row(modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(total) { i -> Box(Modifier.size(9.dp).clip(CircleShape).background(if (i == page) Cr.colors.ink else Color(0xFF93A5B6))) }
    }
}

/** Points du code PIN (18 dp, écart 18) : pleins pour les chiffres saisis. */
@Composable
fun PinDots(filled: Int, total: Int = 4, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = "$filled / $total" }, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(total) { i ->
            val m = Modifier.size(18.dp).clip(CircleShape)
            Box(if (i < filled) m.background(Cr.colors.ink) else m.border(2.dp, Color(0xFF93A5B6), CircleShape))
        }
    }
}

/** Pavé numérique « Verre » : touches blanches 64 de haut, rayon 22, chiffres 26 px. */
@Composable
fun Keypad(onDigit: (String) -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier, extra: (@Composable () -> Unit)? = null) {
    val del = stringResource(R.string.v_delete)
    Column(modifier.padding(horizontal = 24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { k ->
                    val cell = Modifier.weight(1f).height(64.dp)
                    when (k) {
                        "" -> Box(cell, contentAlignment = Alignment.Center) { extra?.invoke() }
                        "<" -> Box(
                            cell.clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onDelete).semantics { contentDescription = del },
                            contentAlignment = Alignment.Center
                        ) { PixelIconView(PixelIcon.BACKSPACE, 28.dp, Cr.colors.ink, strokeWidth = 2f) }
                        else -> Box(
                            cell.shadow(1.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Cr.colors.raised).clickable(role = Role.Button) { onDigit(k) },
                            contentAlignment = Alignment.Center
                        ) { Text(k, style = V.text(26.sp, FontWeight.Medium), color = Cr.colors.ink) }
                    }
                }
            }
        }
    }
}

/** Bandeau bleu des réglages et du coffre (104 de haut, dégradé bleu, icône en verre 112 en haut à droite). */
@Composable
fun BlueBanner(title: String, subtitle: String, @DrawableRes icon: Int, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(104.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1F6FE5), Color(0xFF2F86F6), Color(0xFF7DB3FA)), start = Offset(0f, 0f), end = Offset(900f, 380f)))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
    ) {
        GlassIcon(icon, 112.dp, Modifier.align(Alignment.TopEnd).padding(0.dp).padding(end = 0.dp))
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp).width(220.dp)) {
            Text(title, style = V.text(22.sp, FontWeight.Bold, spacing = -0.014f), color = Color.White)
            Text(subtitle, style = V.body, color = Color(0xFFE3EEFD), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Bouton « + Ajouter … » en pointillés (rayon 22, 58 de haut). */
@Composable
fun DashedAdd(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val color = Color(0xFFA9B6C3)
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(58.dp).clip(RoundedCornerShape(22.dp))
            .drawBehind {
                drawRoundRect(
                    color,
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx())
                )
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PixelIconView(PixelIcon.PLUS, 20.dp, Cr.colors.ink, strokeWidth = 2.4f)
        Text(text, style = V.text(16.sp, FontWeight.SemiBold), color = if (enabled) Cr.colors.ink else Cr.colors.muted)
    }
}

/** Choix en pilules (« Parent », « Tuteur »…) : sélection encre, les autres blancs à bordure. */
@Composable
fun <T> PillChoice(options: List<Pair<T, String>>, current: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            val on = v == current
            val shape = RoundedCornerShape(22.dp)
            Box(
                Modifier.heightIn(min = 44.dp).clip(shape).background(if (on) Cr.colors.ink else Cr.colors.raised)
                    .then(if (on) Modifier else Modifier.border(1.dp, Color(0xFFD3DDE6), shape))
                    .clickable(role = Role.RadioButton) { onSelect(v) }
                    .semantics { selected = on }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) { Text(label, style = V.text(15.sp, if (on) FontWeight.SemiBold else FontWeight.Medium), color = if (on) Cr.colors.raised else Cr.colors.ink) }
        }
    }
}

/** Bulle d'information bleue (configuration) : icône en verre et texte 15 px. */
@Composable
fun InfoNote(text: String, @DrawableRes icon: Int = R.drawable.glass_proches, warn: Boolean = false) {
    val (bg, fg) = if (warn) Tone.Wait else Color(0xFFE8F1FE) to Color(0xFF1F4E8C)
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassIcon(icon, 30.dp)
        Text(text, style = V.text(15.sp, line = 21.sp), color = fg)
    }
}

/** Zone du bas d'un écran (boutons) : marges 18, bas 30. */
@Composable
fun BottomActions(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 30.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/** Lien texte gris centré (« Plus tard », « Passer »). */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Cr.colors.muted) {
    Box(modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = V.text(16.sp, FontWeight.Medium), color = color)
    }
}

/** Contenu posé sur la carte héros (titre 21 px + puces). */
@Composable
fun Bullets(items: List<Pair<String, String?>>, color: Color = Color(0xFF2C3A47)) {
    Column {
        items.forEach { (label, strong) ->
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append("·  ")
                    append(label.trimEnd())
                    append(" ")
                    if (strong != null) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2A33)))
                        append(strong)
                        pop()
                    }
                },
                style = V.text(15.sp, line = 22.sp),
                color = color
            )
        }
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

/** Contour arrondi uniforme de 1 dp (utilitaire). */
fun Modifier.hairline(shape: Shape, color: Color) = border(1.dp, color, shape)

@Composable
fun BoxScope.Fill() = Spacer(Modifier.matchParentSize())

/** Onglets de la barre du bas (maquettes) : Accueil · Trajet · [protection] · Proches · Réglages. */
enum class Tab { HOME, TRIP, CONTACTS, SETTINGS }

/** Barre du bas commune à tous les onglets, avec le bouton central d'activation de la protection. */
@Composable
fun MainNav(selected: Tab, onTab: (Tab) -> Unit, armed: Boolean, onToggle: () -> Unit, insets: Boolean = true) {
    val items = listOf(
        NavItem(stringResource(R.string.nav_home), PixelIcon.HOME) { onTab(Tab.HOME) },
        NavItem(stringResource(R.string.nav_trip), PixelIcon.ROUTE) { onTab(Tab.TRIP) },
        NavItem(stringResource(R.string.nav_contacts), PixelIcon.USERS) { onTab(Tab.CONTACTS) },
        NavItem(stringResource(R.string.nav_settings), PixelIcon.COG) { onTab(Tab.SETTINGS) }
    )
    val a11y = stringResource(if (armed) R.string.home_disc_a11y_on else R.string.home_disc_a11y_off)
    val state = stringResource(if (armed) R.string.home_toggle_on else R.string.home_toggle_off)
    PxNavBar(items, selected.ordinal, center = { ProtectionButton(armed, a11y, state, onToggle, Modifier.size(76.dp)) }, insets = insets)
}
