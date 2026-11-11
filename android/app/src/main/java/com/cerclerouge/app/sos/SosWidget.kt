package com.cerclerouge.app.sos

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.cerclerouge.app.R

/** Widget d'écran d'accueil : un gros bouton SOS rouge (S2.13). Même chemin que la tuile : compte à rebours de 5 s. */
class SosWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_sos).apply {
            setOnClickPendingIntent(R.id.widget_sos_root, SosIntents.pending(context, 12))
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}
