package com.cerclerouge.app.settings

import com.cerclerouge.app.audio.AudioListener
import com.cerclerouge.app.audio.PhraseRepository
import com.cerclerouge.app.evidence.EvidenceStore
import com.cerclerouge.app.server.ServerSync
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.log.EventLog
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Maîtrise de ses données par l'utilisateur.
 * - [emergencyWipe] (S7.05, R-EVD-06) : efface en quelques instants ce qui peut révéler un usage ou une alerte passée
 *   — preuves (clés détruites d'abord), journal, lieux favoris, phrase secrète — mais garde le compte, les contacts et le PIN
 *   pour que la protection continue de marcher.
 * - [deleteEverything] (S3.12) : supprime le compte sur le serveur puis toutes les données du téléphone.
 * - [export] : copie lisible de ses données (droit d'accès et de portabilité), sans les preuves (export chiffré à part).
 */
@Singleton
class DataControl @Inject constructor(
    private val db: PrivacyDatabase,
    private val evidence: EvidenceStore,
    private val phrases: PhraseRepository,
    private val audio: AudioListener,
    private val server: ServerSync,
    private val log: EventLog,
    private val clock: Clock
) {
    suspend fun emergencyWipe() = withContext(Dispatchers.IO) {
        evidence.wipeAll() // les clés d'abord : illisible même si la suite est interrompue
        phrases.delete()
        audio.restartWith(null)
        listOf("trip.favorites", "trip.search.consent").forEach { db.settings().remove(it) }
        db.eventLog().deleteOlderThan(Long.MAX_VALUE)
        log.append("privacy", "emergency_wipe") // seule trace : l'effacement lui-même, sans contenu
    }

    /** Vrai si le serveur a confirmé (ou s'il n'y a pas de serveur) ; le téléphone est effacé dans tous les cas où c'est vrai. */
    suspend fun deleteEverything(): Boolean = withContext(Dispatchers.IO) {
        if (!server.deleteAccount()) return@withContext false
        evidence.wipeAll()
        audio.restartWith(null)
        db.clearAllTables()
        true
    }

    suspend fun export(): String = withContext(Dispatchers.IO) {
        val s = db.settings()
        val account = JSONObject()
            .put("prenom", s.get("account.name"))
            .put("annee_naissance", s.get("account.birthYear"))
            .put("mois_naissance", s.get("account.birthMonth"))
            .put("conservation_preuves", s.get("retention"))
        val contacts = JSONArray(
            db.contacts().all().map {
                JSONObject().put(
                    "nom",
                    it.name
                ).put("telephone", it.phone).put("role", it.role).put("langue", it.language).put("statut", it.status)
            }
        )
        val consents = JSONArray(
            db.consents().history().map {
                JSONObject().put("type", it.scope).put("accorde", it.granted).put("version", it.textVersion)
                    .put("date", Instant.ofEpochMilli(it.atMillis).toString())
            }
        )
        val journal = JSONArray(
            db.eventLog().all().map {
                JSONObject().put("date", Instant.ofEpochMilli(it.utcMillis).toString()).put("type", it.type).put("detail", it.payload)
            }
        )
        val favorites = JSONArray(s.get("trip.favorites")?.lines()?.filter { it.isNotBlank() }.orEmpty())
        val proofs = JSONArray(
            db.evidence().alerts().filter { it.wrappedKey != null }.map {
                JSONObject().put("alerte_ouverte", Instant.ofEpochMilli(it.openedAtMillis).toString())
                    .put("segments", db.evidence().segments(it.alertId).size)
            }
        )
        JSONObject()
            .put("application", "Cercle Rouge")
            .put("exporte_le", clock.instant().toString())
            .put("compte", account)
            .put("contacts_de_confiance", contacts)
            .put("consentements", consents)
            .put("lieux_favoris", favorites)
            .put("preuves_resume", proofs)
            .put("journal", journal)
            .toString(2)
    }
}
