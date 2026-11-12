package com.cerclerouge.app.alert

import android.content.Context
import com.cerclerouge.alert.DispatchKind
import com.cerclerouge.app.account.AccountRepository
import com.cerclerouge.app.contacts.ContactsRepository
import com.cerclerouge.app.contacts.OutboxRepository
import com.cerclerouge.app.server.ServerSync
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
import kotlinx.coroutines.channels.Channel as WakeChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Du moteur d'alerte aux SMS : transforme un effet `Dispatch` en envois dans la file persistante (idempotents), puis vide la
 * file en respectant l'anti-spam, avec attente de 2 à 60 s en cas d'échec (S2.06, S2.07, S2.11). Un test d'alerte suit le même
 * chemin avec le marqueur « TEST » (S2.12, R-ALR-05).
 *
 * Avec le serveur (Sprint 3) : le SMS d'alerte porte un lien de suivi personnel ; sans réseau, la position en coordonnées.
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
    private val clock: Clock,
    private val server: ServerSync,
    private val monitor: com.cerclerouge.app.device.DeviceMonitor,
    private val api: com.cerclerouge.app.server.SupabaseApi,
    db: com.cerclerouge.privacy.db.PrivacyDatabase
) {
    private val settings = db.settings()
    private val drainLock = Mutex()

    /** Réveille la boucle d'envoi avant la fin de son attente (retour du réseau). */
    private val wakeups = WakeChannel<Unit>(WakeChannel.CONFLATED)

    /**
     * Reprend la file (démarrage, redémarrage, tâche WorkManager) : vide ce qui reste à envoyer. Si une boucle tourne déjà,
     * elle est seulement réveillée. `alert` : l'alerte en cours du moteur, pour le contenu des messages (null hors alerte).
     */
    suspend fun resume(alert: Alert?) {
        if (drainLock.isLocked) wakeups.trySend(Unit) else drain(alert)
    }

    /** Retour du réseau (R-NET-06) : tout ce qui attend part tout de suite, sans attendre la fin de l'attente de 2 à 60 s. */
    suspend fun onNetworkBack(alert: Alert?) {
        if (!outbox.hasPending()) return
        outbox.retryNow(clock.instant())
        log.append("dispatch_queue", "network_back")
        resume(alert)
    }

    /** Effet `Dispatch` du moteur. Hors alerte : avis de fin de trajet ou de protection coupée en trajet (R-SEC-03). */
    suspend fun onDispatch(alert: Alert?, kind: DispatchKind) {
        if (alert == null) {
            notifyWithoutAlert(kind)
            return
        }
        server.onDispatch(alert, kind) // serveur en parallèle : ne retarde jamais les SMS
        val message = when (kind) {
            DispatchKind.OPEN, DispatchKind.ESCALATE -> MessageKind.ALERT
            DispatchKind.CLOSE_SAFE -> MessageKind.CLOSURE_SAFE
            DispatchKind.CLOSE_FALSE_ALARM -> MessageKind.CLOSURE_FALSE
            DispatchKind.CLOSE_EXPIRED -> MessageKind.CLOSURE_ENDED
            DispatchKind.PIN_FAILURES -> MessageKind.PIN_FAILURES // S7.01 : 5 PIN faux pendant l'alerte
            else -> return // accusés : par la page de suivi
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
            // Clôture et avertissement « PIN faux » : seulement à ceux qui ont reçu l'alerte (R-ALR-03).
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
        OutboxWork.schedule(context) // si le processus meurt, la file repart au retour du réseau (R-NET-01)
        drain(alert)
    }

    /**
     * Trajet : protection coupée (toujours, R-SM-06, R-SEC-03 : possible contrainte) ou arrivée confirmée (seulement si les
     * proches suivaient le trajet). Aux 3 premiers proches qui ont accepté, comme la surveillance du trajet (ADR-0024).
     */
    private suspend fun notifyWithoutAlert(kind: DispatchKind) {
        val message = when (kind) {
            DispatchKind.PROTECTION_DISABLED -> MessageKind.PROTECTION_STOPPED
            DispatchKind.TRIP_ENDED -> if (settings.get(TRIP_WATCH) == "1") MessageKind.TRIP_ARRIVED else return
            else -> return
        }
        contacts.load()
        val now = clock.instant()
        val id = "trip-${now.toEpochMilli()}"
        val jobs = contacts.book.value.contacts.filter {
            it.status == ContactStatus.ACCEPTED
        }.sortedBy { it.position }.take(TRIP_RECIPIENTS)
            .map { OutboxJob(OutboxJob.key(id, it.id, Channel.SMS, message), id, it.id, Channel.SMS, message, JobPriority.UPDATE, now) }
        jobs.forEach { outbox.enqueue(it) }
        log.append("dispatch_queue", "${kind.name} jobs=${jobs.size}")
        if (jobs.isEmpty()) return
        OutboxWork.schedule(context)
        drain(null)
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
                withTimeoutOrNull(Duration.between(now, next).toMillis().coerceIn(1_000, 60_000)) { wakeups.receive() }
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
        // Secours par le serveur (Vonage) : pas de permission SMS, ou un premier envoi local a échoué (pas de crédit…).
        val viaServer = alert != null && job.kind != MessageKind.TEST && (job.attempts > 0 || !sms.canSendDirect)
        val isAlert = job.kind == MessageKind.ALERT
        val device = if (isAlert) monitor.refresh() else null
        val automatic = alert != null && (alert.trigger == Trigger.AUTOMATIC || alert.trigger == Trigger.TRIP)
        val text = Messages.sms(
            MessageInput(
                name = name,
                reason = alert?.reason ?: Reason.SOS,
                kind = job.kind,
                at = alert?.openedAt ?: now,
                zone = ZoneId.systemDefault(),
                position = if (isAlert) position() else LocationShare.NotShared,
                // Lien de suivi (S3.06) : attente bornée à 10 s, sinon le SMS part avec les coordonnées seules.
                link = if (isAlert && alert != null) server.trackingLink(alert, contact.id, contact.name) else null,
                language = contact.language,
                alertId = job.alertId,
                test = job.kind == MessageKind.TEST,
                unconsented = unconsented,
                custom = if (isAlert) settings.get(CUSTOM_MESSAGE) else null,
                batteryPct = device?.batteryPct,
                online = device?.hasData,
                confidence = if (isAlert && automatic) alert?.confidence else null,
                // Le SMS du serveur ne vient pas du téléphone de l'utilisateur : on donne son numéro vérifié pour le rappeler.
                callback = if (isAlert && viaServer) api.verifiedPhone() else null
            )
        )
        when (val d = ledger.check(job.contactId, job.alertId, job.kind == MessageKind.TEST, Gsm7.segments(text), now)) {
            Decision.Allow -> {
                if (viaServer && alert != null && server.sendSms(alert, contact.phone.value, text)) {
                    outbox.succeeded(job, now)
                    log.append("sms", "relayed ${job.alertId.take(6)}/${job.contactId.take(6)}")
                    return
                }
                sendLocally(job, contact.phone.value, text, now)
            }
            is Decision.Block -> {
                log.append("sms", "blocked ${d.reason}")
                // Limite de fréquence : on réessaie plus tard ; plafonds atteints : on abandonne.
                if (d.reason == com.cerclerouge.contacts.BlockReason.CONTACT_RATE_LIMIT) outbox.failed(job, now) else outbox.gaveUp(job)
            }
        }
    }

    private suspend fun sendLocally(job: OutboxJob, phone: String, text: String, now: java.time.Instant) {
        when (val r = sms.send(job.key, phone, text)) {
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

    /** État réel de l'appareil : données validées, SMS possible (SIM prête, mode avion coupé). */
    private fun deviceStatus(): DeviceStatus = monitor.refresh().let { DeviceStatus(hasData = it.hasData, canSendSms = it.canSendSms) }

    companion object {
        /** Message personnalisé ajouté aux alertes (Réglages, PDF 5.1). */
        const val CUSTOM_MESSAGE = "alert.custom"
        private const val TRIP_WATCH = "trip.server_watch"
        private const val TRIP_RECIPIENTS = 3
    }
}
