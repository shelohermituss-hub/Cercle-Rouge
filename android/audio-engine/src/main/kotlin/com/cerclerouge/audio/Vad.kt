package com.cerclerouge.audio

/**
 * Porte d'énergie et détection d'activité vocale par blocs de 20 ms (S5.03) : plancher de bruit adaptatif,
 * début après 60 ms au-dessus du plancher + 10 dB, fin après 300 ms sous le plancher + 6 dB ou 35 dB sous le pic de la voix.
 * Rend chaque énoncé (0,3 à 5 s, avec 200 ms avant le début) ; rien n'est gardé ensuite (R-AUD-01).
 */
class Vad(private val startMarginDb: Double = 10.0, private val endMarginDb: Double = 6.0, private val absoluteFloorDb: Double = -55.0) {
    private val buffer = RingBuffer(RingBuffer.SAMPLE_RATE * 6)
    private var floor: Double? = null
    private var loud = 0
    private var quiet = 0
    private var speaking = false
    private var speechSamples = 0
    private var peak = -120.0

    val inSpeech: Boolean get() = speaking

    /** Plancher de bruit courant (dBFS), pour l'affichage du niveau à l'enrôlement. */
    val noiseFloorDb: Double get() = floor ?: -90.0

    /** Traite un bloc ; rend l'énoncé terminé (avec son pré-roll), sinon null. */
    fun push(block: ShortArray): ShortArray? {
        buffer.write(block)
        val e = Features.dbfs(block)
        val f = floor ?: maxOf(e, MIN_FLOOR_DB).also { floor = it }
        if (!speaking) {
            // Le plancher suit vite le bruit qui baisse et lentement celui qui monte (2,5 dB/s).
            floor = maxOf(MIN_FLOOR_DB, if (e < f) e else minOf(e, f + FLOOR_RISE_DB))
            loud = if (e > f + startMarginDb && e > absoluteFloorDb) loud + 1 else 0
            if (loud >= START_BLOCKS) {
                speaking = true
                quiet = 0
                speechSamples = (START_BLOCKS + PRE_ROLL_BLOCKS) * block.size
                loud = 0
                peak = e
            }
            return null
        }
        speechSamples += block.size
        peak = maxOf(peak, e)
        // Fin : retour près du plancher, ou 35 dB sous le pic de la voix (le bruit de fond a pu changer pendant l'énoncé).
        quiet = if (e < f + endMarginDb || e < peak - END_BELOW_PEAK_DB) quiet + 1 else 0
        val tooLong = speechSamples >= MAX_SAMPLES
        if (quiet < END_BLOCKS && !tooLong) return null
        speaking = false
        quiet = 0
        val utterance = buffer.last(speechSamples)
        speechSamples = 0
        val voiced = utterance.size - (if (tooLong) 0 else END_BLOCKS * block.size) - PRE_ROLL_BLOCKS * block.size
        return if (voiced >= MIN_SAMPLES) utterance else null
    }

    fun reset() {
        buffer.clear()
        floor = null
        loud = 0
        quiet = 0
        speaking = false
        speechSamples = 0
    }

    companion object {
        const val BLOCK = 320 // 20 ms à 16 kHz
        private const val START_BLOCKS = 3
        private const val END_BLOCKS = 15
        private const val PRE_ROLL_BLOCKS = 10
        private const val END_BELOW_PEAK_DB = 35.0
        private const val FLOOR_RISE_DB = 0.05

        /** Aucun micro réel n'est plus silencieux : sans ce plancher, un silence numérique empêcherait de trouver la fin. */
        private const val MIN_FLOOR_DB = -75.0
        private const val MIN_SAMPLES = RingBuffer.SAMPLE_RATE * 3 / 10
        private const val MAX_SAMPLES = RingBuffer.SAMPLE_RATE * 5
    }
}
