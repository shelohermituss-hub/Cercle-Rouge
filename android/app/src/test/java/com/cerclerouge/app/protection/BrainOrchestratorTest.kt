package com.cerclerouge.app.protection

import com.cerclerouge.alert.RecommendedAction
import com.cerclerouge.brain.GuardState
import com.cerclerouge.brain.MotionKind
import com.cerclerouge.brain.Reasoner
import com.cerclerouge.brain.SituationContext
import com.cerclerouge.brain.SituationEvent
import com.cerclerouge.brain.SoundFamily
import com.cerclerouge.brain.Thought
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ADR-0029 : le cerveau ne peut que monter, passe toujours par la garde, et sa panne laisse le plancher seul. */
class BrainOrchestratorTest {
    private val t0 = Instant.parse("2026-11-18T20:00:00Z")
    private val armed = GuardState(armed = true, alertOpen = false, alertLevel = 0, tripActive = false, acceptedContacts = 2)

    @Test fun `cri et arrachage demandent une alerte`() {
        val b = BrainOrchestrator()
        b.add(SituationEvent.Sound(t0, SoundFamily.DISTRESS, "Screaming", 0.8))
        b.add(SituationEvent.Motion(t0.plusSeconds(1), MotionKind.SNATCH, 0.9))
        val o = assertNotNull(b.think(t0.plusSeconds(2), SituationContext(), armed))
        assertEquals(RecommendedAction.ALERT_CONTACTS, o.request)
        assertTrue(o.journal().startsWith("src=rules-1 ok="))
        // Rien de nouveau : pas de nouvelle réflexion.
        assertNull(b.think(t0.plusSeconds(4), SituationContext(), armed))
    }

    @Test fun `sans proche accepte la garde bloque l alerte`() {
        val b = BrainOrchestrator()
        b.add(SituationEvent.Keyword(t0, 0.9))
        b.add(SituationEvent.Motion(t0, MotionKind.VIOLENT, 0.9))
        val o = assertNotNull(b.think(t0.plusSeconds(1), SituationContext(), armed.copy(acceptedContacts = 0)))
        assertTrue(o.request < RecommendedAction.ALERT_CONTACTS)
        assertTrue(o.refused.isNotEmpty())
    }

    @Test fun `protection coupee rien ne passe`() {
        val b = BrainOrchestrator()
        b.add(SituationEvent.Keyword(t0, 0.9))
        val o = assertNotNull(b.think(t0.plusSeconds(1), SituationContext(), armed.copy(armed = false)))
        assertEquals(RecommendedAction.NONE, o.request)
    }

    @Test fun `musique seule ne demande rien`() {
        val b = BrainOrchestrator()
        b.add(SituationEvent.Sound(t0, SoundFamily.CONTEXT, "Music", 0.9))
        assertEquals(RecommendedAction.NONE, b.think(t0, SituationContext(), armed)?.request)
    }

    @Test fun `un raisonneur en panne rend null et le plancher continue`() {
        val broken = object : Reasoner {
            override val version = "broken"

            override fun think(events: List<SituationEvent>, ctx: SituationContext, now: Instant): Thought = error("panne")
        }
        val b = BrainOrchestrator(broken)
        b.add(SituationEvent.Keyword(t0, 0.9))
        assertNull(b.think(t0, SituationContext(), armed))
    }

    @Test fun `reset oublie la situation`() {
        val b = BrainOrchestrator()
        b.add(SituationEvent.Keyword(t0, 0.9))
        b.reset()
        assertNull(b.think(t0, SituationContext(), armed))
    }
}
