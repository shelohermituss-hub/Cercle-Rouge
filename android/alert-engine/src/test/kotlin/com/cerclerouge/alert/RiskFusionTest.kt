package com.cerclerouge.alert

import com.cerclerouge.domain.VulnerabilityProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RiskFusionTest {
    private val adult = RiskWeights.forProfile(VulnerabilityProfile.ADULT, nightTrip = false)

    @Test fun `ADR-0008 le score est la somme ponderee`() {
        val s = RiskSignals(a = 1.0, b = 0.5, c = 0.5, d = 0.0, e = 1.0, independentSignals = 2)
        assertEquals(0.35 + 0.125 + 0.10 + 0.0 + 0.10, RiskFusion.score(s, adult), 1e-9)
    }

    @Test fun `R-RISK-02 les poids de chaque profil sont valides de jour comme de nuit`() {
        VulnerabilityProfile.entries.forEach { p ->
            listOf(false, true).forEach { night -> RiskWeights.forProfile(p, night) } // l'init vérifie somme et w5
        }
        assertFailsWith<IllegalArgumentException> { RiskWeights(0.3, 0.3, 0.2, 0.0, 0.2) }
        assertFailsWith<IllegalArgumentException> { RiskWeights(0.5, 0.5, 0.5, 0.0, 0.0) }
    }

    @Test fun `R-RISK-01 le contexte seul ne declenche rien`() {
        val young = RiskWeights.forProfile(VulnerabilityProfile.YOUNG_12_14, nightTrip = true)
        assertEquals(RecommendedAction.NONE, RiskFusion.recommend(RiskSignals(e = 1.0, independentSignals = 5), young, Sensitivity.HIGH))
    }

    @Test fun `R-ESC-07 un seul signal fort ne donne qu une entree de journal`() {
        val s = RiskSignals(a = 1.0, b = 1.0, independentSignals = 1)
        assertEquals(RecommendedAction.LOG, RiskFusion.recommend(s, adult))
    }

    @Test fun `deux signaux au dessus de 0,60 demandent une verification`() {
        val s = RiskSignals(a = 1.0, b = 1.0, independentSignals = 2) // 0,60
        assertEquals(RecommendedAction.VERIFY, RiskFusion.recommend(s, adult))
    }

    @Test fun `la sensibilite deplace les seuils`() {
        val s = RiskSignals(a = 0.9, b = 0.9, independentSignals = 2) // 0,54
        assertEquals(RecommendedAction.LOG, RiskFusion.recommend(s, adult, Sensitivity.NORMAL))
        assertEquals(RecommendedAction.VERIFY, RiskFusion.recommend(s, adult, Sensitivity.HIGH))
        assertEquals(
            RecommendedAction.LOG,
            RiskFusion.recommend(RiskSignals(a = 1.0, b = 1.0, independentSignals = 2), adult, Sensitivity.LOW)
        )
    }

    @Test fun `R-ESC-07 un mot-cle sur demande une verification sans second signal`() {
        val s = RiskSignals(a = 0.9, keywordHighConfidence = true, independentSignals = 1)
        assertEquals(RecommendedAction.VERIFY, RiskFusion.recommend(s, adult))
    }

    @Test fun `l alerte directe exige l urgence automatique`() {
        val s = RiskSignals(a = 1.0, b = 1.0, c = 1.0, d = 1.0, independentSignals = 3) // 0,90
        assertEquals(RecommendedAction.VERIFY, RiskFusion.recommend(s, adult, autoEmergency = false))
        assertEquals(RecommendedAction.ALERT_CONTACTS, RiskFusion.recommend(s, adult, autoEmergency = true))
    }

    @Test fun `les composantes hors de 0 a 1 sont refusees`() {
        assertFailsWith<IllegalArgumentException> { RiskSignals(a = 1.2) }
    }
}
