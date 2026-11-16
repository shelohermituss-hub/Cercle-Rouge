package com.cerclerouge.app.evidence

import com.cerclerouge.app.device.DeviceMonitor
import com.cerclerouge.app.location.LocationTracker
import com.cerclerouge.privacy.log.EventLog
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Métadonnées d'une alerte de niveau 2 ou d'un SOS (PDF 5.6, 11.1) : positions (une toutes les 15 s au plus), batterie, réseau
 * et GPS quand ils changent, puis à la clôture l'extrait du journal de l'alerte (transitions, décisions du cerveau avec leurs
 * composantes, détections, envois). Tout est chiffré par la clé de l'alerte, avec ses preuves, même si l'enregistrement audio
 * est coupé. Rien ne quitte le téléphone (l'export reste un geste de l'utilisateur).
 */
@Singleton
class EvidenceNotes @Inject constructor(
    private val store: EvidenceStore,
    private val tracker: LocationTracker,
    private val device: DeviceMonitor,
    private val log: EventLog,
    private val clock: Clock
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val pending = mutableListOf<String>()
    private var jobs: List<Job> = emptyList()
    private var current: String? = null
    private var openedAt: Instant = Instant.EPOCH

    fun begin(alertId: String) = scope.launch {
        mutex.withLock {
            if (current == alertId) return@launch
            current = alertId
            openedAt = clock.instant()
        }
        store.open(alertId, openedAt)
        add("opened")
        var lastFix: Instant? = null
        jobs = listOf(
            scope.launch {
                tracker.fixes.collect { f ->
                    if (lastFix?.let { Duration.between(it, f.time) < POSITION_EVERY } == true) return@collect
                    lastFix = f.time
                    add(
                        String.format(Locale.ROOT, "position lat=%.5f lon=%.5f acc=%.0f", f.point.lat, f.point.lon, f.accuracyMeters),
                        f.time
                    )
                }
            },
            scope.launch {
                device.status.map { Triple(it.batteryPct?.let { b -> b / 5 * 5 }, it.network, it.gpsEnabled) }.distinctUntilChanged()
                    .collect { (battery, network, gps) -> add("device battery=${battery ?: "?"} network=$network gps=$gps") }
            },
            scope.launch {
                while (isActive) {
                    delay(FLUSH_MS)
                    flush()
                }
            }
        )
    }

    fun end(alertId: String) = scope.launch {
        if (mutex.withLock { current } != alertId) return@launch
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        // Extrait du journal pendant l'alerte : sans données personnelles ni audio (R-AL-06), déjà chaîné.
        log.latest(JOURNAL_LINES).filter { it.utcMillis >= openedAt.toEpochMilli() && it.type in JOURNAL_TYPES }
            .sortedBy { it.seq }
            .forEach { add("journal ${it.type} ${it.payload}", Instant.ofEpochMilli(it.utcMillis)) }
        add("closed")
        flush()
        mutex.withLock { current = null }
    }

    private suspend fun add(text: String, at: Instant = clock.instant()) = mutex.withLock { pending += "$at $text" }

    private suspend fun flush() {
        val (id, lines) = mutex.withLock {
            val id = current ?: return
            (id to pending.toList()).also { pending.clear() }
        }
        store.note(id, lines)
    }

    private companion object {
        val POSITION_EVERY: Duration = Duration.ofSeconds(15)
        const val FLUSH_MS = 30_000L
        const val JOURNAL_LINES = 500
        val JOURNAL_TYPES = setOf("transition", "state", "risk", "audio", "trip", "dispatch", "sensor", "evidence", "sms", "email")
    }
}
