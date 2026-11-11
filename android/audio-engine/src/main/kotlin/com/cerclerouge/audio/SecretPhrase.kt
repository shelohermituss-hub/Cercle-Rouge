package com.cerclerouge.audio

import java.nio.ByteBuffer
import java.util.Base64

/**
 * Empreinte de la phrase secrète (S5.06, R-AUD-09) : les suites MFCC des 3 à 5 répétitions et le seuil appris.
 * Aucun audio n'est gardé. L'empreinte est sérialisée puis rangée dans la base chiffrée.
 */
class PhraseTemplate internal constructor(internal val samples: List<Array<DoubleArray>>, val threshold: Double) {
    /** Texte compact (Base64) pour la base chiffrée. */
    fun encode(): String {
        val floats = samples.sumOf { it.size * Features.CEPSTRA }
        val buf = ByteBuffer.allocate(8 + 4 + samples.size * 4 + floats * 4)
        buf.putDouble(threshold).putInt(samples.size)
        samples.forEach { buf.putInt(it.size) }
        samples.forEach { s -> s.forEach { row -> row.forEach { buf.putFloat(it.toFloat()) } } }
        return VERSION + Base64.getEncoder().encodeToString(buf.array())
    }

    companion object {
        private const val VERSION = "v1:"

        fun decode(text: String): PhraseTemplate? = runCatching {
            require(text.startsWith(VERSION))
            val buf = ByteBuffer.wrap(Base64.getDecoder().decode(text.removePrefix(VERSION)))
            val threshold = buf.double
            val n = buf.int
            require(n in SecretPhrase.MIN_REPETITIONS..SecretPhrase.MAX_REPETITIONS)
            val sizes = IntArray(n) { buf.int }
            val samples = sizes.map { size -> Array(size) { DoubleArray(Features.CEPSTRA) { buf.float.toDouble() } } }
            PhraseTemplate(samples, threshold)
        }.getOrNull()
    }
}

sealed interface Enrollment {
    data class Ok(val template: PhraseTemplate) : Enrollment

    /** Une répétition est trop courte, trop longue ou trop faible : la refaire. */
    data class BadSample(val index: Int, val problem: SampleProblem) : Enrollment

    /** Les répétitions ne se ressemblent pas assez : dire la même phrase, de la même façon. */
    data object Inconsistent : Enrollment
}

enum class SampleProblem { TOO_SHORT, TOO_LONG, TOO_QUIET }

/** Résultat d'une comparaison : distance au plus proche, nombre d'enrôlements sous le seuil, confiance dans [0, 1]. */
data class PhraseMatch(val distance: Double, val votes: Int, val confidence: Double, val detected: Boolean)

/**
 * Phrase secrète par comparaison dynamique (DTW) des MFCC, sans modèle entraîné : marche dans toutes les langues, créole compris.
 * Seuil = plus grand écart entre deux répétitions × 2,5, borné à [15, 20] (unités DTW-MFCC) ; enrôlement refusé si deux
 * répétitions s'écartent de plus de 12. Détection si au moins 2 répétitions sont sous le seuil. ⚠ Valeurs fixées sur un banc
 * synthétique : à calibrer sur de vraies voix (S5.12). La confiance n'est pas une probabilité calibrée : c'est une marge au
 * seuil (0,5 au seuil, 1 pour une copie parfaite). La décision reste au moteur d'alerte (R-AE-04).
 */
object SecretPhrase {
    const val MIN_REPETITIONS = 3
    const val MAX_REPETITIONS = 5
    const val MODEL_VERSION = "phrase-dtw-mfcc12-v1"
    private const val MARGIN = 2.5
    private const val MIN_THRESHOLD = 15.0
    private const val MAX_THRESHOLD = 20.0
    private const val MAX_SPREAD = 12.0
    private const val MIN_SECONDS = 0.4
    private const val MAX_SECONDS = 4.0
    private const val MIN_PEAK_DBFS = -40.0
    private const val MIN_VOTES = 2

    fun enroll(repetitions: List<ShortArray>): Enrollment {
        require(repetitions.size in MIN_REPETITIONS..MAX_REPETITIONS) { "3 à 5 répétitions" }
        val features = repetitions.mapIndexed { i, pcm ->
            check(pcm)?.let { return Enrollment.BadSample(i, it) }
            Features.cepstra(pcm).also { if (it.size < MIN_SECONDS * 100) return Enrollment.BadSample(i, SampleProblem.TOO_SHORT) }
        }
        var worst = 0.0
        for (i in features.indices) for (j in i + 1 until features.size) worst = maxOf(worst, Features.dtw(features[i], features[j]))
        if (worst > MAX_SPREAD) return Enrollment.Inconsistent
        val threshold = (worst * MARGIN).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        return Enrollment.Ok(PhraseTemplate(features, threshold))
    }

    fun match(template: PhraseTemplate, utterance: ShortArray): PhraseMatch {
        val f = Features.cepstra(utterance)
        val distances = template.samples.map { Features.dtw(f, it) }
        val best = distances.min()
        val votes = distances.count { it <= template.threshold }
        val confidence = (1.0 - 0.5 * best / template.threshold).coerceIn(0.0, 1.0)
        return PhraseMatch(best, votes, confidence, votes >= MIN_VOTES)
    }

    private fun check(pcm: ShortArray): SampleProblem? {
        val seconds = pcm.size.toDouble() / Features.SAMPLE_RATE
        if (seconds < MIN_SECONDS) return SampleProblem.TOO_SHORT
        if (seconds > MAX_SECONDS + 1.0) return SampleProblem.TOO_LONG
        val loudest = (0 until pcm.size / Vad.BLOCK).maxOfOrNull { b ->
            Features.dbfs(pcm.copyOfRange(b * Vad.BLOCK, (b + 1) * Vad.BLOCK))
        } ?: -120.0
        return if (loudest < MIN_PEAK_DBFS) SampleProblem.TOO_QUIET else null
    }
}

/** Signal rendu au moteur d'alerte, avec la version du modèle (R-AE-03). */
data class AudioSignal(val kind: Kind, val confidence: Double, val modelVersion: String) {
    enum class Kind { SECRET_PHRASE, SPEECH }
}

/**
 * Chaîne d'écoute en continu : VAD → énoncé → comparaison à la phrase secrète. Ne garde aucun énoncé après comparaison.
 */
class Listener(private val template: PhraseTemplate?) {
    private val vad = Vad()

    fun push(block: ShortArray): AudioSignal? {
        val utterance = vad.push(block) ?: return null
        val t = template ?: return AudioSignal(AudioSignal.Kind.SPEECH, 1.0, "vad-energy-v1")
        val m = SecretPhrase.match(t, utterance)
        utterance.fill(0)
        return if (m.detected) AudioSignal(AudioSignal.Kind.SECRET_PHRASE, m.confidence, SecretPhrase.MODEL_VERSION) else null
    }

    fun stop() = vad.reset()
}

/** Mots-clés de détresse par défaut (Q20), pour les mots-clés personnalisés et l'entraînement futur ; à valider avec des locuteurs natifs. */
object DistressKeywords {
    val HT = listOf("Anmwe", "Ede m", "Lage m", "Sekou")
    val FR = listOf("Au secours", "À l'aide", "Lâche-moi")
}
