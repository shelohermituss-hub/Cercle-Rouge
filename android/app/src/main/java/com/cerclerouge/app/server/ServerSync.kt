package com.cerclerouge.app.server

import com.cerclerouge.alert.DispatchKind
import com.cerclerouge.app.BuildConfig
import com.cerclerouge.app.account.AccountRepository
import com.cerclerouge.app.location.LocationTracker
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
            while (isActive && current?.id == alert.id) {
                val id = serverId(alert.id)
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
    }
}
