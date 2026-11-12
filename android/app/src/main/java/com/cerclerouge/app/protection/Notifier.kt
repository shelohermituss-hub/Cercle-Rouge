package com.cerclerouge.app.protection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.cerclerouge.alert.NoticeKind
import com.cerclerouge.alert.VibrationPattern
import com.cerclerouge.app.MainActivity
import com.cerclerouge.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifications et vibrations locales. Sur l'écran de verrouillage, rien de sensible n'apparaît :
 * la version publique dit seulement « Cercle Rouge » (R-SEC-06).
 */
@Singleton
class Notifier @Inject constructor(@ApplicationContext private val context: Context, private val listen: ListenSettings) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    init {
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_STATUS, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_ALERT, context.getString(R.string.channel_alert), NotificationManager.IMPORTANCE_HIGH)
            )
        )
    }

    fun notice(kind: NoticeKind) {
        // areNotificationsEnabled couvre Android 10–12 (pas de permission) et 13+ (permission POST_NOTIFICATIONS).
        if (!nm.areNotificationsEnabled()) return
        // Mode discret : jamais de son ni de bannière, texte neutre sans le mot « alerte » ni « urgence » (R-HOME-03).
        val discreet = listen.prefs.value.discreet
        val (channel, text) = if (discreet) {
            CH_STATUS to R.string.service_text_armed
        } else {
            when (kind) {
                NoticeKind.EMERGENCY -> CH_ALERT to R.string.notif_emergency
                NoticeKind.ALERT_SENT -> CH_ALERT to R.string.notif_alert_sent
                NoticeKind.PROTECTION_DOWNGRADED -> CH_STATUS to R.string.notif_downgraded
                NoticeKind.PROTECTION_INTERRUPTED -> CH_STATUS to R.string.notif_interrupted
            }
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val public = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .build()
        nm.notify(
            kind.ordinal,
            Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
                .build()
        )
    }

    /** Vibrations codées (module produit 5) : 3 courtes = vérification, 1 longue = envoyé, 2 courtes = clôturé. */
    fun vibrate(pattern: VibrationPattern) {
        val timings = when (pattern) {
            VibrationPattern.VERIFY -> longArrayOf(0, 120, 120, 120, 120, 120)
            VibrationPattern.SENT -> longArrayOf(0, 600)
            VibrationPattern.CLOSED -> longArrayOf(0, 120, 120, 120)
        }
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") // seule API disponible sur Android 10 et 11
            context.getSystemService(Vibrator::class.java)
        }
        vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
    }

    private companion object {
        const val CH_STATUS = "status"
        const val CH_ALERT = "alert"
    }
}
