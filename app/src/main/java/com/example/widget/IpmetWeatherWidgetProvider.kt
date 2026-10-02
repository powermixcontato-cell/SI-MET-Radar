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

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            val prefs = db.weatherDao().getUserPreferencesSync()
            val stationId = prefs?.activeStationId ?: "sao_paulo"
            val station = db.weatherDao().getStationByIdSync(stationId)
                ?: db.weatherDao().getStationByIdSync("sao_paulo")

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_weather)
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                if (station != null) {
                    views.setTextViewText(R.id.widget_city, "${station.name} (${station.region})")
                    views.setTextViewText(R.id.widget_temp, "${station.currentTemp}°C")
                    views.setTextViewText(R.id.widget_condition, station.weatherCondition)
                    views.setTextViewText(
                        R.id.widget_rain_alert,
                        if (station.rainProbability > 50) {
                            "🌧️ Chuva: ${station.rainProbability}% | Radar: ${station.dbzReflectivity} dBZ"
                        } else {
                            "🌤️ Radar IPMet: Sem chuva severa no momento (${station.dbzReflectivity} dBZ)"
                        }
                    )
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
