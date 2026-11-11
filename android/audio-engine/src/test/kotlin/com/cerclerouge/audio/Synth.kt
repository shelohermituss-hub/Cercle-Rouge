package com.cerclerouge.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Syllabe synthétique : fréquence fondamentale (Hz), deux formants (Hz), durée (s). */
data class Syllable(val f0: Double, val f1: Double, val f2: Double, val seconds: Double = 0.16)

/** Générateur de voix synthétique pour les tests : harmoniques filtrées par deux formants, enveloppe douce. */
object Synth {
    const val RATE = 16_000

    /** Variation d'une même personne : tempo, hauteur, volume, bruit (SNR en dB, null = sans bruit). */
    data class Variation(val tempo: Double = 1.0, val pitch: Double = 1.0, val gain: Double = 0.5, val snrDb: Double? = null)

    val PHRASE_A =
        listOf(
            Syllable(190.0, 700.0, 1200.0),
            Syllable(210.0, 300.0, 2300.0),
            Syllable(180.0, 500.0, 900.0),
            Syllable(200.0, 400.0, 2000.0)
        )
    val PHRASE_B =
        listOf(
            Syllable(200.0, 300.0, 2300.0),
            Syllable(190.0, 400.0, 2000.0),
            Syllable(210.0, 700.0, 1200.0),
            Syllable(180.0, 300.0, 2300.0)
        )
    val PHRASE_C = listOf(Syllable(220.0, 600.0, 1000.0), Syllable(230.0, 600.0, 1000.0), Syllable(200.0, 350.0, 2600.0))

    fun phrase(syllables: List<Syllable>, v: Variation = Variation(), seed: Int = 0): ShortArray {
        val rnd = Random(seed)
        val out = ArrayList<Double>()
        repeat((0.05 * RATE).toInt()) { out += 0.0 }
        for (s in syllables) {
            val n = (s.seconds / v.tempo * RATE).toInt()
            val f0 = s.f0 * v.pitch * (1 + (rnd.nextDouble() - 0.5) * 0.04)
            for (i in 0 until n) {
                val t = i.toDouble() / RATE
                val env = sin(PI * i / n).pow(0.6)
                var x = 0.0
                var h = 1
                while (h * f0 < 7_000) {
                    val f = h * f0
                    x += (formant(f, s.f1) + 0.6 * formant(f, s.f2)) * sin(2 * PI * f * t) / h
                    h++
                }
                out += env * x
            }
            repeat((0.06 / v.tempo * RATE).toInt()) { out += 0.0 }
        }
        repeat((0.05 * RATE).toInt()) { out += 0.0 }
        val peak = out.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-9)
        val signal = out.map { it / peak * v.gain }
        val noisy = v.snrDb?.let { snr ->
            val power = signal.sumOf { it * it } / signal.size
            val sigma = sqrt(power / 10.0.pow(snr / 10))
            signal.map { it + rnd.nextGaussian() * sigma }
        } ?: signal
        return ShortArray(noisy.size) { (noisy[it].coerceIn(-1.0, 1.0) * 32_767).toInt().toShort() }
    }

    fun noise(seconds: Double, dbfs: Double, seed: Int = 1): ShortArray {
        val rnd = Random(seed)
        val sigma = 10.0.pow(dbfs / 20)
        return ShortArray((seconds * RATE).toInt()) {
            (rnd.nextGaussian() * sigma * 32_767).coerceIn(-32_767.0, 32_767.0).toInt().toShort()
        }
    }

    fun tone(seconds: Double, hz: Double, gain: Double): ShortArray =
        ShortArray((seconds * RATE).toInt()) { (sin(2 * PI * hz * it / RATE) * gain * 32_767).toInt().toShort() }

    /** Mélange de deux signaux (le second répété ou coupé à la longueur du premier). */
    fun mix(a: ShortArray, b: ShortArray): ShortArray = ShortArray(a.size) { (a[it] + b[it % b.size]).coerceIn(-32_767, 32_767).toShort() }

    fun concat(vararg parts: ShortArray): ShortArray = parts.fold(ShortArray(0)) { acc, p -> acc + p }

    private fun formant(f: Double, center: Double): Double = exp(-((f - center) / 180.0).pow(2))

    private fun Random.nextGaussian(): Double {
        val u = nextDouble().coerceAtLeast(1e-12)
        val v = nextDouble()
        return sqrt(-2 * kotlin.math.ln(u)) * kotlin.math.cos(2 * PI * v)
    }
}
