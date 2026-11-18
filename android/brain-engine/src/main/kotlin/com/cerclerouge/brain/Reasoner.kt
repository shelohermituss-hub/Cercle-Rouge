package com.cerclerouge.brain

import java.time.Duration
import java.time.Instant

/** Ce que le cerveau propose pour une situation : des actions (à passer à la garde) et une explication en clair. */
data class Thought(val actions: List<BrainAction>, val explanation: String, val source: String)

/** Un cerveau : règles déterministes ou modèle de langage (même entrée, même forme de sortie). */
interface Reasoner {
    val version: String

    fun think(events: List<SituationEvent>, ctx: SituationContext, now: Instant): Thought
}

/**
 * Cerveau sans modèle de langage (niveau « sans modèle », ADR-0029) : règles lisibles sur les 5 dernières minutes.
 * Il sert aussi de repli quand le modèle est absent, lent ou en erreur. Il ne remplace pas le plancher (`RiskDecider`) : il
 * peut seulement aller plus loin que lui.
 */
object RuleReasoner : Reasoner {
    override val version = "rules-1"

    private val RECENT: Duration = Duration.ofSeconds(90)

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override fun think(events: List<SituationEvent>, ctx: SituationContext, now: Instant): Thought {
        val recent = events.filter { !it.at.isBefore(now.minus(RECENT)) }

        fun sounds(f: SoundFamily, min: Double = 0.5) = recent.filterIsInstance<SituationEvent.Sound>().filter {
            it.family == f &&
                it.p >= min
        }

        fun motion(k: MotionKind) = recent.filterIsInstance<SituationEvent.Motion>().any { it.kind == k && it.strength >= 0.5 }

        fun trip(vararg k: TripKind) = recent.filterIsInstance<SituationEvent.Trip>().any { it.kind in k }

        val distress = sounds(SoundFamily.DISTRESS).isNotEmpty()
        val violence = sounds(SoundFamily.VIOLENCE).isNotEmpty()
        val weapon = sounds(SoundFamily.WEAPON, 0.4).isNotEmpty()
        val vehicle = sounds(SoundFamily.VEHICLE).isNotEmpty()
        val context = sounds(SoundFamily.CONTEXT, 0.6).isNotEmpty()
        val keyword = recent.filterIsInstance<SituationEvent.Keyword>().any { it.confidence >= 0.6 }
        val snatch = motion(MotionKind.SNATCH)
        val violentMove = motion(MotionKind.VIOLENT)
        val fall = motion(MotionKind.FALL)
        val still = recent.filterIsInstance<SituationEvent.Motion>().any { it.kind == MotionKind.STILL }
        val tripIssue = trip(TripKind.FAST_DEVIATION, TripKind.OFF_ROUTE)
        val signalLost = trip(TripKind.SIGNAL_LOST) || recent.filterIsInstance<SituationEvent.Network>().any { !it.hasData }
        val noAnswer = recent.filterIsInstance<SituationEvent.Reply>().any { it.kind == ReplyKind.NO_ANSWER }
        val saidSafe = recent.filterIsInstance<SituationEvent.Reply>().lastOrNull()?.kind == ReplyKind.SAFE
        val bodyInvolved = snatch || violentMove || fall

        val strong = listOf(distress || keyword, violence || violentMove || snatch, tripIssue || vehicle, signalLost).count { it }
        val level = ctx.alertLevel

        fun escalate(why: String, share: Boolean) = Thought(
            buildList {
                if (level < 2) add(BrainAction(ActionKind.RAISE_LEVEL, level = 2))
                add(BrainAction(ActionKind.START_EVIDENCE))
                if (share) add(BrainAction(ActionKind.SHARE_POSITION))
                add(BrainAction(ActionKind.EXPLAIN, text = why))
            },
            why,
            version
        )

        return when {
            saidSafe -> Thought(listOf(BrainAction(ActionKind.NOTHING)), "Tu as dit que tout va bien : je reste en veille.", version)
            // Enlèvement probable : détresse ou lutte + véhicule ou écart rapide + perte de signal.
            strong >= 3 -> escalate(
                "Cri, lutte et trajet anormal en même temps : je préviens tes proches et je garde des preuves.",
                share = true
            )
            // Agression : mot de détresse ou cri + arrachage ou geste violent.
            (keyword || distress) && (snatch || violentMove) ->
                escalate("Mot de détresse et téléphone arraché : alerte silencieuse à tes proches.", share = true)
            // Pas de réponse à une vérification après une chute ou un choc.
            noAnswer && (fall || still || violence) -> escalate("Pas de réponse après la chute : je préviens tes proches.", share = true)
            // Chute puis immobilité : vérifier.
            fall && still -> verify("Chute puis immobilité : je te demande si tu vas bien.")
            // Coup de feu au loin, seul : vérification discrète.
            weapon && !bodyInvolved -> Thought(
                listOf(
                    BrainAction(ActionKind.ASK_VERIFICATION, text = "Bruit fort entendu. Tout va bien ?"),
                    BrainAction(ActionKind.DISCREET_MODE)
                ),
                "Bruit comme un coup de feu, sans autre indice : vérification discrète.",
                version
            )
            // Fête, concert, rara : contexte sans geste violent.
            context && !bodyInvolved && !keyword -> Thought(
                listOf(BrainAction(ActionKind.NOTHING)),
                "Musique ou foule autour de toi, rien d'inquiétant.",
                version
            )
            // Cris ou bagarre à côté, sans implication du téléphone : observer.
            (distress || violence) && !bodyInvolved -> Thought(
                listOf(BrainAction(ActionKind.OBSERVE_MORE, seconds = 30)),
                "Cris ou bruits de bagarre à proximité, sans geste sur ton téléphone : j'observe 30 s.",
                version
            )
            keyword -> verify("Mot de détresse reconnu : je te demande si tu vas bien.")
            else -> Thought(listOf(BrainAction(ActionKind.NOTHING)), "Tout est calme.", version)
        }
    }

    private fun verify(why: String) = Thought(
        listOf(BrainAction(ActionKind.ASK_VERIFICATION, text = "Tout va bien ?"), BrainAction(ActionKind.EXPLAIN, text = why)),
        why,
        version
    )
}
