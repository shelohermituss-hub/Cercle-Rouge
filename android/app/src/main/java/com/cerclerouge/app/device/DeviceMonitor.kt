package com.cerclerouge.app.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.cerclerouge.sensor.DeviceStatus
import com.cerclerouge.sensor.NetworkKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * État de l'appareil en direct (batterie, réseau, GPS, mode avion, économie d'énergie, SIM), sans permission supplémentaire.
 * Démarré par l'activité et par le service de protection ; une seule source pour l'accueil, les alertes et les preuves.
 */
@Singleton
class DeviceMonitor @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status
    private var started = false
    private var battery: Pair<Int?, Boolean> = null to false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    battery = (if (level < 0) null else (level * 100 / scale).coerceIn(0, 100)) to
                        (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
                }
                refresh()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        context.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) {
                    refresh()
                }

                override fun onLost(n: Network) {
                    refresh()
                }
            }
        )
        refresh()
    }

    /** Relit tout l'état (appelé à chaque changement, et à la demande avant un envoi). */
    @Synchronized
    fun refresh(): DeviceStatus {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val kind = when {
            caps == null -> NetworkKind.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.CELLULAR
            else -> NetworkKind.OTHER
        }
        val location = context.getSystemService(LocationManager::class.java)
        val status = DeviceStatus(
            batteryPct = battery.first,
            charging = battery.second,
            network = kind,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            gpsEnabled = location?.isLocationEnabled == true,
            airplaneMode = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0,
            powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true,
            simReady = context.getSystemService(TelephonyManager::class.java)?.simState == TelephonyManager.SIM_STATE_READY
        )
        _status.value = status
        return status
    }
}
