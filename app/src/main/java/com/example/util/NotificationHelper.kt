package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

object NotificationHelper {

    private const val CHANNEL_ID_ALERTS = "ipmet_radar_alerts"
    private const val CHANNEL_NAME_ALERTS = "Alertas de Chuva e Tempestades IPMet"
    private const val CHANNEL_DESC_ALERTS = "Avisos de tempestades, chuva forte e refletividade no radar de SP"

    private const val CHANNEL_ID_CHANGES = "ipmet_weather_changes"
    private const val CHANNEL_NAME_CHANGES = "Mudanças Bruscas no Tempo"
    private const val CHANNEL_DESC_CHANGES = "Alertas de queda súbita de temperatura e rajadas de vento"

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alertsChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                CHANNEL_NAME_ALERTS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC_ALERTS
                enableVibration(true)
            }

            val changesChannel = NotificationChannel(
                CHANNEL_ID_CHANGES,
                CHANNEL_NAME_CHANGES,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC_CHANGES
            }

            notificationManager.createNotificationChannel(alertsChannel)
            notificationManager.createNotificationChannel(changesChannel)
        }
    }

    fun showWeatherAlertNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        regionName: String,
        isSevere: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = if (isSevere) CHANNEL_ID_ALERTS else CHANNEL_ID_CHANGES

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⛈️ $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("[$regionName] $message\nFonte: Radares Meteorológicos IPMet UNESP (Bauru & Pres. Prudente)"))
            .setPriority(if (isSevere) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }
}
