package com.cerclerouge.alert

import com.cerclerouge.domain.VulnerabilityProfile
import kotlin.math.abs

/**
 * Composantes du risque, chacune entre 0 et 1 (docs/ia/modele-ia-locale.md).
 *
 * @property a cri ou détresse vocale
 * @property b bruit de violence
 * @property c gravité de la déviation de trajet
 * @property d perte de réseau ou de mouvement
 * @property e contexte de vulnérabilité (n'est PAS un signal : R-RISK-01, R-ESC-07)
 * @property independentSignals nombre de signaux indépendants observés dans la fenêtre
 * @property keywordHighConfidence mot-clé de détresse reconnu avec une confiance élevée (exception R-ESC-07)
 */
data class RiskSignals(
    val a: Double = 0.0,
    val b: Double = 0.0,
    val c: Double = 0.0,
    val d: Double = 0.0,
    val e: Double = 0.0,
    val independentSignals: Int = 0,
    val keywordHighConfidence: Boolean = false
) {
    init {
        listOf(a, b, c, d, e).forEach { require(it in 0.0..1.0) { "composante hors de [0, 1] : $it" } }
        require(independentSignals >= 0)
    }
}

/** Poids w₁ à w₅ (ADR-0008). R-RISK-02 : somme = 1 et w₅ ≤ 0,15. */
data class RiskWeights(val w1: Double, val w2: Double, val w3: Double, val w4: Double, val w5: Double) {
    init {
        require(abs(w1 + w2 + w3 + w4 + w5 - 1.0) < 1e-9) { "R-RISK-02 : la somme des poids doit valoir 1" }
        require(w5 <= 0.15 + 1e-9) { "R-RISK-02 : w5 doit rester ≤ 0,15" }
        listOf(w1, w2, w3, w4, w5).forEach { require(it >= 0.0) }
    }

    companion object {
        /** Valeurs initiales de docs/ia/modele-ia-locale.md, à calibrer pendant le pilote. */
        fun forProfile(profile: VulnerabilityProfile, nightTrip: Boolean): RiskWeights {
            val base = when (profile) {
                VulnerabilityProfile.ADULT -> RiskWeights(0.35, 0.25, 0.20, 0.10, 0.10)
                VulnerabilityProfile.TEEN_15_17 -> RiskWeights(0.30, 0.25, 0.25, 0.10, 0.10)
                VulnerabilityProfile.YOUNG_12_14 -> RiskWeights(0.30, 0.20, 0.25, 0.10, 0.15)
                VulnerabilityProfile.ISOLATED -> RiskWeights(0.30, 0.25, 0.15, 0.15, 0.15)
            }
            return if (nightTrip) base.copy(w1 = base.w1 - 0.05, w3 = base.w3 + 0.05) else base
        }
    }
}

enum class Sensitivity(val thresholdShift: Double) { LOW(0.10), NORMAL(0.0), HIGH(-0.10) }

enum class RecommendedAction { NONE, LOG, VERIFY, ALERT_CONTACTS }

object RiskFusion {
    /** Risk = w₁A + w₂B + w₃C + w₄D + w₅E (ADR-0008), dans [0, 1]. */
    fun score(s: RiskSignals, w: RiskWeights): Double = w.w1 * s.a + w.w2 * s.b + w.w3 * s.c + w.w4 * s.d + w.w5 * s.e

    /**
     * Action recommandée pour un score (seuils de docs/ia/modele-ia-locale.md, sensibilité normale) :
     * < 0,30 rien ; < 0,60 journal ; ≥ 0,60 avec 2 signaux vérification ;
     * ≥ 0,85 avec 2 signaux et urgence automatique activée : alerte des proches sans vérification.
     */
    fun recommend(
        s: RiskSignals,
        w: RiskWeights,
        sensitivity: Sensitivity = Sensitivity.NORMAL,
        autoEmergency: Boolean = false
    ): RecommendedAction {
        // R-RISK-01 : le contexte seul ne déclenche jamais rien.
        if (s.a == 0.0 && s.b == 0.0 && s.c == 0.0 && s.d == 0.0) return RecommendedAction.NONE
        // R-ESC-07 : un mot-clé de détresse sûr suffit pour demander une vérification.
        if (s.keywordHighConfidence) return RecommendedAction.VERIFY
        val risk = score(s, w)
        val shift = sensitivity.thresholdShift
        val twoSignals = s.independentSignals >= 2
        return when {
            twoSignals && autoEmergency && risk >= 0.85 + shift -> RecommendedAction.ALERT_CONTACTS
            twoSignals && risk >= 0.60 + shift -> RecommendedAction.VERIFY
            risk >= 0.30 + shift -> RecommendedAction.LOG
            else -> RecommendedAction.NONE
        }
    }
}
