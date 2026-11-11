package com.cerclerouge.app.evidence

import com.cerclerouge.domain.Retention
import com.cerclerouge.evidence.EvidenceExport
import com.cerclerouge.privacy.crypto.EvidenceKeys
import com.cerclerouge.privacy.db.EvidenceAlertEntity
import com.cerclerouge.privacy.db.EvidenceDao
import com.cerclerouge.privacy.db.EvidenceSegmentEntity
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** DAO en mémoire : même contrat que Room pour ce que le magasin utilise. */
private class FakeDao : EvidenceDao {
    val alerts = linkedMapOf<String, EvidenceAlertEntity>()
    val segs = mutableListOf<EvidenceSegmentEntity>()

    override suspend fun alerts() = alerts.values.sortedByDescending { it.openedAtMillis }

    override suspend fun alert(id: String) = alerts[id]

    override suspend fun insertAlert(a: EvidenceAlertEntity): Long = if (alerts.putIfAbsent(a.alertId, a) == null) 1 else -1

    override suspend fun close(id: String, at: Long) {
        alerts[id]?.takeIf { it.closedAtMillis == null }?.let { alerts[id] = it.copy(closedAtMillis = at) }
    }

    override suspend fun keep(id: String, until: Long) {
        alerts[id]?.let { alerts[id] = it.copy(keptUntilMillis = until) }
    }

    override suspend fun shredKey(id: String) {
        alerts[id]?.let { alerts[id] = it.copy(wrappedKey = null) }
    }

    override suspend fun segments(id: String) = segs.filter { it.alertId == id }.sortedBy { it.seq }

    override suspend fun insertSegment(s: EvidenceSegmentEntity) {
        segs += s
    }

    override suspend fun touch(id: String, at: Long) {
        alerts[id]?.let { alerts[id] = it.copy(lastWriteMillis = at) }
    }

    override suspend fun deleteSegments(id: String) {
        segs.removeAll { it.alertId == id }
    }

    override suspend fun deleteAlert(id: String) {
        alerts.remove(id)
    }

    override suspend fun shredAll() {
        alerts.replaceAll { _, a -> a.copy(wrappedKey = null) }
    }
}

class EvidenceStoreTest {
    private val t0 = Instant.parse("2026-11-11T20:00:00Z")
    private val root: File = Files.createTempDirectory("evidence").toFile()
    private val dao = FakeDao()
    private val store = EvidenceStore(root, dao, EvidenceKeys.inMemory())
    private val marker = "VOIX-EN-CLAIR-".repeat(200).toByteArray()

    private fun allFileBytes(): String = root.walk().filter { it.isFile }.joinToString("") { String(it.readBytes(), Charsets.ISO_8859_1) }

    @Test fun nothingIsWrittenInClear() = runBlocking {
        // S6.02, R-EVD-01
        store.open("a1", t0)
        repeat(3) { store.append("a1", marker.copyOf(), t0.plusSeconds(30L * it), 30_000, t0.plusSeconds(30L * it + 30)) }
        assertTrue(root.walk().count { it.isFile } == 3)
        assertFalse(allFileBytes().contains("VOIX-EN-CLAIR"))
        assertFalse(root.walk().any { it.name.contains("a1") }) // pas d'identifiant d'alerte en clair dans les noms
    }

    @Test fun readBackAndVerify() = runBlocking {
        store.open("a1", t0)
        store.append("a1", marker.copyOf(), t0, 30_000, t0.plusSeconds(30))
        store.append("a1", "second".toByteArray(), t0.plusSeconds(30), 10_000, t0.plusSeconds(40))
        val back = assertNotNull(store.read("a1"))
        assertTrue(back[0].contentEquals(marker))
        assertEquals("second", String(back[1]))
        assertTrue(store.verify("a1"))
    }

    @Test fun oneFlippedByteOnDiskIsDetected() = runBlocking {
        // S6.03
        store.open("a1", t0)
        store.append("a1", marker.copyOf(), t0, 30_000, t0.plusSeconds(30))
        val file = root.walk().first { it.isFile }
        val bytes = file.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
        file.writeBytes(bytes)
        assertFalse(store.verify("a1"))
        assertNull(store.read("a1"))
    }

    @Test fun deleteShredsKeyThenFiles() = runBlocking {
        // R-EE-02
        store.open("a1", t0)
        store.append("a1", marker.copyOf(), t0, 30_000, t0.plusSeconds(30))
        store.delete("a1")
        assertTrue(root.walk().none { it.isFile })
        assertNull(dao.alert("a1"))
        assertTrue(dao.segments("a1").isEmpty())
    }

    @Test fun purgeFollowsRetentionWithAcceleratedClock() = runBlocking {
        // S6.08 : horloge accélérée → plus rien
        store.open("a1", t0)
        store.append("a1", marker.copyOf(), t0, 30_000, t0.plusSeconds(30))
        store.close("a1", t0.plusSeconds(60))
        assertEquals(0, store.purgeExpired(Retention.HOURS_24, t0.plus(Duration.ofHours(23))))
        assertEquals(1, store.purgeExpired(Retention.HOURS_24, t0.plus(Duration.ofHours(25))))
        assertTrue(root.walk().none { it.isFile })
        assertEquals(0, store.purgeExpired(Retention.HOURS_24, t0.plus(Duration.ofHours(26)))) // idempotent
    }

    @Test fun shreddedKeyLeavesOrphanFilesThatPurgeRemoves() = runBlocking {
        // R-EE-05 : suppression interrompue
        store.open("a1", t0)
        store.append("a1", marker.copyOf(), t0, 30_000, t0.plusSeconds(30))
        dao.shredKey("a1")
        dao.deleteSegments("a1")
        dao.deleteAlert("a1") // la suppression des fichiers n'a pas eu lieu
        assertTrue(root.walk().any { it.isFile })
        store.purgeExpired(null, t0)
        assertTrue(root.walk().none { it.isFile })
    }

    @Test fun exportOpensWithTheCode() = runBlocking {
        // S6.09
        store.open("a1", t0)
        store.append("a1", marker.copyOf(), t0, 30_000, t0.plusSeconds(30))
        val code = EvidenceExport.newCode()
        val archive = assertNotNull(store.export("a1", code))
        val bundle = assertNotNull(EvidenceExport.open(archive, code))
        assertTrue(EvidenceExport.verify(bundle).ok)
        assertTrue(bundle.segments[0].contentEquals(marker))
    }

    @Test fun wipeAllRemovesEverything() = runBlocking {
        // R-EVD-06
        listOf("a1", "a2").forEach {
            store.open(it, t0)
            store.append(it, marker.copyOf(), t0, 30_000, t0.plusSeconds(30))
        }
        store.wipeAll()
        assertTrue(dao.alerts().isEmpty())
        assertFalse(root.exists() && root.walk().any { it.isFile })
    }
}
