package com.cerclerouge.audio

import com.cerclerouge.audio.Synth.PHRASE_A
import com.cerclerouge.audio.Synth.PHRASE_B
import com.cerclerouge.audio.Synth.PHRASE_C
import com.cerclerouge.audio.Synth.Variation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RingBufferTest {
    @Test fun keepsOnlyTheLatestSamples() {
        val r = RingBuffer(4)
        r.write(shortArrayOf(1, 2, 3))
        r.write(shortArrayOf(4, 5))
        assertEquals(listOf<Short>(2, 3, 4, 5), r.last(10).toList())
        assertEquals(listOf<Short>(4, 5), r.last(2).toList())
    }

    @Test fun clearZeroesMemory() { // R-AE-01 : mis à zéro à l'arrêt
        val r = RingBuffer(16_000)
        r.write(ShortArray(20_000) { 7 })
        r.clear()
        assertTrue(r.isZeroed())
        assertEquals(0, r.size)
    }

    @Test fun neverMoreThanTenSeconds() {
        assertFailsWith<IllegalArgumentException> { RingBuffer(16_000 * 10 + 1) }
    }
}

class FeaturesTest {
    @Test fun fftFindsAPureTone() {
        val re = DoubleArray(512) { sin(2 * PI * 1_000 * it / 16_000) }
        val im = DoubleArray(512)
        Features.fft(re, im)
        val mags = (0..256).map { re[it] * re[it] + im[it] * im[it] }
        assertEquals(32, mags.indices.maxBy { mags[it] }) // 1 000 Hz / 31,25 Hz par case
    }

    @Test fun logMelShape() {
        val mel = Features.logMel(Synth.tone(1.0, 440.0, 0.3))
        assertEquals(98, mel.size) // 1 + (16 000 − 400) / 160
        assertEquals(64, mel[0].size)
    }

    @Test fun dbfsOfFullScaleSineIsAboutMinus3() {
        assertTrue(abs(Features.dbfs(Synth.tone(0.1, 1_000.0, 1.0)) + 3.0) < 0.2)
    }

    @Test fun dtwIsZeroOnItselfAndTolerantToTempo() {
        val a = Features.cepstra(Synth.phrase(PHRASE_A))
        val slow = Features.cepstra(Synth.phrase(PHRASE_A, Variation(tempo = 0.85)))
        val other = Features.cepstra(Synth.phrase(PHRASE_B))
        assertEquals(0.0, Features.dtw(a, a), 1e-9)
        assertTrue(Features.dtw(a, slow) < Features.dtw(a, other))
    }

    @Test fun featureCostPerBlockIsSmall() { // S5.03 : < 1 ms par bloc de 20 ms (mesure indicative sur la JVM)
        val second = Synth.phrase(PHRASE_A)
        repeat(20) { Features.logMel(second) } // chauffe
        val t0 = System.nanoTime()
        repeat(20) { Features.logMel(second) }
        val perBlockMs = (System.nanoTime() - t0) / 1e6 / 20 / (second.size / 320.0)
        assertTrue(perBlockMs < 1.0, "coût par bloc : $perBlockMs ms")
    }
}

class VadTest {
    private fun feed(vad: Vad, pcm: ShortArray): List<ShortArray> =
        (0 until pcm.size / Vad.BLOCK).mapNotNull { vad.push(pcm.copyOfRange(it * Vad.BLOCK, (it + 1) * Vad.BLOCK)) }

    @Test fun findsOneUtteranceBetweenSilences() {
        val pcm = Synth.concat(Synth.noise(1.0, -60.0), Synth.phrase(PHRASE_A), Synth.noise(1.0, -60.0, seed = 2))
        val found = feed(Vad(), pcm)
        assertEquals(1, found.size)
        assertTrue(found[0].size in 12_000..24_000, "taille ${found[0].size}")
    }

    @Test fun steadyNoiseIsNotSpeech() {
        assertTrue(feed(Vad(), Synth.noise(5.0, -30.0)).isEmpty())
    }

    @Test fun clickIsTooShort() {
        val click = Synth.tone(0.1, 1_000.0, 0.5)
        assertTrue(feed(Vad(), Synth.concat(Synth.noise(1.0, -60.0), click, Synth.noise(1.0, -60.0, 3))).isEmpty())
    }

    @Test fun phraseInNoiseIsFound() {
        val pcm = Synth.concat(
            Synth.noise(1.0, -45.0),
            Synth.mix(Synth.phrase(PHRASE_A), Synth.noise(2.0, -45.0, 4)),
            Synth.noise(1.0, -45.0, 5)
        )
        assertEquals(1, feed(Vad(), pcm).size)
    }
}

class SecretPhraseTest {
    private val enrollment = listOf(
        Synth.phrase(PHRASE_A, Variation(tempo = 1.0, pitch = 1.0, gain = 0.5), seed = 1),
        Synth.phrase(PHRASE_A, Variation(tempo = 0.92, pitch = 1.03, gain = 0.4), seed = 2),
        Synth.phrase(PHRASE_A, Variation(tempo = 1.08, pitch = 0.97, gain = 0.6), seed = 3)
    )

    private fun template(): PhraseTemplate = assertIs<Enrollment.Ok>(SecretPhrase.enroll(enrollment)).template

    @Test fun samePhraseIsDetected() {
        val t = template()
        listOf(
            Variation(tempo = 0.95, pitch = 1.02, gain = 0.3),
            Variation(tempo = 1.05, pitch = 0.98, gain = 0.7),
            Variation(tempo = 1.0, pitch = 1.0, gain = 0.5, snrDb = 20.0)
        ).forEachIndexed { i, v ->
            val m = SecretPhrase.match(t, Synth.phrase(PHRASE_A, v, seed = 10 + i))
            assertTrue(m.detected, "variation $v : $m (seuil ${t.threshold})")
            assertTrue(m.confidence >= 0.5)
        }
    }

    @Test fun otherPhrasesAreRejected() {
        val t = template()
        listOf(PHRASE_B, PHRASE_C).forEach { p ->
            val m = SecretPhrase.match(t, Synth.phrase(p, seed = 20))
            assertTrue(!m.detected, "phrase $p : $m (seuil ${t.threshold})")
        }
    }

    @Test fun noiseAndToneAreRejected() {
        val t = template()
        assertTrue(!SecretPhrase.match(t, Synth.noise(1.0, -20.0)).detected)
        assertTrue(!SecretPhrase.match(t, Synth.tone(1.0, 440.0, 0.4)).detected)
    }

    @Test fun badSamplesAreRefused() {
        val short = Synth.tone(0.2, 300.0, 0.5)
        assertEquals(Enrollment.BadSample(1, SampleProblem.TOO_SHORT), SecretPhrase.enroll(listOf(enrollment[0], short, enrollment[2])))
        val quiet = Synth.phrase(PHRASE_A, Variation(gain = 0.003))
        assertEquals(Enrollment.BadSample(2, SampleProblem.TOO_QUIET), SecretPhrase.enroll(listOf(enrollment[0], enrollment[1], quiet)))
    }

    @Test fun differentPhrasesCannotBeEnrolled() {
        val mixed = listOf(enrollment[0], Synth.phrase(PHRASE_B), Synth.phrase(PHRASE_C))
        assertEquals(Enrollment.Inconsistent, SecretPhrase.enroll(mixed))
    }

    @Test fun templateRoundTrips() { // R-AUD-09 : empreinte sérialisée, jamais d'audio
        val t = template()
        val text = t.encode()
        val back = assertNotNull(PhraseTemplate.decode(text))
        assertEquals(t.threshold, back.threshold, 1e-9)
        val probe = Synth.phrase(PHRASE_A, Variation(tempo = 0.97), seed = 30)
        assertEquals(SecretPhrase.match(t, probe).detected, SecretPhrase.match(back, probe).detected)
        assertNull(PhraseTemplate.decode("garbage"))
    }

    @Test fun listenerDetectsInAStream() { // S5.06 : la chaîne complète, bloc par bloc
        val listener = Listener(template())
        val stream = Synth.concat(
            Synth.noise(1.0, -55.0),
            Synth.phrase(PHRASE_B, seed = 40),
            Synth.noise(1.0, -55.0, 6),
            Synth.phrase(PHRASE_A, Variation(tempo = 1.03, gain = 0.45), seed = 41),
            Synth.noise(1.0, -55.0, 7)
        )
        val signals = (0 until stream.size / Vad.BLOCK).mapNotNull {
            listener.push(stream.copyOfRange(it * Vad.BLOCK, (it + 1) * Vad.BLOCK))
        }
        assertEquals(1, signals.size, "$signals")
        assertEquals(AudioSignal.Kind.SECRET_PHRASE, signals[0].kind)
        assertEquals(SecretPhrase.MODEL_VERSION, signals[0].modelVersion) // R-AE-03
    }
}
