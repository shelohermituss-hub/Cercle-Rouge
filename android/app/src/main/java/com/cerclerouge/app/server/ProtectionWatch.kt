package com.cerclerouge.app.server

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.cerclerouge.app.contacts.ContactsRepository
import com.cerclerouge.app.location.LocationTracker
import com.cerclerouge.app.protection.ProtectionController
import com.cerclerouge.contacts.ContactStatus
import com.cerclerouge.domain.ProtectionState
import com.cerclerouge.location.TrackFix
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import com.cerclerouge.privacy.log.EventLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
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

/**
 * Surveillance de la **protection** par le serveur (1.17, PDF 13.1, S7.03), opt-in, numéro vérifié requis. Tant que la
 * protection est active hors trajet (un trajet a sa propre surveillance, [TripWatch]), le téléphone envoie un battement toutes
 * les 5 min. Sans battement depuis 10 min (application tuée, téléphone éteint, cassé ou volé), le serveur prévient par SMS
 * jusqu'à 3 proches ayant accepté, avec la dernière position. Session de 12 h au plus, renouvelée.
 */
@Singleton
class ProtectionWatch @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: SupabaseApi,
    private val protection: ProtectionController,
    private val contacts: ContactsRepository,
    private val tracker: LocationTracker,
    private val prefs: com.cerclerouge.app.trip.TripPrefs,
    private val roots: LogRoots,
    private val log: EventLog,
    private val clock: Clock,
    db: PrivacyDatabase
) {
    private val settings = db.settings()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var beats: Job? = null

    @Volatile private var lastFix: TrackFix? = null

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    init {
        scope.launch {
            _enabled.value = settings.get(ENABLED) == "1"
            _shutdownSms.value = settings.get(com.cerclerouge.app.alert.AlertDispatcher.SHUTDOWN_SMS) == "1"
        }
        scope.launch { tracker.fixes.collect { lastFix = it } }
        scope.launch {
            protection.state.map { watched(it.protection) }.distinctUntilChanged().collect { on ->
                mutex.withLock { if (on) begin() else end() }
            }
        }
    }

    private val _shutdownSms = MutableStateFlow(false)

    /** Dernier SMS aux proches si le téléphone s'éteint pendant la protection (opt-in). */
    val shutdownSms: StateFlow<Boolean> = _shutdownSms

    suspend fun setShutdownSms(on: Boolean) {
        settings.put(SettingEntity(com.cerclerouge.app.alert.AlertDispatcher.SHUTDOWN_SMS, if (on) "1" else "0"))
        _shutdownSms.value = on
    }

    suspend fun setEnabled(on: Boolean) {
        settings.put(SettingEntity(ENABLED, if (on) "1" else "0"))
        _enabled.value = on
        mutex.withLock {
            if (on && watched(protection.state.value.protection)) {
                begin()
            } else if (!on) {
                end()
            }
        }
    }

    /** Protection active, alertes comprises, mais hors trajet (le trajet est suivi par [TripWatch]). */
    private fun watched(p: ProtectionState) = p != ProtectionState.OFF && p != ProtectionState.TRIP_ACTIVE

    private suspend fun begin() {
        if (beats?.isActive == true || !_enabled.value || !api.configured || api.verifiedPhone() == null) return
        val accepted = contacts.book.value.contacts.filter { it.status == ContactStatus.ACCEPTED }.sortedBy { it.position }
        val wanted = prefs.recipients()
        val chosen = (accepted.filter { it.id in wanted }.ifEmpty { accepted }).take(MAX)
        if (chosen.isEmpty()) {
            log.append("protection", "server_watch_no_recipients")
            return
        }
        try {
            val now = clock.instant()
            val id = api.rpc(
                "start_trip",
                JSONObject().put("p_idempotency_key", "prot-${now.toEpochMilli()}").put("p_mode", "PROTECTION")
                    .put("p_planned_arrival", now.plus(SESSION).toString())
            ).trim().trim('"')
            settings.put(SettingEntity(SERVER_ID, id))
            api.rpc(
                "set_trip_recipients",
                JSONObject().put("p_trip_id", id).put("p_phones", JSONArray(chosen.map { it.phone.value }))
                    .put("p_langs", JSONArray(chosen.map { it.language.name.lowercase() }))
            )
            log.append("protection", "server_watch_on recipients=${chosen.size}")
            beats = scope.launch {
                val started = clock.instant()
                while (isActive) {
                    heartbeat(id)
                    delay(HEARTBEAT_MS)
                    // Session de 12 h au plus côté serveur : on en ouvre une nouvelle avant la fin.
                    if (Duration.between(started, clock.instant()) > RENEW_AFTER) {
                        // Hors de cette coroutine : end() l'annulerait pendant le renouvellement.
                        scope.launch {
                            mutex.withLock {
                                beats = null
                                end()
                                begin()
                            }
                        }
                        return@launch
                    }
                }
            }
        } catch (e: ServerException) {
            log.append("protection", "server_watch_failed ${e.code}")
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
            roots.push()
        } catch (e: ServerException) {
            // Sans réseau, le serveur préviendra les proches au bout de 10 min : c'est le but.
        }
    }

    private suspend fun end() {
        beats?.cancel()
        beats = null
        val id = settings.get(SERVER_ID) ?: return
        try {
            api.rpc("end_trip", JSONObject().put("p_trip_id", id))
            settings.remove(SERVER_ID)
            log.append("protection", "server_watch_off")
        } catch (e: ServerException) {
            log.append("protection", "server_watch_end_failed ${e.code}") // le serveur arrête seul 2 h après l'échéance
        }
    }

    private fun battery(): Int? {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        return if (level < 0) null else (level * 100 / scale).coerceIn(0, 100)
    }

    private companion object {
        const val ENABLED = "protection.server_watch"
        const val SERVER_ID = "protection.server_id"
        const val MAX = 3
        const val HEARTBEAT_MS = 5 * 60_000L
        val SESSION: Duration = Duration.ofHours(12)
        val RENEW_AFTER: Duration = Duration.ofHours(11)
    }
}
