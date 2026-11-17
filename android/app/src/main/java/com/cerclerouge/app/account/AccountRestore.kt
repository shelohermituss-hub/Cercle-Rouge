package com.cerclerouge.app.account

import com.cerclerouge.app.contacts.ContactsRepository
import com.cerclerouge.app.contacts.toEntity
import com.cerclerouge.app.server.ServerException
import com.cerclerouge.app.server.SupabaseApi
import com.cerclerouge.privacy.db.ContactEntity
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import com.cerclerouge.privacy.log.EventLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Résultat d'une connexion sur un nouveau téléphone. */
enum class RestoreResult { RESTORED, NO_PROFILE, FAILED }

/**
 * Connexion sur un nouveau téléphone et sauvegarde des contacts (1.19, PDF 14.1).
 * - Après le code SMS : le profil revient (`my_profile`), les contacts aussi **si leur sauvegarde était activée**.
 * - Sauvegarde des contacts : **opt-in**, chiffrée par le serveur (clé du coffre, comme les numéros des alertes) ; elle suit
 *   chaque modification du carnet. La désactiver la vide. Les preuves ne sont jamais transférées (vie privée).
 */
@OptIn(FlowPreview::class)
@Singleton
class AccountRestore @Inject constructor(
    private val api: SupabaseApi,
    private val account: AccountRepository,
    private val contacts: ContactsRepository,
    private val log: EventLog,
    db: PrivacyDatabase
) {
    private val settings = db.settings()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _backup = MutableStateFlow(false)
    val backup: StateFlow<Boolean> = _backup

    init {
        scope.launch { _backup.value = settings.get(BACKUP) == "1" }
        scope.launch {
            contacts.book.drop(1).debounce(DEBOUNCE_MS).collect { if (_backup.value) backupNow() }
        }
    }

    suspend fun restoreAfterLogin(): RestoreResult = try {
        val rows = JSONArray(api.rpc("my_profile", JSONObject()))
        if (rows.length() == 0) {
            RestoreResult.NO_PROFILE
        } else {
            val p = rows.getJSONObject(0)
            account.saveProfile(
                p.getString("display_name"),
                p.getInt("birth_year"),
                p.getInt("birth_month"),
                p.getBoolean("is_adult_attested")
            )
            val body = api.rpc("restore_contacts", JSONObject()).trim()
            if (body != "null" && body.isNotEmpty()) {
                val items = JSONArray(body)
                if (items.length() > 0) {
                    contacts.restore((0 until items.length()).mapNotNull { fromJson(items.getJSONObject(it)) })
                    setBackup(true) // elle était active sur l'ancien téléphone
                }
            }
            log.append("account", "restored contacts=${contacts.book.value.contacts.size}")
            RestoreResult.RESTORED
        }
    } catch (e: ServerException) {
        log.append("account", "restore_failed ${e.code}")
        RestoreResult.FAILED
    } catch (e: org.json.JSONException) {
        RestoreResult.FAILED
    } catch (e: IllegalArgumentException) {
        RestoreResult.FAILED // profil de moins de 12 ans : refusé comme à l'inscription
    }

    suspend fun setBackup(on: Boolean) {
        settings.put(SettingEntity(BACKUP, if (on) "1" else "0"))
        _backup.value = on
        if (on) {
            backupNow()
        } else {
            runCatching { api.rpc("forget_contact_backup", JSONObject()) }
        }
    }

    private suspend fun backupNow() {
        if (!api.configured) return
        val items = JSONArray(contacts.book.value.contacts.map { toJson(it.toEntity()) })
        try {
            api.rpc("backup_contacts", JSONObject().put("p_items", items))
        } catch (e: ServerException) {
            log.append("account", "backup_failed ${e.code}")
        }
    }

    private fun toJson(e: ContactEntity) = JSONObject().put("id", e.id).put("name", e.name).put("phone", e.phone).put("grp", e.grp)
        .put("role", e.role).put("language", e.language).put("channels", e.channels).put("status", e.status).put("position", e.position)
        .put("isGuardian", e.isGuardian).put("hasApp", e.hasApp).put("email", e.email ?: JSONObject.NULL)
        .put("relation", e.relation ?: JSONObject.NULL)

    private fun fromJson(o: JSONObject): ContactEntity? = runCatching {
        ContactEntity(
            o.getString("id"),
            o.getString("name"),
            o.getString("phone"),
            o.getString("grp"),
            o.getString("role"),
            o.getString("language"),
            o.getString("channels"),
            o.getString("status"),
            o.getInt("position"),
            o.optBoolean("isGuardian"),
            o.optBoolean("hasApp"),
            o.optString("email").takeIf { !o.isNull("email") && it.isNotEmpty() },
            o.optString("relation").takeIf { !o.isNull("relation") && it.isNotEmpty() }
        )
    }.getOrNull()

    private companion object {
        const val BACKUP = "account.contact_backup"
        const val DEBOUNCE_MS = 3_000L
    }
}
