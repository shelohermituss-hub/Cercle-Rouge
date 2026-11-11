package com.cerclerouge.app.alert

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cerclerouge.app.contacts.OutboxRepository
import com.cerclerouge.privacy.log.EventLog
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Rapports d'envoi et de remise des SMS (S2.07). Un échec d'envoi remet l'envoi dans la file ; tout est journalisé SANS le numéro
 * ni le texte (journal local, chaîné).
 */
@AndroidEntryPoint
class SmsStatusReceiver : BroadcastReceiver() {
    @Inject lateinit var outbox: OutboxRepository

    @Inject lateinit var log: EventLog

    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val ok = resultCode == Activity.RESULT_OK
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val short = key.substringBefore('|').take(6) + "/" + key.substringAfter('|').substringBefore('|').take(6)
                when (intent.action) {
                    ACTION_SENT -> {
                        log.append("sms", (if (ok) "sent " else "send_failed code=$resultCode ") + short)
                        if (!ok) outbox.reopen(key, clock.instant())
                    }
                    ACTION_DELIVERED -> log.append("sms", (if (ok) "delivered " else "not_delivered ") + short)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SENT = "com.cerclerouge.app.SMS_SENT"
        const val ACTION_DELIVERED = "com.cerclerouge.app.SMS_DELIVERED"
        const val EXTRA_KEY = "key"
    }
}
