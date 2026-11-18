package com.cerclerouge.brain

import java.time.Duration
import java.time.Instant
import java.util.Locale

/** Familles de sons (YAMNet ramené à ce qui compte pour la sécurité, docs/plan/cerveau-ia.md IA.2). */
enum class SoundFamily { DISTRESS, VIOLENCE, WEAPON, VEHICLE, CONTEXT }

/**
 * Ce que le cerveau « voit » (IA.4) : des événements typés et horodatés, jamais d'audio, de transcription, de nom ni de numéro.
 * Les libellés sont des mots fixes du programme (classes de sons, types de capteurs), jamais un texte saisi.
 */
sealed interface SituationEvent {
    val at: Instant

    /** Son reconnu : famille, classe (vocabulaire fixe de YAMNet, ex. « Screaming »), probabilité. */
    data class Sound(override val at: Instant, val family: SoundFamily, val label: String, val p: Double) : SituationEvent

    /** Mot de détresse enregistré par l'utilisateur, reconnu avec une confiance (jamais le mot lui-même). */
    data class Keyword(override val at: Instant, val confidence: Double) : SituationEvent

    /** Capteur : chute, geste violent, arrachage, immobilité. */
    data class Motion(override val at: Instant, val kind: MotionKind, val strength: Double) : SituationEvent

    /** Incident de trajet (écart, arrêt prolongé, perte de signal…). */
    data class Trip(override val at: Instant, val kind: TripKind) : SituationEvent

    data class Network(override val at: Instant, val hasData: Boolean) : SituationEvent

    /** Décision du décideur déterministe (le plancher). */
    data class Floor(override val at: Instant, val risk: Double, val signals: Int, val level: Int) : SituationEvent

    /** Réponse de l'utilisateur à une vérification, ou absence de réponse à l'échéance. */
    data class Reply(override val at: Instant, val kind: ReplyKind) : SituationEvent
}

enum class MotionKind { FALL, VIOLENT, SNATCH, STILL }

enum class TripKind { OFF_ROUTE, FAST_DEVIATION, STOP, SIGNAL_LOST, OVERDUE }

enum class ReplyKind { SAFE, HELP, NO_ANSWER }

/** Contexte stable du moment, rendu en tête du texte (aucune donnée personnelle). */
data class SituationContext(
    val hour: Int = 12,
    val profile: String = "adult",
    val protection: String = "armed",
    val alertLevel: Int = 0,
    val tripActive: Boolean = false,
    val acceptedContacts: Int = 0,
    val ignoredContexts: List<String> = emptyList(),
    val battery: Int? = null
)

/**
 * Fenêtre glissante de 5 min des événements (IA.4). Le rendu texte est stable (même historique → même texte), court
 * (≤ [MAX_CHARS] caractères, ≈ 400 jetons) et ne contient que des mots fixes et des nombres.
 */
class SituationLog(private val window: Duration = Duration.ofMinutes(5), private val maxEvents: Int = 200) {
    private val events = ArrayDeque<SituationEvent>()

    fun add(e: SituationEvent) {
        events.addLast(e)
        while (events.size > maxEvents) events.removeFirst()
    }

    /** Événements de la fenêtre, du plus ancien au plus récent ; les plus vieux sont oubliés. */
    fun recent(now: Instant): List<SituationEvent> {
        val from = now.minus(window)
        while (events.isNotEmpty() && events.first().at.isBefore(from)) events.removeFirst()
        return events.filter { !it.at.isAfter(now) }
    }

    fun clear() = events.clear()

    /** Texte pour le modèle de langage et le journal. */
    fun render(now: Instant, ctx: SituationContext): String {
        val head = buildString {
            append("contexte: heure=").append(ctx.hour)
            append(" profil=").append(ctx.profile)
            append(" protection=").append(ctx.protection)
            append(" niveau=").append(ctx.alertLevel)
            append(" trajet=").append(if (ctx.tripActive) "oui" else "non")
            append(" proches=").append(ctx.acceptedContacts)
            ctx.battery?.let { append(" batterie=").append(it) }
            if (ctx.ignoredContexts.isNotEmpty()) append(" ignorer=").append(ctx.ignoredContexts.sorted().joinToString(","))
        }
        val lines = recent(now).map { line(it, now) }
        val out = StringBuilder(head)
        // Les événements les plus récents d'abord tant qu'il reste de la place, puis remis dans l'ordre du temps.
        val kept = ArrayDeque<String>()
        var size = head.length
        for (l in lines.asReversed()) {
            if (size + l.length + 1 > MAX_CHARS) break
            kept.addFirst(l)
            size += l.length + 1
        }
        kept.forEach { out.append('\n').append(it) }
        return out.toString()
    }

    private fun line(e: SituationEvent, now: Instant): String {
        val ago = "-" + Duration.between(e.at, now).seconds + "s"
        return when (e) {
            is SituationEvent.Sound -> "$ago son ${e.family.name.lowercase()} ${clean(e.label)} p=${f(e.p)}"
            is SituationEvent.Keyword -> "$ago mot_de_detresse confiance=${f(e.confidence)}"
            is SituationEvent.Motion -> "$ago capteur ${e.kind.name.lowercase()} force=${f(e.strength)}"
            is SituationEvent.Trip -> "$ago trajet ${e.kind.name.lowercase()}"
            is SituationEvent.Network -> "$ago reseau ${if (e.hasData) "ok" else "perdu"}"
            is SituationEvent.Floor -> "$ago plancher risque=${f(e.risk)} signaux=${e.signals} niveau=${e.level}"
            is SituationEvent.Reply -> "$ago reponse ${e.kind.name.lowercase()}"
        }
    }

    companion object {
        const val MAX_CHARS = 1600

        /** Garde seulement lettres, chiffres, espace, tiret et soulignement (défense contre un libellé inattendu). */
        fun clean(label: String): String = label.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }.take(40).trim()

        private fun f(v: Double) = String.format(Locale.ROOT, "%.2f", v)
    }
}
