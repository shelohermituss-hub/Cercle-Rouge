package com.cerclerouge.app.evidence

import com.cerclerouge.app.server.ServerException
import com.cerclerouge.app.server.SupabaseApi
import com.cerclerouge.privacy.crypto.ShareKeys
import com.cerclerouge.privacy.log.EventLog
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/** Preuve confiée par une personne qui m'a choisi comme proche (1.16). */
data class SharedItem(
    val alertId: String,
    val ownerName: String,
    val folder: String,
    val wrapped: String,
    val segments: Int?,
    val complete: Boolean,
    val openedAt: Instant,
    val deleteAt: Instant
)

/** Contenu déchiffré, en mémoire seulement : segments audio (AAC/ADTS) et métadonnées. */
class SharedContent(val audio: List<ByteArray>, val notes: List<String>) {
    fun wipe() = audio.forEach { it.fill(0) }
}

/**
 * Côté proche (1.16, ADR-0016) : liste les preuves partagées, télécharge les objets **chiffrés**, ouvre l'enveloppe avec la clé
 * privée de cette app, puis déchiffre en mémoire. Rien n'est écrit sur le disque. Une enveloppe qui ne s'ouvre pas (autre
 * téléphone, réinstallation) donne `null` : l'interface le dit.
 */
@Singleton
class SharedEvidence @Inject constructor(private val api: SupabaseApi, private val keys: ShareKeys, private val log: EventLog) {
    suspend fun list(): List<SharedItem>? {
        if (!api.configured) return null
        val body = try {
            api.rpc("my_shared_evidence", JSONObject())
        } catch (e: ServerException) {
            log.append("evidence", "shared_list_failed ${e.code}")
            return null
        }
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SharedItem(
                alertId = o.getString("alert_id"),
                ownerName = o.getString("owner_name"),
                folder = o.getString("folder"),
                wrapped = o.getString("wrapped"),
                segments = if (o.isNull("segments")) null else o.getInt("segments"),
                complete = !o.isNull("chain_root"),
                openedAt = OffsetDateTime.parse(o.getString("opened_at")).toInstant(),
                deleteAt = OffsetDateTime.parse(o.getString("delete_after")).toInstant()
            )
        }
    }

    /** Null si l'enveloppe ne s'ouvre pas avec cette app ; segments manquants ou altérés : arrêt au premier (ordre gardé). */
    suspend fun open(item: SharedItem): SharedContent? {
        val key = keys.unwrap(item.wrapped) ?: return null
        val audio = mutableListOf<ByteArray>()
        val max = item.segments ?: MAX_SEGMENTS
        for (seq in 0 until max) {
            val encrypted = api.download(BUCKET, "${item.folder}/$seq.bin") ?: break
            val plain = runCatching {
                key.streaming.newDecryptingStream(encrypted.inputStream(), aad(key.localAlertId, seq)).use { it.readBytes() }
            }.getOrNull() ?: break
            audio += plain
        }
        val notes = api.download(BUCKET, "${item.folder}/meta.bin")?.let { enc ->
            runCatching {
                key.streaming.newDecryptingStream(enc.inputStream(), aad(key.localAlertId, -1)).use { String(it.readBytes()) }
                    .lines().filter { it.isNotBlank() }
            }.getOrNull()
        }.orEmpty()
        log.append("evidence", "shared_opened segments=${audio.size}")
        return SharedContent(audio, notes)
    }

    private fun aad(localId: String, seq: Int) = "cercle-rouge/evidence/v1/$localId/$seq".toByteArray()

    private companion object {
        const val BUCKET = "evidence"
        const val MAX_SEGMENTS = 120
    }
}
