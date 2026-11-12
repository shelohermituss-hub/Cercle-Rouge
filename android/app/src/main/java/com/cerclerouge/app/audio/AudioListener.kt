package com.cerclerouge.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import com.cerclerouge.audio.AudioSignal
import com.cerclerouge.audio.Features
import com.cerclerouge.audio.Listener
import com.cerclerouge.audio.PhraseTemplate
import com.cerclerouge.audio.RingBuffer
import com.cerclerouge.audio.Vad
import com.cerclerouge.evidence.RecordingPolicy
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Étape de l'écoute pour les visualiseurs de l'accueil. */
enum class ListenStage { IDLE, LISTENING, VOICE, RESULT }

enum class ListenEventKind { VOICE, HIT, MISS }

/** Un événement de l'analyse : une voix détectée, ou une comparaison à la phrase secrète (écart et seuil, unités DTW-MFCC). */
data class ListenEvent(val kind: ListenEventKind, val distance: Double? = null, val threshold: Double? = null)

/**
 * Ce que l'écoute fait en ce moment (visualiseurs de l'accueil) : niveaux du micro (0 à 1, un toutes les 100 ms) et étapes de
 * l'analyse. **Aucun son** : seulement des nombres, perdus à l'arrêt du micro (R-AE-01).
 */
data class ListenActivity(
    val levels: List<Float> = emptyList(),
    val stage: ListenStage = ListenStage.IDLE,
    val events: List<ListenEvent> = emptyList(),
    val hasPhrase: Boolean = false
)

enum class AudioStatus { STOPPED, RUNNING, PAUSED_MIC_BUSY, PAUSED_LOW_POWER, ERROR }

/** Empreinte de la phrase secrète, dans la base chiffrée (R-AUD-09) : jamais d'audio. */
@Singleton
class PhraseRepository @Inject constructor(db: PrivacyDatabase) {
    private val settings = db.settings()

    suspend fun load(): PhraseTemplate? = settings.get(KEY)?.let(PhraseTemplate::decode)

    suspend fun save(t: PhraseTemplate) = settings.put(SettingEntity(KEY, t.encode()))

    suspend fun delete() = settings.put(SettingEntity(KEY, ""))

    private companion object {
        const val KEY = "audio.phrase"
    }
}

/**
 * Écoute locale (S5.01, S5.02) : `AudioRecord` source `VOICE_RECOGNITION`, 16 kHz mono, blocs de 20 ms, sur un fil dédié.
 * Les blocs passent dans la chaîne VAD → phrase secrète et sont écrasés aussitôt : rien n'est écrit ni envoyé (R-AUD-01).
 * Démarré seulement par le moteur, dans le service de premier plan (type `microphone`, R-AUD-05) ; [stop] libère le micro
 * en moins de 500 ms (R-AUD-06). Si une autre application prend le micro, l'état passe à « en pause » (R-AUD-07).
 */
@Singleton
class AudioListener @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val _signals = MutableSharedFlow<AudioSignal>(extraBufferCapacity = 4)
    val signals: SharedFlow<AudioSignal> = _signals
    private val _status = MutableStateFlow(AudioStatus.STOPPED)
    val status: StateFlow<AudioStatus> = _status
    private val _activity = MutableStateFlow(ListenActivity())

    /** Niveaux et étapes de l'analyse pour l'affichage. */
    val activity: StateFlow<ListenActivity> = _activity

    @Volatile private var running = false

    @Volatile private var degraded = false
    private var template: PhraseTemplate? = null
    private var wantListen = false
    private var wantRecord = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null
    private var callback: AudioManager.AudioRecordingCallback? = null

    /** Les 5 dernières secondes, en mémoire seulement : le pré-tampon des preuves (S6.01, R-AE-01). */
    private val preBuffer = RingBuffer(RATE * RecordingPolicy.PRE_BUFFER_SECONDS)

    /** Reçoit chaque bloc de 20 ms pendant un enregistrement de preuve (le receveur copie ce qu'il garde). */
    @Volatile private var tap: ((ShortArray) -> Unit)? = null

    val granted: Boolean
        get() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Écoute de la phrase secrète (moteur, protection active). */
    @Synchronized
    fun start(template: PhraseTemplate?) {
        this.template = template
        wantListen = true
        if (running) restartMic() else ensureMic() // micro déjà ouvert pour une preuve : relancé avec la phrase
    }

    /**
     * Mode dégradé (S5.13, R-AE-05) : batterie sous 10 % ou téléphone en surchauffe. L'écoute de la phrase se met en pause
     * (« Écoute IA en pause ») pour garder de la batterie au SOS et aux SMS ; un enregistrement de preuve en cours continue.
     */
    @Synchronized
    fun setDegraded(on: Boolean) {
        if (degraded == on) return
        degraded = on
        if (running) {
            restartMic()
        } else if (wantListen) {
            ensureMic()
        }
    }

    /** Fin de l'écoute ; le micro reste ouvert si un enregistrement de preuve est en cours. */
    @Synchronized
    fun stop() {
        wantListen = false
        if (wantRecord) restartMic() else shutdownMic()
    }

    /**
     * Enregistrement de preuve (alerte de niveau 2 ou SOS) : envoie à `sink` le pré-tampon des 5 dernières secondes (vide si
     * le micro était fermé), puis chaque bloc jusqu'à [stopRecording]. Rend la taille du pré-tampon.
     */
    @Synchronized
    fun startRecording(sink: (ShortArray) -> Unit): Int {
        val pre = preBuffer.last(preBuffer.size)
        sink(pre) // d'abord le pré-tampon, puis les blocs en direct : l'ordre est garanti
        pre.fill(0)
        tap = sink
        wantRecord = true
        ensureMic()
        return pre.size
    }

    @Synchronized
    fun stopRecording() {
        tap = null
        wantRecord = false
        if (!wantListen) shutdownMic()
    }

    @SuppressLint("MissingPermission") // vérifiée par `granted`
    private fun ensureMic() {
        if (running || !granted) return
        if (degraded && !wantRecord) {
            _status.value = if (wantListen) AudioStatus.PAUSED_LOW_POWER else AudioStatus.STOPPED
            return
        }
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(
                    min,
                    Vad.BLOCK * 16
                )
            )
        }.getOrNull()
        if (r == null || r.state != AudioRecord.STATE_INITIALIZED) {
            r?.release()
            _status.value = AudioStatus.ERROR
            return
        }
        val cb = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                val mine = configs.firstOrNull { it.clientAudioSessionId == r.audioSessionId } ?: return
                if (running) _status.value = if (mine.isClientSilenced) AudioStatus.PAUSED_MIC_BUSY else AudioStatus.RUNNING
            }
        }
        r.registerAudioRecordingCallback(context.mainExecutor, cb)
        record = r
        callback = cb
        running = true
        r.startRecording()
        _status.value = AudioStatus.RUNNING
        // La phrase secrète n'est cherchée que si l'écoute est voulue (pas pendant un simple enregistrement).
        val listener = Listener(if (wantListen && !degraded) template else null)
        thread = Thread({ loop(r, listener, wantListen && !degraded) }, "cr-audio").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    private fun loop(r: AudioRecord, listener: Listener, listening: Boolean) {
        val block = ShortArray(Vad.BLOCK)
        val levels = ArrayDeque<Float>()
        val events = ArrayDeque<ListenEvent>()
        var peakDb = -120.0
        var blocks = 0
        var wasVoice = false
        var seenComparisons = 0
        var resultUntil = 0L
        try {
            while (running) {
                val n = r.read(block, 0, block.size)
                if (n < block.size) continue
                preBuffer.write(block)
                tap?.invoke(block)
                listener.push(block)?.let { _signals.tryEmit(it) }
                peakDb = maxOf(peakDb, Features.dbfs(block))
                if (++blocks < LEVEL_BLOCKS) continue
                blocks = 0
                val now = System.currentTimeMillis()
                levels.addLast(((peakDb - LEVEL_FLOOR_DB) / (LEVEL_CEIL_DB - LEVEL_FLOOR_DB)).toFloat().coerceIn(0f, 1f))
                peakDb = -120.0
                while (levels.size > LEVEL_HISTORY) levels.removeFirst()
                if (listening) {
                    if (listener.inSpeech && !wasVoice) events.addLast(ListenEvent(ListenEventKind.VOICE))
                    wasVoice = listener.inSpeech
                    if (listener.comparisons != seenComparisons) {
                        seenComparisons = listener.comparisons
                        listener.lastMatch?.let {
                            val kind = if (it.detected) ListenEventKind.HIT else ListenEventKind.MISS
                            events.addLast(ListenEvent(kind, it.distance, listener.threshold))
                        }
                        resultUntil = now + RESULT_MS
                    }
                    while (events.size > EVENT_HISTORY) events.removeFirst()
                }
                val stage = when {
                    !listening -> ListenStage.IDLE
                    listener.inSpeech -> ListenStage.VOICE
                    now < resultUntil -> ListenStage.RESULT
                    else -> ListenStage.LISTENING
                }
                _activity.value = ListenActivity(levels.toList(), stage, events.toList(), listener.threshold != null)
            }
        } finally {
            block.fill(0)
            listener.stop()
            _activity.value = ListenActivity()
        }
    }

    private fun restartMic() {
        shutdownMic(keepPreBuffer = true)
        ensureMic()
    }

    /** Arrêt : le fil sort de `read` dès que l'enregistrement s'arrête ; tampons mis à zéro (R-AE-01). */
    private fun shutdownMic(keepPreBuffer: Boolean = false) {
        if (!running) return
        running = false
        record?.let { r ->
            callback?.let { r.unregisterAudioRecordingCallback(it) }
            runCatching { r.stop() }
        }
        thread?.join(STOP_TIMEOUT_MS)
        record?.release()
        record = null
        callback = null
        thread = null
        if (!keepPreBuffer) preBuffer.clear()
        _status.value = AudioStatus.STOPPED
    }

    /** Relance avec une nouvelle empreinte (après l'enrôlement ou la suppression), si l'écoute tournait. */
    @Synchronized
    fun restartWith(template: PhraseTemplate?) {
        this.template = template
        if (running) restartMic()
    }

    /**
     * Enregistre UN énoncé pour l'enrôlement (écran visible, action de l'utilisateur) : l'écoute est suspendue le temps de
     * la capture puis reprise. L'énoncé reste en mémoire et sert uniquement à calculer l'empreinte. `level` reçoit le niveau
     * (dBFS) de chaque bloc pour l'indicateur à l'écran.
     */
    @SuppressLint("MissingPermission")
    fun recordUtterance(timeoutMs: Long, level: (Double) -> Unit): ShortArray? {
        if (!granted) return null
        synchronized(this) {
            if (wantRecord) return null // jamais pendant l'enregistrement d'une preuve
            shutdownMic()
        }
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(
                    min,
                    Vad.BLOCK * 16
                )
            )
        val vad = Vad()
        val block = ShortArray(Vad.BLOCK)
        var result: ShortArray? = null
        try {
            if (r.state != AudioRecord.STATE_INITIALIZED) return null
            r.startRecording()
            val end = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < end && result == null) {
                if (r.read(block, 0, block.size) < block.size) continue
                level(com.cerclerouge.audio.Features.dbfs(block))
                result = vad.push(block)
            }
        } finally {
            runCatching { r.stop() }
            r.release()
            block.fill(0)
            vad.reset()
            synchronized(this) { if (wantListen) ensureMic() }
        }
        return result
    }

    companion object {
        const val RATE = 16_000
        private const val STOP_TIMEOUT_MS = 400L
        private const val LEVEL_BLOCKS = 5 // un niveau toutes les 100 ms (blocs de 20 ms)
        private const val LEVEL_HISTORY = 40
        private const val LEVEL_FLOOR_DB = -70.0
        private const val LEVEL_CEIL_DB = -10.0
        private const val EVENT_HISTORY = 3
        private const val RESULT_MS = 2_500L
    }
}
