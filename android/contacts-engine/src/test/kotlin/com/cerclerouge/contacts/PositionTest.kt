package com.cerclerouge.contacts

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** S2.09 : dernière position connue + position fraîche ; « non partagée » si refusée. */
class PositionTest {
    private val now = Instant.parse("2026-11-11T10:00:00Z")
    private fun fix(agoSeconds: Long, acc: Float = 20f) = Fix(18.5, -72.3, acc, now.minusSeconds(agoSeconds))

    @Test fun deniedPermissionNeverShares() {
        assertEquals(LocationShare.NotShared, chooseFix(false, fix(10), fix(1), now))
    }

    @Test fun freshFixWins() {
        val f = fix(2)
        assertEquals(LocationShare.Fresh(f), chooseFix(true, fix(600), f, now))
    }

    @Test fun staleFreshFixIsTreatedAsLastKnown() {
        val r = chooseFix(true, null, fix(40), now)
        assertIs<LocationShare.LastKnown>(r)
        assertEquals(40, r.ageSeconds)
    }

    @Test fun lastKnownWhenNoFreshFix() {
        val r = chooseFix(true, fix(300), null, now)
        assertEquals(LocationShare.LastKnown(fix(300), 300), r)
    }

    @Test fun veryOldPositionIsNotShared() {
        assertEquals(LocationShare.NotShared, chooseFix(true, fix(31 * 60), null, now))
    }

    @Test fun nothingAtAllIsNotShared() {
        assertEquals(LocationShare.NotShared, chooseFix(true, null, null, now))
    }

    @Test fun mostRecentOfTheTwoIsChosen() {
        val older = fix(100)
        val newer = fix(20)
        assertEquals(LocationShare.LastKnown(newer, 20), chooseFix(true, newer, older, now))
    }
}
