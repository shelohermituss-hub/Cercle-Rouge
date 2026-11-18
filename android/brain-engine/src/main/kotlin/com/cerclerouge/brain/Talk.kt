package com.cerclerouge.brain

/** Ce que l'app sait au moment de la question (aucun nom, aucun numéro). */
data class TalkStatus(
    val armed: Boolean,
    val alert: Boolean,
    val tripMinutesLeft: Long? = null,
    val acceptedContacts: Int = 0,
    val pendingContacts: Int = 0
)

/** Action que l'écran peut proposer après une réponse ; jamais une action grave (annuler, désarmer, effacer). */
enum class TalkAction { ARM, OPEN_TRIP, OPEN_CONTACTS, ENTER_PIN }

data class TalkReply(val text: String, val card: Card? = null, val action: TalkAction? = null)

/**
 * « Parler au veilleur » (IA.8) sans modèle de langage : intention reconnue par règles, puis réponse tirée de l'état de l'app
 * ou de la base de connaissances. Hors de la base, il dit qu'il ne sait pas et rappelle le 114, sans rien inventer.
 * Les commandes graves renvoient vers le code PIN : la voix ne suffit jamais (menace T15).
 */
class Veilleur(private val kb: KnowledgeBase) {
    @Suppress("CyclomaticComplexMethod")
    fun answer(question: String, s: TalkStatus): TalkReply = when (Intents.of(question)) {
        Intent.GRAVE -> TalkReply(
            "Je ne peux pas faire ça à ta place. Pour annuler ou couper la protection, il faut ton code PIN.",
            kb.byId("desactiver"),
            TalkAction.ENTER_PIN
        )
        Intent.ACTIVATE -> if (s.armed || s.alert) {
            TalkReply("La protection est déjà active.")
        } else {
            TalkReply("J'active la protection.", action = TalkAction.ARM)
        }
        Intent.STATUS -> TalkReply(
            when {
                s.alert -> "Une alerte est en cours. Si tu vas bien, réponds avec ton code."
                s.armed -> "La protection est active. Je veille, tout est calme pour l'instant."
                else -> "La protection est coupée. Dis « active la protection » ou appuie sur le bouton central."
            },
            action = if (!s.armed && !s.alert) TalkAction.ARM else null
        )
        Intent.TRIP_TIME -> when (val m = s.tripMinutesLeft) {
            null -> TalkReply("Aucun trajet suivi en ce moment. Veux-tu en lancer un ?", kb.byId("trajet"), TalkAction.OPEN_TRIP)
            in Long.MIN_VALUE..0L -> TalkReply("L'heure d'arrivée prévue est passée. Si tu es arrivé, termine le trajet.")
            else -> TalkReply("Il reste environ $m min avant l'heure d'arrivée prévue.")
        }
        Intent.CONTACTS -> TalkReply(
            when {
                s.acceptedContacts == 0 && s.pendingContacts == 0 -> "Tu n'as encore aucun proche. Ajoute-en dans Proches."
                s.acceptedContacts == 0 -> "Tes proches n'ont pas encore accepté l'invitation : en alerte, seul le SOS les préviendrait."
                else -> "${s.acceptedContacts} proche(s) ont accepté : ce sont eux qui seraient prévenus en alerte."
            },
            kb.byId("proches"),
            TalkAction.OPEN_CONTACTS
        )
        Intent.HELP -> {
            val card = kb.best(question) ?: kb.byId("conduite-suivi")
            TalkReply(
                (card?.body ?: "") + if (s.armed) "" else " La protection est coupée : active-la ou garde le SOS sous le doigt.",
                card,
                if (s.armed || s.alert) TalkAction.OPEN_TRIP else TalkAction.ARM
            )
        }
        Intent.QUESTION -> kb.best(question)?.let { TalkReply(it.body, it) }
            ?: TalkReply("Je ne sais pas répondre à ça. En cas de danger, appelle le 114 ou maintiens le SOS.")
    }
}
