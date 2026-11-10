package com.cerclerouge.spikes

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.app.usage.UsageStatsManager
import java.io.File
import java.time.Instant

/**
 * Journal CSV d'un prototype, dans le stockage privé de l'app (files/logs/).
 * Rien ne sort du téléphone sauf si l'utilisateur appuie sur « Partager les résultats ».
 */
class SpikeLog(context: Context, name: String) {
    private val dir = File(context.filesDir, "logs").apply { mkdirs() }
    val file = File(dir, "$name.csv")

    @Synchronized
    fun line(vararg fields: Any?) {
        file.appendText(
            (listOf(Instant.now().toString()) + fields.map { it?.toString()?.replace(',', ';') ?: "" })
                .joinToString(",") + "\n"
        )
    }

    fun tail(n: Int = 8): String =
        if (file.exists()) file.readLines().takeLast(n).joinToString("\n") else "(aucune mesure)"

    fun clear() { file.delete() }

    companion object {
        fun battery(context: Context): Int =
            context.getSystemService(BatteryManager::class.java)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        fun charging(context: Context): Boolean =
            context.getSystemService(BatteryManager::class.java).isCharging

        /** Contexte de l'appareil : indispensable pour comparer les fabricants (Tecno, Infinix, Samsung…). */
        fun deviceInfo(context: Context): String {
            val pm = context.getSystemService(PowerManager::class.java)
            val bucket = context.getSystemService(UsageStatsManager::class.java).appStandbyBucket
            return "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" +
                " · optimisation batterie ignorée=${pm.isIgnoringBatteryOptimizations(context.packageName)}" +
                " · économie d'énergie=${pm.isPowerSaveMode} · bucket=$bucket"
        }
    }
}
