package com.cerclerouge.app.protection

import com.cerclerouge.alert.RecommendedAction
import com.cerclerouge.brain.ActionKind
import com.cerclerouge.brain.Guard
import com.cerclerouge.brain.GuardState
import com.cerclerouge.brain.Reasoner
import com.cerclerouge.brain.RuleReasoner
import com.cerclerouge.brain.SituationContext
import com.cerclerouge.brain.SituationEvent
import com.cerclerouge.brain.SituationLog
import com.cerclerouge.brain.Thought
import com.cerclerouge.brain.Verdict
import java.time.Instant

/**
 * Le cerveau au-dessus du plancher (IA.5, ADR-0029) : lit la situation des 5 dernières minutes, demande des actions au
 * [Reasoner] (règles maintenant, modèle de langage plus tard), les passe à la [Guard] et ne garde que ce qui est permis.
 *
 * Il ne fait que **monter** : le plancher (`RiskDecider`) agit déjà seul, et ce qui sort d'ici devient au plus une demande
 * de vérification ou d'alerte au moteur (qui, sans urgence automatique, commence toujours par vérifier). Le SOS et la phrase
 * secrète ne passent jamais par ici. Une erreur du raisonneur rend `null` : le plancher continue seul.
 */
class BrainOrchestrator(private val reasoner: Reasoner = RuleReasoner) {
    val log = SituationLog()
    private var lastSeen: Instant? = null

    /** Résultat d'une réflexion : ce qui est proposé, ce que la garde laisse passer et la recommandation au moteur. */
    data class Outcome(
        val thought: Thought,
        val allowed: List<Verdict.Allowed>,
        val refused: List<Verdict.Refused>,
        val request: RecommendedAction
    ) {
        /** Ligne de journal (R-AL-06, R-AUD-04) : version, actions acceptées et refusées ; aucun texte libre. */
        fun journal(): String = "src=${thought.source} ok=${allowed.joinToString(",") { it.action.kind.call }.ifEmpty { "-" }}" +
            " refus=${refused.joinToString(",") { "${it.proposal}:${it.reason.name.lowercase()}" }.ifEmpty { "-" }}"
    }

    fun add(e: SituationEvent) = log.add(e)

    /** Oublie la situation (alerte close par l'utilisateur, protection coupée) : rien ne doit relancer une alerte réglée. */
    fun reset() {
        log.clear()
        lastSeen = null
    }

    /** Réfléchit seulement s'il y a du nouveau depuis la dernière fois ; null sinon ou en cas d'erreur. */
    fun think(now: Instant, ctx: SituationContext, guard: GuardState): Outcome? {
        val events = log.recent(now)
        val newest = events.maxOfOrNull { it.at } ?: return null
        if (lastSeen != null && !newest.isAfter(lastSeen)) return null
        lastSeen = newest
        val thought = runCatching { reasoner.think(events, ctx, now) }.getOrNull() ?: return null
        val verdicts = Guard.check(thought.actions, guard)
        val allowed = verdicts.filterIsInstance<Verdict.Allowed>()
        val request = when {
            allowed.any {
                (it.action.kind == ActionKind.RAISE_LEVEL && (it.action.level ?: 0) >= 2) ||
                    it.action.kind == ActionKind.NOTIFY_CONTACTS
            } ->
                RecommendedAction.ALERT_CONTACTS
            allowed.any {
                it.action.kind == ActionKind.ASK_VERIFICATION || it.action.kind == ActionKind.RAISE_LEVEL
            } -> RecommendedAction.VERIFY
            else -> RecommendedAction.NONE
        }
        return Outcome(thought, allowed, verdicts.filterIsInstance<Verdict.Refused>(), request)
    }
}
