package com.cerclerouge.app

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import java.io.File

/** Fichier du dernier plantage (version de test uniquement), relu au démarrage suivant. */
fun crashFile(app: Application) = File(app.filesDir, "last_crash.txt")

@HiltAndroidApp
class CercleRougeApp :
    Application(),
    Configuration.Provider {
    /** Initialisation de WorkManager à la demande (aussi sous Robolectric, où les fournisseurs de démarrage ne tournent pas). */
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        // Envois restés dans la file (processus tué, téléphone redémarré) : repris dès que le réseau est là (R-NET-01).
        com.cerclerouge.app.alert.OutboxWork.schedule(this)
        if (BuildConfig.DEBUG) {
            // Rapport local, sans réseau : la trace du plantage est gardée pour être partagée volontairement.
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                runCatching { crashFile(this).writeText("${thread.name}\n${error.stackTraceToString().take(6000)}") }
                previous?.uncaughtException(thread, error)
            }
        }
    }
}
