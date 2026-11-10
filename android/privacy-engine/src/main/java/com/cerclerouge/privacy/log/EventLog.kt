package com.cerclerouge.privacy.log

import com.cerclerouge.privacy.db.EventLogDao
import com.cerclerouge.privacy.db.EventLogEntity
import java.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Journal local append-only et chaîné (R-SM-02, R-PE-03, P7). Il reste sur le téléphone.
 * Les entrées ne contiennent jamais d'audio ni de position précise : seulement des faits (« Détection à 20 h 12 — non confirmée »).
 */
class EventLog(private val dao: EventLogDao, private val clock: Clock) {
    private val mutex = Mutex()

    suspend fun append(type: String, payload: String): ChainedEntry = mutex.withLock {
        val entry = HashChain.next(dao.last()?.toChained(), clock.millis(), type, payload)
        dao.insert(EventLogEntity(entry.seq, entry.utcMillis, entry.type, entry.payload, entry.prevHash, entry.hash))
        entry
    }

    suspend fun latest(limit: Int): List<ChainedEntry> = dao.latest(limit).map { it.toChained() }

    /** Numéro de la première entrée altérée, ou null si le journal est intact. */
    suspend fun firstBreak(): Long? = HashChain.firstBreak(dao.all().map { it.toChained() })

    private fun EventLogEntity.toChained() = ChainedEntry(seq, utcMillis, type, payload, prevHash, hash)
}
