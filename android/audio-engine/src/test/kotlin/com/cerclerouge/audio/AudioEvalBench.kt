package com.cerclerouge.audio

import com.cerclerouge.audio.Synth.PHRASE_A
import com.cerclerouge.audio.Synth.PHRASE_B
import com.cerclerouge.audio.Synth.PHRASE_C
import com.cerclerouge.audio.Synth.Variation
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Banc d'évaluation audio (S5.10, `tools/audio-eval/README.md`) : taux de détection selon le bruit, fausses acceptations,
 * fausses alertes par heure sur des ambiances synthétiques et sur les enregistrements réels de `tools/audio-eval/clips/`.
 * Écrit `build/reports/audio-eval.md` ; échoue sous les seuils de **non-régression** du détecteur actuel (DTW-MFCC sans modèle
 * entraîné). Mesure de référence (2026-11-17) : 75 % à 20 dB, 0 % à 10 dB et 5 dB, 0 fausse alerte. La robustesse au bruit est
 * l'objet de la phase 2 (2.1 YAMNet, 2.3 mots-clés entraînés) : relever alors ces seuils.
 */
class AudioEvalBench {
    private val template = (
        SecretPhrase.enroll(
            listOf(
                Synth.phrase(PHRASE_A, Variation(tempo = 1.0), seed = 1),
                Synth.phrase(PHRASE_A, Variation(tempo = 1.06, pitch = 1.02), seed = 2),
                Synth.phrase(PHRASE_A, Variation(tempo = 0.95, pitch = 0.98), seed = 3)
            )
        ) as Enrollment.Ok
        ).template

    /** Passe un flux dans la chaîne complète (VAD → comparaison), bloc par bloc, comme sur le téléphone. */
    private fun alerts(stream: ShortArray): Int {
        val listener = Listener(template)
        return (0 until stream.size / Vad.BLOCK).count {
            listener.push(stream.copyOfRange(it * Vad.BLOCK, (it + 1) * Vad.BLOCK))?.kind == AudioSignal.Kind.SECRET_PHRASE
        }
    }

    private fun padded(pcm: ShortArray, seed: Int) = Synth.concat(Synth.noise(0.6, -60.0, seed), pcm, Synth.noise(0.8, -60.0, seed + 1))

    private fun detectionRate(snr: Double, trials: Int = 12): Double = (0 until trials).count { i ->
        val v = Variation(tempo = 0.94 + 0.01 * i, pitch = 0.97 + 0.005 * i, gain = 0.35 + 0.02 * i, snrDb = snr)
        alerts(padded(Synth.phrase(PHRASE_A, v, seed = 100 + i), 500 + i)) > 0
    }.toDouble() / trials

    private fun engine(seconds: Double): ShortArray = ShortArray((seconds * Synth.RATE).toInt()) {
        val t = it.toDouble() / Synth.RATE
        ((0.3 * sin(2 * PI * 55 * t) + 0.15 * sin(2 * PI * 110 * t) + 0.08 * sin(2 * PI * 165 * t)) * 0.4 * 32_767).toInt().toShort()
    }

    private fun music(seconds: Double): ShortArray {
        val chords = listOf(listOf(261.6, 329.6, 392.0), listOf(220.0, 261.6, 329.6), listOf(196.0, 246.9, 293.7))
        return ShortArray((seconds * Synth.RATE).toInt()) {
            val t = it.toDouble() / Synth.RATE
            val chord = chords[(t / 0.75).toInt() % chords.size]
            (chord.sumOf { f -> sin(2 * PI * f * t) } / 3 * 0.25 * 32_767).toInt().toShort()
        }
    }

    /** Foule : syllabes d'autres « voix » qui se chevauchent, jamais la phrase enrôlée. */
    private fun crowd(seconds: Double): ShortArray {
        val rnd = Random(7)
        var out = Synth.noise(seconds, -45.0, 9)
        repeat((seconds * 2).toInt()) { k ->
            val syl = listOf(Syllable(120.0 + rnd.nextInt(150), 300.0 + rnd.nextInt(600), 900.0 + rnd.nextInt(1500), 0.12))
            val voice = Synth.phrase(syl, Variation(gain = 0.15), seed = 900 + k)
            val at = rnd.nextInt((out.size - voice.size).coerceAtLeast(1))
            out =
                ShortArray(out.size) { i ->
                    if (i in
                        at until at + voice.size
                    ) {
                        (out[i] + voice[i - at]).coerceIn(-32_767, 32_767).toShort()
                    } else {
                        out[i]
                    }
                }
        }
        return out
    }

    /** Pluie sur tôle : impulsions brèves et aléatoires. */
    private fun rainOnMetal(seconds: Double): ShortArray {
        val rnd = Random(11)
        val out = Synth.noise(seconds, -50.0, 12)
        repeat((seconds * 40).toInt()) {
            val at = rnd.nextInt(out.size - 40)
            for (j in 0 until 40) {
                out[at + j] =
                    (out[at + j] + (rnd.nextInt(16_000) - 8_000) * (40 - j) / 40).coerceIn(-32_767, 32_767).toShort()
            }
        }
        return out
    }

    /** WAV mono 16 kHz PCM 16 bits ; null sinon (le fichier est alors signalé dans le rapport). */
    private fun readWav(f: File): ShortArray? {
        val b = f.readBytes()
        if (b.size < 44 || String(b, 0, 4) != "RIFF" || String(b, 8, 4) != "WAVE") return null
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var p = 12
        var fmtOk = false
        while (p + 8 <= b.size) {
            val id = String(b, p, 4)
            val size = bb.getInt(p + 4)
            if (id == "fmt ") {
                fmtOk = bb.getShort(p + 8).toInt() == 1 &&
                    bb.getShort(p + 10).toInt() == 1 &&
                    bb.getInt(p + 12) == Synth.RATE &&
                    bb.getShort(p + 22).toInt() == 16
            }
            if (id == "data") {
                if (!fmtOk) return null
                val n = minOf(size, b.size - p - 8) / 2
                return ShortArray(n) { bb.getShort(p + 8 + it * 2) }
            }
            p += 8 + size + (size and 1)
        }
        return null
    }

    @Test fun benchReport() {
        val rows = mutableListOf<String>()
        val rates = listOf(20.0, 10.0, 5.0).associateWith { detectionRate(it) }
        rates.forEach { (snr, r) -> rows += "| Détection, SNR ${snr.toInt()} dB | ${pct(r)} |" }

        val others = listOf(PHRASE_B, PHRASE_C).flatMapIndexed { k, p ->
            (0 until 6).map { i -> alerts(padded(Synth.phrase(p, Variation(tempo = 0.95 + 0.02 * i), seed = 300 + 10 * k + i), 700 + i)) }
        }.sum()
        rows += "| Fausses acceptations (autres phrases, 12 essais) | $others |"

        val ambiances = mapOf(
            "Bruit blanc" to Synth.noise(60.0, -30.0, 21),
            "Moteur (tap-tap, moto)" to engine(60.0),
            "Musique (accords)" to music(60.0),
            "Foule (voix mêlées)" to crowd(60.0),
            "Pluie sur tôle" to rainOnMetal(60.0)
        )
        var syntheticAlerts = 0
        ambiances.forEach { (name, pcm) ->
            val n = alerts(pcm)
            syntheticAlerts += n
            rows += "| Alertes / heure : $name | ${n * 60} |"
        }

        val clips = File("../../tools/audio-eval/clips").listFiles { f -> f.extension.lowercase() == "wav" }.orEmpty().sortedBy { it.name }
        var realSeconds = 0.0
        var realAlerts = 0
        clips.forEach { f ->
            val pcm = readWav(f)
            if (pcm == null) {
                rows += "| ${f.name} | ignoré (pas en WAV mono 16 kHz 16 bits) |"
                return@forEach
            }
            val n = alerts(pcm)
            val seconds = pcm.size.toDouble() / Synth.RATE
            if (f.name.startsWith("positif_")) {
                rows += "| ${f.name} (positif) | ${if (n > 0) "détecté" else "MANQUÉ"} |"
            } else {
                realSeconds += seconds
                realAlerts += n
                rows += "| ${f.name} (${String.format(Locale.ROOT, "%.0f", seconds)} s) | $n alerte(s) |"
            }
        }
        if (clips.isEmpty()) rows += "| Ambiances réelles d'Haïti | aucune encore (voir tools/audio-eval/README.md) |"
        if (realSeconds >
            0
        ) {
            rows += "| Alertes / heure, ambiances réelles | ${String.format(Locale.ROOT, "%.1f", realAlerts * 3600 / realSeconds)} |"
        }

        val report = buildString {
            appendLine("# Banc audio — ${SecretPhrase.MODEL_VERSION}")
            appendLine()
            appendLine("| Mesure | Résultat |")
            appendLine("|---|---|")
            rows.forEach { appendLine(it) }
        }
        File("build/reports").mkdirs()
        File("build/reports/audio-eval.md").writeText(report)
        println(report)

        assertTrue(rates.getValue(20.0) >= MIN_DETECTION_QUIET, "régression de la détection au calme : $rates")
        assertTrue(others == 0, "fausses acceptations : $others")
        assertTrue(syntheticAlerts == 0, "alertes sur les ambiances synthétiques : $syntheticAlerts")
    }

    private companion object {
        /** Non-régression : 75 % mesurés le 2026-11-17 avec ces variations (tempo, hauteur, volume). */
        const val MIN_DETECTION_QUIET = 0.7
    }

    private fun pct(r: Double) = String.format(Locale.ROOT, "%.0f %%", r * 100)
}
