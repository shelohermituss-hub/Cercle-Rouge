package com.cerclerouge.app.ui.brain

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerclerouge.app.R
import com.cerclerouge.app.contacts.ContactsRepository
import com.cerclerouge.app.protection.ProtectionController
import com.cerclerouge.app.trip.TripController
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PixelIconView
import com.cerclerouge.app.ui.components.PxButton
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.PxVariant
import com.cerclerouge.app.ui.components.TopBar
import com.cerclerouge.app.ui.components.VoiceOrb
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.cerclerouge.brain.KnowledgeBase
import com.cerclerouge.brain.TalkAction
import com.cerclerouge.brain.TalkReply
import com.cerclerouge.brain.TalkStatus
import com.cerclerouge.brain.Veilleur
import com.cerclerouge.contacts.ContactStatus
import com.cerclerouge.privacy.log.EventLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Un échange affiché : la question (effacée de la mémoire en quittant l'écran) ou la réponse du veilleur. */
data class TalkLine(val mine: Boolean, val text: String, val reply: TalkReply? = null)

/** Niveau d'appareil (ADR-0029) d'après la mémoire vive : il décide si le modèle de langage pourra tourner. */
enum class DeviceTier { BASIC, STANDARD, FULL }

fun deviceTier(context: Context): DeviceTier {
    val mi = ActivityManager.MemoryInfo()
    context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(mi)
    val gb = mi.totalMem / (1024.0 * 1024 * 1024)
    return when {
        gb < 3.5 -> DeviceTier.BASIC
        gb < 6.5 -> DeviceTier.STANDARD
        else -> DeviceTier.FULL
    }
}

/**
 * « Parler au veilleur » (IA.8) : questions écrites ou dites (bouton maintenu, reconnaissance **dans le téléphone**
 * seulement), réponses par règles et fiches locales. Rien n'est envoyé ; rien n'est gardé après la fermeture de l'écran.
 * Seule la catégorie de la question est journalisée, jamais le texte.
 */
@HiltViewModel
class TalkViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val controller: ProtectionController,
    private val trips: TripController,
    private val contacts: ContactsRepository,
    private val log: EventLog,
    private val clock: Clock
) : ViewModel() {
    private var veilleur: Veilleur? = null
    private val _lines = MutableStateFlow<List<TalkLine>>(emptyList())
    val lines: StateFlow<List<TalkLine>> = _lines

    fun ask(question: String, onReply: (TalkReply) -> Unit = {}) {
        val q = question.trim().take(MAX_QUESTION)
        if (q.isEmpty()) return
        _lines.value = _lines.value + TalkLine(true, q)
        viewModelScope.launch {
            val v = veilleur ?: withContext(Dispatchers.IO) {
                val text = context.assets.open("brain/fiches-fr.txt").bufferedReader().readText()
                Veilleur(KnowledgeBase(KnowledgeBase.parse(text)))
            }.also { veilleur = it }
            val reply = v.answer(q, status())
            log.append("brain", "talk card=${reply.card?.id ?: "-"} action=${reply.action?.name?.lowercase() ?: "-"}")
            _lines.value = (_lines.value + TalkLine(false, reply.text, reply)).takeLast(MAX_LINES)
            onReply(reply)
        }
    }

    /** Oublie la conversation (sortie de l'écran). */
    fun forget() {
        _lines.value = emptyList()
    }

    private fun status(): TalkStatus {
        val s = controller.state.value
        val book = contacts.book.value.contacts
        val left = trips.trip.value?.plan?.plannedArrival?.let { Duration.between(clock.instant(), it).toMinutes() }
        return TalkStatus(
            armed = s.protection.isArmed,
            alert = s.protection.isAlert,
            tripMinutesLeft = left,
            acceptedContacts = book.count { it.status == ContactStatus.ACCEPTED },
            pendingContacts = book.count { it.status == ContactStatus.PENDING }
        )
    }

    private companion object {
        const val MAX_QUESTION = 300
        const val MAX_LINES = 30
    }
}

/** Reconnaissance vocale sur l'appareil (Android 12+) ; null sinon : l'app ne passe jamais par un service en ligne. */
private fun onDeviceRecognizer(context: Context): SpeechRecognizer? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
        runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
    } else {
        null
    }

@Composable
fun TalkScreen(vm: TalkViewModel, onBack: () -> Unit, onArm: () -> Unit, onTrip: () -> Unit, onContacts: () -> Unit, onHome: () -> Unit) {
    val lines by vm.lines.collectAsState()
    TalkContent(lines, vm::ask, vm::forget, onBack, onArm, onTrip, onContacts, onHome)
}

@Composable
internal fun TalkContent(
    lines: List<TalkLine>,
    ask: (String, (TalkReply) -> Unit) -> Unit,
    forget: () -> Unit,
    onBack: () -> Unit,
    onArm: () -> Unit,
    onTrip: () -> Unit,
    onContacts: () -> Unit,
    onHome: () -> Unit
) {
    val c = Cr.colors
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var speak by remember { mutableStateOf(true) }
    val list = rememberLazyListState()
    val recognizer = remember { onDeviceRecognizer(context) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { ok -> if (ok == TextToSpeech.SUCCESS) engine?.language = Locale.FRENCH }
        tts.value = engine
        onDispose {
            recognizer?.destroy()
            engine.stop()
            engine.shutdown()
            forget()
        }
    }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.animateScrollToItem(lines.size - 1) }
    fun send(text: String) {
        ask(text) { r -> if (speak) tts.value?.speak(r.text, TextToSpeech.QUEUE_FLUSH, null, "veilleur") }
    }
    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                listening = false
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { send(it) }
            }

            override fun onError(error: Int) {
                listening = false
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { }
    }

    Column(Modifier.fillMaxSize().background(c.surface).imePadding()) {
        TopBar(stringResource(R.string.talk_title), onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            state = list,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    VoiceOrb(listening, 104.dp)
                    Text(
                        stringResource(R.string.talk_intro),
                        style = PxType.body,
                        color = c.muted,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            items(lines) { line -> Bubble(line, onArm, onTrip, onContacts, onHome) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.talk_tts), style = PxType.caption, color = c.muted, modifier = Modifier.weight(1f))
            Switch(checked = speak, onCheckedChange = {
                speak = it
                if (!it) tts.value?.stop()
            })
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(300) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.talk_input)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    send(input)
                    input = ""
                })
            )
            val sendLabel = stringResource(R.string.talk_send)
            if (input.isNotBlank() || recognizer == null) {
                RoundAction(PixelIcon.ARROW_RIGHT, sendLabel, c.ink) {
                    send(input)
                    input = ""
                }
            } else {
                val holdLabel = stringResource(if (listening) R.string.talk_listening else R.string.talk_hold)
                Box(
                    Modifier
                        .size(56.dp)
                        .scale(if (listening) 1.12f else 1f)
                        .clip(CircleShape)
                        .background(if (listening) c.danger else c.lime)
                        .semantics {
                            contentDescription = holdLabel
                            role = Role.Button
                        }
                        .pointerInput(recognizer) {
                            detectTapGestures(onPress = {
                                tts.value?.stop()
                                listening = true
                                recognizer.startListening(
                                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                                        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                                )
                                tryAwaitRelease()
                                recognizer.stopListening()
                            })
                        },
                    contentAlignment = Alignment.Center
                ) { PixelIconView(PixelIcon.MIC, 26.dp, Color.White, strokeWidth = 2.2f) }
            }
        }
        if (recognizer == null) {
            Text(
                stringResource(R.string.talk_voice_off),
                style = PxType.caption,
                color = c.muted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun RoundAction(icon: PixelIcon, label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { PixelIconView(icon, 24.dp, Cr.colors.raised, strokeWidth = 2.2f) }
}

@Composable
private fun Bubble(line: TalkLine, onArm: () -> Unit, onTrip: () -> Unit, onContacts: () -> Unit, onHome: () -> Unit) {
    val c = Cr.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = if (line.mine) Alignment.CenterEnd else Alignment.CenterStart) {
        if (line.mine) {
            Text(
                line.text,
                style = PxType.body,
                color = Color.White,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                    .background(c.lime)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        } else {
            PxPanel(Modifier.widthIn(max = 320.dp).semantics { liveRegion = LiveRegionMode.Polite }, radius = 20.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(line.text, style = PxType.body, color = c.ink)
                    line.reply?.card?.let { card ->
                        Text(
                            stringResource(if (card.validated) R.string.talk_card else R.string.talk_card_unvalidated, card.title),
                            style = PxType.caption,
                            color = if (card.validated) c.muted else c.warn
                        )
                    }
                    line.reply?.action?.let { a ->
                        val (label, go) = when (a) {
                            TalkAction.ARM -> R.string.talk_act_arm to onArm
                            TalkAction.OPEN_TRIP -> R.string.talk_act_trip to onTrip
                            TalkAction.OPEN_CONTACTS -> R.string.talk_act_contacts to onContacts
                            TalkAction.ENTER_PIN -> R.string.talk_act_pin to onHome
                        }
                        PxButton(stringResource(label), go, variant = PxVariant.OUTLINE)
                    }
                }
            }
        }
    }
}
