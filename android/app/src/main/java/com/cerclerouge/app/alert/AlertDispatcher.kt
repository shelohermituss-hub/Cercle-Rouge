package com.cerclerouge.app.alert

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import com.cerclerouge.alert.DispatchKind
import com.cerclerouge.app.account.AccountRepository
import com.cerclerouge.app.contacts.ContactsRepository
import com.cerclerouge.app.contacts.OutboxRepository
import com.cerclerouge.contacts.Channel
import com.cerclerouge.contacts.ContactStatus
import com.cerclerouge.contacts.Decision
import com.cerclerouge.contacts.DeviceStatus
import com.cerclerouge.contacts.Gsm7
import com.cerclerouge.contacts.JobPriority
import com.cerclerouge.contacts.JobState
import com.cerclerouge.contacts.LocationShare
import com.cerclerouge.contacts.MessageInput
import com.cerclerouge.contacts.MessageKind
import com.cerclerouge.contacts.Messages
import com.cerclerouge.contacts.OutboxJob
import com.cerclerouge.contacts.SendLedger
import com.cerclerouge.contacts.SendRecord
import com.cerclerouge.contacts.resolveRecipients
import com.cerclerouge.domain.Alert
import com.cerclerouge.domain.Reason
import com.cerclerouge.domain.Trigger
import com.cerclerouge.privacy.log.EventLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Du moteur d'alerte aux SMS : transforme un effet `Dispatch` en envois dans la file persistante (idempotents), puis vide la
 * file en respectant l'anti-spam, avec attente de 2 à 60 s en cas d'échec (S2.06, S2.07, S2.11). Un test d'alerte suit le même
 * chemin avec le marqueur « TEST » (S2.12, R-ALR-05).
 *
 * Sans lien de suivi tant que le serveur n'existe pas (Sprint 3) : le SMS porte la position en coordonnées brutes.
 */
@Singleton
class AlertDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contacts: ContactsRepository,
    private val outbox: OutboxRepository,
    private val account: AccountRepository,
    private val sms: SmsSender,
    private val location: LocationProvider,
    private val log: EventLog,
    private val clock: Clock
) {
    private val drainLock = Mutex()

    /** Effet `Dispatch` du moteur. Les avis sans alerte (ex. protection désactivée) ne partent à personne. */
    suspend fun onDispatch(alert: Alert?, kind: DispatchKind) {
        if (alert == null) return
        val message = when (kind) {
            DispatchKind.OPEN, DispatchKind.ESCALATE -> MessageKind.ALERT
            DispatchKind.CLOSE_SAFE -> MessageKind.CLOSURE_SAFE
            DispatchKind.CLOSE_FALSE_ALARM -> MessageKind.CLOSURE_FALSE
            DispatchKind.CLOSE_EXPIRED -> MessageKind.CLOSURE_ENDED
            else -> return // accusés et échecs de PIN : via le serveur, Sprint 3
        }
        contacts.load()
        val book = contacts.book.value
        val now = clock.instant()
        val jobs = if (message == MessageKind.ALERT) {
            val manual = alert.trigger == Trigger.SOS || alert.trigger == Trigger.SECRET_PHRASE
            val priority = if (manual) JobPriority.SOS else JobPriority.ALERT
            resolveRecipients(alert.level, manual, book.contacts, deviceStatus())
                .map {
                    OutboxJob(
                        OutboxJob.key(alert.id, it.contactId, it.channel, message),
                        alert.id,
                        it.contactId,
                        it.channel,
                        message,
                        priority,
                        now
                    )
                }
        } else {
            // La clôture ne part qu'à ceux qui ont reçu l'alerte (R-ALR-03).
            outbox.all().filter { it.alertId == alert.id && it.kind == MessageKind.ALERT && it.state == JobState.DONE }
                .map {
                    OutboxJob(
                        OutboxJob.key(alert.id, it.contactId, it.channel, message),
                        alert.id,
                        it.contactId,
                        it.channel,
                        message,
                        JobPriority.UPDATE,
                        now
                    )
                }
        }
        jobs.forEach { outbox.enqueue(it) }
        log.append("dispatch_queue", "${kind.name} jobs=${jobs.size}")
        drain(alert)
    }

    /** Test d'alerte : même chemin, marqueur « [TEST] », 3 par jour et par contact (S2.12, R-CON-06). */
    suspend fun sendTest(contactId: String) {
        contacts.load()
        val contact = contacts.book.value.contacts.firstOrNull { it.id == contactId } ?: return
        val id = "test-${clock.millis()}"
        outbox.enqueue(
            OutboxJob(
                OutboxJob.key(id, contact.id, Channel.SMS, MessageKind.TEST),
                id,
                contact.id,
                Channel.SMS,
                MessageKind.TEST,
                JobPriority.TEST,
                clock.instant()
            )
        )
        drain(null)
    }

    private suspend fun drain(alert: Alert?) = drainLock.withLock {
        while (true) {
            val now = clock.instant()
            val due = outbox.due(now)
            if (due.isEmpty()) {
                val next = outbox.nextWakeUp() ?: return
                delay(Duration.between(now, next).toMillis().coerceIn(1_000, 60_000))
                continue
            }
            // Position prise une seule fois par passage (jusqu'à 10 s), seulement s'il y a une vraie alerte à envoyer.
            var cached: LocationShare? = null
            val position: suspend () -> LocationShare = { cached ?: location.share().also { cached = it } }
            for (job in due) process(job, alert, ledger(), position)
        }
    }

    private suspend fun process(job: OutboxJob, alert: Alert?, ledger: SendLedger, position: suspend () -> LocationShare) {
        val now = clock.instant()
        val contact = contacts.book.value.contacts.firstOrNull { it.id == job.contactId }
        if (contact == null || job.channel != Channel.SMS) { // contact retiré, ou canal pas encore disponible (push : Sprint 3)
            outbox.gaveUp(job)
            return
        }
        val unconsented = contact.status != ContactStatus.ACCEPTED && job.kind == MessageKind.ALERT
        val name = account.account.value?.displayName ?: context.getString(com.cerclerouge.app.R.string.app_name)
        val text = Messages.sms(
            MessageInput(
                name = name,
                reason = alert?.reason ?: Reason.SOS,
                kind = job.kind,
                at = alert?.openedAt ?: now,
                zone = ZoneId.systemDefault(),
                position = if (job.kind == MessageKind.ALERT || job.kind == MessageKind.TEST) position() else LocationShare.NotShared,
                link = null,
                language = contact.language,
                alertId = job.alertId,
                test = job.kind == MessageKind.TEST,
                unconsented = unconsented,
                custom = null
            )
        )
        when (val d = ledger.check(job.contactId, job.alertId, job.kind == MessageKind.TEST, Gsm7.segments(text), now)) {
            Decision.Allow -> when (val r = sms.send(job.key, contact.phone.value, text)) {
                SmsResult.Sent, SmsResult.NeedsUserAction -> {
                    outbox.succeeded(job, now)
                    log.append(
                        "sms",
                        "${if (r == SmsResult.Sent) "queued" else "prefilled"} ${job.alertId.take(6)}/${job.contactId.take(6)}"
                    )
                }
                is SmsResult.Failed -> {
                    outbox.failed(job, now)
                    log.append("sms", "failed ${r.reason}")
                }
            }
            is Decision.Block -> {
                log.append("sms", "blocked ${d.reason}")
                // Limite de fréquence : on réessaie plus tard ; plafonds atteints : on abandonne.
                if (d.reason == com.cerclerouge.contacts.BlockReason.CONTACT_RATE_LIMIT) outbox.failed(job, now) else outbox.gaveUp(job)
            }
        }
    }

    /** Registre anti-spam reconstruit depuis la file : les envois réussis, avec leur heure et (approximation) 1 SMS chacun. */
    private suspend fun ledger() = SendLedger(
        outbox.all().filter { it.state == JobState.DONE }.map {
            SendRecord(
                it.contactId,
                it.alertId,
                it.nextAttemptAt,
                it.kind == MessageKind.TEST,
                1
            )
        }
    )

    @Suppress("DEPRECATION")
    private fun deviceStatus(): DeviceStatus {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val hasData = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val sim = context.getSystemService(TelephonyManager::class.java).simState == TelephonyManager.SIM_STATE_READY
        return DeviceStatus(hasData = hasData, canSendSms = sim)
    }
}
