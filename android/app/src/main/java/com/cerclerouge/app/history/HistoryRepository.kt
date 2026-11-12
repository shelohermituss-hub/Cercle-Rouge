package com.cerclerouge.app.history

import com.cerclerouge.domain.Alert
import com.cerclerouge.privacy.db.HistoryEntity
import com.cerclerouge.privacy.db.PrivacyDatabase
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Historique des alertes et des trajets (PDF 16.1) : un résumé par événement, **sans coordonnées**, dans la base chiffrée.
 * Effaçable par l'utilisateur et par l'effacement d'urgence. Le journal chaîné reste la trace d'audit.
 */
@Singleton
class HistoryRepository @Inject constructor(db: PrivacyDatabase, private val clock: Clock) {
    private val dao = db.history()

    /** Suit une alerte d'une transition à l'autre : ouverture, changement de niveau, clôture. */
    suspend fun alertChanged(before: Alert?, after: Alert?, userSafe: Boolean) {
        val now = clock.millis()
        if (after != null && (before == null || before.id != after.id || before.level != after.level)) {
            val old = dao.get(after.id)
            dao.put(
                HistoryEntity(
                    after.id,
                    ALERT,
                    old?.startedAtMillis ?: after.openedAt.toEpochMilli(),
                    null,
                    after.reason.name,
                    after.level.value,
                    null
                )
            )
        }
        if (before != null && after == null) {
            dao.get(before.id)?.takeIf { it.endedAtMillis == null }
                ?.let { dao.put(it.copy(endedAtMillis = now, outcome = if (userSafe) SAFE else ENDED)) }
        }
    }

    suspend fun tripStarted(startedAtMillis: Long, mode: String) {
        dao.put(HistoryEntity("trip-$startedAtMillis", TRIP, startedAtMillis, null, mode, null, null))
    }

    suspend fun tripEnded(startedAtMillis: Long, arrived: Boolean) {
        dao.get("trip-$startedAtMillis")
            ?.let { dao.put(it.copy(endedAtMillis = clock.millis(), outcome = if (arrived) ARRIVED else STOPPED)) }
    }

    suspend fun latest(limit: Int = 200) = dao.latest(limit)

    suspend fun last(kind: String) = dao.last(kind)

    suspend fun clear() = dao.clear()

    companion object {
        const val ALERT = "alert"
        const val TRIP = "trip"
        const val SAFE = "safe"
        const val ENDED = "ended"
        const val ARRIVED = "arrived"
        const val STOPPED = "stopped"
    }
}
