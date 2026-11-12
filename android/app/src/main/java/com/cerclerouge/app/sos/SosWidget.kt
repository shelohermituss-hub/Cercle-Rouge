package com.cerclerouge.app.sos

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.cerclerouge.app.R

/** Widget d'écran d'accueil : deux boutons : SOS rouge (compte à rebours de 5 s, S2.13) et ON pour armer la protection (PDF 8.1). */
class SosWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_sos).apply {
            setOnClickPendingIntent(R.id.widget_sos_root, SosIntents.pending(context, 12))
            setOnClickPendingIntent(R.id.widget_arm_root, SosIntents.armPending(context, 15))
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}
