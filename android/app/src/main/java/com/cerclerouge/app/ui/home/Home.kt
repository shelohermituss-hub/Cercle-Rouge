package com.cerclerouge.app.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import com.cerclerouge.alert.Auth
import com.cerclerouge.alert.EngineState
import com.cerclerouge.alert.Input
import com.cerclerouge.alert.RejectReason
import com.cerclerouge.app.R
import com.cerclerouge.app.protection.ProtectionController
import com.cerclerouge.app.protection.UiEvent
import com.cerclerouge.app.ui.components.ChipTone
import com.cerclerouge.app.ui.components.PinPad
import com.cerclerouge.app.ui.components.PrimaryButton
import com.cerclerouge.app.ui.components.Radar
import com.cerclerouge.app.ui.components.Satellite
import com.cerclerouge.app.ui.components.SecondaryButton
import com.cerclerouge.app.ui.components.SosButton
import com.cerclerouge.app.ui.components.StatusChip
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.HudType
import com.cerclerouge.domain.ProtectionState
import com.cerclerouge.privacy.pin.PinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(val controller: ProtectionController, private val pins: PinRepository) : ViewModel() {
    suspend fun pinConfigured() = pins.isConfigured()
}

/** Ce que l'utilisateur doit confirmer par PIN avant de l'envoyer au moteur. */
private enum class Guarded { DISARM, IM_SAFE }

/** Accueil « Radar » (docs/design/README.md) : SOS au centre, 4 actions autour, état toujours visible. */
@Composable
fun HomeScreen(vm: HomeViewModel, onJournal: () -> Unit) {
    val c = Cr.colors
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val state by vm.controller.state.collectAsState()
    var countdown by remember { mutableIntStateOf(-1) }
    var pinFor by remember { mutableStateOf<Guarded?>(null) }
    val soon = stringResource(R.string.home_soon)

    fun dial114() = context.startActivity(Intent(Intent.ACTION_DIAL, "tel:114".toUri())) // R-HOME-07 : toujours le composeur

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast(context, resources.getString(R.string.mic_needed))
        vm.controller.send { Input.PermissionsChanged(it, mic = granted, location = state.locationGranted) }
        vm.controller.send { Input.Arm(it) }
    }

    LaunchedEffect(Unit) {
        val mic = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val loc = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        vm.controller.send { Input.PermissionsChanged(it, mic, loc) } // R-PE-01 : état réel des autorisations
    }
    LaunchedEffect(Unit) {
        vm.controller.events.collect { e ->
            when (e) {
                UiEvent.OpenEmergencyDialer -> dial114()
                is UiEvent.Rejected -> Toast.makeText(context, resources.getString(e.reason.message()), Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        } else if (countdown == 0) {
            countdown = -1
            vm.controller.send { Input.Sos(it) }
        }
    }

    fun guarded(action: Guarded) = scope.launch {
        if (vm.pinConfigured()) {
            pinFor = action
        } else {
            vm.controller.send { if (action == Guarded.DISARM) Input.Disarm(it, Auth.NO_PIN) else Input.ImSafe(it, Auth.NO_PIN) }
        }
    }

    fun toggleProtection() {
        if (state.protection == ProtectionState.OFF) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO) // demandé en contexte (R-PLAY-02)
        } else {
            guarded(Guarded.DISARM)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bgBase)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onJournal) { Text(stringResource(R.string.home_journal)) }
        }
        StatusChip(stringResource(state.label()), state.tone(), pulse = state.protection.isArmed)

        if (state.protection.isAlert) {
            EmergencyPanel(state, onSafe = { guarded(Guarded.IM_SAFE) }, onCall = ::dial114)
        } else {
            val armed = state.protection.isArmed
            Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
                Radar(active = armed && state.micGranted, size = 280.dp)
                SosButton(onTrigger = { countdown = 5 })
                Box(Modifier.align(Alignment.TopCenter).offset(y = (-8).dp)) {
                    Satellite(stringResource(R.string.home_sat_listen), highlighted = armed, onClick = ::toggleProtection) {
                        Icon(R.drawable.ic_mic)
                    }
                }
                Box(Modifier.align(Alignment.CenterEnd).offset(x = 24.dp)) {
                    Satellite(stringResource(R.string.home_sat_trip), highlighted = false, onClick = {
                        toast(context, soon)
                    }) { Icon(R.drawable.ic_route) }
                }
                Box(Modifier.align(Alignment.BottomCenter).offset(y = 24.dp)) {
                    Satellite(stringResource(R.string.home_sat_call), highlighted = false, onClick = ::dial114) {
                        Icon(R.drawable.ic_phone)
                    }
                }
                Box(Modifier.align(Alignment.CenterStart).offset(x = (-24).dp)) {
                    Satellite(stringResource(R.string.home_sat_contacts), highlighted = false, onClick = {
                        toast(context, soon)
                    }) { Icon(R.drawable.ic_users) }
                }
            }
            Spacer(Modifier.height(40.dp))
            Text(stringResource(R.string.sos_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Text(
                stringResource(if (armed) R.string.home_hud_local else R.string.home_hud_off),
                style = HudType.label,
                color = c.textSecondary,
                textAlign = TextAlign.Center
            )
            if (armed) {
                SecondaryButton(stringResource(R.string.home_deactivate), ::toggleProtection)
            } else {
                PrimaryButton(stringResource(R.string.home_activate), ::toggleProtection)
            }
        }
    }

    if (countdown > 0) SosCountdown(countdown) { countdown = -1 }
    pinFor?.let { action ->
        PinDialog(
            onDismiss = { pinFor = null },
            onPin = { pin ->
                pinFor = null
                scope.launch {
                    val auth = vm.controller.authFor(pin)
                    vm.controller.send { if (action == Guarded.DISARM) Input.Disarm(it, auth) else Input.ImSafe(it, auth) }
                    if (auth == Auth.FAILED) toast(context, resources.getString(R.string.pin_wrong))
                }
            }
        )
    }
}

/** Icône décorative : le libellé du satellite en dessous porte le sens (TalkBack). */
@Composable
private fun Icon(res: Int) =
    androidx.compose.material3.Icon(painterResource(res), contentDescription = null, modifier = Modifier.size(22.dp))

private fun toast(context: android.content.Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

@Composable
private fun EmergencyPanel(state: EngineState, onSafe: () -> Unit, onCall: () -> Unit) {
    val c = Cr.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.emergency_title), style = MaterialTheme.typography.headlineSmall, color = c.alerte)
        state.alert?.let {
            Text(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault()).format(it.openedAt),
                style = HudType.md
            )
        }
        Text(stringResource(R.string.emergency_no_contacts), style = MaterialTheme.typography.bodyLarge)
        PrimaryButton(stringResource(R.string.home_sat_call), onCall, color = c.alerte)
        SecondaryButton(stringResource(R.string.emergency_safe), onSafe)
    }
}

@Composable
private fun SosCountdown(seconds: Int, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.sos_countdown)) },
        text = {
            Text("$seconds", style = HudType.xl, color = Cr.colors.alerte, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        },
        confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onPin: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_enter)) },
        text = {
            PinPad(pin, { v ->
                pin = v
                if (v.length == 4) onPin(v)
            }, maxLength = 4)
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private fun EngineState.label(): Int = when (protection) {
    ProtectionState.OFF -> R.string.protection_state_off
    ProtectionState.ARMED_AUDIO_ONLY -> R.string.protection_state_armed_audio
    ProtectionState.ARMED_FULL -> R.string.protection_state_armed_full
    ProtectionState.TRIP_ACTIVE -> R.string.protection_state_trip
    ProtectionState.ALERT_PENDING -> R.string.protection_state_pending
    ProtectionState.ALERT_SENT -> R.string.protection_state_sent
    ProtectionState.EMERGENCY_MODE -> R.string.protection_state_emergency
}

private fun EngineState.tone(): ChipTone = when (protection) {
    ProtectionState.OFF -> ChipTone.OFF
    ProtectionState.ARMED_AUDIO_ONLY, ProtectionState.ARMED_FULL, ProtectionState.TRIP_ACTIVE -> ChipTone.PROTECTED
    ProtectionState.ALERT_PENDING -> ChipTone.VIGILANCE
    ProtectionState.ALERT_SENT, ProtectionState.EMERGENCY_MODE -> ChipTone.ALERT
}

private fun RejectReason.message(): Int = when (this) {
    RejectReason.CONSENT_REQUIRED -> R.string.reject_consent
    RejectReason.NO_SENSOR -> R.string.reject_sensor
    RejectReason.PIN_REQUIRED -> R.string.reject_pin
    RejectReason.PIN_INVALID -> R.string.pin_wrong
    RejectReason.LOCATION_REQUIRED -> R.string.reject_location
}
