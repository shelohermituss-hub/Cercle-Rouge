package com.cerclerouge.app.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.cerclerouge.sensor.MotionDetector
import com.cerclerouge.sensor.MotionEvent
import com.cerclerouge.sensor.MotionSample
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Accéléromètre et gyroscope à 50 Hz au plus (`SENSOR_DELAY_GAME`, R-SE-01), seulement protection active. Les mesures passent
 * dans [MotionDetector] et ne sont jamais écrites (R-SE-03) ; un capteur absent désactive sa détection sans erreur (R-SE-04).
 */
@Singleton
class MotionSensors @Inject constructor(@param:ApplicationContext private val context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val _events = MutableSharedFlow<MotionEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MotionEvent> = _events
    private var detector: MotionDetector? = null
    private var gyro = FloatArray(3)
    private val bootOffset = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()

    /** Vrai si l'accéléromètre existe (sans lui, aucune détection de mouvement). */
    val available: Boolean get() = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    @Synchronized
    fun start() {
        if (detector != null || manager == null) return
        val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        detector = MotionDetector()
        manager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    @Synchronized
    fun stop() {
        if (detector == null) return
        manager?.unregisterListener(this)
        detector = null
        gyro = FloatArray(3)
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> gyro = e.values.copyOf(3)
            Sensor.TYPE_ACCELEROMETER -> {
                val d = synchronized(this) { detector } ?: return
                val t = bootOffset + e.timestamp / 1_000_000
                val g = gyro
                d.push(MotionSample(t, e.values[0], e.values[1], e.values[2], g[0], g[1], g[2])).forEach { _events.tryEmit(it) }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
}
