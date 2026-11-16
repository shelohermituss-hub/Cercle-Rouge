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

    /** Appelle une Edge Function avec la session courante. */
    suspend fun function(name: String, args: JSONObject): String {
        val token = accessToken()
        return try {
            post("/functions/v1/$name", args.toString(), token)
        } catch (e: ServerException) {
            if (e.http != 401) throw e
            post("/functions/v1/$name", args.toString(), accessToken(forceRefresh = true))
        }
    }

    /**
     * Dépose un objet (déjà chiffré sur le téléphone) dans un bucket Storage privé. Jamais de remplacement : un objet déjà
     * présent (envoi repris après une coupure) compte comme envoyé.
     */
    suspend fun upload(bucket: String, path: String, bytes: ByteArray) {
        val token = accessToken()
        try {
            send("/storage/v1/object/$bucket/$path", bytes, token)
        } catch (e: ServerException) {
            if (e.http == 401) {
                send("/storage/v1/object/$bucket/$path", bytes, accessToken(forceRefresh = true))
                return
            }
            if (e.http == 409 || e.code.contains("Duplicate", ignoreCase = true) || e.code.contains("exists")) return
            throw e
        }
    }

    /** Lit un objet d'un bucket privé (la RLS de Storage décide). Null s'il n'existe pas (encore). */
    suspend fun download(bucket: String, path: String): ByteArray? = withContext(Dispatchers.IO) {
        fun get(token: String): Pair<Int, ByteArray?> {
            val c = URL("$base/storage/v1/object/authenticated/$bucket/$path").openConnection() as HttpURLConnection
            return try {
                c.connectTimeout = TIMEOUT_MS
                c.readTimeout = TIMEOUT_MS * 2
                c.setRequestProperty("apikey", key)
                c.setRequestProperty("Authorization", "Bearer $token")
                val code = c.responseCode
                code to (if (code in 200..299) c.inputStream.use { it.readBytes() } else null)
            } catch (e: IOException) {
                throw ServerException("network", 0)
            } finally {
                c.disconnect()
            }
        }
        var (code, body) = get(accessToken())
        if (code == 401) {
            val retry = get(accessToken(forceRefresh = true))
            code = retry.first
            body = retry.second
        }
        when {
            code in 200..299 -> body
            code == 400 || code == 404 -> null // Storage répond 400 « Object not found » pour un objet absent ou interdit
            else -> throw ServerException("http_$code", code)
        }
    }

    private suspend fun send(path: String, bytes: ByteArray, bearer: String) = withContext(Dispatchers.IO) {
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS * 2
            c.doOutput = true
            c.setFixedLengthStreamingMode(bytes.size)
            c.setRequestProperty("apikey", key)
            c.setRequestProperty("Authorization", "Bearer $bearer")
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.setRequestProperty("x-upsert", "false")
            c.outputStream.use { it.write(bytes) }
            val code = c.responseCode
            if (code !in 200..299) {
                val text = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val msg = runCatching { JSONObject(text).let { o -> o.optString("error").ifEmpty { o.optString("message") } } }.getOrNull()
                // Storage répond parfois 400 avec un statusCode 409 dans le corps pour un doublon.
                val status = runCatching { JSONObject(text).optString("statusCode").toInt() }.getOrNull() ?: code
                throw ServerException(msg?.take(64)?.ifEmpty { null } ?: "http_$code", status)
            }
        } catch (e: IOException) {
            if (e is ServerException) throw e
            throw ServerException("network", 0)
        } finally {
            c.disconnect()
        }
    }

    /**
     * Rattache un numéro au compte (anonyme) : Supabase envoie un code par SMS (Vonage). Le compte et ses données sont gardés.
     */
    suspend fun requestPhoneCode(phoneE164: String) {
        post("/auth/v1/user", JSONObject().put("phone", phoneE164).toString(), accessToken(), method = "PUT")
    }

    /** Vérifie le code reçu ; la nouvelle session (numéro confirmé) remplace l'ancienne. */
    suspend fun verifyPhoneCode(phoneE164: String, code: String) {
        val body = JSONObject().put("type", "phone_change").put("phone", phoneE164).put("token", code).toString()
        val session = post("/auth/v1/verify", body, accessToken())
        mutex.withLock {
            val s = JSONObject(session)
            if (s.has("access_token")) save(s)
            settings.put(SettingEntity(PHONE, phoneE164))
        }
    }

    /** Numéro vérifié du compte, ou null. */
    suspend fun verifiedPhone(): String? = settings.get(PHONE)

    /** Oublie la session (après la suppression du compte). */
    suspend fun forget() = mutex.withLock {
        listOf(ACCESS, REFRESH, EXPIRES, PHONE).forEach { settings.remove(it) }
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

    private suspend fun post(path: String, body: String, bearer: String?, method: String = "POST"): String = withContext(Dispatchers.IO) {
        if (!configured) throw ServerException("not_configured", 0)
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
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
                val msg = runCatching {
                    val o = JSONObject(text)
                    o.optString("message").ifEmpty { o.optString("msg") }.ifEmpty { o.optString("error") }
                }.getOrNull()
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
        const val PHONE = "server.phone"
        const val SKEW_S = 60
        const val TIMEOUT_MS = 8_000
    }
}
