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
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.cerclerouge.app.MainActivity
import com.cerclerouge.app.R
import com.cerclerouge.app.alert.AlertDispatcher
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
 * Types `location` (Sprint 4) et `microphone` (Sprint 5), plus `specialUse` (Android 14+) pour les alertes sans capteur ;
 * les déclarations Play correspondantes sont dans android-permissions-play.md.
 * Désactiver demande le PIN : le bouton ouvre donc l'application, il ne coupe jamais la protection directement (R-SM-04).
 */
@AndroidEntryPoint
class ProtectionService : Service() {
    @Inject lateinit var controller: ProtectionController

    @Inject lateinit var dispatcher: AlertDispatcher

    @Inject lateinit var monitor: com.cerclerouge.app.device.DeviceMonitor

    @Inject lateinit var brain: RiskBrain
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Retour du réseau : la file d'envoi repart tout de suite (R-NET-06), sans attendre la fin de son attente. */
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(n: Network) {
            scope.launch(Dispatchers.Default) { dispatcher.onNetworkBack(controller.state.value.alert) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW)
        )
        startForegroundTyped()
        monitor.start()
        brain.start()
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(network)
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
     * Type de service selon la version d'Android et les autorisations. Depuis Android 14, démarrer un type `location` ou
     * `microphone` sans l'autorisation correspondante lève une SecurityException : on n'ajoute un type qu'avec son autorisation.
     * `microphone` ne peut être demandé que l'application visible (action de l'utilisateur) : relancé en arrière-plan (redémarrage
     * du processus), le service retombe sur les autres types et l'écoute reste coupée (R-SM-08). Le type « special use »
     * n'existe qu'à partir d'Android 14 ; avant, un type inconnu ferait planter le démarrage.
     */
    private fun startForegroundTyped() {
        val notification = build(alert = false)
        val location = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        var base = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) base = base or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (location) base = base or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        val withMic = if (mic &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            base or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            base
        }
        val started = runCatching { startForeground(NOTIFICATION_ID, notification, withMic) }
        if (started.isFailure) {
            // Arrière-plan : le micro est refusé par Android. On garde le service (position, minuteries, alertes) sans lui.
            startForeground(NOTIFICATION_ID, notification, base)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Chaque démarrage demandé depuis l'écran met à jour les types (ex. micro accordé après le premier démarrage).
        if (intent != null) startForegroundTyped()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(network) }
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
