package com.cerclerouge.spikes

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.SystemClock
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.ln

/**
 * S0.19 — Peut-on enregistrer une preuve audio sans JAMAIS écrire de données en clair ?
 *
 * PCM (AudioRecord) → encodeur MediaCodec (Opus, ou AAC en repli) → trames écrites directement
 * dans un flux Tink StreamingAead (AES-256-GCM-HKDF, segments de 1 Mo), clé enveloppée par l'Android Keystore.
 * Puis vérification : aucun nouveau fichier en clair, entropie du fichier, relecture déchiffrée et lecture audio.
 */
class EvidenceSpike(private val context: Context, private val report: (String) -> Unit) {

    private val log = SpikeLog(context, LOG_NAME)

    private fun streamingAead(): StreamingAead {
        StreamingAeadConfig.register()
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context, "spike_evidence_keyset", "spike_evidence_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_1MB"))
            .withMasterKeyUri("android-keystore://cr_spike_evidence_master")
            .build()
            .keysetHandle
        return handle.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
    }

    @SuppressLint("MissingPermission") // vérifiée par MainActivity
    fun run(seconds: Int, useAac: Boolean) {
        val aead = streamingAead()
        val before = snapshot()
        val out = File(context.filesDir, "evidence/spike-${System.currentTimeMillis()}.bin").apply { parentFile?.mkdirs() }
        val mime = if (useAac) MediaFormat.MIMETYPE_AUDIO_AAC else MediaFormat.MIMETYPE_AUDIO_OPUS
        val format = MediaFormat.createAudioFormat(mime, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, if (useAac) 32_000 else 24_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        report("Enregistrement ${seconds}s · codec ${encoder.name}")

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 4
        )
        val cpu0 = android.os.Process.getElapsedCpuTime()
        val t0 = SystemClock.elapsedRealtime()
        var firstFrameProbe: ByteArray? = null
        var frames = 0
        var pcmBytes = 0L

        val sink = DataOutputStream(aead.newEncryptingStream(FileOutputStream(out), AAD))
        sink.writeUTF(MAGIC); sink.writeUTF(mime)
        record.startRecording()
        val pcm = ByteArray(2048)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val endAt = t0 + seconds * 1000L
        while (!outputDone) {
            if (!inputDone) {
                val inIdx = encoder.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val buf = encoder.getInputBuffer(inIdx)!!
                    val n = record.read(pcm, 0, minOf(pcm.size, buf.remaining()))
                    val pts = pcmBytes * 1_000_000L / (SAMPLE_RATE * 2)
                    if (SystemClock.elapsedRealtime() >= endAt) {
                        encoder.queueInputBuffer(inIdx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        buf.put(pcm, 0, maxOf(n, 0))
                        encoder.queueInputBuffer(inIdx, 0, maxOf(n, 0), pts, 0)
                        pcmBytes += maxOf(n, 0)
                    }
                }
            }
            var outIdx = encoder.dequeueOutputBuffer(info, 10_000)
            while (outIdx >= 0) {
                val buf = encoder.getOutputBuffer(outIdx)!!
                val bytes = ByteArray(info.size).also { buf.position(info.offset); buf.get(it) }
                if (info.size > 0) {
                    sink.writeByte(if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) 1 else 0)
                    sink.writeLong(info.presentationTimeUs)
                    sink.writeInt(bytes.size)
                    sink.write(bytes)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        frames++
                        if (firstFrameProbe == null && bytes.size >= 16) firstFrameProbe = bytes.copyOf(16)
                    }
                }
                encoder.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { outputDone = true; break }
                outIdx = encoder.dequeueOutputBuffer(info, 0)
            }
        }
        record.stop(); record.release(); encoder.stop(); encoder.release()
        sink.close()
        val wallMs = SystemClock.elapsedRealtime() - t0
        val cpuMs = android.os.Process.getElapsedCpuTime() - cpu0
        pcm.fill(0)

        // 1. Nouveaux fichiers créés pendant l'enregistrement (hors journaux et clé enveloppée)
        val created = (snapshot() - before).filterNot { it.contains("/logs/") || it.contains("shared_prefs") }
        // 2. Le début de la 1re trame encodée apparaît-il en clair dans un fichier ?
        val leak = firstFrameProbe?.let { probe -> created.any { containsBytes(File(it), probe) } } ?: false
        val entropy = entropy(out)
        val summary = "enregistre codec=$mime trames=$frames taille=${out.length()}o mur=${wallMs}ms cpu=${cpuMs}ms " +
            "cpu_%=${cpuMs * 100 / maxOf(wallMs, 1)} entropie=${"%.3f".format(entropy)}bits/octet " +
            "nouveaux_fichiers=${created.size} fuite_en_clair=$leak"
        log.line("enregistrement", mime, "trames=$frames", "octets=${out.length()}", "mur_ms=$wallMs", "cpu_ms=$cpuMs",
            "entropie=${"%.3f".format(entropy)}", "nouveaux_fichiers=${created.joinToString(" ")}", "fuite_en_clair=$leak",
            SpikeLog.deviceInfo(context))
        report(summary)

        // 3. Relecture : déchiffrement + décodage + lecture
        report(playBack(aead, out))
    }

    private fun playBack(aead: StreamingAead, file: File): String {
        val t0 = SystemClock.elapsedRealtime()
        val input = DataInputStream(aead.newDecryptingStream(FileInputStream(file), AAD))
        check(input.readUTF() == MAGIC) { "en-tête invalide" }
        val mime = input.readUTF()
        val configs = mutableListOf<ByteArray>()
        val packets = mutableListOf<Pair<Long, ByteArray>>()
        try {
            while (true) {
                val isConfig = input.readByte().toInt() == 1
                val pts = input.readLong()
                val data = ByteArray(input.readInt()).also { input.readFully(it) }
                if (isConfig) configs += data else packets += pts to data
            }
        } catch (_: EOFException) { }
        input.close()

        val format = MediaFormat.createAudioFormat(mime, SAMPLE_RATE, 1)
        configs.forEachIndexed { i, c -> format.setByteBuffer("csd-$i", ByteBuffer.wrap(c)) }
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        var next = 0
        var decodedBytes = 0L
        val info = MediaCodec.BufferInfo()
        var done = false
        while (!done) {
            val inIdx = decoder.dequeueInputBuffer(10_000)
            if (inIdx >= 0) {
                if (next < packets.size) {
                    val (pts, data) = packets[next++]
                    decoder.getInputBuffer(inIdx)!!.put(data)
                    decoder.queueInputBuffer(inIdx, 0, data.size, pts, 0)
                } else {
                    decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            }
            var outIdx = decoder.dequeueOutputBuffer(info, 10_000)
            while (outIdx >= 0) {
                if (info.size > 0) {
                    val pcm = ByteArray(info.size)
                    decoder.getOutputBuffer(outIdx)!!.apply { position(info.offset); get(pcm) }
                    track.write(pcm, 0, pcm.size)
                    decodedBytes += pcm.size
                }
                decoder.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { done = true; break }
                outIdx = decoder.dequeueOutputBuffer(info, 0)
            }
        }
        track.stop(); track.release(); decoder.stop(); decoder.release()
        val seconds = decodedBytes / (SAMPLE_RATE * 2.0)
        log.line("relecture", mime, "paquets=${packets.size}", "secondes_decodees=${"%.1f".format(seconds)}",
            "ms=${SystemClock.elapsedRealtime() - t0}")
        return "relecture OK : ${"%.1f".format(seconds)} s décodées depuis le fichier chiffré (tu dois avoir entendu l'enregistrement)"
    }

    private fun snapshot(): Set<String> =
        context.dataDir.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toSet()

    private fun containsBytes(file: File, probe: ByteArray): Boolean {
        val bytes = file.readBytes()
        outer@ for (i in 0..bytes.size - probe.size) {
            for (j in probe.indices) if (bytes[i + j] != probe[j]) continue@outer
            return true
        }
        return false
    }

    private fun entropy(file: File): Double {
        val counts = LongArray(256)
        val bytes = file.readBytes()
        bytes.forEach { counts[it.toInt() and 0xff]++ }
        return counts.filter { it > 0 }.sumOf { val p = it.toDouble() / bytes.size; -p * ln(p) / ln(2.0) }
    }

    /** Supprime les fichiers de test (crypto-shredding non testé ici). */
    fun wipe() {
        File(context.filesDir, "evidence").deleteRecursively()
    }

    companion object {
        const val LOG_NAME = "S0.19-audio-chiffre"
        private const val SAMPLE_RATE = 16_000
        private const val MAGIC = "CRSPIKE1"
        private val AAD = "cercle-rouge-spike-S0.19".toByteArray()
    }
}
