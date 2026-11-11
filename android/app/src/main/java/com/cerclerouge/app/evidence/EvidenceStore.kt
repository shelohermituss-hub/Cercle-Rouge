package com.cerclerouge.app.evidence

import com.cerclerouge.domain.Retention
import com.cerclerouge.evidence.Chain
import com.cerclerouge.evidence.EvidenceAlert
import com.cerclerouge.evidence.EvidenceExport
import com.cerclerouge.evidence.ExportBundle
import com.cerclerouge.evidence.RetentionPolicy
import com.cerclerouge.evidence.SegmentEntry
import com.cerclerouge.privacy.crypto.EvidenceKeys
import com.cerclerouge.privacy.db.EvidenceAlertEntity
import com.cerclerouge.privacy.db.EvidenceDao
import com.cerclerouge.privacy.db.EvidenceSegmentEntity
import com.google.crypto.tink.StreamingAead
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Ce que le coffre-fort affiche d'une alerte enregistrée. */
data class EvidenceSummary(
    val alertId: String,
    val openedAt: Instant,
    val closedAt: Instant?,
    val segments: Int,
    val seconds: Long,
    val bytes: Long,
    val deleteAt: Instant?,
    val kept: Boolean
)

/**
 * Preuves chiffrées sur le téléphone (S6.02, S6.03, S6.07 à S6.09). Chaque segment, gardé en mémoire le temps d'être encodé,
 * est haché puis écrit **directement dans un flux chiffrant** Tink : aucun octet en clair ne touche le disque (R-EVD-01).
 * Fichiers dans le stockage interne privé (`filesDir/evidence`, exclu des sauvegardes, R-EVD-02).
 * Suppression : clé détruite, puis fichiers, puis métadonnées, dans cet ordre (R-EE-02) ; reprise sûre si interrompue.
 */
class EvidenceStore(private val root: File, private val dao: EvidenceDao, private val keys: EvidenceKeys) {
    private val mutex = Mutex()
    private val primitives = mutableMapOf<String, StreamingAead>()

    /** Ouvre (ou retrouve) l'alerte : crée sa clé de données. Idempotent. */
    suspend fun open(alertId: String, at: Instant) = mutex.withLock {
        if (dao.alert(alertId) != null) return@withLock
        val (aead, wrapped) = keys.create(alertId)
        dao.insertAlert(EvidenceAlertEntity(alertId, at.toEpochMilli(), null, at.toEpochMilli(), null, wrapped))
        primitives[alertId] = aead
    }

    /** Ajoute un segment (contenu en clair en mémoire) à la chaîne de l'alerte. Le contenu est effacé de la mémoire ensuite. */
    suspend fun append(alertId: String, content: ByteArray, startedAt: Instant, durationMs: Long, now: Instant): SegmentEntry? =
        mutex.withLock {
            val aead = primitive(alertId) ?: return@withLock null
            val prev = dao.segments(alertId).lastOrNull()?.toEntry()
            val entry = Chain.next(prev, alertId, startedAt, durationMs, Chain.sha256(content))
            val file = segmentFile(alertId, entry.seq)
            file.parentFile?.mkdirs()
            aead.newEncryptingStream(FileOutputStream(file), aad(alertId, entry.seq)).use { it.write(content) }
            dao.addSegment(
                EvidenceSegmentEntity(
                    alertId,
                    entry.seq,
                    startedAt.toEpochMilli(),
                    durationMs,
                    content.size.toLong(),
                    entry.sha256,
                    entry.prevHash,
                    entry.entryHash
                ),
                now.toEpochMilli()
            )
            content.fill(0)
            entry
        }

    suspend fun close(alertId: String, at: Instant) = dao.close(alertId, at.toEpochMilli())

    suspend fun list(retention: Retention?, now: Instant): List<EvidenceSummary> = dao.alerts().filter { it.wrappedKey != null }.map { a ->
        val segs = dao.segments(a.alertId)
        EvidenceSummary(
            a.alertId,
            Instant.ofEpochMilli(a.openedAtMillis),
            a.closedAtMillis?.let(Instant::ofEpochMilli),
            segs.size,
            segs.sumOf { it.durationMs } / 1000,
            segs.sumOf { it.bytes },
            RetentionPolicy.deleteAt(a.toDomain(), retention, now),
            a.keptUntilMillis != null
        )
    }

    /** Contenu déchiffré, en mémoire (lecture dans le coffre-fort, après PIN). Null si la chaîne est rompue. */
    suspend fun read(alertId: String): List<ByteArray>? = mutex.withLock { decryptAll(alertId) }?.takeIf { it.second }?.first

    /** Vérifie la chaîne et chaque contenu. */
    suspend fun verify(alertId: String): Boolean = mutex.withLock { decryptAll(alertId)?.second ?: false }

    suspend fun keep(alertId: String, until: Instant) = dao.keep(alertId, until.toEpochMilli())

    /** Archive chiffrée par un code à usage unique (S6.09). Null si la chaîne est rompue. */
    suspend fun export(alertId: String, code: String): ByteArray? = mutex.withLock {
        val (contents, ok) = decryptAll(alertId) ?: return@withLock null
        if (!ok) return@withLock null
        val entries = dao.segments(alertId).map { it.toEntry() }
        EvidenceExport.seal(ExportBundle(alertId, entries, contents), code).also { contents.forEach { c -> c.fill(0) } }
    }

    /** Crypto-shredding (R-EE-02) : clé, puis fichiers, puis métadonnées. Rejouable sans risque. */
    suspend fun delete(alertId: String) = mutex.withLock {
        primitives.remove(alertId)
        dao.shredKey(alertId)
        alertDir(alertId).deleteRecursively()
        dao.deleteSegments(alertId)
        dao.deleteAlert(alertId)
    }

    /** Purge des preuves arrivées à échéance (S6.08). Rend le nombre d'alertes supprimées. */
    suspend fun purgeExpired(retention: Retention?, now: Instant): Int {
        val due = RetentionPolicy.due(dao.alerts().map { it.toDomain() }, retention, now)
        due.forEach { delete(it) }
        // Fichiers orphelins (suppression interrompue après la destruction de la clé) : illisibles, on les retire.
        val known = dao.alerts().map { dirName(it.alertId) }.toSet()
        root.listFiles()?.filter { it.name !in known }?.forEach { it.deleteRecursively() }
        return due.size
    }

    /** Effacement d'urgence (R-EVD-06) : toutes les clés d'abord (rapide), puis les fichiers. */
    suspend fun wipeAll() = mutex.withLock {
        primitives.clear()
        dao.shredAll()
        root.deleteRecursively()
        dao.alerts().forEach {
            dao.deleteSegments(it.alertId)
            dao.deleteAlert(it.alertId)
        }
    }

    private suspend fun primitive(alertId: String): StreamingAead? {
        primitives[alertId]?.let { return it }
        val wrapped = dao.alert(alertId)?.wrappedKey ?: return null
        return keys.open(alertId, wrapped).also { primitives[alertId] = it }
    }

    private suspend fun decryptAll(alertId: String): Pair<List<ByteArray>, Boolean>? {
        val aead = primitive(alertId) ?: return null
        val segs = dao.segments(alertId)
        val contents = segs.map { s ->
            runCatching { aead.newDecryptingStream(segmentFile(alertId, s.seq).inputStream(), aad(alertId, s.seq)).use { it.readBytes() } }
                .getOrElse { return emptyList<ByteArray>() to false }
        }
        return contents to Chain.verify(segs.map { it.toEntry() }, contents).ok
    }

    private fun aad(alertId: String, seq: Int) = "cercle-rouge/evidence/v1/$alertId/$seq".toByteArray()

    /** Nom de dossier sans l'identifiant en clair : un haché court. */
    private fun dirName(alertId: String) = Chain.sha256(alertId.toByteArray()).take(24)

    private fun alertDir(alertId: String) = File(root, dirName(alertId))

    private fun segmentFile(alertId: String, seq: Int) = File(alertDir(alertId), "%04d.bin".format(seq))

    private fun EvidenceSegmentEntity.toEntry() =
        SegmentEntry(alertId, seq, Instant.ofEpochMilli(startedAtMillis), durationMs, sha256, prevHash, entryHash)

    private fun EvidenceAlertEntity.toDomain() = EvidenceAlert(
        alertId,
        Instant.ofEpochMilli(openedAtMillis),
        closedAtMillis?.let(Instant::ofEpochMilli),
        Instant.ofEpochMilli(lastWriteMillis),
        keptUntilMillis?.let(Instant::ofEpochMilli)
    )
}
