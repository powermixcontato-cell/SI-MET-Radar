package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Pede ao widget para reler o banco depois de uma atualização no app (antes só a cada 30 min). */
object WidgetUpdater {
    fun requestUpdate(context: Context) {
        try {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, IpmetWeatherWidgetProvider::class.java))
            if (ids == null || ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, IpmetWeatherWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        } catch (e: Exception) {
            android.util.Log.w("WidgetUpdater", "Falha ao atualizar widget", e)
        }
    }
}
