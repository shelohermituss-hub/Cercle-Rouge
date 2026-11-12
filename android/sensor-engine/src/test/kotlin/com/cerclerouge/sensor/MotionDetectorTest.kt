package com.cerclerouge.sensor

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Banc de rejeu sur traces synthétiques à 50 Hz (à compléter par des traces réelles enregistrées sur téléphone). */
class MotionDetectorTest {
    private val g = G.toFloat()

    /** Génère des mesures toutes les 20 ms de `fromMs` à `toMs` ; `accel(t)` = ‖a‖ portée sur z, `rot(t)` sur x. */
    private fun trace(fromMs: Long, toMs: Long, accel: (Long) -> Float, rot: (Long) -> Float = { 0f }) =
        (fromMs until toMs step 20).map { t -> MotionSample(t, 0f, 0f, accel(t), rot(t), 0f, 0f) }

    private fun run(samples: List<MotionSample>, config: MotionConfig = MotionConfig()): List<MotionKind> {
        val d = MotionDetector(config)
        return samples.flatMap { d.push(it) }.map { it.kind }
    }

    @Test fun `marcher ne declenche rien`() {
        val walk = trace(0, 60_000, { t -> g + 0.3f * g * sin(2 * PI * t / 550.0).toFloat() }, { t ->
            1.5f *
                sin(2 * PI * t / 1100.0).toFloat()
        })
        assertEquals(emptyList(), run(walk))
    }

    @Test fun `courir ne declenche pas de mouvement violent`() { // secousses fortes mais rotations modérées
        val runTrace = trace(0, 30_000, { t -> g + 1.0f * g * sin(2 * PI * t / 350.0).toFloat() }, { t ->
            2.5f *
                sin(2 * PI * t / 700.0).toFloat()
        })
        assertEquals(emptyList(), run(runTrace))
    }

    @Test fun `une chute est detectee`() {
        val samples = trace(0, 1_000, { g }) + // tenu
            trace(1_000, 1_300, { 0.1f * g }) + // chute libre 300 ms
            trace(1_300, 1_360, { 3.2f * g }) + // impact
            trace(1_360, 5_000, { g }) // au sol, immobile
        assertTrue(MotionKind.FALL in run(samples))
    }

    @Test fun `poser le telephone n'est pas une chute`() {
        val samples = trace(0, 1_000, { g }) + trace(1_000, 1_200, { 1.4f * g }) + trace(1_200, 5_000, { g })
        assertTrue(MotionKind.FALL !in run(samples))
    }

    @Test fun `une lutte est un mouvement violent`() {
        val rnd = Random(7)
        val fight = trace(0, 1_000, { g }) + trace(1_000, 4_000, { g + (rnd.nextFloat() * 4 - 2) * g }, { 7f + rnd.nextFloat() * 3 })
        assertTrue(MotionKind.VIOLENT_MOTION in run(fight))
    }

    @Test fun `un arrachage apres un moment calme est detecte`() {
        val samples = trace(0, 2_000, { g }) + trace(2_000, 2_100, { 3.6f * g }, { 8f }) + trace(2_100, 4_000, { g + 0.5f * g }, { 2f })
        assertTrue(MotionKind.SNATCH in run(samples))
    }

    @Test fun `un pic au milieu d'une course n'est pas un arrachage`() {
        val samples = trace(0, 2_000, { t -> g + 1.0f * g * sin(2 * PI * t / 350.0).toFloat() }, { 2f }) +
            trace(2_000, 2_100, { 3.6f * g }, { 8f })
        assertTrue(MotionKind.SNATCH !in run(samples))
    }

    @Test fun `immobilite prolongee signalee une seule fois`() {
        val cfg = MotionConfig(stillLongMillis = 60_000)
        val events = run(trace(0, 200_000, { g }), cfg)
        assertEquals(listOf(MotionKind.STILL), events)
    }

    @Test fun `R-SE-04 sans gyroscope pas de mouvement violent ni d'arrachage`() {
        val rnd = Random(3)
        val shaking = trace(0, 4_000, { g + (rnd.nextFloat() * 4 - 2) * g }) // rotation 0 : capteur absent
        val kinds = run(shaking)
        assertTrue(MotionKind.VIOLENT_MOTION !in kinds && MotionKind.SNATCH !in kinds)
    }
}
