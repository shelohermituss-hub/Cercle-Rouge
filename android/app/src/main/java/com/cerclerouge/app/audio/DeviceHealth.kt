package com.cerclerouge.app.audio

import android.content.Context
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
    private val log: EventLog,
    private val monitor: com.cerclerouge.app.device.DeviceMonitor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lowBattery = false
    private var hot = false
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        monitor.start()
        scope.launch {
            monitor.status.collect { st ->
                val pct = st.batteryPct ?: return@collect
                val low = if (lowBattery) !st.charging && pct < RESUME_PCT else !st.charging && pct < LOW_PCT
                update(battery = low, heat = hot)
            }
        }
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
