package com.cerclerouge.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.cerclerouge.privacy.log.EventLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Surveille la batterie et la température (S5.13, R-AE-05) et met l'écoute en mode dégradé : batterie sous 10 % hors charge,
 * ou surchauffe sévère. Reprise automatique au-dessus de 15 % (hystérésis) ou en charge, et quand le téléphone refroidit.
 */
@Singleton
class DeviceHealth @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val audio: AudioListener,
    private val log: EventLog
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lowBattery = false
    private var hot = false
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    if (level < 0) return
                    val pct = level * 100 / scale
                    update(battery = if (lowBattery) !charging && pct < RESUME_PCT else !charging && pct < LOW_PCT, heat = hot)
                }
            },
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        context.getSystemService(PowerManager::class.java)?.addThermalStatusListener(ContextCompat.getMainExecutor(context)) { status ->
            update(battery = lowBattery, heat = status >= PowerManager.THERMAL_STATUS_SEVERE)
        }
    }

    @Synchronized
    private fun update(battery: Boolean, heat: Boolean) {
        val before = lowBattery || hot
        lowBattery = battery
        hot = heat
        val now = lowBattery || hot
        if (before == now) return
        audio.setDegraded(now)
        scope.launch { log.append("audio", if (now) "degraded ${if (hot) "heat" else "battery"}" else "degraded_end") }
    }

    private companion object {
        const val LOW_PCT = 10
        const val RESUME_PCT = 15
    }
}
