package com.cerclerouge.app.server

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.cerclerouge.app.contacts.ContactsRepository
import com.cerclerouge.app.location.LocationTracker
import com.cerclerouge.app.trip.TripController
import com.cerclerouge.contacts.ContactStatus
import com.cerclerouge.location.TrackFix
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import com.cerclerouge.privacy.log.EventLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** État de la surveillance serveur du trajet en cours. */
sealed interface WatchStatus {
    data object Off : WatchStatus
    data object Starting : WatchStatus

    /** Le serveur surveille : `recipients` proches seront prévenus par SMS si le téléphone se tait 10 min. */
    data class On(val recipients: Int) : WatchStatus

    /** Pas de surveillance : `no_recipients`, `phone_required`, `rate_limited`, `network`… Le trajet local continue. */
    data class Failed(val code: String) : WatchStatus
}

/**
 * Surveillance du trajet par le serveur (S4.07, S4.08, ADR-0024), **opt-in** et réservée aux comptes au numéro vérifié.
 * Pendant un trajet protégé, le téléphone envoie un battement toutes les 2 min (dernière position, batterie). Sans battement
 * depuis 10 min, le serveur prévient par SMS jusqu'à 3 proches **ayant accepté** (Vonage) ; un 2ᵉ SMS quand le téléphone revient.
 * Les numéros sont envoyés au démarrage seulement et chiffrés côté serveur ; position et numéros sont effacés à la fin du trajet.
 * Local d'abord : un échec serveur ne touche jamais le trajet ni les alertes du téléphone.
 */
@Singleton
class TripWatch @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: SupabaseApi,
    private val trips: TripController,
    private val contacts: ContactsRepository,
    private val tracker: LocationTracker,
    private val log: EventLog,
    private val dispatcher: com.cerclerouge.app.alert.AlertDispatcher,
    db: PrivacyDatabase
) {
    private val settings = db.settings()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var beats: Job? = null

    @Volatile private var lastFix: TrackFix? = null

    private val _enabled = MutableStateFlow(false)

    /** Choix de l'utilisateur, gardé pour les trajets suivants. */
    val enabled: StateFlow<Boolean> = _enabled

    private val _status = MutableStateFlow<WatchStatus>(WatchStatus.Off)
    val status: StateFlow<WatchStatus> = _status

    init {
        scope.launch { _enabled.value = settings.get(ENABLED) == "1" }
        scope.launch { tracker.fixes.collect { lastFix = it } }
        scope.launch {
            trips.trip.map { it?.plan?.startedAt }.distinctUntilChanged().collect { started ->
                // Au démarrage (null) : un trajet interrompu (processus tué) est clos, pour éviter un faux SMS de perte.
                mutex.withLock {
                    if (started != null) begin(started.toEpochMilli()) else settings.get(SERVER_ID)?.let { end(it) }
                }
            }
        }
    }

    /** Vrai si le compte a un numéro vérifié (condition pour activer la surveillance). */
    suspend fun available(): Boolean = api.configured && api.verifiedPhone() != null

    suspend fun setEnabled(on: Boolean) {
        settings.put(SettingEntity(ENABLED, if (on) "1" else "0"))
        _enabled.value = on
    }

    private suspend fun begin(startedMillis: Long) {
        if (!_enabled.value || !available()) {
            _status.value = WatchStatus.Off
            return
        }
        _status.value = WatchStatus.Starting
        val chosen = contacts.book.value.contacts.filter { it.status == ContactStatus.ACCEPTED }.sortedBy { it.position }.take(MAX)
        if (chosen.isEmpty()) {
            _status.value = WatchStatus.Failed("no_recipients")
            return
        }
        val plan = trips.trip.value?.plan ?: return
        try {
            val id = api.rpc(
                "start_trip",
                JSONObject().put("p_idempotency_key", "trip-$startedMillis").put("p_mode", plan.mode.name)
                    .put("p_planned_arrival", plan.plannedArrival.toString())
            ).trim().trim('"')
            settings.put(SettingEntity(SERVER_ID, id))
            val n = api.rpc(
                "set_trip_recipients",
                JSONObject().put("p_trip_id", id).put("p_phones", JSONArray(chosen.map { it.phone.value }))
                    .put("p_langs", JSONArray(chosen.map { it.language.name.lowercase() }))
            ).trim().toIntOrNull() ?: 0
            // Chaque proche choisi reçoit son lien de suivi du trajet par SMS (S4.10) ; sans réseau : le serveur surveille quand même.
            val links = chosen.mapNotNull { c ->
                runCatching {
                    api.rpc(
                        "create_trip_link",
                        JSONObject().put("p_trip_id", id).put("p_label", c.name.take(40)).put("p_ttl_minutes", LINK_TTL_MIN)
                    )
                        .trim().trim('"')
                }.getOrNull()?.let { c.id to com.cerclerouge.app.BuildConfig.TRACK_PAGE_URL + it }
            }.toMap()
            dispatcher.shareTrip(id, links)
            _status.value = if (n > 0) WatchStatus.On(n) else WatchStatus.Failed("no_recipients")
            log.append("trip", "server_watch_on recipients=$n")
            heartbeat(id)
            beats?.cancel()
            beats = scope.launch {
                while (isActive) {
                    delay(HEARTBEAT_MS)
                    heartbeat(id)
                }
            }
        } catch (e: ServerException) {
            _status.value = WatchStatus.Failed(e.code)
            log.append("trip", "server_watch_failed ${e.code}")
        } catch (e: org.json.JSONException) {
            _status.value = WatchStatus.Failed("bad_response")
        }
    }

    private suspend fun heartbeat(id: String) {
        val fix = lastFix
        val args = JSONObject().put("p_trip_id", id).put("p_battery", battery() ?: JSONObject.NULL)
        if (fix != null) {
            args.put("p_lat", fix.point.lat).put("p_lon", fix.point.lon).put("p_accuracy", fix.accuracyMeters.toDouble())
        } else {
            args.put("p_lat", JSONObject.NULL).put("p_lon", JSONObject.NULL).put("p_accuracy", JSONObject.NULL)
        }
        try {
            api.rpc("trip_heartbeat", args)
        } catch (e: ServerException) {
            // Sans réseau, le serveur préviendra les proches au bout de 10 min : c'est le but. Rien d'autre à faire ici.
            log.append("trip", "heartbeat_failed ${e.code}")
        }
    }

    private suspend fun end(id: String) {
        beats?.cancel()
        beats = null
        try {
            api.rpc("end_trip", JSONObject().put("p_trip_id", id))
            settings.remove(SERVER_ID)
            log.append("trip", "server_watch_off")
        } catch (e: ServerException) {
            // Gardé pour un nouvel essai au prochain démarrage ; le serveur arrête de toute façon 2 h après l'arrivée prévue.
            log.append("trip", "server_end_failed ${e.code}")
        }
        _status.value = WatchStatus.Off
    }

    private fun battery(): Int? {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        return if (level < 0) null else (level * 100 / scale).coerceIn(0, 100)
    }

    private companion object {
        const val ENABLED = "trip.server_watch"
        const val SERVER_ID = "trip.server_id"
        const val MAX = 3
        const val LINK_TTL_MIN = 240
        const val HEARTBEAT_MS = 120_000L
    }
}
