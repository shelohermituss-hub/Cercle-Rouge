package com.cerclerouge.spikes

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.telephony.SmsManager
import android.telephony.SmsMessage
import androidx.core.content.ContextCompat

/**
 * S0.20 — Un SMS d'alerte avec accents arrive-t-il vite chez Digicel et Natcom ?
 *
 * Envoi multipart via SmsManager, avec accusés d'envoi (réseau) et de remise (téléphone destinataire)
 * horodatés pour chaque segment.
 */
class SmsSpike(private val context: Context, private val report: (String) -> Unit) {

    private val log = SpikeLog(context, LOG_NAME)
    private var receiver: BroadcastReceiver? = null

    fun send(number: String, text: String) {
        val sms = context.getSystemService(SmsManager::class.java)
        val parts = sms.divideMessage(text)
        val encoding = SmsMessage.calculateLength(text, false) // [segments, unités utilisées, restantes, encodage]
        val id = System.currentTimeMillis()
        val t0 = SystemClock.elapsedRealtime()
        val sentAt = arrayOfNulls<Long>(parts.size)
        val deliveredAt = arrayOfNulls<Long>(parts.size)

        receiver?.let { context.unregisterReceiver(it) }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val part = intent.getIntExtra(EXTRA_PART, -1)
                if (intent.getLongExtra(EXTRA_ID, 0) != id || part !in parts.indices) return
                val ms = SystemClock.elapsedRealtime() - t0
                when (intent.action) {
                    ACTION_SENT -> {
                        sentAt[part] = ms
                        val ok = resultCode == Activity.RESULT_OK
                        log.line("envoye", "segment=${part + 1}/${parts.size}", "ms=$ms", "ok=$ok", "code=$resultCode")
                        report("Segment ${part + 1}/${parts.size} envoyé au réseau en $ms ms (ok=$ok)")
                    }
                    ACTION_DELIVERED -> {
                        deliveredAt[part] = ms
                        log.line("remis", "segment=${part + 1}/${parts.size}", "ms=$ms")
                        report("Segment ${part + 1}/${parts.size} remis au destinataire en $ms ms")
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter().apply { addAction(ACTION_SENT); addAction(ACTION_DELIVERED) },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        fun pending(action: String, part: Int) = PendingIntent.getBroadcast(
            context, (id % 100_000).toInt() * 10 + part * 2 + if (action == ACTION_SENT) 0 else 1,
            Intent(action).setPackage(context.packageName).putExtra(EXTRA_ID, id).putExtra(EXTRA_PART, part),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val sent = ArrayList(parts.indices.map { pending(ACTION_SENT, it) })
        val delivered = ArrayList(parts.indices.map { pending(ACTION_DELIVERED, it) })

        // Seul le préfixe international est journalisé : pas de numéro complet dans les mesures.
        log.line("depart", "prefixe=${number.take(4)}", "segments=${parts.size}", "encodage=${if (encoding[3] == SmsMessage.ENCODING_16BIT) "UCS-2" else "GSM-7"}",
            "caracteres=${text.length}", SpikeLog.deviceInfo(context))
        report("Envoi de ${parts.size} segment(s), encodage ${if (encoding[3] == SmsMessage.ENCODING_16BIT) "UCS-2 (accents)" else "GSM-7"}…")
        sms.sendMultipartTextMessage(number, null, parts, sent, delivered)
    }

    fun release() { receiver?.let { context.unregisterReceiver(it) }; receiver = null }

    companion object {
        const val LOG_NAME = "S0.20-sms"
        private const val ACTION_SENT = "com.cerclerouge.spikes.SMS_SENT"
        private const val ACTION_DELIVERED = "com.cerclerouge.spikes.SMS_DELIVERED"
        private const val EXTRA_ID = "id"
        private const val EXTRA_PART = "part"

        val SAMPLE_TEXT =
            "TEST Cercle Rouge — Naïka a peut-être besoin d'aide. Raison : déviation de trajet. " +
                "Heure : 22:14. Position : 18.5392,-72.3364 (±12 m). Suivi : https://cr.example/t/Ab3x9 " +
                "Kreyòl : Naïka ka bezwen èd. Rele l oswa rele 114."
    }
}
