package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_stations")
data class WeatherStationEntity(
    @PrimaryKey
    val id: String, // e.g. "sao_paulo", "bauru", "campinas"
    val name: String,
    val region: String,
    val lat: Double,
    val lon: Double,
    val currentTemp: Double,
    val minTemp: Double,
    val maxTemp: Double,
    val feelsLike: Double,
    val humidity: Int, // in %
    val pressure: Int, // in hPa
    val windSpeed: Double, // in km/h
    val windDirection: String, // e.g. "SE 14 km/h"
    val rainVolumeMm: Double, // in mm
    val rainProbability: Int, // in %
    val dbzReflectivity: Int, // Doppler radar reflectivity (dBZ)
    val weatherCondition: String, // e.g. "Pancadas de Chuva", "Céu Limpo"
    val iconType: String, // "rain", "storm", "cloudy", "sunny", "fog"
    val synopticSummary: String,
    val sunrise: String,
    val sunset: String,
    val uvIndex: Int,
    val aqi: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "hourly_forecasts")
data class HourlyForecastEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stationId: String,
    val hourText: String, // "14:00", "15:00"
    val temp: Double,
    val rainProbability: Int,
    val rainVolumeMm: Double,
    val condition: String,
    val iconType: String
)

@Entity(tableName = "daily_forecasts")
data class DailyForecastEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stationId: String,
    val dayOfWeek: String, // "Seg", "Ter", "Qua"
    val dateText: String, // "19/09"
    val minTemp: Double,
    val maxTemp: Double,
    val rainProbability: Int,
    val rainVolumeMm: Double,
    val condition: String,
    val iconType: String
)

@Entity(tableName = "weather_alerts")
data class WeatherAlertEntity(
    @PrimaryKey
    val id: String,
    val regionId: String,
    val regionName: String,
    val severity: String, // "ALERTA_VERMELHO", "ALERTA_LARANJA", "ALERTA_AMARELO", "AVISO_METEOROLOGICO"
    val title: String,
    val description: String,
    val radarStationSource: String, // "Radar IPMet Bauru", "Radar Pres. Prudente"
    val dbzPeak: Int,
    val timestamp: Long,
    val isAcknowledged: Boolean = false
)

@Entity(tableName = "climate_trends")
data class ClimateTrendEntity(
    @PrimaryKey
    val monthIndex: Int, // 1 to 12
    val monthName: String, // "Jan", "Fev", ...
    val avgTempCurrent: Double,
    val avgTempHistorical: Double,
    val rainCurrentMm: Double,
    val rainHistoricalMm: Double,
    val rainyDaysCurrent: Int,
    val rainyDaysHistorical: Int
)

@Entity(tableName = "region_subscriptions")
data class RegionSubscriptionEntity(
    @PrimaryKey
    val regionId: String,
    val regionName: String,
    val isSubscribed: Boolean = true,
    val alertThresholdMm: Int = 10, // mm/h threshold
    val notifySuddenDrop: Boolean = true,
    val notifyStorms: Boolean = true
)

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey
    val id: Int = 1,
    val selectedThemeKey: String = "oled_dark", // "oled_dark" (Padrão OLED Puro), "ipmet_cyan", "phosphor_green", "purple_storm", "amber_warn"
    val isEnergySaverEnabled: Boolean = false,
    val openWeatherApiKey: String = "",
    val weatherbitApiKey: String = "",
    val activeStationId: String = "sao_paulo",
    val isOfflineModeForced: Boolean = false
)

/** true quando a estação já recebeu ao menos uma atualização real (lastUpdated = 0 → nunca). */
fun WeatherStationEntity.hasRealData(): Boolean = lastUpdated > 0L

/** Temperatura formatada ou "—" quando não há dado real. */
fun WeatherStationEntity.tempLabel(): String = if (hasRealData()) "${currentTemp.toInt()}°C" else "—"
