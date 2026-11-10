package com.cerclerouge.spikes

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * S0.18 — Le micro tient-il écran éteint, et à quel coût ?
 *
 * Service de premier plan « microphone » + AudioRecord 16 kHz mono + porte d'énergie
 * + YAMNet (LiteRT) sur des fenêtres de 0,975 s avec un pas de 0,5 s.
 * Aucun audio n'est écrit ni envoyé : seuls des compteurs sont journalisés chaque minute.
 */
class MicService : Service() {

    private lateinit var log: SpikeLog
    @Volatile private var running = false
    private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log = SpikeLog(this, LOG_NAME)
        if (running) return START_NOT_STICKY
        startForeground(
            NOTIF_ID,
            Notifications.foreground(this, "S0.18 · écoute IA en test"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        val useWakeLock = intent?.getBooleanExtra(EXTRA_WAKELOCK, true) ?: true
        if (useWakeLock) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cr:spike-mic").apply { acquire() }
        }
        log.line("start", "wakelock=$useWakeLock", "batterie=${SpikeLog.battery(this)}", SpikeLog.deviceInfo(this))
        running = true
        worker = Thread(::loop, "spike-mic").apply { start() }
        return START_NOT_STICKY // un redémarrage automatique masquerait un arrêt par le fabricant
    }

    override fun onDestroy() {
        running = false
        worker?.join(1000)
        wakeLock?.takeIf { it.isHeld }?.release()
        log.line("stop", "batterie=${SpikeLog.battery(this)}")
        super.onDestroy()
    }

    @SuppressLint("MissingPermission") // vérifiée par MainActivity avant le démarrage
    private fun loop() {
        val interpreter = Interpreter(loadModel(this), Interpreter.Options().setNumThreads(2))
        val labels = assets.open("yamnet_labels.txt").bufferedReader().readLines()
        val inShape = interpreter.getInputTensor(0).shape()
        val outShape = interpreter.getOutputTensor(0).shape()
        val windowSize = inShape.last() // 15 600 échantillons = 0,975 s
        val classes = outShape.last()

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, BLOCK * 2 * 4)
        )
        record.startRecording()
        log.line("audio", "etat=${record.recordingState}", "modele_entree=${inShape.toList()}", "sortie=${outShape.toList()}")

        val block = ShortArray(BLOCK)
        val ring = FloatArray(windowSize)
        var ringPos = 0
        var samplesSinceInference = 0
        var gateOpenUntil = 0L

        // Compteurs de la minute en cours
        var minuteStart = SystemClock.elapsedRealtime()
        val serviceStart = minuteStart
        var blocks = 0; var gateBlocks = 0; var silentBlocks = 0
        var inferences = 0; var inferenceMs = 0L; var inferenceMax = 0L
        var lastTop = "-"
        var cpuStart = android.os.Process.getElapsedCpuTime()

        while (running) {
            val n = record.read(block, 0, BLOCK)
            if (n <= 0) { log.line("erreur_lecture", n); Thread.sleep(50); continue }
            blocks++
            var sum = 0.0
            var allZero = true
            for (i in 0 until n) {
                val s = block[i] / 32768f
                if (block[i].toInt() != 0) allZero = false
                sum += s * s
                ring[ringPos] = s
                ringPos = (ringPos + 1) % windowSize
            }
            if (allZero) silentBlocks++ // le système coupe le micro d'une app en arrière-plan : à surveiller
            val dbfs = 10 * log10(sum / n + 1e-12)
            val now = SystemClock.elapsedRealtime()
            if (dbfs > GATE_DBFS) gateOpenUntil = now + 1000
            val gateOpen = now < gateOpenUntil
            if (gateOpen) gateBlocks++

            samplesSinceInference += n
            if (gateOpen && samplesSinceInference >= HOP) {
                samplesSinceInference = 0
                val window = FloatArray(windowSize) { ring[(ringPos + it) % windowSize] }
                val input: Any = if (inShape.size == 1) window else arrayOf(window)
                val scores = FloatArray(classes)
                val output: Any = if (outShape.size == 1) scores else arrayOf(scores)
                val t0 = SystemClock.elapsedRealtimeNanos()
                interpreter.run(input, output)
                val ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
                val res = if (outShape.size == 1) scores else (output as Array<*>)[0] as FloatArray
                val top = res.indices.maxBy { res[it] }
                lastTop = "${labels.getOrElse(top) { "?" }} ${"%.2f".format(res[top])}"
                inferences++; inferenceMs += ms; inferenceMax = max(inferenceMax, ms)
            }

            if (now - minuteStart >= 60_000) {
                val cpuNow = android.os.Process.getElapsedCpuTime()
                log.line(
                    "battement",
                    "minute=${(now - serviceStart) / 60_000}",
                    "batterie=${SpikeLog.battery(this)}",
                    "en_charge=${SpikeLog.charging(this)}",
                    "porte_ouverte_%=${if (blocks > 0) gateBlocks * 100 / blocks else 0}",
                    "blocs_muets_%=${if (blocks > 0) silentBlocks * 100 / blocks else 0}",
                    "inferences=$inferences",
                    "ms_moy=${if (inferences > 0) inferenceMs / inferences else 0}",
                    "ms_max=$inferenceMax",
                    "cpu_%=${(cpuNow - cpuStart) * 100 / 60_000}",
                    "derniere_classe=$lastTop"
                )
                minuteStart = now; cpuStart = cpuNow
                blocks = 0; gateBlocks = 0; silentBlocks = 0; inferences = 0; inferenceMs = 0; inferenceMax = 0
            }
        }
        record.stop(); record.release(); interpreter.close()
        ring.fill(0f) // R-AE-01 : rien ne reste en mémoire
    }

    companion object {
        const val LOG_NAME = "S0.18-micro"
        const val EXTRA_WAKELOCK = "wakelock"
        private const val NOTIF_ID = 18
        private const val SAMPLE_RATE = 16_000
        private const val BLOCK = 320 // 20 ms
        private const val HOP = 8_000 // 0,5 s
        private const val GATE_DBFS = -50.0

        fun loadModel(context: Context) = context.assets.openFd("yamnet.tflite").use { fd ->
            FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }
}
