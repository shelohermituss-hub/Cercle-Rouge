package com.cerclerouge.app.protection

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.cerclerouge.alert.Timer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alarmes système des minuteries d'alerte (vérification, escalade, expiration), en plus des coroutines : l'alarme réveille
 * l'application même après la mort du processus ou en mode Doze (R-AL-03).
 *
 * Sans l'autorisation d'alarmes exactes (❓ Q16 : acceptable pour Play ?), on utilise `setAndAllowWhileIdle`, inexacte mais
 * qui traverse le Doze. Tant que le service de premier plan tourne, la coroutine reste la voie exacte.
 */
@Singleton
class AlarmTimers @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun schedule(timer: Timer, at: Instant) {
        val pending = pending(timer, at.toEpochMilli(), create = true) ?: return
        // canScheduleExactAlarms n'existe qu'à partir d'Android 12 ; avant, les alarmes exactes sont libres.
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (exact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        }
    }

    fun cancel(timer: Timer) {
        // L'instant n'entre pas dans l'identité de l'alarme (une alarme par minuterie) : on la retrouve sans lui.
        pending(timer, 0L, create = false)?.let {
            manager.cancel(it)
            it.cancel()
        }
    }

    private fun pending(timer: Timer, atMillis: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, TimerReceiver::class.java).putExtra(EXTRA_TIMER, timer.name).putExtra(EXTRA_AT, atMillis)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, REQUEST_BASE + timer.ordinal, intent, flags)
    }

    companion object {
        const val EXTRA_TIMER = "timer"
        const val EXTRA_AT = "at"
        private const val REQUEST_BASE = 7100
    }
}
