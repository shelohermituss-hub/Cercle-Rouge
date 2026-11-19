package com.cerclerouge.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cerclerouge.app.R
import com.cerclerouge.app.ui.components.BackHeader
import com.cerclerouge.app.security.Biometric
import androidx.compose.foundation.clickable
import com.cerclerouge.app.ui.components.BottomActions
import com.cerclerouge.app.ui.components.GlassIcon
import com.cerclerouge.app.ui.components.InkButton
import com.cerclerouge.app.ui.components.Keypad
import com.cerclerouge.app.ui.components.Marks
import com.cerclerouge.app.ui.components.MonoBox
import com.cerclerouge.app.ui.components.MonoLine
import com.cerclerouge.app.ui.components.PinDots
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PixelIconView
import com.cerclerouge.app.ui.components.RedButton
import com.cerclerouge.app.ui.components.V
import com.cerclerouge.app.ui.components.VCard
import com.cerclerouge.app.ui.components.WhiteButton
import com.cerclerouge.app.ui.components.heroCard
import com.cerclerouge.app.ui.theme.Cr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Noms courts des proches, « Jean et Nadège » / « Jean, Nadège et Paul ». */
@Composable
internal fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    else -> names.dropLast(1).joinToString(", ") + " " + stringResource(R.string.v_and) + " " + names.last()
}

/**
 * SOS · maintenir (maquette VerreSos) : grand bouton rouge de 216 dans un anneau qui se remplit en 1,5 s, ce qui part tout
 * de suite, et l'appel au 114 (composeur). TalkBack : l'action « activer » déclenche directement.
 */
@Composable
internal fun SosHoldScreen(onBack: () -> Unit, onTrigger: () -> Unit, names: List<String> = emptyList(), number: String = "114", onCall: () -> Unit = {}) {
    val c = Cr.colors
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val a11y = stringResource(R.string.sos_a11y)
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(c.surface)) {
        BackHeader(stringResource(R.string.sos), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.v_sos_hold_title),
                style = V.text(19.sp, FontWeight.Medium, 26.sp),
                color = c.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 26.dp)
            )
            Box(Modifier.padding(top = 18.dp).size(260.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(260.dp)) {
                    val w = 10.dp.toPx()
                    drawCircle(Color(0xFFF3D3D5), size.minDimension / 2 - w / 2, style = Stroke(w))
                    drawArc(
                        Color(0xFFE31B23),
                        -90f,
                        360f * progress.value,
                        false,
                        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                        style = Stroke(w, cap = StrokeCap.Round)
                    )
                }
                Column(
                    Modifier.size(216.dp)
                        .shadow(24.dp, CircleShape, ambientColor = Color(0x8CC8141D), spotColor = Color(0x8CC8141D))
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFFF0333E), Color(0xFFC8141D))))
                        .semantics {
                            role = Role.Button
                            contentDescription = a11y
                            onClick {
                                onTrigger()
                                true
                            }
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                val job = scope.launch {
                                    progress.snapTo(0f)
                                    progress.animateTo(1f, tween(1500, easing = LinearEasing))
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTrigger()
                                }
                                waitForUpOrCancellation()
                                if (progress.value < 1f) {
                                    job.cancel()
                                    scope.launch { progress.animateTo(0f, tween(200)) }
                                }
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.sos), style = V.text(52.sp, FontWeight.ExtraBold, spacing = 0.06f), color = Color.White)
                    Text(
                        stringResource(R.string.v_sos_seconds, String.format(java.util.Locale.FRANCE, "%.1f", progress.value * 1.5f)),
                        style = V.text(15.sp),
                        color = Color(0xFFFFE1E3)
                    )
                }
            }
            VCard(Modifier.padding(top = 24.dp), padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                Text(stringResource(R.string.v_sos_sends), style = V.text(15.sp, FontWeight.SemiBold), color = c.ink)
                SendsRow(R.drawable.glass_proches, if (names.isEmpty()) stringResource(R.string.v_sos_all_invited) else stringResource(R.string.v_sos_to, joinNames(names)))
                SendsRow(R.drawable.glass_pin, stringResource(R.string.v_sos_position))
                SendsRow(R.drawable.glass_lock, stringResource(R.string.v_sos_recording))
            }
        }
        BottomActions { InkButton(stringResource(R.string.v_call_police, number), onCall, height = 54.dp, icon = PixelIcon.PHONE) }
    }
}

@Composable
private fun SendsRow(icon: Int, text: String) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassIcon(icon, 28.dp)
        Text(text, style = V.text(15.sp), color = Color(0xFF2C3A47))
    }
}

/** SOS · compte à rebours (maquette VerreSosCompte) : 5 s pour annuler, anneau rouge qui se vide, puis l'alerte part. */
@Composable
internal fun CountdownScreen(onCancel: () -> Unit, onDone: () -> Unit, names: List<String> = emptyList()) {
    val c = Cr.colors
    var seconds by remember { mutableIntStateOf(COUNTDOWN) }
    val start = remember { mutableLongStateOf(System.currentTimeMillis()) }
    var frac by remember { mutableStateOf(1f) }
    val a11y = pluralStringResource(R.plurals.countdown_a11y, seconds, seconds)
    BackHandler(onBack = onCancel)
    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(100)
            val left = COUNTDOWN * 1000 - (System.currentTimeMillis() - start.longValue)
            frac = (left / (COUNTDOWN * 1000f)).coerceIn(0f, 1f)
            seconds = ((left + 999) / 1000).toInt().coerceAtLeast(0)
        }
        onDone()
    }
    Column(Modifier.fillMaxSize().background(c.surface)) {
        Box(Modifier.padding(start = 16.dp, top = 16.dp)) { Wordmark(height = 36.dp) }
        Column(Modifier.weight(1f).fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.v_alert_in), style = V.dialog, color = Color(0xFFD91F36), modifier = Modifier.semantics { heading() })
            Box(Modifier.padding(top = 22.dp).size(220.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(220.dp)) {
                    val w = 12.dp.toPx()
                    val r = 88.dp.toPx()
                    drawCircle(Color.White, r)
                    drawCircle(Color(0xFFF3D3D5), r, style = Stroke(w))
                    drawArc(
                        Color(0xFFE31B23),
                        -90f,
                        360f * frac,
                        false,
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - r, center.y - r),
                        size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
                        style = Stroke(w, cap = StrokeCap.Round)
                    )
                }
                Text(
                    "$seconds",
                    style = V.text(84.sp, FontWeight.Bold, spacing = -0.02f),
                    color = c.ink,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Assertive
                        contentDescription = a11y
                    }
                )
            }
            Text(
                if (names.isEmpty()) stringResource(R.string.countdown_body) else stringResource(R.string.v_countdown_names, joinNames(names)),
                style = V.text(17.sp, line = 24.sp),
                color = Color(0xFF2C3A47),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 34.dp, end = 34.dp, top = 22.dp)
            )
        }
        BottomActions {
            WhiteButton(stringResource(R.string.v_cancel_mistake), onCancel, height = 62.dp)
            Text(stringResource(R.string.v_countdown_note, seconds), style = V.small, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

private const val COUNTDOWN = 5

/** Fond flouté des dialogues d'alerte (maquette : silhouettes de l'accueil, voile ardoise). */
@Composable
private fun BlurredHome() {
    Box(Modifier.fillMaxSize().blur(3.dp)) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 74.dp).fillMaxWidth().height(186.dp).clip(RoundedCornerShape(34.dp)).background(Color.White.copy(alpha = 0.6f)))
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 300.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f).height(156.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.6f)))
            Box(Modifier.weight(1f).height(156.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.6f)))
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0x2E1F2A33)))
}

/**
 * Vérification « Tout va bien ? » (maquette VerreVerification) : pourquoi le veilleur demande, temps restant avant de
 * prévenir les proches, « Oui, je vais bien » (code) et « Non, alerter maintenant ».
 */
@Composable
internal fun VerificationContent(reason: String, names: List<String>, secondsLeft: Long?, totalSeconds: Long, onSafe: () -> Unit, onHelp: () -> Unit) {
    val c = Cr.colors
    Box(Modifier.fillMaxSize().background(c.surface)) {
        BlurredHome()
        Column(
            Modifier.align(Alignment.TopCenter).padding(start = 20.dp, end = 20.dp, top = 150.dp).fillMaxWidth().heroCard().padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassIcon(R.drawable.glass_terminal, 64.dp)
            Text(stringResource(R.string.v_all_ok), style = V.dialog, color = c.ink, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            Text(
                if (names.isEmpty()) reason else stringResource(R.string.v_verify_body, reason, joinNames(names)),
                style = V.text(16.sp, line = 23.sp),
                color = Color(0xFF2C3A47),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
            secondsLeft?.let { left ->
                Box(Modifier.padding(top = 16.dp).width(220.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE4E9EE))) {
                    Box(Modifier.fillMaxWidth((left.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE31B23)))
                }
                Text(
                    stringResource(R.string.v_alert_in_time, "%d:%02d".format(left / 60, left % 60)),
                    style = V.text(13.sp).copy(fontFamily = com.cerclerouge.app.ui.theme.Code),
                    color = Color(0xFF8A1219),
                    modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WhiteButton(stringResource(R.string.v_im_ok), onSafe)
                RedButton(stringResource(R.string.v_alert_now), onHelp)
            }
            Text(stringResource(R.string.v_safe_needs_code), style = V.small, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** Ce qui s'est passé pendant l'alerte, en lignes de terminal (heure, marque, texte). */
data class AlertLine(val time: String, val kind: String, val text: String)

/**
 * Alerte en cours (maquette VerreAlerte) : animation « Face ID », « Tes proches sont prévenus », qui voit ta position et
 * qui a répondu, le journal en lignes de terminal, « Je suis en sécurité » (code) et l'appel au 114 (composeur).
 */
@Composable
internal fun AlertContent(title: String, body: String, lines: List<AlertLine>, number: String, onSafe: () -> Unit, onCall: () -> Unit, face: @Composable (Dp) -> Unit) {
    val c = Cr.colors
    Box(Modifier.fillMaxSize().background(c.surface)) {
        BlurredHome()
        Box(Modifier.padding(start = 16.dp, top = 16.dp)) { Wordmark(height = 36.dp) }
        Column(
            Modifier.align(Alignment.TopCenter).padding(start = 20.dp, end = 20.dp, top = 150.dp).fillMaxWidth().heroCard().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            face(104.dp)
            Text(
                title,
                style = V.dialog,
                color = Color(0xFFD91F36),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp).semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                }
            )
            Text(body, style = V.text(16.sp, line = 23.sp), color = Color(0xFF2C3A47), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            if (lines.isNotEmpty()) {
                MonoBox(
                    lines.map { l ->
                        when (l.kind) {
                            "ok" -> MonoLine("✓", Marks.done, "${l.time} ${l.text}")
                            "th" -> MonoLine("◆", Marks.thought, "${l.time} ${l.text}")
                            else -> MonoLine("→", Marks.next, "${l.time} ${l.text}")
                        }
                    },
                    Modifier.padding(top = 14.dp),
                    side = 0.dp
                )
            }
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WhiteButton(stringResource(R.string.urgent_safe), onSafe)
                InkButton(stringResource(R.string.v_call_police, number), onCall, height = 54.dp, icon = PixelIcon.PHONE)
            }
            Text(stringResource(R.string.v_safe_needs_code), style = V.small, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/**
 * Saisie du code (maquette VerrePin), plein écran : bouclier, explication, 4 points, erreur, empreinte si permise, pavé.
 * Même logique qu'avant : vérification, succès puis fermeture, nombre d'essais compté par le moteur.
 */
@Composable
internal fun PinDialog(
    onDismiss: () -> Unit,
    verify: suspend (String) -> Boolean,
    onDone: () -> Unit,
    onBiometric: (suspend () -> Unit)? = null,
    reason: String? = null
) {
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val bioTitle = stringResource(R.string.bio_title)
    val bioCancel = stringResource(R.string.bio_use_pin)
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        PinContent(
            pin,
            error,
            reason ?: stringResource(R.string.v_pin_reason),
            showBio = onBiometric != null && Biometric.available(activity),
            onBack = onDismiss,
            onDigit = { d ->
                if (busy || pin.length >= 4) return@PinContent
                error = false
                pin += d
                if (pin.length == 4) {
                    busy = true
                    val v = pin
                    scope.launch {
                        val ok = verify(v)
                        pin = ""
                        busy = false
                        if (ok) onDone() else error = true
                    }
                }
            },
            onDelete = { if (!busy) pin = pin.dropLast(1) },
            onBio = {
                scope.launch {
                    if (onBiometric != null && Biometric.prompt(activity, bioTitle, bioCancel)) {
                        onBiometric()
                        onDone()
                    }
                }
            }
        )
    }
}

@Composable
internal fun PinContent(pin: String, error: Boolean, reason: String, showBio: Boolean, onBack: () -> Unit, onDigit: (String) -> Unit, onDelete: () -> Unit, onBio: () -> Unit) {
    val c = Cr.colors
    Column(Modifier.fillMaxSize().background(c.surface).padding(bottom = 28.dp)) {
        Spacer(Modifier.height(24.dp))
        BackHeader(stringResource(R.string.v_your_code), onBack)
        Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GlassIcon(R.drawable.glass_shield, 64.dp)
            Text(reason, style = V.text(17.sp, line = 24.sp), color = Color(0xFF2C3A47), textAlign = TextAlign.Center, modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp))
            PinDots(pin.length, 4, Modifier.padding(top = 30.dp))
            if (error) {
                Text(
                    stringResource(R.string.pin_wrong),
                    style = V.text(14.sp),
                    color = Color(0xFF8A1219),
                    modifier = Modifier.padding(top = 14.dp).semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            if (showBio) {
                Row(
                    Modifier.padding(top = 14.dp).clip(RoundedCornerShape(22.dp)).background(c.raised)
                        .clickable(role = Role.Button, onClick = onBio).padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PixelIconView(PixelIcon.FINGER, 20.dp, c.ink, strokeWidth = 2f)
                    Text(stringResource(R.string.bio_button), style = V.text(15.sp, FontWeight.Medium), color = c.ink)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Keypad(onDigit, onDelete)
    }
}


/** Écran d'urgence : vérification en attente, ou alerte envoyée (proches, réponses, journal). */
@Composable
internal fun EmergencyScreen(vm: HomeViewModel, state: com.cerclerouge.alert.EngineState, number: String, onSafe: () -> Unit, onCall: () -> Unit) {
    com.cerclerouge.app.security.SecureScreen() // S7.04 : pas de capture de l'écran d'urgence
    val book by vm.contacts.book.collectAsState()
    val feedback by vm.server.feedback.collectAsState()
    val names = book.contacts.filter { it.status == com.cerclerouge.contacts.ContactStatus.ACCEPTED }.sortedBy { it.position }.map { it.name.substringBefore(' ') }.take(3)
    val pending = state.protection == com.cerclerouge.domain.ProtectionState.ALERT_PENDING
    if (pending) {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                now = System.currentTimeMillis()
            }
        }
        val opened = state.alert?.openedAt?.toEpochMilli()
        val deadline = state.escalateAt?.toEpochMilli() ?: state.verifyAt?.toEpochMilli()
        val left = deadline?.let { ((it - now) / 1000).coerceAtLeast(0) }
        val total = if (deadline != null && opened != null) ((deadline - opened) / 1000).coerceAtLeast(1) else 60
        VerificationContent(
            reason = state.alert?.reason?.let { com.cerclerouge.app.ui.history.reasonLabel(it.name) } ?: stringResource(R.string.v_verify_generic),
            names = names,
            secondsLeft = left,
            totalSeconds = total,
            onSafe = onSafe,
            onHelp = { vm.controller.send { com.cerclerouge.alert.Input.NeedHelp(it) } }
        )
    } else {
        var log by remember { mutableStateOf(emptyList<com.cerclerouge.privacy.log.ChainedEntry>()) }
        LaunchedEffect(state, feedback) { log = vm.log.latest(6) }
        val time = remember { java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").withZone(java.time.ZoneId.systemDefault()) }
        val replies = feedback.sortedByDescending { it.at }.mapNotNull { f ->
            val who = f.label ?: stringResource(R.string.urgent_fb_someone)
            when (f.action) {
                "TOOK_CONTACT" -> stringResource(R.string.urgent_fb_took, who)
                "FALSE_ALARM" -> stringResource(R.string.urgent_fb_false, who)
                "CALLED_EMERGENCY" -> stringResource(R.string.urgent_fb_called, who)
                else -> null
            }
        }
        val body = buildString {
            append(
                when {
                    book.contacts.isEmpty() -> stringResource(R.string.emergency_no_contacts, number)
                    names.isEmpty() -> pluralStringResource(R.plurals.urgent_notified, book.contacts.size, book.contacts.size)
                    else -> stringResource(R.string.v_alert_see_position, joinNames(names))
                }
            )
            replies.firstOrNull()?.let { append(" ").append(it).append(".") }
            append(" ").append(stringResource(R.string.v_alert_safe_place))
        }
        AlertContent(
            title = stringResource(if (names.isEmpty()) R.string.urgent_sent else R.string.v_alert_title),
            body = body,
            lines = log.map { e ->
                val t = time.format(java.time.Instant.ofEpochMilli(e.utcMillis))
                AlertLine(t, if (e.type == "evidence" || e.type == "brain" || e.type == "risk") "th" else if (e.type == "dispatch") "ok" else "nx", e.payload.take(48))
            },
            number = number,
            onSafe = onSafe,
            onCall = onCall,
            face = { FaceIdLoop(it) }
        )
    }
}
