package com.cerclerouge.app.ui.listen

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cerclerouge.alert.IgnoreContext
import com.cerclerouge.alert.Sensitivity
import com.cerclerouge.app.R
import com.cerclerouge.app.protection.ListenPrefs
import com.cerclerouge.app.protection.ListenSettings
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Durées proposées pour un contexte à ignorer. */
enum class ContextDuration { ONE_HOUR, THREE_HOURS, UNTIL_TOMORROW }

/**
 * Réglages de la détection (PDF 8.3, 8.6 ; S5.09) : sensibilité, délai de vérification, contextes à ignorer avec leur durée.
 * Un contexte relève les seuils des sons concernés ; il ne coupe jamais la phrase secrète, les mots-clés sûrs ni le SOS.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetectionSettings(
    prefs: ListenPrefs,
    now: Instant,
    onSensitivity: (Sensitivity) -> Unit,
    onVerify: (Int) -> Unit,
    onContext: (IgnoreContext, ContextDuration?) -> Unit
) {
    val c = Cr.colors
    Text(stringResource(R.string.detect_title).uppercase(), style = PxType.label, color = c.lime)
    Text(stringResource(R.string.detect_sensitivity), style = PxType.bodyBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Sensitivity.entries.forEach { s ->
            Choice(stringResource(s.label()), s == prefs.sensitivity) { onSensitivity(s) }
        }
    }
    Text(stringResource(R.string.detect_sensitivity_ex), style = PxType.caption, color = c.muted)

    Text(stringResource(R.string.detect_verify), style = PxType.bodyBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ListenSettings.VERIFY_CHOICES.forEach { sec ->
            Choice(stringResource(R.string.detect_seconds, sec), sec == prefs.verifySeconds) { onVerify(sec) }
        }
    }

    Text(stringResource(R.string.detect_contexts), style = PxType.bodyBold)
    Text(stringResource(R.string.detect_contexts_ex), style = PxType.caption, color = c.muted)
    IgnoreContext.entries.forEach { ctx ->
        val until = prefs.ignored[ctx]?.takeIf { it.isAfter(now) }
        PxPanel(Modifier.fillMaxWidth(), border = if (until != null) c.warn else c.line) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(ctx.label()).uppercase(), style = PxType.bodyBold)
                if (until != null) {
                    Text(
                        stringResource(R.string.detect_context_until, HHMM.format(until.atZone(ZoneId.systemDefault()))),
                        style = PxType.caption,
                        color = c.warn
                    )
                    Choice(stringResource(R.string.detect_context_stop), false) { onContext(ctx, null) }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Choice(stringResource(R.string.detect_1h), false) { onContext(ctx, ContextDuration.ONE_HOUR) }
                        Choice(stringResource(R.string.detect_3h), false) { onContext(ctx, ContextDuration.THREE_HOURS) }
                        Choice(stringResource(R.string.detect_tomorrow), false) { onContext(ctx, ContextDuration.UNTIL_TOMORROW) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Cr.colors
    Box(
        Modifier.heightIn(min = 48.dp).border(2.dp, if (on) c.lime else c.lineControl)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text.uppercase(), style = PxType.button, color = if (on) c.lime else c.ink) }
}

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun Sensitivity.label(): Int = when (this) {
    Sensitivity.LOW -> R.string.detect_low
    Sensitivity.NORMAL -> R.string.detect_normal
    Sensitivity.HIGH -> R.string.detect_high
}

private fun IgnoreContext.label(): Int = when (this) {
    IgnoreContext.CONCERT -> R.string.detect_ctx_concert
    IgnoreContext.SPORT -> R.string.detect_ctx_sport
    IgnoreContext.DRIVING -> R.string.detect_ctx_driving
    IgnoreContext.SLEEP -> R.string.detect_ctx_sleep
}
