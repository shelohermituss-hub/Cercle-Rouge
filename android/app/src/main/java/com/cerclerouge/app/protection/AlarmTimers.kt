package com.cerclerouge.app.protection

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cerclerouge.alert.Timer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alarmes système des minuteries d'alerte (vérification, escalade, expiration), en plus des coroutines : l'alarme réveille
 * l'application même après la mort du processus ou en mode Doze (R-AL-03).
 *
 * Volontairement `setAndAllowWhileIdle` (inexacte, traverse le Doze) et jamais `setExactAndAllowWhileIdle` : cette dernière exige
 * `SCHEDULE_EXACT_ALARM` ou `USE_EXACT_ALARM`, dont l'acceptation par Play est ouverte (❓ Q16). La voie exacte est la coroutine du
 * service de premier plan ; l'alarme n'est que le filet si le processus meurt.
 */
@Singleton
class AlarmTimers @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun schedule(timer: Timer, at: Instant) {
        val pending = pending(timer, at.toEpochMilli(), create = true) ?: return
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
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
