package com.cerclerouge.audio

/**
 * Tampon circulaire d'échantillons PCM 16 bits, **en mémoire seulement** (R-AE-01, R-AUD-01) : 10 s au plus à 16 kHz.
 * Les échantillons les plus anciens sont écrasés ; [clear] remet tout à zéro (arrêt de l'écoute).
 */
class RingBuffer(val capacity: Int) {
    init {
        require(capacity in 1..MAX_SAMPLES) { "R-AE-01 : 10 s au plus ($MAX_SAMPLES échantillons)" }
    }

    private val data = ShortArray(capacity)
    private var head = 0 // prochaine écriture
    var size = 0
        private set

    fun write(samples: ShortArray, count: Int = samples.size) {
        for (i in 0 until count) {
            data[head] = samples[i]
            head = (head + 1) % capacity
        }
        size = minOf(capacity, size + count)
    }

    /** Copie des `n` derniers échantillons (moins s'il y en a moins), du plus ancien au plus récent. */
    fun last(n: Int): ShortArray {
        val k = minOf(n, size)
        val out = ShortArray(k)
        val start = (head - k + capacity) % capacity
        for (i in 0 until k) out[i] = data[(start + i) % capacity]
        return out
    }

    /** Efface le contenu : les échantillons sont réellement mis à zéro, pas seulement oubliés. */
    fun clear() {
        data.fill(0)
        head = 0
        size = 0
    }

    /** Pour les tests : vrai si plus aucun échantillon non nul ne reste en mémoire. */
    fun isZeroed(): Boolean = data.all { it.toInt() == 0 }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SAMPLES = SAMPLE_RATE * 10
    }
}
