package com.cerclerouge.contacts

import java.time.Duration
import java.time.Instant

/** Priorité d'un envoi : plus petit = plus urgent. Un SOS passe avant une mise à jour, avant un test. */
enum class JobPriority { SOS, ALERT, UPDATE, TEST }

enum class JobState { PENDING, DONE, GAVE_UP }

/**
 * Un envoi à faire, persisté (R-ALR-04 : local d'abord). `key` rend l'envoi idempotent : alerte + contact + canal + sorte de
 * message. Le même envoi mis deux fois dans la file n'existe qu'une fois (ni doublon ni perte après la mort du processus).
 */
data class OutboxJob(
    val key: String,
    val alertId: String,
    val contactId: String,
    val channel: Channel,
    val kind: MessageKind,
    val priority: JobPriority,
    val createdAt: Instant,
    val attempts: Int = 0,
    val nextAttemptAt: Instant = createdAt,
    val state: JobState = JobState.PENDING
) {
    companion object {
        fun key(alertId: String, contactId: String, channel: Channel, kind: MessageKind) =
            "$alertId|$contactId|${channel.name}|${kind.name}"
    }
}

/** Règles de la file : ordre, nouvelles tentatives, abandon. Fonctions pures, testées en JVM. */
object Outbox {
    private val MIN_BACKOFF: Duration = Duration.ofSeconds(2)
    private val MAX_BACKOFF: Duration = Duration.ofSeconds(60)

    /** Après l'abandon d'un envoi d'alerte, on ne réessaie plus : au-delà, le contact a de toute façon été joint par l'autre canal. */
    const val MAX_ATTEMPTS = 30

    /** Attente avant la tentative suivante : 2 s, 4 s, 8 s… plafonnée à 60 s (pendant une alerte). */
    fun backoff(attempts: Int): Duration {
        val seconds = MIN_BACKOFF.seconds shl (attempts - 1).coerceIn(0, 10)
        return Duration.ofSeconds(seconds).coerceAtMost(MAX_BACKOFF)
    }

    /** Ajoute un envoi s'il n'existe pas déjà (clé identique) : idempotent. */
    fun enqueue(queue: List<OutboxJob>, job: OutboxJob): List<OutboxJob> = if (queue.any { it.key == job.key }) queue else queue + job

    /** Envois à tenter maintenant : en attente et échus, les plus urgents d'abord, puis les plus anciens. */
    fun due(queue: List<OutboxJob>, now: Instant): List<OutboxJob> = queue
        .filter { it.state == JobState.PENDING && it.nextAttemptAt <= now }
        .sortedWith(compareBy<OutboxJob> { it.priority }.thenBy { it.createdAt }.thenBy { it.key })

    /** Envoi réussi : `nextAttemptAt` garde l'heure d'envoi, qui sert à l'anti-spam. */
    fun succeeded(job: OutboxJob, now: Instant) = job.copy(state = JobState.DONE, nextAttemptAt = now)

    /** Abandon immédiat (plafond atteint : inutile de réessayer). */
    fun gaveUp(job: OutboxJob) = job.copy(state = JobState.GAVE_UP)

    /** Le rapport d'envoi de l'opérateur annonce un échec après coup : l'envoi redevient à faire, avec attente. */
    fun reopened(job: OutboxJob, now: Instant) = failed(job.copy(state = JobState.PENDING), now)

    /** Échec : on programme la tentative suivante, ou on abandonne après [MAX_ATTEMPTS]. */
    fun failed(job: OutboxJob, now: Instant): OutboxJob {
        val attempts = job.attempts + 1
        return if (attempts >= MAX_ATTEMPTS) {
            job.copy(attempts = attempts, state = JobState.GAVE_UP)
        } else {
            job.copy(attempts = attempts, nextAttemptAt = now.plus(backoff(attempts)))
        }
    }

    /**
     * Retour du réseau (R-NET-06) : les envois en attente redeviennent à faire tout de suite, sans compter une tentative de plus.
     * Les envois déjà faits ou abandonnés ne bougent pas.
     */
    fun retryNow(queue: List<OutboxJob>, now: Instant): List<OutboxJob> =
        queue.map { if (it.state == JobState.PENDING && it.nextAttemptAt > now) it.copy(nextAttemptAt = now) else it }

    /** Prochain réveil nécessaire pour vider la file, ou null s'il n'y a plus rien en attente. */
    fun nextWakeUp(queue: List<OutboxJob>): Instant? = queue.filter { it.state == JobState.PENDING }.minOfOrNull { it.nextAttemptAt }
}

private fun Duration.coerceAtMost(max: Duration) = if (this > max) max else this
