package com.cerclerouge.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import java.io.File

/** Fichier du dernier plantage (version de test uniquement), relu au démarrage suivant. */
fun crashFile(app: Application) = File(app.filesDir, "last_crash.txt")

@HiltAndroidApp
class CercleRougeApp : Application() {
    override fun onCreate() {
        super.onCreate()
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
