package com.cerclerouge.app.server

import com.cerclerouge.app.BuildConfig
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Erreur renvoyée par une RPC (message SQL court, ex. `protection_not_allowed`), ou réseau indisponible. */
class ServerException(val code: String, val http: Int) : IOException("server: $code ($http)")

/**
 * Client Supabase minimal (S3.06) : compte **anonyme** (ADR-0022), rafraîchissement de session, appels RPC.
 * HTTP de la plateforme, sans dépendance de plus. La session (jetons) vit dans la base chiffrée ; la clé publique
 * (`sb_publishable_…`) est faite pour être embarquée : la RLS protège les données. Jamais de clé `service_role` ici.
 */
@Singleton
class SupabaseApi @Inject constructor(db: PrivacyDatabase, private val clock: Clock) {
    private val settings = db.settings()
    private val mutex = Mutex()
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    val configured: Boolean get() = base.startsWith("https://") && key.isNotEmpty()

    /** Appelle une RPC avec la session courante (créée au besoin). Rend le corps JSON brut. */
    suspend fun rpc(name: String, args: JSONObject): String {
        val token = accessToken()
        return try {
            post("/rest/v1/rpc/$name", args.toString(), token)
        } catch (e: ServerException) {
            if (e.http != 401) throw e
            post("/rest/v1/rpc/$name", args.toString(), accessToken(forceRefresh = true))
        }
    }

    /** Oublie la session (après la suppression du compte). */
    suspend fun forget() = mutex.withLock {
        listOf(ACCESS, REFRESH, EXPIRES).forEach { settings.remove(it) }
    }

    private suspend fun accessToken(forceRefresh: Boolean = false): String = mutex.withLock {
        val access = settings.get(ACCESS)
        val expires = settings.get(EXPIRES)?.toLongOrNull() ?: 0
        if (!forceRefresh && access != null && expires - SKEW_S > clock.instant().epochSecond) return@withLock access
        val refresh = settings.get(REFRESH)
        val session =
            refresh?.let {
                runCatching {
                    post("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", it).toString(), null)
                }.getOrNull()
            }
                ?: post("/auth/v1/signup", JSONObject().put("data", JSONObject()).toString(), null) // compte anonyme
        save(JSONObject(session))
    }

    private suspend fun save(s: JSONObject): String {
        val access = s.getString("access_token")
        settings.put(SettingEntity(ACCESS, access))
        settings.put(SettingEntity(REFRESH, s.getString("refresh_token")))
        settings.put(SettingEntity(EXPIRES, (clock.instant().epochSecond + s.optLong("expires_in", 3600)).toString()))
        return access
    }

    private suspend fun post(path: String, body: String, bearer: String?): String = withContext(Dispatchers.IO) {
        if (!configured) throw ServerException("not_configured", 0)
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.doOutput = true
            c.setRequestProperty("apikey", key)
            c.setRequestProperty("Content-Type", "application/json")
            bearer?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).optString("message").ifEmpty { JSONObject(text).optString("msg") } }.getOrNull()
                throw ServerException(msg?.take(64)?.ifEmpty { null } ?: "http_$code", code)
            }
            text
        } catch (e: ServerException) {
            throw e
        } catch (e: IOException) {
            throw ServerException("network", 0)
        } finally {
            c.disconnect()
        }
    }

    private companion object {
        const val ACCESS = "server.access"
        const val REFRESH = "server.refresh"
        const val EXPIRES = "server.expires"
        const val SKEW_S = 60
        const val TIMEOUT_MS = 8_000
    }
}
