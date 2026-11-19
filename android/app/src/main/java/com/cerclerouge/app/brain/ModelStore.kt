package com.cerclerouge.app.brain

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** État du modèle de langage sur le téléphone. */
sealed interface ModelState {
    data object Absent : ModelState

    data class Downloading(val percent: Int) : ModelState

    data object Verifying : ModelState

    data object Ready : ModelState

    /** Échec : `reason` est un code court (réseau, empreinte, espace). */
    data class Failed(val reason: String) : ModelState
}

/**
 * Stockage du modèle de langage (ADR-0029, IA.10) : Gemma 3 270M (q8, LiteRT-LM, 304 Mo), **jamais embarqué** dans l'APK.
 * Deux façons de l'obtenir : téléchargement en Wi-Fi seulement (reprise possible), ou import d'un fichier déjà sur le
 * téléphone. Le fichier n'est gardé que si son SHA-256 est exactement celui attendu (menace T17, modèle altéré).
 */
@Singleton
class ModelStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, "models").apply { mkdirs() }
    val file: File get() = File(dir, FILE_NAME)
    private val part: File get() = File(dir, "$FILE_NAME.part")
    private val prefs get() = context.getSharedPreferences("brain_model", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<ModelState>(if (installed()) ModelState.Ready else ModelState.Absent)
    val state: StateFlow<ModelState> = _state

    /** Le porteur peut couper le modèle : les règles seules restent alors (aucun effet sur la protection). */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_ENABLED, value) }
        }

    private fun installed() = file.exists() && file.length() == SIZE

    /** Téléchargement en Wi-Fi (réseau non facturé), en arrière-plan, repris là où il s'est arrêté. */
    fun download() {
        val req = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresStorageNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, req)
        _state.value = ModelState.Downloading(percentOf(part.length()))
    }

    /** Import d'un fichier choisi par l'utilisateur (copié, puis vérifié). */
    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        _state.value = ModelState.Verifying
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> copyVerified(input, append = false) } ?: false
        }.getOrDefault(false)
        if (!ok) _state.value = ModelState.Failed(REASON_HASH)
    }

    fun delete() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        file.delete()
        part.delete()
        _state.value = ModelState.Absent
    }

    /** Vérifie l'empreinte du fichier installé (avant chaque chargement). */
    fun verifyInstalled(): Boolean = installed() && sha256(file.inputStream()) == SHA256

    internal suspend fun runDownload(): Boolean = withContext(Dispatchers.IO) {
        val done = part.length()
        val conn = (URL(URL_MODEL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            if (done > 0) setRequestProperty("Range", "bytes=$done-")
        }
        try {
            val append = conn.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (conn.responseCode !in 200..299) {
                _state.value = ModelState.Failed(REASON_NETWORK)
                return@withContext false
            }
            if (!append) part.delete()
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(BUFFER)
                    var total = part.length()
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                        _state.value = ModelState.Downloading(percentOf(total))
                    }
                }
            }
            _state.value = ModelState.Verifying
            val ok = part.length() == SIZE && sha256(part.inputStream()) == SHA256
            if (ok) part.renameTo(file) else part.delete()
            _state.value = if (ok) ModelState.Ready else ModelState.Failed(REASON_HASH)
            ok
        } catch (e: java.io.IOException) {
            _state.value = ModelState.Failed(REASON_NETWORK)
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun copyVerified(input: InputStream, append: Boolean): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        java.io.FileOutputStream(part, append).use { out ->
            val buf = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                digest.update(buf, 0, n)
                total += n
            }
        }
        val ok = total == SIZE && hex(digest.digest()) == SHA256
        if (ok) part.renameTo(file) else part.delete()
        _state.value = if (ok) ModelState.Ready else ModelState.Failed(REASON_HASH)
        return ok
    }

    private fun percentOf(bytes: Long) = (bytes * 100 / SIZE).toInt().coerceIn(0, 100)

    companion object {
        const val FILE_NAME = "gemma3-270m-it-q8.litertlm"
        const val VERSION = "gemma3-270m-it-q8"
        const val SIZE = 304_005_120L
        const val SHA256 = "757e9119fa5bd667a2774fb470ac4afcd3190a21c677f8e69a5d6bc908abdd63"

        /** Fichier publié par le porteur dans une release GitHub du dépôt (Hugging Face exige un compte, licence Gemma). */
        const val URL_MODEL = "https://github.com/shelohermituss-hub/Cercle-Rouge/releases/download/model-gemma3-270m/$FILE_NAME"
        const val REASON_NETWORK = "network"
        const val REASON_HASH = "hash"
        private const val WORK = "brain-model-download"
        private const val KEY_ENABLED = "enabled"
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER = 1 shl 16

        fun sha256(input: InputStream): String = input.use { s ->
            val d = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(BUFFER)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                d.update(buf, 0, n)
            }
            hex(d.digest())
        }

        private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ModelEntryPoint {
    fun store(): ModelStore
}

/** Téléchargement du modèle en arrière-plan (Wi-Fi seulement, contraintes posées à l'envoi). */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = EntryPointAccessors.fromApplication(applicationContext, ModelEntryPoint::class.java).store()
        return when {
            store.runDownload() -> Result.success()
            runAttemptCount < MAX_ATTEMPTS && store.state.value == ModelState.Failed(ModelStore.REASON_NETWORK) -> Result.retry()
            else -> Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
