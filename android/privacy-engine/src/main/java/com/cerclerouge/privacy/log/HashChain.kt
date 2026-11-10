package com.cerclerouge.privacy.log

import java.security.MessageDigest

/** Une entrée du journal local, chaînée à la précédente par son hachage (R-PE-03, P7). */
data class ChainedEntry(val seq: Long, val utcMillis: Long, val type: String, val payload: String, val prevHash: String, val hash: String)

/** Chaîne de hachages SHA-256 : toute suppression ou modification d'une entrée devient détectable. */
object HashChain {
    const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000"

    fun hash(prevHash: String, seq: Long, utcMillis: Long, type: String, payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        // Séparateur explicite : « a|bc » et « ab|c » ne doivent pas produire le même hachage.
        listOf(prevHash, seq.toString(), utcMillis.toString(), type, payload).forEach {
            val bytes = it.toByteArray(Charsets.UTF_8)
            digest.update(bytes.size.toString().toByteArray())
            digest.update(':'.code.toByte())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun next(previous: ChainedEntry?, utcMillis: Long, type: String, payload: String): ChainedEntry {
        val seq = (previous?.seq ?: 0) + 1
        val prev = previous?.hash ?: GENESIS
        return ChainedEntry(seq, utcMillis, type, payload, prev, hash(prev, seq, utcMillis, type, payload))
    }

    /** Retourne le numéro de la première entrée altérée, absente ou réordonnée ; null si la chaîne est intacte. */
    fun firstBreak(entries: List<ChainedEntry>): Long? {
        var expectedPrev = GENESIS
        var expectedSeq = 1L
        for (e in entries.sortedBy { it.seq }) {
            val ok = e.seq == expectedSeq && e.prevHash == expectedPrev && e.hash == hash(e.prevHash, e.seq, e.utcMillis, e.type, e.payload)
            if (!ok) return expectedSeq
            expectedPrev = e.hash
            expectedSeq++
        }
        return null
    }
}
