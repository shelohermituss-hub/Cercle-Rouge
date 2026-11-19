package com.cerclerouge.app.brain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import java.io.FileOutputStream
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

    /** Demandé, mais le téléphone n'est pas en Wi-Fi : il démarrera seul en Wi-Fi (ou tout de suite si on accepte les données). */
    data object WaitingWifi : ModelState

    data class Downloading(val percent: Int) : ModelState

    data object Verifying : ModelState

    data object Ready : ModelState

    /** Échec : `reason` est un code court (réseau, empreinte). */
    data class Failed(val reason: String) : ModelState
}

/**
 * Stockage du modèle de langage (ADR-0029, IA.10) : Gemma 3 270M (q8, LiteRT-LM, 304 Mo), **jamais embarqué** dans l'APK.
 * Deux façons de l'obtenir : téléchargement (Wi-Fi par défaut, données mobiles si le porteur l'accepte, reprise là où il
 * s'est arrêté) ou import d'un fichier déjà sur le téléphone. Le fichier n'est gardé que si son SHA-256 est exactement
 * celui attendu (menace T17) : la source importe donc peu, un fichier altéré est toujours refusé.
 */
@Singleton
class ModelStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, "models").apply { mkdirs() }
    val file: File get() = File(dir, FILE_NAME)
    private val part: File get() = File(dir, "$FILE_NAME.part")
    private val prefs get() = context.getSharedPreferences("brain_model", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(initial())
    val state: StateFlow<ModelState> = _state

    /** Le porteur peut couper le modèle : les règles seules restent alors (aucun effet sur la protection). */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_ENABLED, value) }
        }

    private fun installed() = file.exists() && file.length() == SIZE

    private fun initial(): ModelState = when {
        installed() -> ModelState.Ready
        prefs.getBoolean(KEY_PENDING, false) -> ModelState.Downloading(percentOf(part.length()))
        else -> ModelState.Absent
    }

    /**
     * Lance le téléchargement en arrière-plan. Sans [mobileData], il attend le Wi-Fi (304 Mo) et l'écran le dit ; avec, il
     * part sur n'importe quel réseau. Reprend là où il s'est arrêté (coupure, limite de 10 min de WorkManager).
     */
    fun download(mobileData: Boolean = false) {
        val network = if (mobileData) NetworkType.CONNECTED else NetworkType.UNMETERED
        val req = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).setRequiresStorageNotLow(true).build())
            .build()
        prefs.edit { putBoolean(KEY_PENDING, true) }
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
        _state.value = if (!mobileData && meteredNow()) ModelState.WaitingWifi else ModelState.Downloading(percentOf(part.length()))
    }

    private fun meteredNow(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Import d'un fichier choisi par l'utilisateur (copié, puis vérifié). */
    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        _state.value = ModelState.Verifying
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> copyVerified(input) } ?: false
        }.getOrDefault(false)
        if (!ok) _state.value = ModelState.Failed(REASON_HASH)
    }

    fun delete() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        prefs.edit { putBoolean(KEY_PENDING, false) }
        file.delete()
        part.delete()
        _state.value = ModelState.Absent
    }

    /** Vérifie l'empreinte du fichier installé (avant chaque chargement). */
    fun verifyInstalled(): Boolean = installed() && sha256(file.inputStream()) == SHA256

    /**
     * Télécharge depuis la première source qui répond, en reprenant le fichier partiel (`Range`). Rend vrai si le modèle est
     * installé et vérifié, faux sinon ; [Failed] dit pourquoi. Les paramètres ne servent qu'aux tests.
     */
    internal suspend fun runDownload(sources: List<String> = SOURCES, size: Long = SIZE, sha: String = SHA256): Boolean =
        withContext(Dispatchers.IO) {
            _state.value = ModelState.Downloading(percentOf(part.length(), size))
            for (url in sources) {
                when (fetch(url, size)) {
                    Fetch.DONE -> {
                        _state.value = ModelState.Verifying
                        val ok = part.length() == size && sha256(part.inputStream()) == sha
                        if (ok) part.renameTo(file) else part.delete()
                        if (ok) prefs.edit { putBoolean(KEY_PENDING, false) }
                        _state.value = if (ok) ModelState.Ready else ModelState.Failed(REASON_HASH)
                        return@withContext ok
                    }
                    Fetch.NOT_HERE -> continue
                    Fetch.CUT -> break
                }
            }
            _state.value = ModelState.Failed(REASON_NETWORK)
            false
        }

    private enum class Fetch { DONE, NOT_HERE, CUT }

    private fun fetch(url: String, size: Long): Fetch {
        val done = part.length().takeIf { it < size } ?: 0L.also { part.delete() }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            if (done > 0) setRequestProperty("Range", "bytes=$done-")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) return Fetch.NOT_HERE
            // 206 : la source reprend où on s'était arrêté ; 200 : elle renvoie tout, on repart de zéro.
            val append = code == HttpURLConnection.HTTP_PARTIAL
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(BUFFER)
                    var total = if (append) done else 0L
                    var shown = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                        val pct = percentOf(total, size)
                        if (pct != shown) {
                            shown = pct
                            _state.value = ModelState.Downloading(pct)
                        }
                    }
                }
            }
            Fetch.DONE
        } catch (e: java.io.IOException) {
            Fetch.CUT
        } finally {
            conn.disconnect()
        }
    }

    private fun copyVerified(input: InputStream): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        FileOutputStream(part, false).use { out ->
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

    private fun percentOf(bytes: Long, size: Long = SIZE) = (bytes * 100 / size).toInt().coerceIn(0, 100)

    companion object {
        const val FILE_NAME = "gemma3-270m-it-q8.litertlm"
        const val VERSION = "gemma3-270m-it-q8"
        const val SIZE = 304_005_120L
        const val SHA256 = "757e9119fa5bd667a2774fb470ac4afcd3190a21c677f8e69a5d6bc908abdd63"

        /**
         * Sources, dans l'ordre. 1) Copie publique, sans compte, du fichier officiel `litert-community/gemma-3-270m-it`
         * (même taille, même SHA-256, vérifié le 2026-11-19), figée sur une révision. 2) Release GitHub du dépôt, si le
         * porteur la publie. Le fichier officiel exige un compte Hugging Face (licence Gemma), inutilisable depuis l'app.
         */
        val SOURCES = listOf(
            "https://huggingface.co/warped-community/gemma-3-270m-it-litert-lm/resolve/" +
                "097476ffea7815326d665251ef8716a264673b1d/gemma-3-270m-it.litertlm",
            "https://github.com/shelohermituss-hub/Cercle-Rouge/releases/download/model-gemma3-270m/$FILE_NAME"
        )
        const val REASON_NETWORK = "network"
        const val REASON_HASH = "hash"
        private const val WORK = "brain-model-download"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PENDING = "pending"
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
            // Coupure réseau : on réessaie (le fichier partiel est gardé). Empreinte fausse : inutile d'insister.
            runAttemptCount < MAX_ATTEMPTS && store.state.value == ModelState.Failed(ModelStore.REASON_NETWORK) -> Result.retry()
            else -> Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 20
    }
}
