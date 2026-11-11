package com.cerclerouge.app.alert

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.cerclerouge.contacts.Fix
import com.cerclerouge.contacts.LocationShare
import com.cerclerouge.contacts.chooseFix
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Position ponctuelle d'une alerte (S2.09) : la dernière connue, plus une position fraîche demandée avec 10 s au plus. Le choix
 * est fait par `chooseFix` (testé). Sans autorisation : « position non partagée ». Aucune position n'est lue hors alerte (P4).
 */
@Singleton
class LocationProvider @Inject constructor(@ApplicationContext private val context: Context, private val clock: Clock) {
    @SuppressLint("MissingPermission") // vérifié juste en dessous
    suspend fun share(): LocationShare {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return LocationShare.NotShared
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
            runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
        }
        val last = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        val fresh = providers.firstOrNull()?.let { provider -> withTimeoutOrNull(FRESH_TIMEOUT_MS) { current(manager, provider) } }
        return chooseFix(true, last?.toFix(), fresh?.toFix(), clock.instant())
    }

    @SuppressLint("MissingPermission")
    private suspend fun current(manager: LocationManager, provider: String): Location? = suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        val direct = Executor { it.run() }
        LocationManagerCompat.getCurrentLocation(manager, provider, signal, direct) { cont.resume(it) }
    }

    private fun Location.toFix() = Fix(latitude, longitude, accuracy, Instant.ofEpochMilli(time))

    private companion object {
        const val FRESH_TIMEOUT_MS = 10_000L
    }
}
