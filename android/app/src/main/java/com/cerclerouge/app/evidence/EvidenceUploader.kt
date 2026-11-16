package com.cerclerouge.app.evidence

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cerclerouge.app.security.DeviceKey
import com.cerclerouge.app.server.ServerException
import com.cerclerouge.app.server.ServerSync
import com.cerclerouge.app.server.SupabaseApi
import com.cerclerouge.evidence.Chain
import com.cerclerouge.evidence.EvidenceAlert
import com.cerclerouge.evidence.RetentionPolicy
import com.cerclerouge.privacy.Settings
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import com.cerclerouge.privacy.log.EventLog
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Copie de secours des preuves sur le serveur (1.15, S6.03–S6.05, ADR-0028). Ce qui part est le fichier **tel qu'il est sur le
 * disque**, déjà chiffré par la clé de l'alerte : le serveur ne peut rien écouter. Chaque segment de 30 s est un objet à part,
 * jamais remplacé : une coupure ne fait reprendre que les segments manquants (envoi reprenable segment par segment).
 *
 * Réseau (R-NET-04) : pendant l'alerte et juste à sa fin, n'importe quel réseau ; le reste attend le Wi-Fi (tâche WorkManager
 * « réseau non facturé »). À la clôture, quand tout est envoyé : métadonnées puis racine de la chaîne signée par `K_device`.
 * Suppression locale (manuelle, rétention, effacement d'urgence) : la copie serveur est marquée à effacer dans l'heure.
 */
@Singleton
class EvidenceUploader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: SupabaseApi,
    private val server: ServerSync,
    private val store: EvidenceStore,
    private val prefs: Settings,
    private val deviceKey: DeviceKey,
    private val log: EventLog,
    private val clock: Clock,
    db: PrivacyDatabase
) {
    private val dao = db.evidence()
    private val settings = db.settings()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** Pendant l'alerte (après chaque segment) et à sa clôture : envoi immédiat, sur n'importe quel réseau. */
    fun kick() = scope.launch { if (!sync()) scheduleBackground() }

    /** Ce qui reste partira au prochain Wi-Fi (ou réseau non facturé), même si l'app est fermée. */
    fun scheduleBackground() {
        if (!api.configured) return
        val request = OneTimeWorkRequestBuilder<EvidenceUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
    }

    /** Vrai quand plus rien n'attend (ou qu'il n'y a pas de serveur). */
    suspend fun sync(): Boolean = mutex.withLock {
        if (!api.configured) return@withLock true
        var done = true
        val now = clock.instant()
        for (a in dao.alerts().filter { it.wrappedKey != null }) {
            if (settings.get(ROOT + a.alertId) != null) continue
            val sid = server.serverAlertId(a.alertId) ?: continue // alerte jamais synchronisée : rien à rattacher
            try {
                val folder = settings.get(FOLDER + a.alertId) ?: api.rpc("begin_evidence_upload", JSONObject().put("p_alert_id", sid))
                    .trim().trim('"').also { settings.put(SettingEntity(FOLDER + a.alertId, it)) }
                for (s in dao.pendingUploads().filter { it.alertId == a.alertId }) {
                    val bytes = store.encryptedSegment(a.alertId, s.seq) ?: continue
                    api.upload(BUCKET, "$folder/${s.seq}.bin", bytes)
                    dao.markUploaded(a.alertId, s.seq)
                }
                if (a.closedAtMillis != null && dao.pendingUploads().none { it.alertId == a.alertId }) {
                    store.encryptedNotes(a.alertId)?.let { api.upload(BUCKET, "$folder/meta.bin", it) }
                    registerRoot(a.alertId, sid, a.toDomain(), now)
                } else if (a.closedAtMillis == null || dao.pendingUploads().any { it.alertId == a.alertId }) {
                    done = false
                }
            } catch (e: ServerException) {
                log.append("evidence", "upload_failed ${e.code}")
                if (e.code == "upload_closed" || e.code == "alert_not_found") {
                    settings.put(SettingEntity(ROOT + a.alertId, "skipped")) // copie effacée ou alerte purgée : on n'insiste pas
                } else {
                    done = false
                    if (e.code == "network") break
                }
            }
        }
        done
    }

    private suspend fun registerRoot(alertId: String, serverId: String, a: EvidenceAlert, now: Instant) {
        val chain = store.chain(alertId)
        val root = Chain.root(chain)
        val signature = runCatching { deviceKey.sign("cercle-rouge/root/v1/$serverId/$root".toByteArray()) }.getOrNull()
        val publicKey = runCatching { deviceKey.publicKey() }.getOrNull()
        api.rpc(
            "register_chain_root",
            JSONObject().put("p_alert_id", serverId).put("p_root", root).put("p_segments", chain.size)
                .put("p_signature", signature ?: JSONObject.NULL).put("p_public_key", publicKey ?: JSONObject.NULL)
                .put("p_keep_hours", RetentionPolicy.serverKeepHours(a, prefs.retention(), now))
        )
        settings.put(SettingEntity(ROOT + alertId, root))
        log.append("evidence", "uploaded segments=${chain.size}")
    }

    /** La preuve a été supprimée sur le téléphone : la copie serveur sera effacée dans l'heure (Edge Function). */
    suspend fun forget(alertId: String) {
        val sid = server.serverAlertId(alertId) ?: return
        runCatching { api.rpc("forget_evidence", JSONObject().put("p_alert_id", sid)) }
            .onFailure { log.append("evidence", "forget_failed") }
    }

    /** Effacement d'urgence : toutes les copies serveur. */
    suspend fun forgetAll() {
        if (!api.configured) return
        runCatching { api.rpc("forget_evidence", JSONObject()) }.onFailure { log.append("evidence", "forget_failed") }
    }

    private fun com.cerclerouge.privacy.db.EvidenceAlertEntity.toDomain() = EvidenceAlert(
        alertId,
        Instant.ofEpochMilli(openedAtMillis),
        closedAtMillis?.let(Instant::ofEpochMilli),
        Instant.ofEpochMilli(lastWriteMillis),
        keptUntilMillis?.let(Instant::ofEpochMilli)
    )

    private companion object {
        const val BUCKET = "evidence"
        const val WORK = "cr-evidence-upload"
        const val FOLDER = "evidence.folder."
        const val ROOT = "evidence.root."
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface EvidenceUploadEntryPoint {
    fun uploader(): EvidenceUploader
}

/** Reprise au Wi-Fi : relance l'envoi ; nouvel essai plus tard si tout n'est pas parti. */
class EvidenceUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uploader = EntryPointAccessors.fromApplication(applicationContext, EvidenceUploadEntryPoint::class.java).uploader()
        return if (uploader.sync()) Result.success() else Result.retry()
    }
}
