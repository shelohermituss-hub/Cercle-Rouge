package com.cerclerouge.app.alert

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SmsResult {
    /** Remis à la radio : l'opérateur confirmera l'envoi, puis la remise, par les rapports. */
    data object Sent : SmsResult

    /** Pas de permission d'envoi : la messagerie est ouverte avec le SMS pré-rempli, un appui sur « envoyer » suffit. */
    data object NeedsUserAction : SmsResult

    data class Failed(val reason: String) : SmsResult
}

/**
 * Canal SMS direct (S2.07, R-ALR-04 : local d'abord, sans serveur). Envoi multipart avec rapports d'envoi et de remise ; si la
 * permission `SEND_SMS` est refusée ou absente (version Google Play sans l'exception, ADR-0005), SMS pré-rempli dans la messagerie.
 */
@Singleton
class SmsSender @Inject constructor(@ApplicationContext private val context: Context) {
    /** Vrai si l'app peut envoyer elle-même (permission SEND_SMS accordée). */
    val canSendDirect: Boolean
        get() = context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun send(jobKey: String, phone: String, text: String): SmsResult {
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return prefilled(phone, text)
        return runCatching {
            val manager = context.getSystemService(SmsManager::class.java)
            val parts = manager.divideMessage(text)
            val sent = parts.indices.map { report(SmsStatusReceiver.ACTION_SENT, jobKey, it) }
            val delivered = parts.indices.map { report(SmsStatusReceiver.ACTION_DELIVERED, jobKey, it) }
            manager.sendMultipartTextMessage(phone, null, parts, ArrayList(sent), ArrayList(delivered))
            SmsResult.Sent
        }.getOrElse { SmsResult.Failed(it.javaClass.simpleName) }
    }

    private fun prefilled(phone: String, text: String): SmsResult = runCatching {
        val intent = Intent(Intent.ACTION_SENDTO, "smsto:$phone".toUri()).putExtra("sms_body", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        SmsResult.NeedsUserAction
    }.getOrElse { SmsResult.Failed(it.javaClass.simpleName) }

    private fun report(action: String, key: String, part: Int): PendingIntent {
        val intent = Intent(action).setPackage(context.packageName).putExtra(SmsStatusReceiver.EXTRA_KEY, key)
        return PendingIntent.getBroadcast(
            context,
            key.hashCode() * 31 + part + action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
