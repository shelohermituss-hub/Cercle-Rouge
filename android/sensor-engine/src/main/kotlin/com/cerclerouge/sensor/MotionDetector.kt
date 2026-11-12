package com.cerclerouge.sensor

import kotlin.math.sqrt

/** Une mesure de mouvement (50 Hz au plus, R-SE-01) : accélération en m/s² (gravité comprise), rotation en rad/s (0 si absent). */
data class MotionSample(
    val tMillis: Long,
    val ax: Float,
    val ay: Float,
    val az: Float,
    val gx: Float = 0f,
    val gy: Float = 0f,
    val gz: Float = 0f
) {
    val accel: Double get() = sqrt((ax * ax + ay * ay + az * az).toDouble())
    val rotation: Double get() = sqrt((gx * gx + gy * gy + gz * gz).toDouble())
}

/** Ce que les capteurs peuvent signaler (docs/modules/sensor-engine.md). Jamais une alerte à eux seuls : un signal pour le cerveau. */
enum class MotionKind {
    /** Chute libre, impact, puis immobilité. */
    FALL,

    /** Secousses fortes et rotations rapides pendant 2 s (lutte, bousculade). */
    VIOLENT_MOTION,

    /** Téléphone tenu calme puis arraché d'un coup (pic fort et rotation brusque). */
    SNATCH,

    /** Aucun mouvement depuis longtemps (téléphone posé, abandonné ou retiré). */
    STILL
}

data class MotionEvent(val kind: MotionKind, val atMillis: Long, val strength: Double)

/** Seuils initiaux (sensor-engine.md), **à calibrer** sur de vrais téléphones et de vraies traces. */
data class MotionConfig(
    val freeFall: Double = 0.3 * G,
    val freeFallMillis: Long = 100,
    val impact: Double = 2.5 * G,
    val impactWithinMillis: Long = 1_000,
    val stillAfterMillis: Long = 2_000,
    /** Variation maximale de ‖a‖ (m/s²) pour « immobile ». */
    val stillTolerance: Double = 0.6,
    val violentStd: Double = 0.6 * G,
    val violentRotation: Double = 5.0,
    val violentWindowMillis: Long = 2_000,
    val snatchPeak: Double = 3.0 * G,
    val snatchRotation: Double = 5.0,
    /** Calme exigé juste avant un arrachage. */
    val calmBeforeMillis: Long = 1_500,
    val stillLongMillis: Long = 10 * 60_000,
    /** Pause après un événement du même type (évite les rafales). */
    val cooldownMillis: Long = 10_000
)

const val G = 9.81

/**
 * Détecteurs de mouvement par règles, sans apprentissage (sensor-engine.md « V1 par règles ») : chute, mouvement violent,
 * arrachage, immobilité prolongée. Garde ~3 s de mesures en mémoire, jamais sur disque (R-SE-03). Une instance par session.
 */
class MotionDetector(private val config: MotionConfig = MotionConfig()) {
    private val window = ArrayDeque<MotionSample>()
    private val lastEvent = mutableMapOf<MotionKind, Long>()
    private var freeFallSince: Long? = null
    private var freeFallEndedAt: Long? = null
    private var impactAt: Long? = null
    private var stillSince: Long? = null
    private var stillReported = false

    fun push(s: MotionSample): List<MotionEvent> {
        window.addLast(s)
        while (window.isNotEmpty() && s.tMillis - window.first().tMillis > HISTORY_MILLIS) window.removeFirst()
        val events = mutableListOf<MotionEvent>()
        fall(s)?.let(events::add)
        violent(s)?.let(events::add)
        snatch(s)?.let(events::add)
        still(s)?.let(events::add)
        return events.filter { emit(it) }
    }

    private fun emit(e: MotionEvent): Boolean {
        val last = lastEvent[e.kind]
        if (last != null && e.atMillis - last < config.cooldownMillis) return false
        lastEvent[e.kind] = e.atMillis
        return true
    }

    /** Chute libre (‖a‖ < 0,3 g plus de 100 ms) → impact (> 2,5 g en moins d'1 s) → immobilité (> 2 s). */
    private fun fall(s: MotionSample): MotionEvent? {
        val a = s.accel
        if (a < config.freeFall) {
            if (freeFallSince == null) freeFallSince = s.tMillis
            return null
        }
        val since = freeFallSince
        if (since != null) {
            if (s.tMillis - since >= config.freeFallMillis) freeFallEndedAt = s.tMillis
            freeFallSince = null
        }
        val ended = freeFallEndedAt
        if (ended != null && impactAt == null && a > config.impact && s.tMillis - ended <= config.impactWithinMillis) impactAt = s.tMillis
        if (ended != null && impactAt == null && s.tMillis - ended > config.impactWithinMillis) freeFallEndedAt = null
        val impact = impactAt ?: return null
        if (s.tMillis - impact < config.stillAfterMillis) return null
        freeFallEndedAt = null
        impactAt = null
        val after = window.filter { it.tMillis >= impact + 300 }
        return if (after.isNotEmpty() && isStill(after)) MotionEvent(MotionKind.FALL, s.tMillis, 0.8) else null
    }

    /** Écart-type de ‖a‖ sur 2 s au-dessus du seuil, avec des rotations rapides : lutte, bousculade. */
    private fun violent(s: MotionSample): MotionEvent? {
        val recent = window.filter { s.tMillis - it.tMillis <= config.violentWindowMillis }
        if (recent.size < MIN_SAMPLES || recent.last().tMillis - recent.first().tMillis < config.violentWindowMillis * 3 / 4) return null
        val std = std(recent.map { it.accel })
        val rot = recent.maxOf { it.rotation }
        if (std < config.violentStd || rot < config.violentRotation) return null
        return MotionEvent(MotionKind.VIOLENT_MOTION, s.tMillis, (std / (2 * config.violentStd)).coerceIn(0.5, 1.0))
    }

    /** Calme puis pic d'accélération et rotation brusque : le téléphone est arraché de la main ou de la poche. */
    private fun snatch(s: MotionSample): MotionEvent? {
        if (s.accel < config.snatchPeak || s.rotation < config.snatchRotation) return null
        val before = window.filter { it.tMillis < s.tMillis - 100 && s.tMillis - it.tMillis <= config.calmBeforeMillis + 100 }
        if (before.size < MIN_SAMPLES || !isStill(before, tolerance = config.stillTolerance * 3)) return null
        return MotionEvent(MotionKind.SNATCH, s.tMillis, 0.9)
    }

    /** Immobilité continue au-delà de [MotionConfig.stillLongMillis] : signalée une fois, jusqu'au prochain mouvement. */
    private fun still(s: MotionSample): MotionEvent? {
        val recent = window.filter { s.tMillis - it.tMillis <= 1_000 }
        if (recent.size < MIN_SAMPLES || !isStill(recent)) {
            stillSince = null
            stillReported = false
            return null
        }
        val since = stillSince ?: s.tMillis.also { stillSince = it }
        if (stillReported || s.tMillis - since < config.stillLongMillis) return null
        stillReported = true
        return MotionEvent(MotionKind.STILL, s.tMillis, 0.5)
    }

    private fun isStill(samples: List<MotionSample>, tolerance: Double = config.stillTolerance): Boolean {
        val a = samples.map { it.accel }
        return (a.max() - a.min()) <= tolerance && samples.maxOf { it.rotation } < 0.5
    }

    private fun std(v: List<Double>): Double {
        val mean = v.average()
        return sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size)
    }

    private companion object {
        const val HISTORY_MILLIS = 3_000L
        const val MIN_SAMPLES = 10
    }
}
