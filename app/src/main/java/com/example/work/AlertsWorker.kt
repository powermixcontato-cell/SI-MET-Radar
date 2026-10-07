package com.example.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.AppSettings
import com.example.data.repository.WeatherRepository
import com.example.domain.HazardRules
import com.example.domain.HazardSeverity
import com.example.util.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * v5.1 — verificação periódica (1 h, com rede) dos avisos oficiais do INMET.
 * Notifica apenas avisos NOVOS de severidade Laranja/Vermelho (Perigo / Grande Perigo) que atinjam o estado
 * selecionado e se enquadrem em Enchentes, Ciclones e vendavais ou Granizo. Opcional (desligado por padrão).
 */
class AlertsWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = AppSettings(applicationContext)
        if (!settings.alertNotificationsEnabled) return Result.success()
        val db = AppDatabase.getInstance(applicationContext)
        val repo = WeatherRepository(dao = db.weatherDao(), keyStore = com.example.data.secure.SecureApiKeyStore(applicationContext))
        repo.refreshInmetAlerts()
        val state = settings.selectedState
        val all = db.weatherDao().getAllAlertsSync()
        val hazards = HazardRules.fromInmet(all, state).filter { it.severity.rank >= HazardSeverity.LARANJA.rank }
        val notified = settings.notifiedAlertIds
        val activeIds = all.map { it.id }.toSet()
        val fresh = hazards.groupBy { it.id.substringBefore('#') }.filterKeys { it !in notified }
        fresh.forEach { (baseId, list) ->
            val top = list.maxBy { it.severity.rank }
            NotificationHelper.initNotificationChannels(applicationContext)
            NotificationHelper.showWeatherAlertNotification(
                context = applicationContext,
                notificationId = baseId.hashCode(),
                title = "${list.joinToString(" / ") { it.category.label }} — ${top.severity.label}",
                message = "${top.title}. Validade: ${top.validity}",
                regionName = top.area,
                isSevere = true,
                source = top.source,
                emoji = list.first().category.emoji
            )
        }
        // Mantém só IDs ainda ativos + os recém-notificados
        settings.notifiedAlertIds = (notified.filter { it in activeIds } + fresh.keys).toSet()
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "simet_alerts_check"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<AlertsWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
