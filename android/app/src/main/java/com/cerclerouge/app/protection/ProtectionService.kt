package com.cerclerouge.app.protection

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.cerclerouge.app.MainActivity
import com.cerclerouge.app.R
import com.cerclerouge.app.sos.SosIntents
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Service de premier plan unique : il garde l'application vivante tant que la protection est active et affiche la notification
 * persistante [SOS] [Désactiver] (S2.04). Il s'arrête tout seul quand la protection repasse à OFF.
 *
 * ⚠ Type `specialUse` pour le Sprint 2 : ce service ne capte encore ni micro ni position. Il passera aux types `microphone` et
 * `location` avec les moteurs (Sprints 4 et 5) ; les déclarations Play correspondantes sont dans android-permissions-play.md.
 * Désactiver demande le PIN : le bouton ouvre donc l'application, il ne coupe jamais la protection directement (R-SM-04).
 */
@AndroidEntryPoint
class ProtectionService : Service() {
    @Inject lateinit var controller: ProtectionController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW)
        )
        startForegroundTyped()
        scope.launch {
            controller.ensureRestored()
            controller.state.map { it.protection }.collect { p ->
                if (p == com.cerclerouge.domain.ProtectionState.OFF) {
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(alert = p.isAlert))
                }
            }
        }
    }

    /**
     * Type de service selon la version d'Android et les autorisations. Depuis Android 14, démarrer un service de type `location`
     * sans autorisation de position lève une SecurityException : on n'ajoute le type qu'avec l'autorisation. Le type « special use »
     * n'existe qu'à partir d'Android 14 ; avant, un type inconnu ferait planter le démarrage.
     */
    private fun startForegroundTyped() {
        val notification = build(alert = false)
        val location = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val type = if (location) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                startForeground(NOTIFICATION_ID, notification, type)
            }
            location -> startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else -> startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun build(alert: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val sos = SosIntents.pending(this, 13) // compte à rebours de 5 s, annulable : pas d'envoi direct
        // Écran verrouillé : version publique neutre, sans le mot « protection » ni « alerte » (R-SEC-06).
        val public = Notification.Builder(
            this,
            CHANNEL
        ).setSmallIcon(R.drawable.ic_notification).setContentTitle(getString(R.string.app_name)).build()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(if (alert) R.string.service_text_alert else R.string.service_text_armed))
            .setContentIntent(open)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .addAction(Notification.Action.Builder(null, getString(R.string.service_action_sos), sos).build())
            .addAction(Notification.Action.Builder(null, getString(R.string.service_action_disable), open).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "protection_service"
        private const val NOTIFICATION_ID = 4100

        /** À appeler depuis l'application au premier plan (Android 12+ interdit de démarrer un service depuis l'arrière-plan). */
        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, ProtectionService::class.java))
    }
}
