package com.cerclerouge.contacts

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutboxTest {
    private val t0 = Instant.parse("2026-11-11T10:00:00Z")

    private fun job(
        contact: String = "c1",
        kind: MessageKind = MessageKind.ALERT,
        priority: JobPriority = JobPriority.ALERT,
        at: Instant = t0,
        channel: Channel = Channel.SMS
    ) = OutboxJob(OutboxJob.key("A1", contact, channel, kind), "A1", contact, channel, kind, priority, at)

    @Test fun backoffDoublesFromTwoToSixtySeconds() { // S2.06 : 2 à 60 s
        assertEquals(listOf(2L, 4L, 8L, 16L, 32L, 60L, 60L, 60L), (1..8).map { Outbox.backoff(it).seconds })
        assertEquals(60L, Outbox.backoff(500).seconds)
    }

    @Test fun enqueuingTheSameSendTwiceKeepsOneJob() { // idempotence : ni doublon après un redémarrage
        val q = Outbox.enqueue(Outbox.enqueue(emptyList(), job()), job(at = t0.plusSeconds(5)))
        assertEquals(1, q.size)
        assertEquals(2, Outbox.enqueue(q, job(channel = Channel.PUSH)).size)
        assertEquals(2, Outbox.enqueue(q, job(contact = "c2")).size)
    }

    @Test fun mostUrgentComesFirstThenOldest() {
        val q = listOf(
            job("a", MessageKind.TEST, JobPriority.TEST, t0),
            job("b", MessageKind.ALERT, JobPriority.UPDATE, t0),
            job("c", MessageKind.ALERT, JobPriority.SOS, t0.plusSeconds(10)),
            job("d", MessageKind.ALERT, JobPriority.SOS, t0.plusSeconds(1))
        )
        assertEquals(listOf("d", "c", "b", "a"), Outbox.due(q, t0.plusSeconds(60)).map { it.contactId })
    }

    @Test fun jobsNotYetDueOrFinishedAreSkipped() {
        val waiting = Outbox.failed(job("a"), t0) // prochaine tentative dans 2 s
        val done = Outbox.succeeded(job("b"))
        assertEquals(emptyList(), Outbox.due(listOf(waiting, done), t0.plusSeconds(1)))
        assertEquals(listOf("a"), Outbox.due(listOf(waiting, done), t0.plusSeconds(2)).map { it.contactId })
    }

    @Test fun failuresScheduleTheNextAttemptAndEventuallyGiveUp() {
        var j = job()
        var now = t0
        repeat(Outbox.MAX_ATTEMPTS - 1) {
            j = Outbox.failed(j, now)
            assertEquals(JobState.PENDING, j.state)
            now = j.nextAttemptAt
        }
        j = Outbox.failed(j, now)
        assertEquals(JobState.GAVE_UP, j.state)
        assertEquals(emptyList(), Outbox.due(listOf(j), now.plus(Duration.ofDays(1))))
    }

    @Test fun nextWakeUpIsTheEarliestPendingAttempt() {
        assertNull(Outbox.nextWakeUp(emptyList()))
        assertNull(Outbox.nextWakeUp(listOf(Outbox.succeeded(job()))))
        val q = listOf(Outbox.failed(job("a"), t0), job("b", at = t0.plusSeconds(1)))
        assertEquals(t0.plusSeconds(1), Outbox.nextWakeUp(q))
    }

    @Test fun keyIsStablePerAlertContactChannelAndKind() {
        assertEquals("A1|c1|SMS|ALERT", OutboxJob.key("A1", "c1", Channel.SMS, MessageKind.ALERT))
    }
}
