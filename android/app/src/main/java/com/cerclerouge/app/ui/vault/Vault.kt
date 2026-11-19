package com.cerclerouge.app.ui.vault

import android.media.MediaDataSource
import android.media.MediaPlayer
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerclerouge.app.R
import com.cerclerouge.app.evidence.EvidenceSettings
import com.cerclerouge.app.evidence.EvidenceStore
import com.cerclerouge.app.evidence.EvidenceSummary
import com.cerclerouge.app.security.SecuritySettings
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PxButton
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.PxVariant
import com.cerclerouge.app.ui.home.PinDialog
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.cerclerouge.evidence.EvidenceExport
import com.cerclerouge.evidence.RetentionPolicy
import com.cerclerouge.privacy.Settings
import com.cerclerouge.privacy.log.EventLog
import com.cerclerouge.privacy.pin.PinRepository
import com.cerclerouge.privacy.pin.PinResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VaultUi(
    val unlocked: Boolean = false,
    val items: List<EvidenceSummary> = emptyList(),
    val audioEnabled: Boolean = true,
    val message: Int? = null,
    val playing: String? = null,
    val verified: Map<String, Boolean> = emptyMap(),
    /** Export prêt : le code (affiché une seule fois) et l'archive à enregistrer. */
    val export: Pair<String, ByteArray>? = null,
    /** Conservation manuelle (ADR-0027) : rien n'est supprimé automatiquement. */
    val manual: Boolean = false,
    /** Métadonnées affichées (alerte, lignes), déchiffrées en mémoire. */
    val details: Pair<String, List<String>>? = null
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val store: EvidenceStore,
    private val settings: Settings,
    private val evidence: EvidenceSettings,
    private val pins: PinRepository,
    private val security: SecuritySettings,
    private val log: EventLog,
    private val clock: Clock,
    private val uploader: com.cerclerouge.app.evidence.EvidenceUploader
) : ViewModel() {
    private val _ui = MutableStateFlow(VaultUi())
    val ui: StateFlow<VaultUi> = _ui
    private var player: MediaPlayer? = null

    suspend fun pinConfigured() = pins.isConfigured()

    suspend fun pinOk(pin: String) = pins.verify(pin) == PinResult.Ok

    suspend fun biometric() = security.biometric()

    fun unlock() = viewModelScope.launch {
        _ui.update { it.copy(unlocked = true, audioEnabled = evidence.audioEnabled()) }
        refresh()
    }

    fun lock() {
        stopPlaying()
        _ui.value = VaultUi()
    }

    fun setAudio(on: Boolean) = viewModelScope.launch {
        evidence.setAudioEnabled(on)
        log.append("evidence", "audio_setting enabled=$on")
        _ui.update { it.copy(audioEnabled = on) }
    }

    /** Lecture : tout est déchiffré en mémoire, jamais sur disque. */
    fun play(alertId: String) = viewModelScope.launch {
        stopPlaying()
        val segments = withContext(Dispatchers.IO) { store.read(alertId) }
        if (segments == null) {
            _ui.update { it.copy(message = R.string.vault_broken) }
            return@launch
        }
        val bytes = segments.fold(ByteArray(0)) { acc, s -> acc + s }
        segments.forEach { it.fill(0) }
        val source = MemorySource(bytes)
        player = MediaPlayer().apply {
            setDataSource(source)
            setOnCompletionListener { stopPlaying() }
            prepare()
            start()
        }
        _ui.update { it.copy(playing = alertId) }
    }

    fun stopPlaying() {
        player?.runCatching {
            stop()
            release()
        }
        player = null
        _ui.update { it.copy(playing = null) }
    }

    fun details(alertId: String?) = viewModelScope.launch {
        val lines = alertId?.let { withContext(Dispatchers.IO) { store.notes(it) } }
        _ui.update { it.copy(details = alertId?.let { id -> id to lines.orEmpty() }) }
    }

    fun verify(alertId: String) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { store.verify(alertId) }
        _ui.update { it.copy(verified = it.verified + (alertId to ok)) }
    }

    fun keep(alertId: String, days: Long) = viewModelScope.launch {
        store.keep(alertId, RetentionPolicy.keep(clock.instant(), Duration.ofDays(days)))
        log.append("evidence", "kept days=$days")
        refresh()
    }

    fun delete(alertId: String) = viewModelScope.launch {
        if (_ui.value.playing == alertId) stopPlaying()
        withContext(Dispatchers.IO) { store.delete(alertId) }
        uploader.forget(alertId)
        log.append("evidence", "deleted_by_user")
        _ui.update { it.copy(message = R.string.vault_deleted) }
        refresh()
    }

    fun export(alertId: String) = viewModelScope.launch {
        val code = EvidenceExport.newCode()
        val archive = withContext(Dispatchers.Default) { store.export(alertId, code) }
        if (archive == null) {
            _ui.update { it.copy(message = R.string.vault_broken) }
            return@launch
        }
        log.append("evidence", "exported")
        _ui.update { it.copy(export = EvidenceExport.formatCode(code) to archive) }
    }

    fun exportDone(saved: Boolean) {
        _ui.update { it.copy(export = null, message = if (saved) R.string.vault_export_saved else null) }
    }

    private suspend fun refresh() {
        val retention = settings.retention()
        val items = withContext(Dispatchers.IO) { store.list(retention, clock.instant()) }
        _ui.update { it.copy(items = items, manual = retention == com.cerclerouge.domain.Retention.MANUAL) }
    }

    override fun onCleared() = stopPlaying()
}

/** Source de lecture en mémoire (déchiffrée), pour MediaPlayer. */
internal class MemorySource(private val data: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= data.size) return -1
        val n = minOf(size.toLong(), data.size - position).toInt()
        System.arraycopy(data, position.toInt(), buffer, offset, n)
        return n
    }

    override fun getSize(): Long = data.size.toLong()

    override fun close() = data.fill(0)
}

private val WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

private fun Instant.short(): String = WHEN.format(atZone(ZoneId.systemDefault()))

/**
 * Coffre-fort de preuves (S6.07) : déverrouillé par le PIN, invisible aux captures d'écran (`FLAG_SECURE`), verrouillé dès
 * qu'on le quitte. Écouter, vérifier l'intégrité, conserver (90 j au plus), supprimer, exporter chiffré ; interrupteur
 * de l'enregistrement automatique.
 */
@Composable
fun VaultScreen(vm: VaultViewModel, onBack: () -> Unit) {
    val c = Cr.colors
    val ui by vm.ui.collectAsState()
    val activity = LocalActivity.current
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            vm.lock()
        }
    }
    var askPin by remember { mutableStateOf(false) }
    var bio by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { bio = vm.biometric() }
    LaunchedEffect(Unit) { if (vm.pinConfigured()) askPin = true else vm.unlock() }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(c.surface)) {
        com.cerclerouge.app.ui.components.BackHeader(stringResource(R.string.v_vault_title), onBack, stringResource(R.string.v_vault_sub))
        if (!ui.unlocked) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.vault_locked), style = com.cerclerouge.app.ui.components.V.body, color = c.muted)
                com.cerclerouge.app.ui.components.InkButton(stringResource(R.string.vault_unlock), { askPin = true }, icon = PixelIcon.LOCK)
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 24.dp)) {
                com.cerclerouge.app.ui.components.BlueBanner(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.v_vault_count, ui.items.size, ui.items.size),
                    stringResource(R.string.v_vault_banner),
                    R.drawable.glass_lock
                )
                com.cerclerouge.app.ui.components.SectionLabel(stringResource(R.string.v_vault_recording))
                com.cerclerouge.app.ui.components.VCard(padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp)) {
                    com.cerclerouge.app.ui.components.ToggleRow(
                        stringResource(R.string.vault_audio_toggle),
                        stringResource(R.string.vault_audio_toggle_ex),
                        ui.audioEnabled
                    ) { vm.setAudio(it) }
                }
                com.cerclerouge.app.ui.components.SectionLabel(stringResource(R.string.v_kind_alert) + "s")
                ui.message?.let {
                    Text(
                        stringResource(it),
                        style = PxType.bodyBold,
                        color = c.lime,
                        modifier = Modifier.semantics {
                            liveRegion =
                                LiveRegionMode.Polite
                        }
                    )
                }
                if (ui.items.isEmpty()) {
                    Text(
                        stringResource(R.string.vault_empty),
                        style = com.cerclerouge.app.ui.components.V.body,
                        color = c.muted,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ui.items.forEach { item ->
                        EvidenceCard(item, ui, vm, onDelete = { confirmDelete = item.alertId })
                    }
                }
                Text(
                    stringResource(R.string.vault_legal),
                    style = com.cerclerouge.app.ui.components.V.small,
                    color = c.muted,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 12.dp)
                )
            }
        }
    }

    if (askPin) {
        PinDialog(
            onDismiss = {
                askPin = false
                if (!ui.unlocked) onBack()
            },
            verify = { vm.pinOk(it) },
            onDone = {
                askPin = false
                vm.unlock()
            },
            onBiometric = if (bio) {
                {}
            } else {
                null
            }
        )
    }
    confirmDelete?.let { id ->
        Dialog(onDismissRequest = { confirmDelete = null }) {
            PxPanel(border = c.danger, fill = c.surface, padding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.vault_delete), style = PxType.title)
                    Text(stringResource(R.string.vault_delete_confirm), style = PxType.body)
                    PxButton(stringResource(R.string.vault_delete), {
                        confirmDelete = null
                        vm.delete(id)
                    }, variant = PxVariant.DANGER)
                    PxButton(stringResource(R.string.action_cancel), { confirmDelete = null }, variant = PxVariant.OUTLINE)
                }
            }
        }
    }
    ui.export?.let { (code, archive) -> ExportDialog(code, archive, vm) }
    ui.details?.let { (_, lines) -> DetailsDialog(lines) { vm.details(null) } }
}

/** Métadonnées de l'alerte (positions, batterie, réseau, journal), déchiffrées en mémoire seulement. */
@Composable
private fun DetailsDialog(lines: List<String>, onDismiss: () -> Unit) {
    val c = Cr.colors
    Dialog(onDismissRequest = onDismiss) {
        PxPanel(border = c.lime, fill = c.surface, padding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.vault_details), style = PxType.title)
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (lines.isEmpty()) Text(stringResource(R.string.vault_details_empty), style = PxType.caption, color = c.muted)
                    lines.forEach { Text(it, style = PxType.caption) }
                }
                PxButton(stringResource(R.string.action_finish), onDismiss, variant = PxVariant.DARK)
            }
        }
    }
}

@Composable
private fun EvidenceCard(item: EvidenceSummary, ui: VaultUi, vm: VaultViewModel, onDelete: () -> Unit) {
    val c = Cr.colors
    var keepMenu by remember { mutableStateOf(false) }
    PxPanel(Modifier.fillMaxWidth(), border = if (ui.playing == item.alertId) c.lime else c.line) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.vault_item_title, item.openedAt.short()), style = PxType.bodyBold)
            Text(
                stringResource(R.string.vault_item_meta, item.seconds / 60, item.seconds % 60, item.bytes / 1024),
                style = PxType.caption,
                color = c.muted
            )
            Text(
                item.deleteAt?.let { stringResource(if (item.kept) R.string.vault_kept_until else R.string.vault_delete_at, it.short()) }
                    ?: stringResource(if (ui.manual) R.string.vault_manual else R.string.vault_open_alert),
                style = PxType.caption,
                color = c.warn
            )
            ui.verified[item.alertId]?.let { ok ->
                Text(
                    stringResource(if (ok) R.string.vault_verified_ok else R.string.vault_verified_ko),
                    style = PxType.caption,
                    color = if (ok) c.lime else c.danger
                )
            }
            if (ui.playing == item.alertId) {
                PxButton(stringResource(R.string.vault_stop), { vm.stopPlaying() }, icon = PixelIcon.CROSS)
            } else {
                PxButton(stringResource(R.string.vault_play), { vm.play(item.alertId) }, icon = PixelIcon.MIC, enabled = item.segments > 0)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    PxButton(stringResource(R.string.vault_verify), { vm.verify(item.alertId) }, variant = PxVariant.OUTLINE)
                }
                Column(Modifier.weight(1f)) {
                    PxButton(stringResource(R.string.vault_keep), { keepMenu = true }, variant = PxVariant.OUTLINE)
                }
            }
            PxButton(stringResource(R.string.vault_details), { vm.details(item.alertId) }, variant = PxVariant.OUTLINE)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    PxButton(stringResource(R.string.vault_export), { vm.export(item.alertId) }, variant = PxVariant.OUTLINE)
                }
                Column(Modifier.weight(1f)) { PxButton(stringResource(R.string.vault_delete), onDelete, variant = PxVariant.DARK_DANGER) }
            }
        }
    }
    if (keepMenu) {
        Dialog(onDismissRequest = { keepMenu = false }) {
            PxPanel(border = c.lime, fill = c.surface, padding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.vault_keep), style = PxType.title)
                    Text(stringResource(R.string.vault_keep_ex), style = PxType.caption, color = c.muted)
                    listOf(30L to R.string.vault_keep_30, 90L to R.string.vault_keep_90).forEach { (d, label) ->
                        PxButton(stringResource(label), {
                            keepMenu = false
                            vm.keep(item.alertId, d)
                        }, variant = PxVariant.OUTLINE)
                    }
                    PxButton(stringResource(R.string.action_cancel), { keepMenu = false }, variant = PxVariant.DARK)
                }
            }
        }
    }
}

/** Le code n'est montré qu'ici, une fois : à transmettre séparément (oralement ou par SMS), jamais avec le fichier. */
@Composable
private fun ExportDialog(code: String, archive: ByteArray, vm: VaultViewModel) {
    val c = Cr.colors
    val context = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val ok =
            uri != null &&
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(archive) } != null }.getOrDefault(false)
        vm.exportDone(ok)
    }
    Dialog(onDismissRequest = { vm.exportDone(false) }) {
        PxPanel(border = c.lime, fill = c.surface, padding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.vault_export), style = PxType.title)
                Text(stringResource(R.string.vault_export_code), style = PxType.body)
                Text(code, style = PxType.count, color = c.lime)
                Text(stringResource(R.string.vault_export_ex), style = PxType.caption, color = c.muted)
                PxButton(stringResource(R.string.vault_export_save), {
                    save.launch("cercle-rouge-preuve.crx")
                }, icon = PixelIcon.ARROW_RIGHT)
                PxButton(stringResource(R.string.action_cancel), { vm.exportDone(false) }, variant = PxVariant.OUTLINE)
            }
        }
    }
}
