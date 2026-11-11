package com.cerclerouge.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Caractéristiques audio : log-Mel 64 bandes (fenêtre de 25 ms, pas de 10 ms, FFT 512, 60 à 7 600 Hz), puis coefficients
 * cepstraux (MFCC 1 à 12) normalisés par la moyenne de l'énoncé. Ces nombres ne permettent pas de réécouter l'énoncé
 * (pas de phase, résolution réduite) ; ils sont tout de même chiffrés (R-AUD-09).
 */
object Features {
    const val SAMPLE_RATE = 16_000
    const val WINDOW = 400 // 25 ms
    const val HOP = 160 // 10 ms
    const val FFT = 512
    const val MELS = 64
    const val CEPSTRA = 12

    private val hann = DoubleArray(WINDOW) { 0.5 - 0.5 * cos(2 * PI * it / (WINDOW - 1)) }
    private val filters: Array<DoubleArray> = melFilters()
    private val dct: Array<DoubleArray> =
        Array(CEPSTRA) { k -> DoubleArray(MELS) { n -> cos(PI * (k + 1) * (n + 0.5) / MELS) } }

    /** log-Mel de chaque trame : `frames × 64`. Moins d'une fenêtre → aucune trame. */
    fun logMel(samples: ShortArray): Array<DoubleArray> {
        if (samples.size < WINDOW) return emptyArray()
        val count = 1 + (samples.size - WINDOW) / HOP
        val re = DoubleArray(FFT)
        val im = DoubleArray(FFT)
        val power = DoubleArray(FFT / 2 + 1)
        return Array(count) { f ->
            re.fill(0.0)
            im.fill(0.0)
            val off = f * HOP
            for (i in 0 until WINDOW) re[i] = samples[off + i] / 32_768.0 * hann[i]
            fft(re, im)
            for (k in power.indices) power[k] = re[k] * re[k] + im[k] * im[k]
            DoubleArray(MELS) { m ->
                var e = 0.0
                val w = filters[m]
                for (k in w.indices) e += w[k] * power[k]
                ln(e + 1e-10)
            }
        }
    }

    /** MFCC 1 à 12, moyenne de l'énoncé retirée (robuste au micro et au volume). Les bords silencieux sont retirés. */
    fun cepstra(samples: ShortArray): Array<DoubleArray> {
        val mel = trimSilence(logMel(samples))
        if (mel.isEmpty()) return emptyArray()
        // Plage dynamique limitée à 50 dB sous le pic : le silence pèse pareil, qu'il soit numérique ou bruité.
        val peak = mel.maxOf { row -> row.max() }
        val floor = peak - 50.0 / 4.343
        for (row in mel) for (n in row.indices) if (row[n] < floor) row[n] = floor
        val c = Array(mel.size) { f -> DoubleArray(CEPSTRA) { k -> (0 until MELS).sumOf { n -> dct[k][n] * mel[f][n] } } }
        val mean = DoubleArray(CEPSTRA) { k -> c.sumOf { it[k] } / c.size }
        for (row in c) for (k in 0 until CEPSTRA) row[k] -= mean[k]
        return c
    }

    /** Énergie d'un bloc en dBFS (0 = pleine échelle). */
    fun dbfs(block: ShortArray, count: Int = block.size): Double {
        if (count == 0) return -120.0
        var s = 0.0
        for (i in 0 until count) {
            val x = block[i] / 32_768.0
            s += x * x
        }
        return 10 * log10(s / count + 1e-12)
    }

    /** Retire les trames de bord à plus de 35 dB sous la trame la plus forte. */
    private fun trimSilence(mel: Array<DoubleArray>): Array<DoubleArray> {
        if (mel.isEmpty()) return mel
        val energy = mel.map { row -> row.maxOrNull() ?: -23.0 }
        val peak = energy.max()
        val limit = peak - 35.0 / 4.343 // 35 dB en logarithme népérien de puissance
        val first = energy.indexOfFirst { it >= limit }
        val last = energy.indexOfLast { it >= limit }
        return mel.copyOfRange(first, last + 1)
    }

    private fun mel(hz: Double) = 2595.0 * log10(1 + hz / 700.0)

    private fun hz(mel: Double) = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1)

    private fun melFilters(): Array<DoubleArray> {
        val lo = mel(60.0)
        val hi = mel(7_600.0)
        val centers = DoubleArray(MELS + 2) { hz(lo + (hi - lo) * it / (MELS + 1)) }
        val binHz = SAMPLE_RATE.toDouble() / FFT
        return Array(MELS) { m ->
            DoubleArray(FFT / 2 + 1) { k ->
                val f = k * binHz
                val (l, c, r) = Triple(centers[m], centers[m + 1], centers[m + 2])
                when {
                    f <= l || f >= r -> 0.0
                    f <= c -> (f - l) / (c - l)
                    else -> (r - f) / (r - c)
                }
            }
        }
    }

    /** FFT radix 2 en place (taille puissance de 2). */
    internal fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            for (i in 0 until n step len) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val tr = re[b] * cr - im[b] * ci
                    val ti = re[b] * ci + im[b] * cr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
            }
            len = len shl 1
        }
    }

    /** Distance DTW normalisée entre deux suites de vecteurs (distance euclidienne par trame, divisée par n + m). */
    fun dtw(a: Array<DoubleArray>, b: Array<DoubleArray>): Double {
        if (a.isEmpty() || b.isEmpty()) return Double.POSITIVE_INFINITY
        val n = a.size
        val m = b.size
        var prev = DoubleArray(m + 1) { Double.POSITIVE_INFINITY }
        var cur = DoubleArray(m + 1)
        prev[0] = 0.0
        for (i in 1..n) {
            cur.fill(Double.POSITIVE_INFINITY)
            for (j in 1..m) {
                var d = 0.0
                val x = a[i - 1]
                val y = b[j - 1]
                for (k in x.indices) {
                    val t = x[k] - y[k]
                    d += t * t
                }
                cur[j] = sqrt(d) + minOf(prev[j], cur[j - 1], prev[j - 1])
            }
            prev = cur.also { cur = prev }
        }
        return prev[m] / (n + m)
    }
}
