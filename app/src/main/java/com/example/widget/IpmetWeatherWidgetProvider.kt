package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class IpmetWeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // goAsync(): mantém o BroadcastReceiver vivo até a consulta ao banco terminar
        // (sem isso o processo pode ser encerrado antes de atualizar o widget).
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
          try {
            val db = AppDatabase.getInstance(context)
            val prefs = db.weatherDao().getUserPreferencesSync()
            val stationId = prefs?.activeStationId ?: "sao_paulo"
            val station = db.weatherDao().getStationByIdSync(stationId)
                ?: db.weatherDao().getStationByIdSync("sao_paulo")

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_weather)
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                // lastUpdated = 0: estação ainda sem dado real (antes mostrava "0.0°C")
                if (station != null && station.lastUpdated > 0L) {
                    views.setTextViewText(R.id.widget_city, "${station.name} (${station.region})")
                    views.setTextViewText(R.id.widget_temp, String.format(Locale("pt", "BR"), "%.1f°C", station.currentTemp))
                    views.setTextViewText(R.id.widget_condition, station.weatherCondition)
                    views.setTextViewText(
                        R.id.widget_rain_alert,
                        if (station.rainProbability > 50) {
                            "🌧️ Chuva: ${station.rainProbability}% | Radar: ${station.dbzReflectivity} dBZ"
                        } else {
                            "🌤️ Radar IPMet: Sem chuva severa no momento (${station.dbzReflectivity} dBZ)"
                        }
                    )
                } else {
                    station?.let { views.setTextViewText(R.id.widget_city, "${it.name} (${it.region})") }
                    views.setTextViewText(R.id.widget_temp, context.getString(R.string.widget_placeholder_temp))
                    views.setTextViewText(R.id.widget_condition, context.getString(R.string.widget_placeholder_condition))
                    views.setTextViewText(R.id.widget_rain_alert, context.getString(R.string.widget_placeholder_rain))
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
          } finally {
            pendingResult.finish()
          }
        }
    }
}
