package com.cerclerouge.app.evidence

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.cerclerouge.app.audio.AudioListener
import com.cerclerouge.evidence.RecordingPolicy
import com.cerclerouge.privacy.log.EventLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Enregistrement des preuves audio (S6.01) : à partir du niveau 2 ou du SOS seulement (effet `StartRecording` du moteur),
 * si l'utilisateur ne l'a pas coupé. AAC-LC 32 kbit/s mono 16 kHz en trames ADTS (ADR-0021), segments de 30 s, plafond
 * de 60 min, avec le pré-tampon des 5 s précédentes. Chaque segment reste en mémoire jusqu'à son écriture chiffrée.
 */
@Singleton
class EvidenceRecorder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val audio: AudioListener,
    private val store: EvidenceStore,
    private val settings: EvidenceSettings,
    private val log: EventLog,
    private val clock: Clock
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _recording = MutableStateFlow<String?>(null)

    /** Identifiant de l'alerte en cours d'enregistrement (pour l'écran d'urgence). */
    val recording: StateFlow<String?> = _recording

    @Volatile private var session: Session? = null

    fun start(alertId: String) = scope.launch {
        if (session != null) return@launch
        if (!settings.audioEnabled()) {
            log.append("evidence", "audio_disabled_by_user")
            return@launch
        }
        if (!audio.granted || !RecordingPolicy.canRecordAudio(context.filesDir.usableSpace)) {
            log.append("evidence", "audio_unavailable")
            return@launch
        }
        store.open(alertId, clock.instant())
        val s = Session(alertId)
        session = s
        _recording.value = alertId
        val pre = audio.startRecording { block -> s.queue.offer(block.copyOf()) }
        s.startedAt = clock.instant().minusMillis(pre * 1000L / AudioListener.RATE)
        log.append("evidence", "audio_started prebuffer_ms=${pre * 1000L / AudioListener.RATE}")
        s.run()
    }

    fun stop(alertId: String) = scope.launch {
        val s = session ?: run {
            store.close(alertId, clock.instant())
            return@launch
        }
        if (s.alertId != alertId) return@launch
        s.stopRequested = true
        s.queue.offer(END)
    }

    /** Une session : encodeur AAC alimenté par la file des blocs PCM du micro. */
    private inner class Session(val alertId: String) {
        val queue = LinkedBlockingQueue<ShortArray>()

        @Volatile var stopRequested = false
        var startedAt: Instant = Instant.EPOCH
        private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        private val out = ByteArrayOutputStream()
        private var segmentSamples = 0L
        private var totalSamples = 0L
        private var segments = 0
        private val info = MediaCodec.BufferInfo()

        fun run() {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AudioListener.RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            try {
                while (true) {
                    val block = queue.take()
                    if (block === END) break
                    feed(block)
                    block.fill(0)
                    if (segmentSamples >= SEGMENT_SAMPLES) flushSegment(final = false)
                    if (segments >= RecordingPolicy.maxSegments()) {
                        log.append("evidence", "audio_cap_reached minutes=${RecordingPolicy.MAX_MINUTES}")
                        break
                    }
                }
                endOfStream()
                flushSegment(final = true)
            } finally {
                audio.stopRecording()
                runCatching { codec.stop() }
                codec.release()
                queue.forEach { it.fill(0) }
                queue.clear()
                session = null
                _recording.value = null
                runBlocking {
                    if (stopRequested) store.close(alertId, clock.instant())
                    log.append("evidence", "audio_stopped segments=$segments seconds=${totalSamples / AudioListener.RATE}")
                }
            }
        }

        private fun feed(pcm: ShortArray) {
            var offset = 0
            while (offset < pcm.size) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val buf = codec.getInputBuffer(index) ?: continue
                    buf.clear()
                    val n = minOf(pcm.size - offset, buf.remaining() / 2)
                    for (i in 0 until n) buf.putShort(pcm[offset + i])
                    codec.queueInputBuffer(index, 0, n * 2, totalSamples * 1_000_000 / AudioListener.RATE, 0)
                    offset += n
                    totalSamples += n
                    segmentSamples += n
                }
                drain()
            }
        }

        private fun endOfStream() {
            val index = codec.dequeueInputBuffer(TIMEOUT_US * 10)
            if (index >=
                0
            ) {
                codec.queueInputBuffer(index, 0, 0, totalSamples * 1_000_000 / AudioListener.RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            repeat(50) { if (drain()) return }
        }

        /** Vide les trames encodées dans le segment courant (en mémoire), avec un en-tête ADTS. Vrai à la fin du flux. */
        private fun drain(): Boolean {
            while (true) {
                val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (index < 0) return false
                val buf = codec.getOutputBuffer(index)
                if (buf != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    val frame = ByteArray(info.size)
                    buf.position(info.offset)
                    buf.get(frame)
                    out.write(adts(frame.size))
                    out.write(frame)
                    frame.fill(0)
                }
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                codec.releaseOutputBuffer(index, false)
                if (eos) return true
            }
        }

        private fun flushSegment(final: Boolean) {
            if (out.size() == 0) return
            val content = out.toByteArray()
            out.reset()
            val durationMs = segmentSamples * 1000 / AudioListener.RATE
            val started = startedAt
            startedAt = startedAt.plusMillis(durationMs)
            segmentSamples = 0
            segments++
            runBlocking { store.append(alertId, content, started, durationMs, clock.instant()) }
            if (final) content.fill(0)
        }
    }

    private companion object {
        val END = ShortArray(0)
        const val BITRATE = 32_000
        const val TIMEOUT_US = 10_000L
        const val SEGMENT_SAMPLES = AudioListener.RATE.toLong() * RecordingPolicy.SEGMENT_SECONDS

        /** En-tête ADTS (7 octets) : AAC-LC, 16 kHz (indice 8), mono. Rend chaque trame lisible seule. */
        fun adts(payload: Int): ByteArray {
            val len = payload + 7
            val profile = 2 - 1 // AAC LC
            val freqIdx = 8 // 16 000 Hz
            val chan = 1
            return byteArrayOf(
                0xFF.toByte(),
                0xF1.toByte(),
                ((profile shl 6) + (freqIdx shl 2) + (chan shr 2)).toByte(),
                (((chan and 3) shl 6) + (len shr 11)).toByte(),
                ((len and 0x7FF) shr 3).toByte(),
                (((len and 7) shl 5) + 0x1F).toByte(),
                0xFC.toByte()
            )
        }
    }
}
