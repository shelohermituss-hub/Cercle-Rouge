package com.cerclerouge.evidence

import com.cerclerouge.domain.Retention
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val T0: Instant = Instant.parse("2026-11-11T20:00:00Z")

private fun chain(n: Int): Pair<List<SegmentEntry>, List<ByteArray>> {
    val data = (0 until n).map { Random(it).nextBytes(1_000 + it) }
    var prev: SegmentEntry? = null
    val entries = data.mapIndexed { i, d ->
        Chain.next(prev, "alert-1", T0.plusSeconds(30L * i), 30_000, Chain.sha256(d)).also { prev = it }
    }
    return entries to data
}

class ChainTest {
    @Test fun intactChainVerifies() {
        val (entries, data) = chain(5)
        assertEquals(ChainCheck(true, null, null), Chain.verify(entries, data))
        assertEquals(entries.last().entryHash, Chain.root(entries))
        assertEquals(Chain.GENESIS, entries.first().prevHash)
    }

    @Test fun oneFlippedByteIsDetected() { // S6.03 : altération d'1 octet détectée
        val (entries, data) = chain(4)
        val tampered = data.map { it.copyOf() }
        tampered[2][17] = (tampered[2][17] + 1).toByte()
        val check = Chain.verify(entries, tampered)
        assertFalse(check.ok)
        assertEquals(2, check.firstBad)
    }

    @Test fun removedOrReorderedSegmentIsDetected() {
        val (entries, data) = chain(4)
        assertEquals(1, Chain.verify(entries.filterIndexed { i, _ -> i != 1 }, data.filterIndexed { i, _ -> i != 1 }).firstBad)
        assertFalse(Chain.verify(listOf(entries[1], entries[0]) + entries.drop(2), listOf(data[1], data[0]) + data.drop(2)).ok)
    }

    @Test fun editedMetadataIsDetected() {
        val (entries, _) = chain(3)
        val edited = entries.toMutableList()
        edited[1] = edited[1].copy(startedAt = edited[1].startedAt.plusSeconds(3_600))
        assertEquals(1, Chain.verify(edited).firstBad)
    }

    @Test fun missingContentIsDetected() {
        val (entries, data) = chain(3)
        assertFalse(Chain.verify(entries, data.dropLast(1)).ok)
    }
}

class RetentionTest {
    private val closed = EvidenceAlert("a", T0, closedAt = T0.plusSeconds(600), lastWriteAt = T0.plusSeconds(600))

    @Test fun defaultIs24hAfterClosure() { // ADR-0017 : 24 h si rien n'est choisi
        assertEquals(T0.plusSeconds(600).plus(Duration.ofHours(24)), RetentionPolicy.deleteAt(closed, null, T0))
    }

    @Test fun choicesApplyAfterClosure() {
        assertEquals(T0.plusSeconds(600).plus(Duration.ofDays(30)), RetentionPolicy.deleteAt(closed, Retention.DAYS_30, T0))
        assertEquals(T0.plusSeconds(600), RetentionPolicy.deleteAt(closed, Retention.ON_CLOSURE, T0))
    }

    @Test fun openAlertIsKept() {
        val open = closed.copy(closedAt = null, lastWriteAt = T0.plusSeconds(600))
        assertNull(RetentionPolicy.deleteAt(open, Retention.ON_CLOSURE, T0.plusSeconds(700)))
        assertTrue(RetentionPolicy.due(listOf(open), Retention.ON_CLOSURE, T0.plusSeconds(700)).isEmpty())
    }

    @Test fun orphanAlertIsNeverKeptForever() { // N6 : alerte jamais close (app tuée)
        val orphan = closed.copy(closedAt = null)
        val at = RetentionPolicy.deleteAt(orphan, Retention.HOURS_24, T0.plus(Duration.ofDays(3)))
        assertEquals(T0.plusSeconds(600).plus(Duration.ofHours(25)), at)
    }

    @Test fun purgeIsDueExactlyAtTheDeadlineAndIdempotent() { // R-EE-05
        val deadline = T0.plusSeconds(600).plus(Duration.ofDays(7))
        assertTrue(RetentionPolicy.due(listOf(closed), Retention.DAYS_7, deadline.minusMillis(1)).isEmpty())
        assertEquals(listOf("a"), RetentionPolicy.due(listOf(closed), Retention.DAYS_7, deadline))
        assertEquals(listOf("a"), RetentionPolicy.due(listOf(closed), Retention.DAYS_7, deadline))
    }

    @Test fun keepIsCappedAt90Days() {
        val until = RetentionPolicy.keep(T0, Duration.ofDays(365))
        assertEquals(T0.plus(Duration.ofDays(90)), until)
        val kept = closed.copy(keptUntil = until)
        assertEquals(until, RetentionPolicy.deleteAt(kept, Retention.HOURS_24, T0))
    }

    @Test fun keepNeverShortensRetention() {
        val kept = closed.copy(keptUntil = T0.plusSeconds(60))
        assertEquals(T0.plusSeconds(600).plus(Duration.ofDays(30)), RetentionPolicy.deleteAt(kept, Retention.DAYS_30, T0))
    }

    @Test fun recordingCaps() {
        assertEquals(120, RecordingPolicy.maxSegments())
        assertFalse(RecordingPolicy.canRecordAudio(10L * 1024 * 1024))
    }
}

class ExportTest {
    @Test fun roundTripAndVerify() { // S6.09 : vérification d'un export OK
        val (entries, data) = chain(3)
        val code = EvidenceExport.newCode()
        val archive = EvidenceExport.seal(ExportBundle("alert-1", entries, data), code)
        val back = assertNotNull(EvidenceExport.open(archive, EvidenceExport.formatCode(code).lowercase()))
        assertEquals(entries, back.entries)
        assertTrue(EvidenceExport.verify(back).ok)
        assertTrue(back.segments.zip(data).all { (a, b) -> a.contentEquals(b) })
    }

    @Test fun wrongCodeOrTamperedArchiveFails() {
        val (entries, data) = chain(2)
        val archive = EvidenceExport.seal(ExportBundle("alert-1", entries, data), "ABCDEFGHJKMN")
        assertNull(EvidenceExport.open(archive, "ABCDEFGHJKMP"))
        val tampered = archive.copyOf().also { it[it.size - 5] = (it[it.size - 5] + 1).toByte() }
        assertNull(EvidenceExport.open(tampered, "ABCDEFGHJKMN"))
    }

    @Test fun archiveHasNoPlaintext() {
        val marker = "PLAINTEXT-MARKER-0123456789".toByteArray()
        val entries = listOf(Chain.next(null, "a", T0, 1_000, Chain.sha256(marker)))
        val archive = EvidenceExport.seal(ExportBundle("a", entries, listOf(marker)), EvidenceExport.newCode())
        assertFalse(String(archive, Charsets.ISO_8859_1).contains(String(marker, Charsets.ISO_8859_1)))
    }

    @Test fun codesAreReadable() {
        val code = EvidenceExport.newCode()
        assertEquals(12, code.length)
        assertTrue(code.none { it in "01OIL" })
        assertEquals(14, EvidenceExport.formatCode(code).length)
    }
}
