package com.cerclerouge.alert

import com.cerclerouge.domain.Confidence
import com.cerclerouge.domain.Reason
import com.cerclerouge.domain.VulnerabilityProfile
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RiskDeciderTest {
    private val t0 = Instant.parse("2026-11-12T20:00:00Z")
    private val adult = DecisionContext()

    private fun obs(
        c: Component,
        v: Double,
        origin: String,
        at: Instant = t0,
        reason: Reason = Reason.VIOLENCE_SOUND,
        cls: String? = null,
        kw: Boolean = false
    ) = Observation(c, v, reason, origin, at, cls, kw)

    private fun run(
        vararg o: Observation,
        ctx: DecisionContext = adult,
        state: DeciderState = DeciderState(),
        now: Instant = t0
    ): Pair<DeciderState, Decision> {
        val s = o.fold(state) { acc, x -> RiskDecider.observe(acc, x, ctx, now) }
        return RiskDecider.evaluate(s, ctx, now)
    }

    @Test fun `un signal fort seul ne declenche pas de verification`() { // deux signaux indépendants (PDF 8.6)
        val (_, d) = run(obs(Component.B, 1.0, "sensor.fall"))
        assertEquals(1, d.signals)
        assertTrue(d.action < RecommendedAction.VERIFY)
    }

    @Test fun `deux signaux independants et un risque suffisant declenchent la verification`() {
        val (_, d) = run(
            obs(Component.A, 1.0, "audio.scream", reason = Reason.DISTRESS_SCREAM),
            obs(Component.B, 1.0, "sensor.violent"),
            obs(Component.C, 0.6, "trip", reason = Reason.TRIP_DEVIATION)
        )
        assertEquals(3, d.signals)
        assertEquals(0.35 + 0.25 + 0.12, d.risk, 1e-9)
        assertEquals(RecommendedAction.VERIFY, d.action)
        assertEquals(Reason.DISTRESS_SCREAM, d.reason) // la plus forte contribution pondérée
        assertEquals(Confidence.MEDIUM, d.confidence)
    }

    @Test fun `la meme origine repetee ne compte qu'une fois`() {
        val (_, d) = run(
            obs(Component.B, 0.9, "sensor.fall"),
            obs(Component.B, 0.95, "sensor.fall", at = t0.plusSeconds(1)),
            now = t0.plusSeconds(1)
        )
        assertEquals(1, d.signals)
    }

    @Test fun `une observation sortie de la fenetre de 10 s est oubliee`() {
        val (_, d) = run(obs(Component.A, 1.0, "audio.scream"), now = t0.plusSeconds(11))
        assertEquals(0.0, d.a)
        assertEquals(RecommendedAction.NONE, d.action)
    }

    @Test fun `R-RISK-01 le contexte seul ne declenche rien`() {
        val ctx = DecisionContext(VulnerabilityProfile.YOUNG_12_14, nightTrip = true, e = 1.0)
        val (_, d) = run(ctx = ctx)
        assertEquals(RecommendedAction.NONE, d.action)
    }

    @Test fun `R-ESC-07 un mot-cle sur suffit pour une verification`() {
        val (_, d) = run(obs(Component.A, 0.9, "keyword", reason = Reason.DISTRESS_KEYWORD, kw = true))
        assertEquals(RecommendedAction.VERIFY, d.action)
        assertEquals(Confidence.HIGH, d.confidence)
    }

    @Test fun `l'alerte directe exige l'urgence automatique choisie`() {
        val strong = arrayOf(
            obs(Component.A, 1.0, "audio.scream"),
            obs(Component.B, 1.0, "sensor.violent"),
            obs(Component.C, 1.0, "trip"),
            obs(Component.D, 1.0, "network")
        )
        assertEquals(RecommendedAction.VERIFY, run(*strong).second.action)
        assertEquals(RecommendedAction.ALERT_CONTACTS, run(*strong, ctx = adult.copy(autoEmergency = true)).second.action)
    }

    @Test fun `D additionne les causes distinctes jusqu'a 1`() {
        val (_, d) = run(obs(Component.D, 0.5, "sensor.still"), obs(Component.D, 0.5, "network"), obs(Component.D, 0.5, "network"))
        assertEquals(1.0, d.d, 1e-9)
    }

    @Test fun `hysteresis une seule emission par episode et reengagement apres la sortie`() {
        val ctx = adult
        val first = run(obs(Component.A, 1.0, "audio.scream"), obs(Component.B, 1.0, "sensor.violent"), obs(Component.C, 0.6, "trip"))
        assertEquals(RecommendedAction.VERIFY, first.second.action)
        // Une seconde plus tard, même situation : rien de nouveau n'est transmis au moteur.
        val again = RiskDecider.evaluate(first.first, ctx, t0.plusSeconds(1))
        assertEquals(RecommendedAction.NONE, again.second.action)
        assertEquals(RecommendedAction.VERIFY, again.second.engaged)
        // Le risque retombe (fenêtre écoulée) : l'épisode se termine…
        val calm = RiskDecider.evaluate(again.first, ctx, t0.plusSeconds(20))
        assertEquals(RecommendedAction.NONE, calm.second.engaged)
        // …et un nouvel épisode déclenche à nouveau.
        val t = t0.plusSeconds(30)
        val s = listOf(
            obs(Component.A, 1.0, "audio.scream", at = t),
            obs(Component.B, 1.0, "sensor.violent", at = t),
            obs(Component.C, 0.6, "trip", at = t)
        )
            .fold(calm.first) { acc, o -> RiskDecider.observe(acc, o, ctx, t) }
        assertEquals(RecommendedAction.VERIFY, RiskDecider.evaluate(s, ctx, t).second.action)
    }

    @Test fun `un contexte a ignorer releve le seuil des classes concernees`() {
        val concert = adult.copy(ignored = setOf(IgnoreContext.CONCERT))
        val weak = run(obs(Component.A, 0.6, "audio.scream", cls = "scream"), ctx = concert)
        assertEquals(0.0, weak.second.a) // 0,6 < 0,5 + 0,2 : ignoré
        val strong = run(obs(Component.A, 0.8, "audio.scream", cls = "scream"), ctx = concert)
        assertEquals(0.8, strong.second.a)
        val kw = run(obs(Component.A, 0.6, "keyword", cls = "scream", kw = true), ctx = concert)
        assertEquals(0.6, kw.second.a) // un mot-clé sûr n'est jamais ignoré
    }

    @Test fun `la sensibilite basse demande plus de risque`() {
        val o = arrayOf(obs(Component.A, 1.0, "audio.scream"), obs(Component.B, 1.0, "sensor.violent"), obs(Component.C, 0.6, "trip"))
        assertEquals(RecommendedAction.VERIFY, run(*o).second.action) // 0,72 ≥ 0,60
        val low =
            run(
                obs(Component.A, 1.0, "audio.scream"),
                obs(Component.B, 1.0, "sensor.violent"),
                ctx = adult.copy(sensitivity = Sensitivity.LOW)
            )
        assertTrue(low.second.action < RecommendedAction.VERIFY) // 0,60 < 0,70
    }

    @Test fun `E se calcule a partir de la nuit du profil et de la zone`() {
        assertEquals(0.0, DecisionContext.vulnerability(false, VulnerabilityProfile.ADULT, false))
        assertEquals(0.6, DecisionContext.vulnerability(true, VulnerabilityProfile.YOUNG_12_14, false), 1e-9)
        assertEquals(1.0, DecisionContext.vulnerability(true, VulnerabilityProfile.ISOLATED, true))
    }

    @Test fun `R-AL-06 le journal contient composantes seuil et version`() {
        val (_, d) = run(obs(Component.A, 1.0, "audio.scream"), obs(Component.B, 1.0, "sensor.violent"), obs(Component.C, 0.6, "trip"))
        val line = d.journal()
        listOf("risk=0.72", "A=1.00", "B=1.00", "C=0.60", "D=0.00", "E=0.00", "sig=3", "act=VERIFY", "cfg=risk-1", "enter=0.60").forEach {
            assertTrue(it in line, "absent : $it dans $line")
        }
    }
}
