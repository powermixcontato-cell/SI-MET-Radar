package com.example.util

import android.content.Context
import android.content.Intent
import com.example.data.local.entity.WeatherStationEntity

object ShareHelper {

    fun shareWeatherStatus(context: Context, station: WeatherStationEntity) {
        val message = """
            🛰️ *SI Met RADAR • Previsão do Tempo & Vento SP*
            📍 *${station.name} - ${station.region}*
            
            🌡️ Temperatura: *${station.currentTemp}°C* (Sensação: ${station.feelsLike}°C)
            ⛅ Condição: ${station.weatherCondition}
            📊 Mín: ${station.minTemp}°C / Máx: ${station.maxTemp}°C
            💧 Umidade: ${station.humidity}% | Vento: ${station.windDirection}
            🌧️ Probabilidade de Chuva: *${station.rainProbability}%* (${station.rainVolumeMm} mm)
            📡 Ecos de Radar Doppler: *${station.dbzReflectivity} dBZ*
            
            ⚠️ *Sinopse Meteorológica:*
            ${station.synopticSummary}
            
            📲 _Compartilhado via SI Met RADAR - Monitoramento em Tempo Real_
        """.trimIndent()

        shareText(context, message, "Compartilhar Previsão IPMet")
    }

    fun shareWeatherSummary(context: Context, text: String) {
        shareText(context, text, "Compartilhar Diagnóstico de Chuva Maps")
    }

    fun shareText(context: Context, text: String, chooserTitle: String = "Compartilhar") {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, chooserTitle)
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }
}
