package com.cerclerouge.evidence

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant

/**
 * Un segment de preuve (30 s d'audio) et sa place dans la chaîne d'intégrité (S6.03).
 * `sha256` est le hachage du contenu **en clair**, calculé avant chiffrement ; `entryHash` lie le segment au précédent :
 * supprimer, réordonner ou modifier un segment casse la chaîne (R-EE-03).
 */
data class SegmentEntry(
    val alertId: String,
    val seq: Int,
    val startedAt: Instant,
    val durationMs: Long,
    val sha256: String,
    val prevHash: String,
    val entryHash: String
)

/** Résultat d'une vérification : `firstBad` = indice du premier segment fautif, null si tout est intact. */
data class ChainCheck(val ok: Boolean, val firstBad: Int?, val reason: String?)

object Chain {
    /** Hachage d'origine de chaque chaîne (une chaîne par alerte). */
    val GENESIS: String = "0".repeat(64)

    fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** Entrée suivante : H(prevHash ‖ alertId ‖ seq ‖ début ‖ durée ‖ hachage du segment). */
    fun next(prev: SegmentEntry?, alertId: String, startedAt: Instant, durationMs: Long, contentSha256: String): SegmentEntry {
        val prevHash = prev?.entryHash ?: GENESIS
        val seq = (prev?.seq ?: -1) + 1
        return SegmentEntry(
            alertId,
            seq,
            startedAt,
            durationMs,
            contentSha256,
            prevHash,
            entryHash(prevHash, alertId, seq, startedAt, durationMs, contentSha256)
        )
    }

    /**
     * Vérifie une chaîne complète. `contents` (facultatif) : contenus en clair des segments, pour revérifier chaque hachage
     * (outil de vérification, export) ; sans eux, seule la cohérence des entrées est contrôlée.
     */
    fun verify(entries: List<SegmentEntry>, contents: List<ByteArray>? = null): ChainCheck {
        if (contents != null &&
            contents.size != entries.size
        ) {
            return ChainCheck(false, minOf(contents.size, entries.size), "segment manquant")
        }
        var prev = GENESIS
        entries.forEachIndexed { i, e ->
            if (e.seq != i) return ChainCheck(false, i, "numéro de segment inattendu")
            if (e.prevHash != prev) return ChainCheck(false, i, "lien rompu avec le segment précédent")
            if (contents != null && sha256(contents[i]) != e.sha256) return ChainCheck(false, i, "contenu modifié")
            val expected = entryHash(e.prevHash, e.alertId, e.seq, e.startedAt, e.durationMs, e.sha256)
            if (expected != e.entryHash) return ChainCheck(false, i, "entrée modifiée")
            prev = e.entryHash
        }
        return ChainCheck(true, null, null)
    }

    /** Racine de la chaîne : le dernier `entryHash` (à contresigner par le serveur au Sprint 3). */
    fun root(entries: List<SegmentEntry>): String = entries.lastOrNull()?.entryHash ?: GENESIS

    private fun entryHash(prevHash: String, alertId: String, seq: Int, startedAt: Instant, durationMs: Long, sha: String): String {
        val id = alertId.toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(32 + 4 + id.size + 4 + 8 + 8 + 32)
        buf.put(unhex(prevHash)).putInt(id.size).put(id).putInt(seq).putLong(startedAt.toEpochMilli()).putLong(durationMs).put(unhex(sha))
        return sha256(buf.array())
    }

    internal fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    internal fun unhex(s: String): ByteArray {
        require(s.length == 64) { "hachage SHA-256 attendu" }
        return ByteArray(32) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
