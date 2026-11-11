package com.cerclerouge.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Looper
import com.cerclerouge.location.GeoPoint
import com.cerclerouge.location.TrackFix
import com.cerclerouge.location.Tracking
import com.cerclerouge.location.TrackingProfile
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Suivi de position du téléphone (S4.01). Fused Location de Google quand les services Google sont disponibles (batterie,
 * précision en ville), sinon repli sur `LocationManager` (GPS + réseau) : l'application marche aussi sans services Google (Q15).
 *
 * Le suivi n'existe que dans les états qui l'autorisent (R-TRIP-01, R-LE-01) : l'appelant l'arrête dans tous les autres. Sans
 * autorisation de position, rien ne démarre. Aucune position n'est enregistrée ici : elles sont diffusées au moniteur du trajet,
 * qui n'en garde que les 3 dernières en mémoire (R-LE-04). Pas d'`ACCESS_BACKGROUND_LOCATION` : le suivi en arrière-plan passe par
 * le service de premier plan (ADR-0006).
 */
@Singleton
class LocationTracker @Inject constructor(@ApplicationContext private val context: Context) {
    private val _fixes = MutableSharedFlow<TrackFix>(extraBufferCapacity = 32)
    val fixes: SharedFlow<TrackFix> = _fixes

    private var profile: TrackingProfile? = null
    private var stopUpdates: (() -> Unit)? = null

    /** Vrai si seule la position approximative est accordée : les détections fines sont alors désactivées (R-LE-02). */
    val approximateOnly: Boolean
        get() = !granted(Manifest.permission.ACCESS_FINE_LOCATION) && granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    @Synchronized
    fun start(newProfile: TrackingProfile) {
        if (profile == newProfile && stopUpdates != null) return
        stop()
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return
        profile = newProfile
        val intervalMs = Tracking.intervalFor(newProfile, batteryPercent()).toMillis()
        stopUpdates = if (googleAvailable()) fused(newProfile, intervalMs) else platform(intervalMs)
    }

    @Synchronized
    fun stop() {
        stopUpdates?.invoke()
        stopUpdates = null
        profile = null
    }

    @SuppressLint("MissingPermission") // au moins une des deux autorisations est vérifiée dans start()
    private fun fused(profile: TrackingProfile, intervalMs: Long): () -> Unit {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val priority = when {
            approximateOnly -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            profile == TrackingProfile.EMERGENCY -> Priority.PRIORITY_HIGH_ACCURACY
            profile == TrackingProfile.TRIP -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            else -> Priority.PRIORITY_LOW_POWER
        }
        val request = LocationRequest.Builder(priority, intervalMs).setMinUpdateIntervalMillis(intervalMs / 2).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { _fixes.tryEmit(it.toFix()) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        return { client.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    private fun platform(intervalMs: Long): () -> Unit {
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { _fixes.tryEmit(it.toFix()) }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            .filter { it != LocationManager.GPS_PROVIDER || granted(Manifest.permission.ACCESS_FINE_LOCATION) }
        providers.forEach { runCatching { manager.requestLocationUpdates(it, intervalMs, 0f, listener, Looper.getMainLooper()) } }
        return { manager.removeUpdates(listener) }
    }

    private fun googleAvailable() = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun batteryPercent(): Int? {
        val intent: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else null
    }

    private fun Location.toFix() = TrackFix(
        point = GeoPoint(latitude, longitude),
        accuracyMeters = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        time = Instant.ofEpochMilli(time),
        speedMps = if (hasSpeed()) speed else null
    )
}
