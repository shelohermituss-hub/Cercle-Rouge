package com.cerclerouge.app.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cerclerouge.alert.Timer
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Reçoit l'alarme système d'une minuterie et la transmet au moteur ; le contrôleur restaure d'abord l'état persisté si besoin. */
@AndroidEntryPoint
class TimerReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: ProtectionController

    override fun onReceive(context: Context, intent: Intent) {
        val timer = runCatching { Timer.valueOf(intent.getStringExtra(AlarmTimers.EXTRA_TIMER).orEmpty()) }.getOrNull() ?: return
        val at = Instant.ofEpochMilli(intent.getLongExtra(AlarmTimers.EXTRA_AT, 0L))
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                controller.timerElapsed(timer, at)
            } finally {
                pending.finish()
            }
        }
    }
}
