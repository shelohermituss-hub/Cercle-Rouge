package com.cerclerouge.brain

import java.time.Duration
import java.time.Instant

/**
 * Raisonneur lent qui travaille en arrière-plan (modèle de langage) : on lui confie une situation, il répond plus tard.
 * Ne bloque jamais le décideur : sa réponse est reprise au passage suivant si elle est encore fraîche.
 */
interface AsyncReasoner {
    val version: String

    /** Confie la situation (ignoré si une réflexion est déjà en cours). */
    fun request(events: List<SituationEvent>, ctx: SituationContext, now: Instant)

    /** Dernière réponse produite depuis [since], ou null. */
    fun result(since: Instant): Thought?
}

/**
 * Cerveau combiné (ADR-0029) : les **règles** décident toujours ; le modèle de langage peut seulement **ajouter** des actions
 * (jamais en retirer) et donner une meilleure explication. Une réponse du modèle absente, en retard ou invalide ne change
 * rien. Les actions passent ensuite par la garde comme avant.
 */
class CombinedReasoner(
    private val rules: Reasoner,
    private val llm: AsyncReasoner?,
    private val freshness: Duration = Duration.ofSeconds(FRESH_SECONDS)
) : Reasoner {
    override val version: String get() = llm?.let { "${rules.version}+${it.version}" } ?: rules.version

    override fun think(events: List<SituationEvent>, ctx: SituationContext, now: Instant): Thought {
        val base = rules.think(events, ctx, now)
        val model = llm ?: return base
        runCatching { model.request(events, ctx, now) }
        val extra = runCatching { model.result(now.minus(freshness)) }.getOrNull() ?: return base
        return merge(base, extra)
    }

    companion object {
        const val FRESH_SECONDS = 10L

        /** Union des actions (une seule par type, la règle d'abord) ; explication du modèle si elle est utilisable. */
        fun merge(base: Thought, extra: Thought): Thought {
            val kinds = base.actions.map { it.kind }.toSet()
            val added = extra.actions.filter { it.kind !in kinds && it.kind != ActionKind.NOTHING }
            val actions = base.actions.filter { it.kind != ActionKind.NOTHING || added.isEmpty() } + added
            val explanation = extra.explanation.takeIf { it.isNotBlank() && Guard.safeText(it, required = true) } ?: base.explanation
            return Thought(actions.ifEmpty { listOf(BrainAction(ActionKind.NOTHING)) }, explanation, "${base.source}+${extra.source}")
        }
    }
}

/**
 * Transforme la sortie brute du modèle en pensée : appels de fonction connus seulement (les inconnus sont comptés et
 * refusés), explication tirée de `expliquer(...)`.
 */
object ModelOutput {
    data class Read(val thought: Thought, val unknown: List<String>)

    fun read(output: String, source: String): Read {
        val parsed = FunctionCalls.parse(output)
        val known = parsed.filterIsInstance<FunctionCalls.Parsed.Known>().map { it.action }
        val unknown = parsed.filterIsInstance<FunctionCalls.Parsed.Unknown>().map { it.name }
        val explanation = known.lastOrNull { it.kind == ActionKind.EXPLAIN }?.text.orEmpty()
        return Read(Thought(known, explanation, source), unknown)
    }

    /** Message utilisateur envoyé au modèle : la situation rendue en texte, puis la consigne. */
    fun prompt(situation: String): String = "Situation :\n$situation\nRéponds seulement par des appels de fonction, un par ligne."
}
