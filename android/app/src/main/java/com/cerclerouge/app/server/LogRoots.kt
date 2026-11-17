package com.cerclerouge.app.server

import com.cerclerouge.privacy.log.EventLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Racine du journal anti-effacement (1.17) : pendant une alerte ou une session suivie, la dernière entrée du journal chaîné
 * (numéro + haché) est déposée au serveur, qui l'horodate. Si le journal du téléphone est effacé ou réécrit ensuite, la
 * différence se voit. Aucune donnée du journal ne part : seulement le haché. Échecs silencieux (réessai au passage suivant).
 */
@Singleton
class LogRoots @Inject constructor(private val api: SupabaseApi, private val log: EventLog) {
    private val mutex = Mutex()
    private var lastSeq = -1L

    suspend fun push() = mutex.withLock {
        if (!api.configured) return@withLock
        val last = log.latest(1).firstOrNull() ?: return@withLock
        if (last.seq == lastSeq) return@withLock
        try {
            api.rpc("register_log_root", JSONObject().put("p_seq", last.seq).put("p_root", last.hash))
            lastSeq = last.seq
        } catch (_: ServerException) {
            // Pas de journalisation ici : elle ajouterait une entrée et changerait la racine à chaque échec.
        }
    }
}
