package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidade para armazenar e cachear dados ao vivo e previsões do CIIAGRO
 * (Centro Integrado de Informações Agrometeorológicas / IAC - SP)
 */
@Entity(tableName = "ciiagro_records")
data class CiiagroRecordEntity(
    @PrimaryKey
    val stationId: String, // e.g. "barretos", "bauru", "campinas"
    val municipality: String,
    val airTemp: Double,
    val tempMin: Double,
    val tempMax: Double,
    val relativeHumidity: Int,
    val rainAccumulatedMm: Double,
    val et0MmDay: Double, // Evapotranspiração de referência Penman-Monteith / Hargreaves
    val windSpeedKmH: Double,
    val solarRadiationMj: Double,
    val soilWaterDeficitRisk: String, // "Sem Déficit", "Atenção Leve", "Déficit Moderado", "Déficit Crítico"
    val cropManagementRecommendation: String,
    val forecastRain7DaysMm: Double,
    val liveDataSource: String = "CIIAGRO / IAC - SP",
    val lastUpdated: Long = System.currentTimeMillis()
)
