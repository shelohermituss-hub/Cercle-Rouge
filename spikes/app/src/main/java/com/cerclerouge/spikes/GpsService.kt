package com.cerclerouge.spikes

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * S0.21 — Le suivi GPS tient-il pendant un vrai trajet (tap-tap, moto), avec quelle précision et quel coût ?
 *
 * Service de premier plan « location », une position toutes les 10 s, via Fused Location
 * (Google Play Services) ou LocationManager (repli sans Play Services).
 * Les positions restent dans le journal local du téléphone de test.
 */
class GpsService : Service() {

    private lateinit var log: SpikeLog
    private var fusedCallback: LocationCallback? = null
    private var managerListener: LocationListener? = null
    private var lastFixAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission") // vérifiée par MainActivity
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log = SpikeLog(this, LOG_NAME)
        if (fusedCallback != null || managerListener != null) return START_NOT_STICKY
        startForeground(NOTIF_ID, Notifications.foreground(this, "S0.21 · trajet en test"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

        val wantFused = intent?.getBooleanExtra(EXTRA_FUSED, true) ?: true
        val hasPlay = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
        val useFused = wantFused && hasPlay
        log.line("start", "fournisseur=${if (useFused) "fused" else "LocationManager"}", "play_services=$hasPlay",
            "batterie=${SpikeLog.battery(this)}", SpikeLog.deviceInfo(this))

        if (useFused) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
                .setMinUpdateIntervalMillis(INTERVAL_MS / 2)
                .build()
            fusedCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) { result.locations.forEach(::record) }
            }
            LocationServices.getFusedLocationProviderClient(this)
                .requestLocationUpdates(request, fusedCallback!!, Looper.getMainLooper())
        } else {
            val lm = getSystemService(LocationManager::class.java)
            managerListener = LocationListener(::record)
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, managerListener!!, Looper.getMainLooper())
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, INTERVAL_MS, 0f, managerListener!!, Looper.getMainLooper())
            }
        }
        return START_NOT_STICKY
    }

    private fun record(loc: Location) {
        val now = SystemClock.elapsedRealtime()
        val gap = if (lastFixAt == 0L) 0 else (now - lastFixAt) / 1000
        lastFixAt = now
        log.line(
            "position", loc.provider, "%.6f".format(java.util.Locale.ROOT, loc.latitude), "%.6f".format(java.util.Locale.ROOT, loc.longitude),
            "precision_m=${loc.accuracy.toInt()}", "vitesse_ms=${"%.1f".format(java.util.Locale.ROOT, loc.speed)}",
            "ecart_s=$gap", "batterie=${SpikeLog.battery(this)}", "en_charge=${SpikeLog.charging(this)}"
        )
    }

    override fun onDestroy() {
        fusedCallback?.let { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(it) }
        managerListener?.let { getSystemService(LocationManager::class.java).removeUpdates(it) }
        log.line("stop", "batterie=${SpikeLog.battery(this)}")
        super.onDestroy()
    }

    companion object {
        const val LOG_NAME = "S0.21-gps"
        const val EXTRA_FUSED = "fused"
        private const val NOTIF_ID = 21
        private const val INTERVAL_MS = 10_000L
    }
}
