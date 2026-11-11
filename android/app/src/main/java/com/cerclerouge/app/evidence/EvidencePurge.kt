package com.cerclerouge.app.evidence

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.cerclerouge.privacy.Settings
import com.cerclerouge.privacy.log.EventLog
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Suppression automatique des preuves (S6.08, R-EVD-03) : alarme inexacte toutes les heures **et** rattrapage au démarrage
 * de l'app et du téléphone. Une alarme système plutôt que WorkManager : même garantie ici, sans dépendance de plus (ADR-0021).
 * Idempotent : la rejouer ne change rien (R-EE-05).
 */
@Singleton
class EvidencePurge @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: EvidenceStore,
    private val settings: Settings,
    private val log: EventLog,
    private val clock: Clock
) {
    suspend fun run() {
        val n = store.purgeExpired(settings.retention(), clock.instant())
        if (n > 0) log.append("evidence", "purged alerts=$n")
    }

    fun schedule() {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, PurgeReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarms.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HOUR,
            AlarmManager.INTERVAL_HOUR,
            pending
        )
    }

    private companion object {
        const val REQUEST = 4200
    }
}

@AndroidEntryPoint
class PurgeReceiver : BroadcastReceiver() {
    @Inject lateinit var purge: EvidencePurge

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                purge.run()
            } finally {
                pending.finish()
            }
        }
    }
}
