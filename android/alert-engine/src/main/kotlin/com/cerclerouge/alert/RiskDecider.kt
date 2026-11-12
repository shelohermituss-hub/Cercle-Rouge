package com.cerclerouge.alert

import com.cerclerouge.domain.Confidence
import com.cerclerouge.domain.Reason
import com.cerclerouge.domain.VulnerabilityProfile
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** Composantes du risque observées par les détecteurs (E, le contexte, est fourni à part : ce n'est pas un signal). */
enum class Component { A, B, C, D }

/**
 * Un signal observé par un détecteur (audio, capteurs, trajet, réseau), déjà ramené entre 0 et 1.
 *
 * @property origin source du signal : deux observations de même origine ne comptent qu'une fois dans la règle des deux signaux
 *   indépendants (ex. « keyword », « sensor.fall », « audio.scream », « trip », « network »).
 * @property audioClass classe audio sujette aux contextes à ignorer (« scream », « impact », « horn_brake »…), null sinon.
 * @property keywordHighConfidence mot-clé de détresse reconnu avec une confiance élevée (exception R-ESC-07).
 */
data class Observation(
    val component: Component,
    val value: Double,
    val reason: Reason,
    val origin: String,
    val at: Instant,
    val audioClass: String? = null,
    val keywordHighConfidence: Boolean = false
) {
    init {
        require(value in 0.0..1.0) { "valeur hors de [0, 1] : $value" }
    }
}

/** Contexte à ignorer choisi par l'utilisateur (PDF 8.6, S5.09) : relève les seuils des classes audio concernées. */
enum class IgnoreContext(val audioClasses: Set<String>) {
    CONCERT(setOf("scream", "music_crowd")),
    SPORT(setOf("scream", "impact")),
    DRIVING(setOf("horn_brake")),
    SLEEP(setOf("alarm", "crying"))
}

/** Réglages versionnés de la décision (R-RISK-03) : la version est inscrite avec chaque décision (R-AL-06). */
data class DecisionConfig(
    val version: String = "risk-1",
    val window: Duration = Duration.ofSeconds(10),
    /** Valeur minimale pour qu'une observation compte comme un signal. */
    val signalMin: Double = 0.5,
    /** Seuil de sortie de l'hystérésis (l'entrée est le seuil de vérification de RiskFusion, 0,60). */
    val exitAt: Double = 0.40,
    /** Relèvement du seuil d'une classe audio sous un contexte à ignorer. */
    val ignoredClassMargin: Double = 0.20
)

/** Ce que l'app connaît du moment : profil, nuit, sensibilité, contextes actifs, et la composante E calculée. */
data class DecisionContext(
    val profile: VulnerabilityProfile = VulnerabilityProfile.ADULT,
    val nightTrip: Boolean = false,
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
    val autoEmergency: Boolean = false,
    val ignored: Set<IgnoreContext> = emptySet(),
    /** Contexte de vulnérabilité (nuit, profil, zone non sûre), plafonné à 1 ; jamais un signal (R-RISK-01). */
    val e: Double = 0.0
) {
    init {
        require(e in 0.0..1.0) { "E hors de [0, 1] : $e" }
    }

    companion object {
        /** E = nuit (22 h – 5 h) +0,3 ; profil 12–14 ans ou personne isolée +0,3 ; zone non sûre +0,4 ; plafond 1. */
        fun vulnerability(night: Boolean, profile: VulnerabilityProfile, unsafeZone: Boolean): Double {
            var e = 0.0
            if (night) e += 0.3
            if (profile == VulnerabilityProfile.YOUNG_12_14 || profile == VulnerabilityProfile.ISOLATED) e += 0.3
            if (unsafeZone) e += 0.4
            return e.coerceAtMost(1.0)
        }
    }
}

/** Mémoire du décideur : observations récentes et niveau engagé (hystérésis). */
data class DeciderState(val observations: List<Observation> = emptyList(), val engaged: RecommendedAction = RecommendedAction.NONE)

/**
 * Une décision : composantes, score, nombre de signaux, action à transmettre au moteur. `action` n'est VERIFY ou ALERT_CONTACTS
 * qu'au **front montant** (une seule fois par épisode) ; tant que le risque reste au-dessus du seuil de sortie, rien n'est
 * renvoyé au moteur.
 */
data class Decision(
    val a: Double,
    val b: Double,
    val c: Double,
    val d: Double,
    val e: Double,
    val risk: Double,
    val signals: Int,
    val action: RecommendedAction,
    val engaged: RecommendedAction,
    val reason: Reason?,
    val confidence: Confidence,
    val configVersion: String,
    val enterAt: Double
) {
    /** Ligne de journal (R-AL-06) : composantes, seuil, version ; aucune donnée personnelle ni audio. */
    fun journal(): String = String.format(
        Locale.ROOT,
        "risk=%.2f A=%.2f B=%.2f C=%.2f D=%.2f E=%.2f sig=%d act=%s level=%s reason=%s cfg=%s enter=%.2f",
        risk,
        a,
        b,
        c,
        d,
        e,
        signals,
        action,
        engaged,
        reason ?: "-",
        configVersion,
        enterAt
    )
}

/**
 * Le « cerveau » (PDF 8.2, 8.5, 8.6 ; S5.08) : reçoit les observations de tous les détecteurs, garde une fenêtre glissante de
 * 10 s, calcule `Risk = w₁A + w₂B + w₃C + w₄D + w₅E` (ADR-0008, [RiskFusion]) avec les poids du profil, exige deux signaux
 * indépendants, applique une hystérésis (entrée 0,60, sortie 0,40, décalées par la sensibilité) et ne décide jamais d'appeler
 * les secours (R-AUD-03). La période réfractaire après une fausse alerte est appliquée par le moteur ([AlertEngine]).
 * Fonctions pures : l'heure est passée en paramètre.
 */
object RiskDecider {
    /**
     * Ajoute une observation. Sous un contexte à ignorer, une classe audio concernée ne compte que si sa valeur dépasse le seuil
     * relevé de [DecisionConfig.ignoredClassMargin] ; jamais pour un mot-clé sûr (ni pour la phrase secrète et le SOS, qui ne
     * passent pas par ici).
     */
    fun observe(
        state: DeciderState,
        obs: Observation,
        ctx: DecisionContext,
        now: Instant,
        config: DecisionConfig = DecisionConfig()
    ): DeciderState {
        val ignoredClasses = ctx.ignored.flatMap { it.audioClasses }.toSet()
        val muted = obs.audioClass != null &&
            obs.audioClass in ignoredClasses &&
            !obs.keywordHighConfidence &&
            obs.value < config.signalMin + config.ignoredClassMargin
        if (muted) return state.copy(observations = prune(state.observations, now, config))
        return state.copy(observations = prune(state.observations, now, config) + obs)
    }

    /** Évalue le risque à l'instant `now` et rend la décision et le nouvel état. */
    fun evaluate(
        state: DeciderState,
        ctx: DecisionContext,
        now: Instant,
        config: DecisionConfig = DecisionConfig()
    ): Pair<DeciderState, Decision> {
        val recent = prune(state.observations, now, config)
        fun max(c: Component) = recent.filter { it.component == c }.maxOfOrNull { it.value } ?: 0.0

        // D : 0,5 par cause (immobilité, perte de réseau…), 1 si les deux : somme des origines distinctes, plafonnée.
        val d = recent.filter { it.component == Component.D }.groupBy { it.origin }.values.sumOf { group -> group.maxOf { it.value } }
            .coerceAtMost(1.0)
        val a = max(Component.A)
        val b = max(Component.B)
        val c = max(Component.C)
        val signals = recent.filter { it.value >= config.signalMin }.map { it.origin }.toSet().size
        val keyword = recent.any { it.keywordHighConfidence }
        val inputs = RiskSignals(a, b, c, d, ctx.e, signals, keyword)
        val weights = RiskWeights.forProfile(ctx.profile, ctx.nightTrip)
        val risk = RiskFusion.score(inputs, weights)
        val raw = RiskFusion.recommend(inputs, weights, ctx.sensitivity, ctx.autoEmergency)

        // Hystérésis : un épisode engagé (vérification ou alerte) le reste tant que le risque ne passe pas sous la sortie.
        val exit = config.exitAt + ctx.sensitivity.thresholdShift
        val engaged = when {
            raw >= state.engaged -> raw
            state.engaged >= RecommendedAction.VERIFY && risk >= exit -> state.engaged
            else -> raw
        }
        val rising = engaged >= RecommendedAction.VERIFY && engaged > state.engaged
        val action = when {
            rising -> engaged
            engaged >= RecommendedAction.VERIFY -> RecommendedAction.NONE // déjà transmis : rien de nouveau au moteur
            else -> raw // NONE ou LOG (journal seulement)
        }
        val reason = recent.maxByOrNull { contribution(it, weights) }?.reason
        val confidence = when {
            risk >= 0.85 || keyword -> Confidence.HIGH
            risk >= 0.60 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        val decision = Decision(
            a,
            b,
            c,
            d,
            ctx.e,
            risk,
            signals,
            action,
            engaged,
            reason,
            confidence,
            config.version,
            0.60 + ctx.sensitivity.thresholdShift
        )
        return DeciderState(recent, engaged) to decision
    }

    private fun contribution(o: Observation, w: RiskWeights) = o.value * when (o.component) {
        Component.A -> w.w1
        Component.B -> w.w2
        Component.C -> w.w3
        Component.D -> w.w4
    }

    private fun prune(list: List<Observation>, now: Instant, config: DecisionConfig) =
        list.filter { !it.at.isBefore(now.minus(config.window)) && !it.at.isAfter(now) }
}
