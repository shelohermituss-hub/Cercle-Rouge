package com.cerclerouge.app.audio

import android.content.Context
import com.cerclerouge.brain.SoundFamilies
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.tensorflow.lite.Interpreter

/**
 * Oreilles du cerveau (IA.2, ADR-0029) : YAMNet (Google, Apache 2.0, 521 classes AudioSet) exécuté **dans le téléphone**
 * avec LiteRT. Entrée : 0,975 s de son 16 kHz en flottants ; sortie : un score par classe, ramené à nos familles par
 * [SoundFamilies]. Le modèle est vérifié (SHA-256) au chargement : un fichier altéré n'est pas utilisé (menace T17).
 * Aucun son n'est gardé : la fenêtre est remise à zéro par l'appelant après chaque passage.
 */
class Yamnet private constructor(private val interpreter: Interpreter, val labels: List<String>) {
    private val out = Array(1) { FloatArray(labels.size) }

    /** Familles entendues dans [window] (exactement [WINDOW] échantillons entre -1 et 1) et ambiance (voix, musique). */
    fun classify(window: FloatArray): Pair<List<SoundFamilies.Heard>, Pair<Float, Float>> {
        require(window.size == WINDOW)
        interpreter.run(window, out)
        return SoundFamilies.heard(out[0], labels) to SoundFamilies.ambience(out[0], labels)
    }

    fun close() = interpreter.close()

    companion object {
        const val WINDOW = 15_600
        const val VERSION = "yamnet-1"
        const val SHA256 = "10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de"

        /** Null si le modèle manque, est altéré ou si LiteRT ne se charge pas : l'écoute continue sans lui (plancher). */
        fun load(context: Context): Yamnet? = runCatching {
            val bytes = context.assets.open("yamnet.tflite").use { it.readBytes() }
            if (sha256(bytes) != SHA256) return null
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
            buf.rewind()
            val labels = context.assets.open("yamnet_labels.txt").bufferedReader().readLines().filter { it.isNotBlank() }
            Yamnet(Interpreter(buf, Interpreter.Options().setNumThreads(1)), labels)
        }.getOrNull()

        fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    }
}
