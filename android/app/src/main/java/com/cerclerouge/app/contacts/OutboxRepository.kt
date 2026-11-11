package com.cerclerouge.app.contacts

import com.cerclerouge.contacts.Channel
import com.cerclerouge.contacts.JobPriority
import com.cerclerouge.contacts.JobState
import com.cerclerouge.contacts.MessageKind
import com.cerclerouge.contacts.Outbox
import com.cerclerouge.contacts.OutboxJob
import com.cerclerouge.privacy.db.OutboxEntity
import com.cerclerouge.privacy.db.PrivacyDatabase
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File d'attente persistante des envois (S2.06). Les règles (ordre, attente de 2 à 60 s, abandon) sont dans `contacts-engine` ;
 * ce dépôt les persiste. Un envoi mis deux fois dans la file n'existe qu'une fois : après la mort du processus, ni doublon ni perte.
 */
@Singleton
class OutboxRepository @Inject constructor(db: PrivacyDatabase) {
    private val dao = db.outbox()

    suspend fun enqueue(job: OutboxJob) {
        dao.insert(job.toEntity())
    }

    /** Envois à tenter maintenant, les plus urgents d'abord. */
    suspend fun due(now: Instant): List<OutboxJob> = Outbox.due(all(), now)

    suspend fun succeeded(job: OutboxJob, now: Instant) = save(Outbox.succeeded(job, now))

    suspend fun failed(job: OutboxJob, now: Instant) = save(Outbox.failed(job, now))

    /** Prochain réveil nécessaire pour vider la file, ou null. */
    suspend fun nextWakeUp(): Instant? = Outbox.nextWakeUp(all())

    suspend fun gaveUp(job: OutboxJob) = save(Outbox.gaveUp(job))

    /** Rapport d'envoi négatif reçu après coup : l'envoi redevient à faire. */
    suspend fun reopen(key: String, now: Instant) {
        dao.get(key)?.toJob()?.let { save(Outbox.reopened(it, now)) }
    }

    suspend fun all(): List<OutboxJob> = dao.all().map { it.toJob() }

    private suspend fun save(job: OutboxJob) = dao.update(job.key, job.attempts, job.nextAttemptAt.toEpochMilli(), job.state.name)
}

private fun OutboxJob.toEntity() = OutboxEntity(
    key, alertId, contactId, channel.name, kind.name, priority.ordinal, createdAt.toEpochMilli(), attempts, nextAttemptAt.toEpochMilli(),
    state.name
)

private fun OutboxEntity.toJob() = OutboxJob(
    key, alertId, contactId, Channel.valueOf(channel), MessageKind.valueOf(kind), JobPriority.entries[priority],
    Instant.ofEpochMilli(createdAtMillis), attempts, Instant.ofEpochMilli(nextAttemptAtMillis), JobState.valueOf(state)
)
