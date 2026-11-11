package com.cerclerouge.app.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Après un redémarrage du téléphone, restaure l'état : une protection armée devient « interrompue » avec une notification
 * « Réarmer » (Android interdit de relancer micro et position depuis l'arrière-plan, R-SM-08), une alerte rearme ses minuteries.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: ProtectionController

    @Inject lateinit var purge: com.cerclerouge.app.evidence.EvidencePurge

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                controller.ensureRestored()
                purge.schedule()
                purge.run() // rattrapage des suppressions manquées pendant que le téléphone était éteint (R-EVD-03)
            } finally {
                pending.finish()
            }
        }
    }
}
