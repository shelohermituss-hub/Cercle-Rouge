package com.cerclerouge.brain

import java.text.Normalizer
import kotlin.math.ln

/**
 * Fiche de la base de connaissances (IA.7) : ce que le veilleur a le droit de dire. `validated` reste faux tant qu'un
 * professionnel n'a pas relu une fiche de conduite à tenir ; l'app l'indique alors à l'utilisateur.
 */
data class Card(
    val id: String,
    val title: String,
    val body: String,
    val keywords: List<String> = emptyList(),
    val validated: Boolean = false
)

/**
 * Recherche locale BM25 (sans modèle d'embeddings pour l'instant). Une fiche n'est retenue que si elle couvre au moins
 * [minCoverage] des mots utiles de la question. Hors de la base : null, et l'app répond « Je ne sais pas » avec le numéro
 * d'urgence, jamais une invention.
 */
class KnowledgeBase(val cards: List<Card>, private val minCoverage: Double = 0.3) {
    private val docs = cards.map { tokens(it.title + " " + it.title + " " + it.keywords.joinToString(" ") + " " + it.body) }
    private val avgLen = docs.map { it.size }.average().takeIf { !it.isNaN() && it > 0 } ?: 1.0
    private val df: Map<String, Int> = docs.flatMap { it.toSet() }.groupingBy { it }.eachCount()

    val ids: Set<String> get() = cards.map { it.id }.toSet()

    fun search(question: String, top: Int = 3): List<Pair<Card, Double>> {
        val q = tokens(question).toSet()
        if (q.isEmpty()) return emptyList()
        val n = docs.size
        return docs.mapIndexedNotNull { i, d ->
            val tf = d.groupingBy { it }.eachCount()
            if (q.count { it in tf } < q.size * minCoverage) return@mapIndexedNotNull null
            val score = q.sumOf { t ->
                val f = tf[t] ?: return@sumOf 0.0
                val idf = ln(1 + (n - (df[t] ?: 0) + 0.5) / ((df[t] ?: 0) + 0.5))
                idf * f * (K1 + 1) / (f + K1 * (1 - B + B * d.size / avgLen))
            }
            cards[i] to score
        }.filter { it.second > 0.0 }.sortedByDescending { it.second }.take(top)
    }

    fun best(question: String): Card? = search(question, 1).firstOrNull()?.first

    fun byId(id: String): Card? = cards.firstOrNull { it.id == id }

    companion object {
        private const val K1 = 1.2
        private const val B = 0.75

        private val STOP = setOf(
            "le", "la", "les", "un", "une", "des", "de", "du", "d", "l", "et", "ou", "a", "au", "aux", "en", "dans", "sur", "pour",
            "par", "avec", "je", "tu", "il", "elle", "on", "nous", "vous", "ils", "me", "te", "se", "mon", "ma", "mes", "ton", "ta",
            "tes", "son", "sa", "ses", "est", "suis", "es", "que", "qui", "quoi", "comment", "faire", "fait", "ce", "cet", "cette",
            "y", "ne", "pas", "plus", "si", "c", "j", "m", "t", "s", "qu", "n"
        )

        /** Minuscules, sans accents, mots de 2 lettres ou plus, mots vides retirés, pluriel simple ramené au singulier. */
        fun tokens(text: String): List<String> {
            val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            return plain.split(Regex("[^a-z0-9]+")).filter { it.length >= 2 && it !in STOP }.map { stem(it) }
        }

        private fun stem(w: String) = if (w.length > 4 && w.endsWith('s')) w.dropLast(1) else w

        /** Lit le format des fiches (assets) : blocs séparés par une ligne `---` ; en-têtes `id:`, `titre:`, `mots:`, `valide:`. */
        fun parse(text: String): List<Card> = text.split(Regex("""(?m)^---\s*$""")).mapNotNull { block ->
            val lines = block.trim().lines()
            val head = lines.takeWhile { it.contains(':') && !it.startsWith(" ") && it.substringBefore(':').trim() in HEADERS }
            val meta = head.associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
            val id = meta["id"] ?: return@mapNotNull null
            Card(
                id = id,
                title = meta["titre"].orEmpty(),
                body = lines.drop(head.size).joinToString("\n").trim(),
                keywords = meta["mots"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
                validated = meta["valide"] == "oui"
            )
        }

        private val HEADERS = setOf("id", "titre", "mots", "valide")
    }
}
