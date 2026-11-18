package com.cerclerouge.brain

import java.text.Normalizer

/**
 * Ce que l'utilisateur demande au veilleur (IA.8), reconnu par règles en attendant le modèle de langage.
 * Les commandes graves (annuler, désarmer, effacer) ne sont jamais exécutées par la voix : l'app ouvre l'écran du PIN.
 */
enum class Intent {
    /** « Active la protection ». */
    ACTIVATE,

    /** « Annule l'alerte », « désactive », « arrête », « efface » : demande le PIN, jamais exécuté directement. */
    GRAVE,

    /** « Tout va bien ? », « Où en est la protection ? ». */
    STATUS,

    /** « Combien de temps avant d'arriver ? ». */
    TRIP_TIME,

    /** « Où sont mes proches ? », « Qui est prévenu ? ». */
    CONTACTS,

    /** « Aide-moi », « j'ai peur », « on me suit » : conseils + proposer un trajet suivi ou le SOS. */
    HELP,

    /** Toute autre question : base de connaissances. */
    QUESTION
}

object Intents {
    private fun norm(t: String) = Normalizer.normalize(t.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private val GRAVE = listOf("annul", "desactiv", "desarm", "arret", "stop", "efface", "supprim", "coupe la protection")
    private val ACTIVATE = listOf("active la protection", "activer la protection", "allume", "protege moi", "demarre la protection")
    private val STATUS = listOf("ca va", "tout va bien", "etat", "suis-je protege", "suis je protege", "protection active")
    private val TRIP = listOf("combien de temps", "quand j'arrive", "quand j arrive", "arriver", "temps restant")
    private val CONTACTS = listOf("mes proches", "qui est prevenu", "mon pere", "ma mere", "contacts")
    private val HELP = listOf("aide", "peur", "me suit", "suivi", "danger", "agress", "enlev", "kidnap", "secours")

    fun of(text: String): Intent {
        val t = norm(text)
        return when {
            GRAVE.any { it in t } -> Intent.GRAVE
            ACTIVATE.any { it in t } -> Intent.ACTIVATE
            TRIP.any { it in t } -> Intent.TRIP_TIME
            CONTACTS.any { it in t } -> Intent.CONTACTS
            HELP.any { it in t } -> Intent.HELP
            STATUS.any { it in t } -> Intent.STATUS
            else -> Intent.QUESTION
        }
    }
}
