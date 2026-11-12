package com.cerclerouge.app.server

import com.cerclerouge.alert.DispatchKind
import com.cerclerouge.app.BuildConfig
import com.cerclerouge.app.account.AccountRepository
import com.cerclerouge.app.location.LocationTracker
import com.cerclerouge.contacts.ContactStatus
import com.cerclerouge.contacts.TrustedContact
import com.cerclerouge.domain.Alert
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import com.cerclerouge.privacy.log.EventLog
import java.time.Clock
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/** Demande d'accord reçue par un adulte référent. */
data class GuardianRequest(val linkId: String, val minorName: String, val ageBand: String, val relationship: String)

/** Réponse d'un proche, vue dans l'écran d'urgence. */
data class ContactFeedback(val action: String, val label: String?, val at: Instant)

/**
 * Synchronisation de l'alerte avec Supabase (S3.06, ADR-0022). **Le local reste prioritaire** : les SMS partent du téléphone
 * quoi qu'il arrive ; le serveur ajoute le lien de suivi des proches, les positions pendant l'alerte et leurs réponses.
 * Chaque appel est idempotent (clé = identifiant local de l'alerte) ; sans réseau, l'alerte continue sans lien
 * (le SMS porte alors les coordonnées). Aucun audio, aucune preuve n'est envoyé ici (R-BE-02).
 */
@Singleton
class ServerSync @Inject constructor(
    private val api: SupabaseApi,
    private val account: AccountRepository,
    private val tracker: LocationTracker,
    private val log: EventLog,
    private val clock: Clock,
    private val monitor: com.cerclerouge.app.device.DeviceMonitor,
    db: PrivacyDatabase
) {
    private val settings = db.settings()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var current: Alert? = null
    private var follow: Job? = null
    private var lastPositionAt: Instant = Instant.EPOCH

    /** Serveur injoignable : les SMS suivants n'attendent plus (60 s) avant un nouvel essai. */
    @Volatile private var downUntil: Instant = Instant.EPOCH

    private val _feedback = MutableStateFlow<List<ContactFeedback>>(emptyList())

    /** Réponses des proches à l'alerte en cours (« J'ai pris contact »…), rafraîchies toutes les 15 s. */
    val feedback: StateFlow<List<ContactFeedback>> = _feedback

    init {
        // Positions pendant l'alerte seulement (R-LE-04), au plus une toutes les 15 s.
        scope.launch {
            tracker.fixes.collect { fix ->
                val alert = current ?: return@collect
                if (fix.time.isBefore(lastPositionAt.plusSeconds(POSITION_EVERY_S))) return@collect
                lastPositionAt = fix.time
                val id = serverId(alert.id) ?: return@collect
                quietly("position") {
                    api.rpc(
                        "add_alert_position",
                        JSONObject().put("p_alert_id", id).put("p_lat", fix.point.lat).put("p_lon", fix.point.lon)
                            .put("p_accuracy", fix.accuracyMeters.toDouble()).put("p_ts", fix.time.toString())
                    )
                }
            }
        }
    }

    /**
     * Lien de suivi pour un proche (un par alerte et par proche, réutilisé si le SMS est renvoyé). Attente bornée à 10 s :
     * passé ce délai, le SMS part sans lien (local d'abord). Null si le serveur est injoignable ou refuse.
     */
    suspend fun trackingLink(alert: Alert, contactId: String, contactName: String): String? {
        if (!api.configured) return null
        val key = "server.link.${alert.id}.$contactId"
        settings.get(key)?.let { return BuildConfig.TRACK_PAGE_URL + it }
        if (clock.instant().isBefore(downUntil)) return null
        val token = withTimeoutOrNull(LINK_TIMEOUT_MS) {
            val id = ensureAlert(alert) ?: return@withTimeoutOrNull null
            quietly("link") {
                api.rpc(
                    "create_tracking_link",
                    JSONObject().put("p_alert_id", id).put("p_label", contactName.take(40)).put("p_ttl_minutes", LINK_TTL_MIN)
                )
                    .trim('"')
            }
        }
        if (token == null) {
            downUntil = clock.instant().plusSeconds(DOWN_PAUSE_S)
            return null
        }
        settings.put(SettingEntity(key, token))
        return BuildConfig.TRACK_PAGE_URL + token
    }

    /**
     * 1.8 : donne au serveur de quoi prévenir ce proche si le téléphone ne le fait pas (SMS de secours après 20 s) et par e-mail
     * (si `email`). N'attend rien : le téléphone envoie ses SMS d'abord. Le texte est celui que le téléphone envoie.
     */
    fun shareRecipient(alert: Alert, contact: TrustedContact, message: String, email: Boolean) {
        if (!api.configured) return
        scope.launch {
            val id = ensureAlert(alert) ?: return@launch
            val channels = JSONArray().put("sms").also { if (email) it.put("email") }
            val item = JSONObject().put("ref", contact.id).put("phone", contact.phone.value).put("message", message.take(MESSAGE_MAX))
                .put("lang", contact.language.name.lowercase()).put("channels", channels)
            contact.email?.takeIf { email }?.let { item.put("email", it) }
            quietly("recipients") {
                api.rpc("set_alert_recipients", JSONObject().put("p_alert_id", id).put("p_items", JSONArray().put(item)))
            } ?: return@launch
            quietly("notify") { api.function("notify-alert", JSONObject().put("alert_id", id)) }
        }
    }

    /** Le téléphone a envoyé ce SMS : le serveur ne l'enverra pas à ce proche (R-NET-03). Sans attente. */
    fun reportDelivery(alert: Alert, phoneE164: String) {
        if (!api.configured) return
        scope.launch {
            val id = serverId(alert.id) ?: ensureAlert(alert) ?: return@launch
            quietly("delivery") {
                api.rpc("report_delivery", JSONObject().put("p_alert_id", id).put("p_phone", phoneE164).put("p_channel", "sms"))
            }
        }
    }

    /** Suit le cycle de l'alerte (effets `Dispatch` du moteur). Ne bloque jamais l'envoi des SMS. */
    fun onDispatch(alert: Alert, kind: DispatchKind) {
        scope.launch {
            when (kind) {
                DispatchKind.OPEN, DispatchKind.ESCALATE -> {
                    mutex.withLock { current = alert }
                    val id = ensureAlert(alert) ?: return@launch
                    if (kind == DispatchKind.ESCALATE) {
                        quietly("escalate") {
                            api.rpc("update_alert", JSONObject().put("p_alert_id", id).put("p_level", alert.level.value))
                        }
                    }
                    startFollowing(alert)
                }
                DispatchKind.CLOSE_SAFE, DispatchKind.CLOSE_FALSE_ALARM, DispatchKind.CLOSE_EXPIRED -> {
                    mutex.withLock {
                        if (current?.id == alert.id) current = null
                        follow?.cancel()
                    }
                    _feedback.value = emptyList()
                    val id = serverId(alert.id) ?: return@launch
                    val closure = if (kind == DispatchKind.CLOSE_FALSE_ALARM) "FALSE_ALARM" else "USER_SAFE"
                    quietly("close") { api.rpc("close_alert", JSONObject().put("p_alert_id", id).put("p_closure", closure)) }
                }
                else -> Unit
            }
        }
    }

    /**
     * SMS de secours par le serveur (Vonage) quand le téléphone ne peut pas envoyer : pas de permission SMS, ou un premier
     * envoi local a échoué (pas de crédit…). Vrai si le fournisseur a accepté le message. Bornée à 10 s.
     */
    suspend fun sendSms(alert: Alert, phoneE164: String, text: String): Boolean {
        if (!api.configured || clock.instant().isBefore(downUntil)) return false
        val ok = withTimeoutOrNull(LINK_TIMEOUT_MS) {
            val id = ensureAlert(alert) ?: return@withTimeoutOrNull false
            quietly("server_sms") {
                api.function("alert-sms", JSONObject().put("alert_id", id).put("to", phoneE164).put("text", text))
            } != null
        } ?: false
        log.append("server", if (ok) "sms_relayed" else "sms_relay_failed")
        return ok
    }

    /**
     * Invite un proche par SMS (S3.03) : il reçoit un lien pour accepter, refuser ou ne plus rien recevoir.
     * Rend null si l'invitation est partie, sinon un code (`stopped`, `rate_limited`, `already_accepted`, `network`…).
     */
    suspend fun inviteContact(contact: TrustedContact): String? {
        if (!api.configured) return "not_configured"
        val args = JSONObject().put("client_ref", contact.id).put("label", contact.name.take(40)).put("to", contact.phone.value)
            .put("lang", contact.language.name.lowercase())
        return try {
            try {
                api.function("contact-invite", args)
            } catch (e: ServerException) {
                if (e.code != "profile_required") throw e
                pushProfile()
                api.function("contact-invite", args)
            }
            log.append("contacts", "invite_sent")
            null
        } catch (e: ServerException) {
            log.append("contacts", "invite_failed ${e.code}")
            e.code
        }
    }

    /** Statut des invitations, par identifiant local du contact (accepté, refusé, STOP…). Vide si le serveur est injoignable. */
    suspend fun contactStatuses(): Map<String, ContactStatus> {
        if (!api.configured) return emptyMap()
        val body = quietly("invites") { api.rpc("my_contact_invites", JSONObject()) } ?: return emptyMap()
        val arr = JSONArray(body)
        return (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            o.getString("client_ref") to when (o.getString("status")) {
                "accepted" -> ContactStatus.ACCEPTED
                "refused" -> ContactStatus.DECLINED
                "stopped" -> ContactStatus.WITHDRAWN
                else -> ContactStatus.PENDING
            }
        }
    }

    /**
     * Jeune de 12 à 15 ans : demande l'accord d'un adulte référent (RPC request_guardian_consent) puis lui envoie le SMS
     * d'invitation (Vonage). Null si c'est parti, sinon un code (`invalid_phone`, `own_phone`, `rate_limited`, `network`…).
     */
    suspend fun requestGuardian(phoneE164: String, relationship: String, lang: String): String? {
        if (!api.configured) return "not_configured"
        return try {
            pushProfile()
            api.rpc("request_guardian_consent", JSONObject().put("p_guardian_phone", phoneE164).put("p_relationship", relationship))
            api.function("guardian-invite", JSONObject().put("lang", lang))
            log.append("guardian", "request_sent")
            null
        } catch (e: ServerException) {
            log.append("guardian", "request_failed ${e.code}")
            e.code
        }
    }

    /** Jeune : vrai si l'adulte référent a confirmé (protection automatique permise), null si le serveur est injoignable. */
    suspend fun guardianConfirmed(): Boolean? {
        if (!api.configured) return null
        return quietly("guardian_status") { api.rpc("protection_allowed", JSONObject()) }?.trim()?.toBooleanStrictOrNull()
    }

    /** Adulte : demandes d'accord adressées à son numéro vérifié. */
    suspend fun guardianRequests(): List<GuardianRequest>? {
        if (!api.configured) return null
        val body = quietly("guardian_requests") { api.rpc("pending_guardian_requests", JSONObject()) } ?: return null
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GuardianRequest(
                o.getString("link_id"),
                o.getString("minor_display_name"),
                o.getString("minor_age_band"),
                o.getString("relationship")
            )
        }
    }

    /** Adulte : accepte ou refuse (son profil d'adulte est d'abord envoyé au serveur). Null si c'est fait, sinon un code. */
    suspend fun answerGuardian(linkId: String, accept: Boolean): String? = try {
        pushProfile()
        api.rpc("confirm_guardian_consent", JSONObject().put("p_link_id", linkId).put("p_accept", accept))
        log.append("guardian", if (accept) "confirmed_by_me" else "refused_by_me")
        null
    } catch (e: ServerException) {
        e.code
    }

    /** Suppression du compte serveur et de toutes ses données (S3.12). */
    suspend fun deleteAccount(): Boolean {
        if (!api.configured) return true
        val ok = quietly("delete_account") { api.rpc("delete_my_account", JSONObject()) } != null
        if (ok) api.forget()
        return ok
    }

    private fun startFollowing(alert: Alert) {
        follow?.cancel()
        follow = scope.launch {
            var round = 0
            while (isActive && current?.id == alert.id) {
                val id = serverId(alert.id)
                // Batterie et réseau pour la page des proches : toutes les minutes (1.9).
                if (id != null && round++ % DEVICE_EVERY_ROUNDS == 0) {
                    val d = monitor.refresh()
                    quietly("device") {
                        api.rpc(
                            "set_alert_device",
                            JSONObject().put("p_alert_id", id).put("p_battery", d.batteryPct ?: JSONObject.NULL).put("p_online", d.hasData)
                        )
                    }
                }
                if (id != null) {
                    quietly("feedback") { api.rpc("alert_feedback", JSONObject().put("p_alert_id", id)) }?.let { body ->
                        val arr = JSONArray(body)
                        val list = (0 until arr.length()).map { i ->
                            val o = arr.getJSONObject(i)
                            ContactFeedback(
                                o.getString("action"),
                                o.optString("label").ifEmpty {
                                    null
                                },
                                Instant.parse(normalize(o.getString("at")))
                            )
                        }
                        val previous = _feedback.value
                        _feedback.value = list
                        list.filter { it.action != "VIEWED" && it !in previous }.forEach { log.append("contact_action", it.action) }
                    }
                }
                delay(FEEDBACK_EVERY_MS)
            }
        }
    }

    /** Identifiant serveur de l'alerte : créé une fois (idempotent côté serveur), gardé dans la base chiffrée. */
    private suspend fun ensureAlert(alert: Alert): String? {
        serverId(alert.id)?.let { return it }
        val args = JSONObject()
            .put("p_idempotency_key", alert.id.take(64).padEnd(8, '0'))
            .put("p_level", alert.level.value)
            .put("p_reason", alert.reason.name)
            .put("p_confidence", alert.confidence.name)
            .put("p_trigger", alert.trigger.name)
            .put("p_silent", alert.silent)
        val id = quietly("create_alert") {
            try {
                api.rpc("create_alert", args)
            } catch (e: ServerException) {
                if (e.code != "profile_required") throw e
                pushProfile()
                api.rpc("create_alert", args)
            }
        }?.trim('"') ?: return null
        settings.put(SettingEntity("server.alert.${alert.id}", id))
        log.append("server", "alert_synced")
        return id
    }

    private suspend fun serverId(localId: String): String? = settings.get("server.alert.$localId")

    private suspend fun pushProfile() {
        val a = account.account.value ?: account.load() ?: throw ServerException("no_local_profile", 0)
        val locale = Locale.getDefault().language.takeIf { it in setOf("fr", "ht", "en") } ?: "fr"
        api.rpc(
            "set_profile",
            JSONObject().put("p_display_name", a.displayName.take(40)).put("p_locale", locale).put("p_birth_year", a.birthYear)
                .put("p_birth_month", a.birthMonth).put("p_is_adult_attested", a.adultAttested)
        )
    }

    /** Exécute un appel serveur ; toute erreur est journalisée sans donnée personnelle et rend null. */
    private suspend fun <T> quietly(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: ServerException) {
        log.append("server", "$what failed ${e.code}")
        null
    } catch (e: org.json.JSONException) {
        log.append("server", "$what bad_response")
        null
    }

    /** PostgREST renvoie « 2026-11-11T21:12:52.801424+00:00 » : Instant.parse veut un décalage compatible. */
    private fun normalize(ts: String) = java.time.OffsetDateTime.parse(ts).toInstant().toString()

    private companion object {
        const val POSITION_EVERY_S = 15L
        const val FEEDBACK_EVERY_MS = 15_000L
        const val LINK_TIMEOUT_MS = 10_000L
        const val LINK_TTL_MIN = 240
        const val DOWN_PAUSE_S = 60L
        const val MESSAGE_MAX = 480
        const val DEVICE_EVERY_ROUNDS = 4 // un tour = 15 s
    }
}
