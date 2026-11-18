package com.cerclerouge.brain

/**
 * Liste fermée des actions que le cerveau peut proposer (ADR-0029). Rien d'autre n'existe pour lui : ni appeler les secours,
 * ni annuler, ni désarmer, ni effacer.
 *
 * @property call nom de la fonction pour le modèle de langage.
 */
enum class ActionKind(val call: String) {
    OBSERVE_MORE("observer_encore"),
    ASK_VERIFICATION("demander_verification"),
    RAISE_LEVEL("monter_niveau"),
    NOTIFY_CONTACTS("prevenir_proches"),
    START_EVIDENCE("demarrer_preuves"),
    SHARE_POSITION("partager_position"),
    DISCREET_MODE("mode_discret"),
    EXPLAIN("expliquer"),
    ANSWER("repondre"),
    NOTHING("rien");

    companion object {
        fun ofCall(name: String): ActionKind? = entries.firstOrNull { it.call == name.trim().lowercase() }
    }
}

/** Une action proposée, avec ses paramètres éventuels. */
data class BrainAction(
    val kind: ActionKind,
    val level: Int? = null,
    val count: Int? = null,
    val seconds: Int? = null,
    val text: String? = null,
    val card: String? = null
)

/** Ce que la garde connaît de l'app au moment de décider. */
data class GuardState(
    val armed: Boolean,
    val alertOpen: Boolean,
    val alertLevel: Int,
    val tripActive: Boolean,
    val acceptedContacts: Int,
    val notifiedLastHour: Int = 0,
    val knownCards: Set<String> = emptySet()
)

sealed interface Verdict {
    data class Allowed(val action: BrainAction) : Verdict

    data class Refused(val proposal: String, val reason: RefusalReason) : Verdict
}

enum class RefusalReason {
    /** Action hors de la liste fermée (appeler les secours, annuler, désarmer, effacer…). */
    UNKNOWN_ACTION,
    PROTECTION_OFF,
    LOWER_OR_SAME_LEVEL,
    LEVEL_OUT_OF_RANGE,
    NO_CONTACTS,
    RATE_LIMIT,
    NOT_IN_ALERT_OR_TRIP,
    NO_ALERT_SIGNAL,
    BAD_TEXT,
    UNKNOWN_CARD,
    TOO_MANY_ACTIONS,
    BAD_ARGUMENT
}

/**
 * Garde de sécurité (IA.6) : valide chaque action proposée selon l'état. Déterministe, sans effet de bord ; l'appelant
 * journalise les refus. Le chemin d'urgence (SOS, phrase secrète) et le plancher ne passent jamais par ici.
 */
object Guard {
    /** Le niveau 3 (cercle élargi) au plus : le niveau 4 (autorités) n'est jamais automatique (R-ESC-02). */
    const val MAX_LEVEL = 3

    /** Proches alertés au niveau 2 au plus (CLAUDE.md : 3 alertés). */
    const val MAX_NOTIFY = 3

    /** Envois aux proches proposés par le cerveau, par heure. */
    const val MAX_NOTIFY_PER_HOUR = 3

    const val MAX_ACTIONS = 4
    const val MAX_TEXT = 200

    fun check(proposals: List<BrainAction>, s: GuardState): List<Verdict> = proposals.mapIndexed { i, a ->
        if (i >= MAX_ACTIONS) Verdict.Refused(a.kind.call, RefusalReason.TOO_MANY_ACTIONS) else check(a, s)
    }

    /** Un appel de fonction inconnu (« annuler_alerte », « appeler_secours »…) est toujours refusé. */
    fun unknown(name: String): Verdict = Verdict.Refused(SituationLog.clean(name), RefusalReason.UNKNOWN_ACTION)

    @Suppress("CyclomaticComplexMethod")
    fun check(a: BrainAction, s: GuardState): Verdict {
        fun refuse(r: RefusalReason) = Verdict.Refused(a.kind.call, r)
        val active = s.armed || s.alertOpen
        val reading = a.kind == ActionKind.EXPLAIN || a.kind == ActionKind.ANSWER || a.kind == ActionKind.NOTHING
        if (!active && !reading) return refuse(RefusalReason.PROTECTION_OFF)
        return when (a.kind) {
            ActionKind.NOTHING -> Verdict.Allowed(a)
            ActionKind.OBSERVE_MORE -> {
                val sec = a.seconds ?: DEFAULT_OBSERVE
                if (sec !in 5..60) refuse(RefusalReason.BAD_ARGUMENT) else Verdict.Allowed(a.copy(seconds = sec))
            }
            ActionKind.ASK_VERIFICATION -> when {
                s.alertOpen -> refuse(RefusalReason.LOWER_OR_SAME_LEVEL)
                !safeText(a.text, required = false) -> refuse(RefusalReason.BAD_TEXT)
                else -> Verdict.Allowed(a)
            }
            ActionKind.RAISE_LEVEL -> {
                val l = a.level
                when {
                    l == null || l < 1 || l > MAX_LEVEL -> refuse(RefusalReason.LEVEL_OUT_OF_RANGE)
                    l <= s.alertLevel -> refuse(RefusalReason.LOWER_OR_SAME_LEVEL)
                    l >= 2 && s.acceptedContacts == 0 -> refuse(RefusalReason.NO_CONTACTS)
                    else -> Verdict.Allowed(a)
                }
            }
            ActionKind.NOTIFY_CONTACTS -> {
                val n = a.count ?: MAX_NOTIFY
                when {
                    s.acceptedContacts == 0 -> refuse(RefusalReason.NO_CONTACTS)
                    n < 1 -> refuse(RefusalReason.BAD_ARGUMENT)
                    s.notifiedLastHour >= MAX_NOTIFY_PER_HOUR -> refuse(RefusalReason.RATE_LIMIT)
                    else -> Verdict.Allowed(a.copy(count = minOf(n, MAX_NOTIFY, s.acceptedContacts)))
                }
            }
            // Pas d'enregistrement hors d'une alerte ouverte ou d'une montée de niveau dans la même décision (vérifié par l'appelant).
            ActionKind.START_EVIDENCE -> if (s.alertOpen || s.alertLevel >= 1) Verdict.Allowed(a) else refuse(RefusalReason.NO_ALERT_SIGNAL)
            ActionKind.SHARE_POSITION -> if (s.alertOpen || s.tripActive) Verdict.Allowed(a) else refuse(RefusalReason.NOT_IN_ALERT_OR_TRIP)
            ActionKind.DISCREET_MODE -> Verdict.Allowed(a)
            ActionKind.EXPLAIN -> if (safeText(a.text, required = true)) Verdict.Allowed(a) else refuse(RefusalReason.BAD_TEXT)
            ActionKind.ANSWER -> if (a.card != null && a.card in s.knownCards) Verdict.Allowed(a) else refuse(RefusalReason.UNKNOWN_CARD)
        }
    }

    /**
     * Texte montré à l'utilisateur : court, sans lien ni numéro de téléphone (un modèle manipulé ne doit pas pouvoir faire
     * appeler ou ouvrir un autre numéro que ceux de l'app), sans caractère de contrôle.
     */
    fun safeText(t: String?, required: Boolean): Boolean {
        if (t == null) return !required
        if (t.isBlank() || t.length > MAX_TEXT) return false
        if (t.any { it.isISOControl() }) return false
        val lower = t.lowercase()
        if ("http" in lower || "www." in lower || "://" in lower) return false
        if (Regex("""\d[\d\s.\-]{5,}\d""").containsMatchIn(t)) return false
        return true
    }

    private const val DEFAULT_OBSERVE = 20
}
