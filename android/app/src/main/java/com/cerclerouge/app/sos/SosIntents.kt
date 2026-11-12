package com.cerclerouge.app.sos

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cerclerouge.app.MainActivity

/**
 * SOS depuis la tuile, le widget ou la notification (S2.13). Jamais d'envoi direct : on ouvre l'application sur le compte à
 * rebours de 5 s, annulable, pour qu'un effleurement accidentel n'alerte personne (docs/01-etats-et-escalade.md). L'activité
 * s'affiche par-dessus l'écran verrouillé.
 */
object SosIntents {
    const val EXTRA_SOS = "com.cerclerouge.app.EXTRA_SOS"
    const val EXTRA_ARM = "com.cerclerouge.app.EXTRA_ARM"

    /** Armer la protection depuis la tuile ou le widget : l'application s'ouvre et demande les autorisations en contexte. */
    fun armIntent(context: Context): Intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_ARM, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun armPending(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        armIntent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun launch(context: Context): Intent = Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_SOS, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun pending(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(context, requestCode, launch(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
