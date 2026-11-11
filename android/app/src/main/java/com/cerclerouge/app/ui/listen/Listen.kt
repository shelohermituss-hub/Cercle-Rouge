package com.cerclerouge.app.ui.listen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerclerouge.app.R
import com.cerclerouge.app.audio.AudioListener
import com.cerclerouge.app.audio.AudioStatus
import com.cerclerouge.app.audio.PhraseRepository
import com.cerclerouge.app.ui.components.Orb
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PxButton
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.PxVariant
import com.cerclerouge.app.ui.components.TopBar
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.cerclerouge.audio.DistressKeywords
import com.cerclerouge.audio.Enrollment
import com.cerclerouge.audio.PhraseTemplate
import com.cerclerouge.audio.SampleProblem
import com.cerclerouge.audio.SecretPhrase
import com.cerclerouge.privacy.log.EventLog
import com.valentinerutto.orbmotion.orbs.OrbState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Étape de l'enrôlement ou du test, affichée à l'écran. */
sealed interface Step {
    data object Idle : Step

    /** Écoute de la répétition `index` (0 à 2), ou du test si `test`. */
    data class Recording(val index: Int, val test: Boolean = false) : Step

    data class Message(val text: Int, val ok: Boolean) : Step
}

data class ListenUi(
    val enrolled: Boolean = false,
    val step: Step = Step.Idle,
    val samples: Int = 0,
    val level: Double = -90.0,
    val testConfidence: Double? = null
)

@HiltViewModel
class ListenViewModel @Inject constructor(
    private val audio: AudioListener,
    private val phrases: PhraseRepository,
    private val log: EventLog
) : ViewModel() {
    val status: StateFlow<AudioStatus> = audio.status
    private val _ui = MutableStateFlow(ListenUi())
    val ui: StateFlow<ListenUi> = _ui
    private val samples = mutableListOf<ShortArray>()
    val granted: Boolean get() = audio.granted

    init {
        viewModelScope.launch { _ui.update { it.copy(enrolled = phrases.load() != null) } }
    }

    /** Enregistre la répétition suivante ; à la 3ᵉ, calcule l'empreinte. Les répétitions ne vivent qu'en mémoire. */
    fun recordNext() = viewModelScope.launch {
        val index = samples.size
        _ui.update { it.copy(step = Step.Recording(index), testConfidence = null) }
        val pcm = capture()
        if (pcm == null) {
            _ui.update { it.copy(step = Step.Message(R.string.listen_nothing_heard, ok = false)) }
            return@launch
        }
        samples += pcm
        _ui.update { it.copy(samples = samples.size, step = Step.Idle) }
        if (samples.size < SecretPhrase.MIN_REPETITIONS) return@launch
        val result = withContext(Dispatchers.Default) { SecretPhrase.enroll(samples.toList()) }
        samples.forEach { it.fill(0) }
        samples.clear()
        when (result) {
            is Enrollment.Ok -> save(result.template)
            is Enrollment.BadSample -> _ui.update {
                it.copy(samples = 0, step = Step.Message(result.problem.message(), ok = false))
            }
            Enrollment.Inconsistent -> _ui.update { it.copy(samples = 0, step = Step.Message(R.string.listen_inconsistent, ok = false)) }
        }
    }

    /** Dis ta phrase une fois pour vérifier qu'elle est reconnue (rien n'est déclenché). */
    fun test() = viewModelScope.launch {
        val t = phrases.load() ?: return@launch
        _ui.update { it.copy(step = Step.Recording(0, test = true), testConfidence = null) }
        val pcm = capture()
        if (pcm == null) {
            _ui.update { it.copy(step = Step.Message(R.string.listen_nothing_heard, ok = false)) }
            return@launch
        }
        val m = withContext(Dispatchers.Default) { SecretPhrase.match(t, pcm) }
        pcm.fill(0)
        _ui.update {
            it.copy(
                step = Step.Message(if (m.detected) R.string.listen_test_ok else R.string.listen_test_ko, ok = m.detected),
                testConfidence = m.confidence
            )
        }
    }

    fun delete() = viewModelScope.launch {
        phrases.delete()
        withContext(Dispatchers.Default) { audio.restartWith(null) }
        log.append("audio", "secret_phrase_deleted")
        _ui.update { ListenUi(enrolled = false, step = Step.Message(R.string.listen_deleted, ok = true)) }
    }

    fun restart() {
        samples.forEach { it.fill(0) }
        samples.clear()
        _ui.update { it.copy(samples = 0, step = Step.Idle) }
    }

    private suspend fun save(t: PhraseTemplate) {
        phrases.save(t)
        // Si l'écoute tourne déjà, elle reprend avec la nouvelle empreinte ; sinon elle démarrera à l'activation.
        withContext(Dispatchers.Default) { audio.restartWith(t) }
        log.append("audio", "secret_phrase_enrolled model=${SecretPhrase.MODEL_VERSION}")
        _ui.update { it.copy(enrolled = true, samples = 0, step = Step.Message(R.string.listen_enrolled, ok = true)) }
    }

    private suspend fun capture(): ShortArray? = withContext(Dispatchers.IO) {
        audio.recordUtterance(CAPTURE_MS) { db -> _ui.update { it.copy(level = db) } }
    }

    private fun SampleProblem.message(): Int = when (this) {
        SampleProblem.TOO_SHORT -> R.string.listen_too_short
        SampleProblem.TOO_LONG -> R.string.listen_too_long
        SampleProblem.TOO_QUIET -> R.string.listen_too_quiet
    }

    private companion object {
        const val CAPTURE_MS = 7_000L
    }
}

/**
 * Écran « Écoute IA » (S5.06, S5.09) : état de l'écoute, phrase secrète (enrôlement en 3 répétitions, test, suppression),
 * mots-clés par défaut. Tout reste sur le téléphone : seule une empreinte chiffrée est gardée (R-AUD-09).
 */
@Composable
fun ListenScreen(vm: ListenViewModel, onBack: () -> Unit) {
    val c = Cr.colors
    val ui by vm.ui.collectAsState()
    val status by vm.status.collectAsState()
    var granted by remember { mutableStateOf(vm.granted) }
    var confirmDelete by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val busy = ui.step is Step.Recording

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.listen_title), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PxPanel(Modifier.fillMaxWidth(), border = if (status == AudioStatus.RUNNING) c.lime else c.line) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Orb(
                        if (status ==
                            AudioStatus.RUNNING
                        ) {
                            OrbState.LISTENING
                        } else {
                            OrbState.BREATHING
                        },
                        48.dp,
                        if (status ==
                            AudioStatus.RUNNING
                        ) {
                            c.lime
                        } else {
                            c.muted
                        }
                    )
                    Text(
                        stringResource(status.label()),
                        style = PxType.bodyBold,
                        color = if (status == AudioStatus.PAUSED_MIC_BUSY || status == AudioStatus.PAUSED_LOW_POWER) c.warn else c.ink,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
            Text(stringResource(R.string.listen_privacy), style = PxType.caption, color = c.muted)

            Text(stringResource(R.string.listen_phrase_title).uppercase(), style = PxType.label, color = c.lime)
            Text(stringResource(R.string.listen_phrase_intro), style = PxType.body)
            if (!granted) {
                PxButton(stringResource(R.string.listen_allow_mic), {
                    permission.launch(Manifest.permission.RECORD_AUDIO)
                }, icon = PixelIcon.MIC)
            } else if (!ui.enrolled) {
                Repetitions(ui.samples, busy)
                Level(ui.level, busy)
                PxButton(
                    if (busy) {
                        stringResource(R.string.listen_speak_now_short)
                    } else {
                        stringResource(R.string.listen_record_n, ui.samples + 1, SecretPhrase.MIN_REPETITIONS)
                    },
                    { vm.recordNext() },
                    icon = PixelIcon.MIC,
                    tall = true,
                    enabled = !busy
                )
                if (ui.samples > 0 &&
                    !busy
                ) {
                    PxButton(stringResource(R.string.listen_restart), { vm.restart() }, variant = PxVariant.OUTLINE)
                }
            } else {
                Level(ui.level, busy)
                PxButton(stringResource(if (busy) R.string.listen_speak_now_short else R.string.listen_test), {
                    vm.test()
                }, icon = PixelIcon.MIC, enabled = !busy)
                PxButton(stringResource(R.string.listen_delete), { confirmDelete = true }, variant = PxVariant.DARK_DANGER, enabled = !busy)
            }
            (ui.step as? Step.Message)?.let { m ->
                Text(
                    stringResource(m.text),
                    style = PxType.bodyBold,
                    color = if (m.ok) c.lime else c.warn,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            ui.testConfidence?.let {
                Text(stringResource(R.string.listen_confidence, (it * 100).toInt()), style = PxType.caption, color = c.muted)
            }

            Text(stringResource(R.string.listen_keywords_title).uppercase(), style = PxType.label, color = c.lime)
            Text(stringResource(R.string.listen_keywords_intro), style = PxType.body, color = c.muted)
            Text((DistressKeywords.HT + DistressKeywords.FR).joinToString(" · "), style = PxType.bodyBold)
        }
    }
    if (confirmDelete) {
        Dialog(onDismissRequest = { confirmDelete = false }) {
            PxPanel(border = c.danger, fill = c.surface, padding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.listen_delete).uppercase(), style = PxType.title)
                    Text(stringResource(R.string.listen_delete_confirm), style = PxType.body)
                    PxButton(stringResource(R.string.listen_delete), {
                        confirmDelete = false
                        vm.delete()
                    }, variant = PxVariant.DANGER)
                    PxButton(stringResource(R.string.action_cancel), { confirmDelete = false }, variant = PxVariant.OUTLINE)
                }
            }
        }
    }
}

private fun AudioStatus.label(): Int = when (this) {
    AudioStatus.RUNNING -> R.string.listen_status_running
    AudioStatus.PAUSED_MIC_BUSY -> R.string.listen_status_paused
    AudioStatus.STOPPED -> R.string.listen_status_stopped
    AudioStatus.PAUSED_LOW_POWER -> R.string.listen_status_low_power
    AudioStatus.ERROR -> R.string.listen_status_error
}

/** Trois cases : les répétitions déjà enregistrées. */
@Composable
private fun Repetitions(done: Int, busy: Boolean) {
    val c = Cr.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(SecretPhrase.MIN_REPETITIONS) { i ->
            val color = when {
                i < done -> c.lime
                i == done && busy -> c.warn
                else -> c.line
            }
            Box(Modifier.size(width = 56.dp, height = 12.dp).background(color))
        }
    }
}

/** Niveau du micro pendant la capture (−60 à 0 dBFS), en barre pixel. */
@Composable
private fun Level(db: Double, active: Boolean) {
    val c = Cr.colors
    val fraction = if (active) ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat() else 0f
    Box(Modifier.fillMaxWidth().height(10.dp).background(c.raised)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(if (fraction > 0.75f) c.warn else c.lime))
    }
}
