package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.local.entity.ClimateTrendEntity
import com.example.data.local.entity.DailyForecastEntity
import com.example.data.local.entity.HourlyForecastEntity
import com.example.data.local.entity.WeatherAlertEntity
import com.example.data.local.entity.WeatherStationEntity
import com.example.data.model.WeatherNewsItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfExportOptions(
    val includeDailyForecast: Boolean = true,
    val includeHourlyForecast: Boolean = true,
    val includeRegionalNews: Boolean = true,
    val includeAlerts: Boolean = true,
    val includeWindAnalysis: Boolean = true,
    val includeStationMetrics: Boolean = true,
    val includeGpsLocation: Boolean = true
)

object PdfExporter {

    fun generateWeatherReportPdf(
        context: Context,
        station: WeatherStationEntity,
        dailyForecasts: List<DailyForecastEntity>,
        alerts: List<WeatherAlertEntity>,
        climateTrends: List<ClimateTrendEntity>
    ): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 at 72dpi
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(0, 50, 100)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(80, 90, 100)
            textSize = 11f
        }
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(30, 30, 30)
            textSize = 10f
        }
        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(20, 20, 20)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Header Background Banner
        paint.color = Color.rgb(10, 30, 60)
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        titlePaint.color = Color.WHITE
        canvas.drawText("IPMet - Instituto de Pesquisas Meteorológicas", 30f, 35f, titlePaint)
        subPaint.color = Color.rgb(0, 229, 255)
        canvas.drawText("BOLETIM METEOROLÓGICO & RADAR DO ESTADO DE SÃO PAULO", 30f, 55f, subPaint)
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        subPaint.color = Color.rgb(200, 210, 225)
        subPaint.textSize = 9f
        canvas.drawText("Emitido em: $dateStr (Horário de Brasília) | Rede UNESP Bauru & Prudente", 30f, 75f, subPaint)

        var y = 120f

        // Station Details Section
        paint.color = Color.rgb(240, 245, 250)
        canvas.drawRoundRect(25f, y - 15f, 570f, y + 80f, 8f, 8f, paint)

        boldPaint.textSize = 13f
        boldPaint.color = Color.rgb(0, 60, 120)
        canvas.drawText("Estação de Monitoramento: ${station.name} (${station.region})", 35f, y + 5f, boldPaint)

        boldPaint.textSize = 10f
        boldPaint.color = Color.rgb(30, 30, 30)
        canvas.drawText("Condição Atual: ${station.weatherCondition}", 35f, y + 25f, boldPaint)
        canvas.drawText("Temperatura: ${station.currentTemp}°C (Sensação: ${station.feelsLike}°C)", 35f, y + 42f, textPaint)
        canvas.drawText("Variação Térmica: Mín ${station.minTemp}°C / Máx ${station.maxTemp}°C", 35f, y + 58f, textPaint)

        canvas.drawText("Umidade Relativa: ${station.humidity}%", 300f, y + 25f, textPaint)
        canvas.drawText("Pressão: ${station.pressure} hPa | Vento: ${station.windDirection}", 300f, y + 42f, textPaint)
        canvas.drawText("Índice UV: ${station.uvIndex} | Qualidade do Ar (AQI): ${station.aqi}", 300f, y + 58f, textPaint)
        canvas.drawText("Chuva: ${station.rainVolumeMm} mm (${station.rainProbability}%) | Radar: ${station.dbzReflectivity} dBZ", 300f, y + 74f, boldPaint)

        y += 115f

        // Synoptic Analysis
        boldPaint.textSize = 12f
        boldPaint.color = Color.rgb(0, 50, 100)
        canvas.drawText("Sinopse Meteorológica e Cobertura de Radar", 25f, y, boldPaint)
        y += 18f
        paint.color = Color.rgb(248, 250, 252)
        canvas.drawRect(25f, y - 5f, 570f, y + 45f, paint)
        textPaint.textSize = 9.5f
        canvas.drawText(station.synopticSummary, 35f, y + 14f, textPaint)
        canvas.drawText("Horários Solares: Nascer ${station.sunrise} | Pôr do Sol ${station.sunset}", 35f, y + 32f, textPaint)

        y += 65f

        // 7-Day Forecast Table
        boldPaint.textSize = 12f
        boldPaint.color = Color.rgb(0, 50, 100)
        canvas.drawText("Previsão Estendida para os Próximos 7 Dias", 25f, y, boldPaint)
        y += 15f

        // Table Header
        paint.color = Color.rgb(220, 230, 242)
        canvas.drawRect(25f, y, 570f, y + 20f, paint)
        boldPaint.textSize = 9.5f
        boldPaint.color = Color.rgb(20, 40, 70)
        canvas.drawText("Dia / Data", 35f, y + 14f, boldPaint)
        canvas.drawText("Condição Prevista", 120f, y + 14f, boldPaint)
        canvas.drawText("Mín / Máx", 310f, y + 14f, boldPaint)
        canvas.drawText("Prob. Chuva", 410f, y + 14f, boldPaint)
        canvas.drawText("Acumulado Estimado", 480f, y + 14f, boldPaint)

        y += 20f
        dailyForecasts.take(7).forEachIndexed { index, item ->
            paint.color = if (index % 2 == 0) Color.WHITE else Color.rgb(248, 249, 250)
            canvas.drawRect(25f, y, 570f, y + 18f, paint)

            textPaint.textSize = 9f
            canvas.drawText("${item.dayOfWeek} (${item.dateText})", 35f, y + 13f, boldPaint)
            canvas.drawText(item.condition, 120f, y + 13f, textPaint)
            canvas.drawText("${item.minTemp}°C / ${item.maxTemp}°C", 310f, y + 13f, textPaint)
            canvas.drawText("${item.rainProbability}%", 410f, y + 13f, textPaint)
            canvas.drawText("${item.rainVolumeMm} mm", 480f, y + 13f, textPaint)
            y += 18f
        }

        y += 20f

        // Active Alerts Section
        boldPaint.textSize = 12f
        boldPaint.color = Color.rgb(180, 40, 20)
        canvas.drawText("Avisos e Alertas Ativos da Rede IPMet", 25f, y, boldPaint)
        y += 15f

        val activeAlerts = alerts.take(2)
        if (activeAlerts.isNotEmpty()) {
            activeAlerts.forEach { alert ->
                paint.color = Color.rgb(254, 242, 242)
                canvas.drawRoundRect(25f, y, 570f, y + 42f, 4f, 4f, paint)
                boldPaint.textSize = 9.5f
                boldPaint.color = Color.rgb(185, 28, 28)
                canvas.drawText("[${alert.severity}] ${alert.title} - ${alert.regionName}", 35f, y + 15f, boldPaint)
                textPaint.textSize = 8.5f
                canvas.drawText(alert.description, 35f, y + 30f, textPaint)
                y += 48f
            }
        } else {
            textPaint.textSize = 9.5f
            canvas.drawText("Nenhum alerta meteorológico severo vigente no momento para a região.", 35f, y + 12f, textPaint)
            y += 25f
        }

        y += 10f

        // Climate Normal & Tendency
        boldPaint.textSize = 12f
        boldPaint.color = Color.rgb(0, 50, 100)
        canvas.drawText("Tendências Climáticas e Anomalias Históricas (Base IPMet/Normal Climatológica)", 25f, y, boldPaint)
        y += 15f
        paint.color = Color.rgb(240, 244, 248)
        canvas.drawRoundRect(25f, y, 570f, y + 45f, 6f, 6f, paint)
        textPaint.textSize = 8.5f
        val currentMonth = climateTrends.getOrNull(8) ?: climateTrends.firstOrNull() // September
        if (currentMonth != null) {
            val deltaTemp = currentMonth.avgTempCurrent - currentMonth.avgTempHistorical
            val deltaRain = currentMonth.rainCurrentMm - currentMonth.rainHistoricalMm
            val tempSign = if (deltaTemp >= 0) "+${String.format(Locale.US, "%.1f", deltaTemp)}" else String.format(Locale.US, "%.1f", deltaTemp)
            val rainSign = if (deltaRain >= 0) "+${String.format(Locale.US, "%.0f", deltaRain)}" else String.format(Locale.US, "%.0f", deltaRain)

            canvas.drawText("Mês de Referência: ${currentMonth.monthName} 2026 | Temperatura Média: ${currentMonth.avgTempCurrent}°C (Normal: ${currentMonth.avgTempHistorical}°C | Anomalia: ${tempSign}°C)", 35f, y + 16f, textPaint)
            canvas.drawText("Precipitação Acumulada: ${currentMonth.rainCurrentMm} mm (Normal Histórica: ${currentMonth.rainHistoricalMm} mm | Desvio: ${rainSign} mm)", 35f, y + 32f, textPaint)
        }

        // Footer
        paint.color = Color.rgb(150, 160, 170)
        canvas.drawLine(25f, 800f, 570f, 800f, paint)
        textPaint.textSize = 8f
        textPaint.color = Color.rgb(100, 110, 120)
        canvas.drawText("Documento gerado pelo aplicativo SI Met RADAR. Dados integrados WRF, INMET, CIIAGRO e Radares UNESP.", 25f, 815f, textPaint)

        document.finishPage(page)

        return try {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val fileName = "SIMet_Relatorio_${station.id}_${System.currentTimeMillis()}.pdf"
            val file = File(reportsDir, fileName)
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            document.close()
            file
        } catch (_: Exception) {
            document.close()
            null
        }
    }

    /**
     * Gera boletim especializado do AgroClima focado na localização atual/selecionada,
     * detalhando o impacto de chuva e seca na Cana-de-Açúcar e Citrus, ETo e pulverização.
     */
    fun generateAgroReportPdf(
        context: Context,
        station: WeatherStationEntity,
        dailyForecasts: List<DailyForecastEntity>,
        alerts: List<WeatherAlertEntity>
    ): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 at 72dpi
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(16, 185, 129)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(209, 250, 229)
            textSize = 10f
        }
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(30, 41, 59)
            textSize = 9.5f
        }
        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 23, 42)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Header Background Banner (Agro Green)
        paint.color = Color.rgb(6, 78, 59) // Deep Emerald
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        titlePaint.color = Color.WHITE
        canvas.drawText("AgroClima SP - Boletim Agronômico Inteligente", 30f, 35f, titlePaint)
        subPaint.color = Color.rgb(52, 211, 153)
        canvas.drawText("RELATÓRIO CLIMÁTICO ESPECIALIZADO: CHUVA E SECA EM CANA & CITRUS", 30f, 55f, subPaint)
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        subPaint.color = Color.rgb(209, 250, 229)
        subPaint.textSize = 8.5f
        canvas.drawText("Emitido em: $dateStr | Base de Dados: IPMet / IAC / UNESP / Estações SP", 30f, 75f, subPaint)

        var y = 115f

        // Localidade / Polo Agro Ativo
        paint.color = Color.rgb(236, 253, 245)
        canvas.drawRoundRect(25f, y - 15f, 570f, y + 65f, 8f, 8f, paint)

        boldPaint.textSize = 12f
        boldPaint.color = Color.rgb(6, 95, 70)
        canvas.drawText("Polo Regional Ativo: ${station.name} (${station.region})", 35f, y + 2f, boldPaint)

        // Cálculos Agrometeorológicos
        val tempMean = station.currentTemp.coerceIn(10.0, 45.0)
        val rh = station.humidity.coerceIn(15, 100)
        val windKmH = station.windSpeed.coerceIn(0.0, 100.0)
        val et0 = Math.max(1.5, (0.0023 * (tempMean + 17.8) * 4.2 * (100 - rh) / 50.0) + (windKmH * 0.04))
        val et0Formatted = String.format(Locale.US, "%.1f", et0)
        val estimatedSoilMoisture = Math.min(98, Math.max(20, (rh * 0.55 + station.rainVolumeMm * 4.2 - et0 * 1.5).toInt()))
        val soilStatus = when {
            estimatedSoilMoisture > 75 -> "Saturado / Encharcado"
            estimatedSoilMoisture >= 45 -> "Capacidade de Campo Ideal"
            estimatedSoilMoisture >= 30 -> "Déficit Leve (Monitorar)"
            else -> "Déficit Hídrico Crítico"
        }

        textPaint.textSize = 9.5f
        canvas.drawText("Temperatura Atual: ${station.currentTemp}°C (Mín: ${station.minTemp}°C / Máx: ${station.maxTemp}°C)", 35f, y + 22f, textPaint)
        canvas.drawText("Chuva Acumulada: ${station.rainVolumeMm} mm | Probabilidade: ${station.rainProbability}%", 35f, y + 38f, textPaint)
        canvas.drawText("Vento: ${windKmH.toInt()} km/h (${station.windDirection}) | Umidade Relativa: $rh%", 35f, y + 54f, textPaint)

        canvas.drawText("Evapotranspiração ETo: $et0Formatted mm/dia", 310f, y + 22f, boldPaint)
        canvas.drawText("Umidade do Solo Estimada: $estimatedSoilMoisture% ($soilStatus)", 310f, y + 38f, textPaint)
        canvas.drawText("Pressão: ${station.pressure} hPa | Radiação Estimada: Normal", 310f, y + 54f, textPaint)

        y += 95f

        // BLOCO 1: CANA-DE-AÇÚCAR (CHUVA E SECA)
        paint.color = Color.rgb(240, 253, 244)
        canvas.drawRoundRect(25f, y - 10f, 570f, y + 115f, 6f, 6f, paint)

        boldPaint.textSize = 11.5f
        boldPaint.color = Color.rgb(22, 101, 52)
        canvas.drawText("CANA-DE-AÇÚCAR: Impacto do Balanço Chuva x Seca & Operações", 35f, y + 8f, boldPaint)

        val caneTraffic = when {
            station.rainVolumeMm >= 12.0 || estimatedSoilMoisture > 75 -> "INTERROMPIDA: Risco Alto de Atolamento de Colhedoras e Compactação"
            estimatedSoilMoisture in 45..75 && station.rainVolumeMm in 1.0..11.9 -> "MODERADA: Atenção ao tráfego de transbordos em baixadas"
            else -> "FAVORÁVEL: Solo Firme e Seco para Colheita Mecânica 24h"
        }
        val caneFire = when {
            rh < 30 && tempMean > 31.0 && windKmH > 16.0 -> "ALERTA CRÍTICO: Risco Extremo de Incêndio em Palhada Seca"
            rh < 45 && tempMean > 28.0 -> "ALERTA MODERADO: Palha desidratada propensa a faíscas"
            else -> "BAIXO: Umidade da palha dentro dos limites seguros"
        }
        val caneSucrose = when {
            estimatedSoilMoisture in 30..50 && tempMean in 22.0..32.0 -> "FAVORÁVEL: Estresse hídrico moderado estimula acúmulo de sacarose (ATR)"
            estimatedSoilMoisture > 75 -> "ALERTA: Excesso de água dilui brix e estimula vegetação indesejada"
            else -> "NEUTRO: Manutenção do teor de ATR e maturação"
        }

        textPaint.textSize = 9f
        canvas.drawText("• Impacto da Chuva (Trafegabilidade): $caneTraffic", 35f, y + 28f, textPaint)
        canvas.drawText("• Impacto da Seca (Sacarose/ATR): $caneSucrose", 35f, y + 46f, textPaint)
        canvas.drawText("• Risco de Fogo em Palhada: $caneFire", 35f, y + 64f, textPaint)
        canvas.drawText("• Manejo Recomendado: Programar colhedoras nas áreas altas com declividade favorável;", 35f, y + 82f, textPaint)
        canvas.drawText("  monitorar umidade dos canaviais vizinhos a rodovias e linhas de transmissão.", 35f, y + 98f, textPaint)

        y += 140f

        // BLOCO 2: CITRUS / POMAR (CHUVA E SECA)
        paint.color = Color.rgb(255, 251, 235)
        canvas.drawRoundRect(25f, y - 10f, 570f, y + 115f, 6f, 6f, paint)

        boldPaint.textSize = 11.5f
        boldPaint.color = Color.rgb(180, 83, 9)
        canvas.drawText("CITRICULTURA: Impacto do Balanço Chuva x Seca & Fitossanitário", 35f, y + 8f, boldPaint)

        val citrusDormancy = when {
            estimatedSoilMoisture in 25..45 -> "FAVORÁVEL: Estresse de seca adequado para quebra de dormência e indução floral"
            estimatedSoilMoisture < 25 -> "CRÍTICO: Seca severa com risco de abortamento de chumbinhos e murcha"
            else -> "NEUTRO: Umidade elevada mantém fluxo vegetativo sem estímulo reprodutivo"
        }
        val citrusFungal = when {
            rh >= 78 && tempMean >= 23.0 && station.rainVolumeMm > 0.0 -> "ALERTA ALTO: Molhamento foliar crítico para Cancro Cítrico e Pinta Preta"
            rh >= 75 && tempMean >= 22.0 -> "ATENÇÃO: Monitorar tempo de folhas molhadas nas primeiras horas da manhã"
            else -> "BAIXO: Sem condições para esporulação severa de fungos"
        }
        val psyllidFlight = when {
            tempMean in 24.0..33.0 && windKmH < 12.0 && station.rainVolumeMm < 1.0 -> "ALTO RISCO: Condições térmicas ideais para voo e dispersão do Psilídeo (Greening)"
            windKmH > 18.0 || station.rainVolumeMm >= 5.0 -> "BAIXO: Chuva ou vento forte inibem deslocamento do vetor"
            else -> "MODERADO: Vistorias rigorosas em armadilhas adesivas de bordadura"
        }
        val citrusWaterMm = Math.max(0.0, Math.round((et0 * 0.75 - (station.rainVolumeMm * 0.7)) * 10.0) / 10.0)

        canvas.drawText("• Impacto da Seca (Florada/Fixação): $citrusDormancy", 35f, y + 28f, textPaint)
        canvas.drawText("• Impacto da Chuva (Cancro/Pinta Preta): $citrusFungal", 35f, y + 46f, textPaint)
        canvas.drawText("• Dispersão do Vetor do Greening (Psilídeo): $psyllidFlight", 35f, y + 64f, textPaint)
        canvas.drawText("• Demanda Hídrica Líquida (Lâmina de Irrigação): $citrusWaterMm mm/dia (ETo x Kc 0.75)", 35f, y + 82f, boldPaint)
        canvas.drawText("• Manejo Recomendado: Aplicar fungicidas protetores antes das chuvas em pomares sob risco.", 35f, y + 98f, textPaint)

        y += 140f

        // BLOCO 3: JANELA DE PULVERIZAÇÃO E PREVISÃO 7 DIAS
        boldPaint.textSize = 11.5f
        boldPaint.color = Color.rgb(15, 23, 42)
        canvas.drawText("Planejamento Operacional: Janela de Pulverização & Previsão Estendida", 25f, y, boldPaint)
        y += 16f

        val isSprayOk = windKmH in 3.0..12.0 && tempMean <= 30.0 && rh >= 50 && station.rainProbability < 40
        val sprayTxt = if (isSprayOk) "FAVORÁVEL (Temperatura <= 30°C, Vento 3-12 km/h, UR >= 50%, Sem Chuva)"
                       else "DESFAVORÁVEL: Atenção a deriva de vento ou rápida evaporação das gotas"
        paint.color = if (isSprayOk) Color.rgb(220, 252, 231) else Color.rgb(254, 242, 242)
        canvas.drawRoundRect(25f, y - 5f, 570f, y + 25f, 4f, 4f, paint)
        boldPaint.textSize = 9.5f
        boldPaint.color = if (isSprayOk) Color.rgb(22, 101, 52) else Color.rgb(185, 28, 28)
        canvas.drawText("Janela Atual de Pulverização: $sprayTxt", 35f, y + 14f, boldPaint)

        y += 35f

        // Tabela Resumo dos Próximos 7 Dias
        paint.color = Color.rgb(220, 230, 242)
        canvas.drawRect(25f, y, 570f, y + 18f, paint)
        boldPaint.textSize = 9f
        boldPaint.color = Color.rgb(15, 23, 42)
        canvas.drawText("Dia / Data", 35f, y + 13f, boldPaint)
        canvas.drawText("Condição Agrometeorológica", 130f, y + 13f, boldPaint)
        canvas.drawText("Mín / Máx", 320f, y + 13f, boldPaint)
        canvas.drawText("Chuva (mm)", 420f, y + 13f, boldPaint)
        canvas.drawText("Operação de Campo", 490f, y + 13f, boldPaint)

        y += 18f
        dailyForecasts.take(6).forEachIndexed { index, item ->
            paint.color = if (index % 2 == 0) Color.WHITE else Color.rgb(248, 250, 252)
            canvas.drawRect(25f, y, 570f, y + 17f, paint)
            textPaint.textSize = 8.5f
            canvas.drawText("${item.dayOfWeek} (${item.dateText})", 35f, y + 12f, boldPaint)
            canvas.drawText(item.condition, 130f, y + 12f, textPaint)
            canvas.drawText("${item.minTemp}°C / ${item.maxTemp}°C", 320f, y + 12f, textPaint)
            canvas.drawText("${item.rainVolumeMm} mm (${item.rainProbability}%)", 420f, y + 12f, textPaint)
            val opStatus = if (item.rainVolumeMm > 8.0) "Parada de Colheita" else if (item.rainProbability > 50) "Monitorar Chuva" else "Colheita Normal"
            canvas.drawText(opStatus, 490f, y + 12f, textPaint)
            y += 17f
        }

        // Footer
        paint.color = Color.rgb(148, 163, 184)
        canvas.drawLine(25f, 805f, 570f, 805f, paint)
        textPaint.textSize = 8f
        textPaint.color = Color.rgb(100, 116, 139)
        canvas.drawText("Boletim Agro gerado pelo aplicativo SI Met RADAR. Parâmetros calculados por algoritmos agrometeorológicos.", 25f, 820f, textPaint)

        document.finishPage(page)

        return try {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val fileName = "SIMet_AgroClima_${station.id}_${System.currentTimeMillis()}.pdf"
            val file = File(reportsDir, fileName)
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            document.close()
            file
        } catch (_: Exception) {
            document.close()
            null
        }
    }

    /**
     * Gera relatório completo e personalizável com múltiplas páginas,
     * incluindo Correntes de Vento, Análise Horária, Previsão 7 Dias,
     * Notícias do Clima Regionais e Alertas da Defesa Civil.
     */
    fun generateEnhancedWeatherReportPdf(
        context: Context,
        station: WeatherStationEntity,
        dailyForecasts: List<DailyForecastEntity>,
        hourlyForecasts: List<HourlyForecastEntity>,
        alerts: List<WeatherAlertEntity>,
        climateTrends: List<ClimateTrendEntity>,
        newsList: List<WeatherNewsItem>,
        userCoords: Pair<Double, Double>?,
        options: PdfExportOptions = PdfExportOptions()
    ): File? {
        val document = PdfDocument()

        val paint = Paint().apply { isAntiAlias = true }
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(0, 50, 100)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(80, 90, 100)
            textSize = 10f
        }
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(30, 30, 30)
            textSize = 9.5f
        }
        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(20, 20, 20)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // ==========================================
        // PÁGINA 1: Estação, Vento, Horária & Radar
        // ==========================================
        val page1Info = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page1 = document.startPage(page1Info)
        val canvas1: Canvas = page1.canvas

        // Header Background Banner
        paint.color = Color.rgb(10, 30, 60)
        canvas1.drawRect(0f, 0f, 595f, 90f, paint)

        titlePaint.color = Color.WHITE
        canvas1.drawText("SI Met RADAR • Boletim Meteorológico & Vento", 30f, 35f, titlePaint)
        subPaint.color = Color.rgb(0, 229, 255)
        canvas1.drawText("RELATÓRIO CLIMÁTICO, COBERTURA DOPPLER & FLUXO AEROLÓGICO", 30f, 55f, subPaint)
        val dateStr = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale("pt", "BR")).format(Date())
        subPaint.color = Color.rgb(200, 210, 225)
        subPaint.textSize = 8.5f
        val gpsInfo = if (userCoords != null) " | GPS: ${String.format(Locale.US, "%.3f", userCoords.first)}, ${String.format(Locale.US, "%.3f", userCoords.second)}" else ""
        canvas1.drawText("Emitido em: $dateStr$gpsInfo | Rede IPMet UNESP Bauru & Prudente", 30f, 75f, subPaint)

        var y1 = 115f

        // 1. Dados da Estação
        if (options.includeStationMetrics) {
            paint.color = Color.rgb(240, 245, 250)
            canvas1.drawRoundRect(25f, y1 - 15f, 570f, y1 + 82f, 8f, 8f, paint)

            boldPaint.textSize = 12f
            boldPaint.color = Color.rgb(0, 60, 120)
            canvas1.drawText("Local Monitorado: ${station.name} (${station.region})", 35f, y1 + 5f, boldPaint)

            boldPaint.textSize = 9.5f
            boldPaint.color = Color.rgb(30, 30, 30)
            canvas1.drawText("Condição Atual: ${station.weatherCondition}", 35f, y1 + 25f, boldPaint)
            canvas1.drawText("Temperatura: ${station.currentTemp}°C (Sensação: ${station.feelsLike}°C)", 35f, y1 + 42f, textPaint)
            canvas1.drawText("Mínima / Máxima: ${station.minTemp}°C / ${station.maxTemp}°C", 35f, y1 + 58f, textPaint)

            canvas1.drawText("Umidade: ${station.humidity}% | Pressão: ${station.pressure} hPa", 300f, y1 + 25f, textPaint)
            canvas1.drawText("Índice UV: ${station.uvIndex} | Qualidade do Ar (AQI): ${station.aqi}", 300f, y1 + 42f, textPaint)
            canvas1.drawText("Precipitação: ${station.rainVolumeMm} mm (${station.rainProbability}%)", 300f, y1 + 58f, boldPaint)
            canvas1.drawText("Radar Doppler: ${station.dbzReflectivity} dBZ (Operação Contínua)", 300f, y1 + 74f, textPaint)

            y1 += 115f
        }

        // 2. Análise de Correntes de Vento (Estilo Windy)
        if (options.includeWindAnalysis) {
            boldPaint.textSize = 11f
            boldPaint.color = Color.rgb(2, 132, 199)
            canvas1.drawText("💨 Dinâmica de Correntes de Vento e Deslocamento Atmosférico", 25f, y1, boldPaint)
            y1 += 14f

            paint.color = Color.rgb(240, 249, 255)
            canvas1.drawRoundRect(25f, y1 - 4f, 570f, y1 + 48f, 6f, 6f, paint)

            val windSpeedText = "${String.format(Locale("pt", "BR"), "%.1f", station.windSpeed)} km/h"
            val gustText = "${String.format(Locale("pt", "BR"), "%.1f", station.windSpeed * 1.45)} km/h"
            textPaint.textSize = 9f
            canvas1.drawText("Vento Médio à Superfície: $windSpeedText | Quadrante: ${station.windDirection} | Rajada Estimada: $gustText", 35f, y1 + 14f, boldPaint)
            canvas1.drawText("As partículas aerológicas e linhas de corrente do padrão Windy indicam transporte de massas de ar com estabilidade regional.", 35f, y1 + 28f, textPaint)
            canvas1.drawText("Radares IPMet operando em modo Doppler volumétrico de 240 km cobrindo toda a bacia hidrográfica paulista.", 35f, y1 + 42f, textPaint)

            y1 += 68f
        }

        // 3. Previsão Horária Detalhada (Próximas 10-12 Horas)
        if (options.includeHourlyForecast && hourlyForecasts.isNotEmpty()) {
            boldPaint.textSize = 11f
            boldPaint.color = Color.rgb(0, 50, 100)
            canvas1.drawText("⏱️ Inspeção Horária de Chuva e Temperatura (Próximas Horas)", 25f, y1, boldPaint)
            y1 += 14f

            // Table Header
            paint.color = Color.rgb(226, 232, 240)
            canvas1.drawRect(25f, y1, 570f, y1 + 18f, paint)
            boldPaint.textSize = 8.5f
            boldPaint.color = Color.rgb(30, 41, 59)
            canvas1.drawText("Horário", 35f, y1 + 12f, boldPaint)
            canvas1.drawText("Condição", 100f, y1 + 12f, boldPaint)
            canvas1.drawText("Temp", 240f, y1 + 12f, boldPaint)
            canvas1.drawText("Chuva mm", 310f, y1 + 12f, boldPaint)
            canvas1.drawText("Probabilidade", 390f, y1 + 12f, boldPaint)
            canvas1.drawText("Vento / Fluxo", 480f, y1 + 12f, boldPaint)
            y1 += 18f

            hourlyForecasts.take(10).forEachIndexed { idx, hr ->
                paint.color = if (idx % 2 == 0) Color.WHITE else Color.rgb(248, 250, 252)
                canvas1.drawRect(25f, y1, 570f, y1 + 16f, paint)

                textPaint.textSize = 8.5f
                canvas1.drawText(hr.hourText, 35f, y1 + 11f, boldPaint)
                canvas1.drawText(hr.condition.take(24), 100f, y1 + 11f, textPaint)
                canvas1.drawText("${hr.temp}°C", 240f, y1 + 11f, textPaint)
                canvas1.drawText("${hr.rainVolumeMm} mm", 310f, y1 + 11f, if (hr.rainVolumeMm > 0) boldPaint else textPaint)
                canvas1.drawText("${hr.rainProbability}%", 390f, y1 + 11f, textPaint)
                canvas1.drawText(station.windDirection, 480f, y1 + 11f, textPaint)

                y1 += 16f
            }
            y1 += 18f
        }

        // Rodapé da Página 1
        paint.color = Color.rgb(150, 160, 170)
        canvas1.drawLine(25f, 805f, 570f, 805f, paint)
        textPaint.textSize = 8f
        textPaint.color = Color.rgb(100, 110, 120)
        canvas1.drawText("Página 1/2 • SI Met RADAR: Monitoramento em Tempo Real do Estado de São Paulo", 25f, 820f, textPaint)

        document.finishPage(page1)

        // ==========================================
        // PÁGINA 2: Previsão 7 Dias, Notícias & Alertas
        // ==========================================
        val page2Info = PdfDocument.PageInfo.Builder(595, 842, 2).create()
        val page2 = document.startPage(page2Info)
        val canvas2: Canvas = page2.canvas

        // Header Background Banner Página 2
        paint.color = Color.rgb(10, 30, 60)
        canvas2.drawRect(0f, 0f, 595f, 60f, paint)
        titlePaint.color = Color.WHITE
        titlePaint.textSize = 14f
        canvas2.drawText("Previsão Estendida & Notícias do Clima Regional", 30f, 30f, titlePaint)
        subPaint.color = Color.rgb(0, 229, 255)
        subPaint.textSize = 8.5f
        canvas2.drawText("Região de ${station.name} (${station.region}) | Fonte Oficial Defesa Civil, IPMet e INMET", 30f, 48f, subPaint)

        var y2 = 85f

        // 1. Tabela de Previsão 7 Dias
        if (options.includeDailyForecast && dailyForecasts.isNotEmpty()) {
            boldPaint.textSize = 11f
            boldPaint.color = Color.rgb(0, 50, 100)
            canvas2.drawText("📅 Previsão Estendida para os Próximos 7 Dias", 25f, y2, boldPaint)
            y2 += 14f

            paint.color = Color.rgb(220, 230, 242)
            canvas2.drawRect(25f, y2, 570f, y2 + 18f, paint)
            boldPaint.textSize = 8.5f
            boldPaint.color = Color.rgb(20, 40, 70)
            canvas2.drawText("Dia / Data", 35f, y2 + 12f, boldPaint)
            canvas2.drawText("Condição Prevista", 120f, y2 + 12f, boldPaint)
            canvas2.drawText("Mín / Máx", 310f, y2 + 12f, boldPaint)
            canvas2.drawText("Prob. Chuva", 410f, y2 + 12f, boldPaint)
            canvas2.drawText("Acumulado", 490f, y2 + 12f, boldPaint)
            y2 += 18f

            dailyForecasts.take(7).forEachIndexed { index, item ->
                paint.color = if (index % 2 == 0) Color.WHITE else Color.rgb(248, 249, 250)
                canvas2.drawRect(25f, y2, 570f, y2 + 16f, paint)

                textPaint.textSize = 8.5f
                canvas2.drawText("${item.dayOfWeek} (${item.dateText})", 35f, y2 + 11f, boldPaint)
                canvas2.drawText(item.condition, 120f, y2 + 11f, textPaint)
                canvas2.drawText("${item.minTemp}°C / ${item.maxTemp}°C", 310f, y2 + 11f, textPaint)
                canvas2.drawText("${item.rainProbability}%", 410f, y2 + 11f, textPaint)
                canvas2.drawText("${item.rainVolumeMm} mm", 490f, y2 + 11f, boldPaint)
                y2 += 16f
            }
            y2 += 22f
        }

        // 2. Alertas Meteorológicos e Defesa Civil
        if (options.includeAlerts) {
            boldPaint.textSize = 11f
            boldPaint.color = Color.rgb(185, 28, 28)
            canvas2.drawText("🚨 Avisos Meteorológicos Oficiais (Defesa Civil & INMET)", 25f, y2, boldPaint)
            y2 += 14f

            val activeAlerts = alerts.take(2)
            if (activeAlerts.isNotEmpty()) {
                activeAlerts.forEach { alert ->
                    paint.color = Color.rgb(254, 242, 242)
                    canvas2.drawRoundRect(25f, y2, 570f, y2 + 38f, 4f, 4f, paint)
                    boldPaint.textSize = 9f
                    boldPaint.color = Color.rgb(185, 28, 28)
                    canvas2.drawText("[${alert.severity}] ${alert.title} - ${alert.regionName}", 35f, y2 + 14f, boldPaint)
                    textPaint.textSize = 8f
                    canvas2.drawText(alert.description.take(110), 35f, y2 + 28f, textPaint)
                    y2 += 44f
                }
            } else {
                paint.color = Color.rgb(240, 253, 244)
                canvas2.drawRoundRect(25f, y2, 570f, y2 + 25f, 4f, 4f, paint)
                textPaint.textSize = 8.5f
                textPaint.color = Color.rgb(21, 128, 61)
                canvas2.drawText("✅ Nenhum alerta meteorológico severo em vigor para a região no momento.", 35f, y2 + 16f, textPaint)
                y2 += 32f
            }
            y2 += 10f
        }

        // 3. Notícias do Clima Regionais Baseadas na Localização / GPS
        if (options.includeRegionalNews && newsList.isNotEmpty()) {
            boldPaint.textSize = 11f
            boldPaint.color = Color.rgb(59, 130, 246)
            canvas2.drawText("📰 Notícias do Clima & Boletins Regionais (${station.region})", 25f, y2, boldPaint)
            y2 += 14f

            newsList.take(3).forEach { news ->
                paint.color = Color.rgb(248, 250, 252)
                canvas2.drawRoundRect(25f, y2, 570f, y2 + 45f, 6f, 6f, paint)

                boldPaint.textSize = 9f
                boldPaint.color = Color.rgb(15, 23, 42)
                canvas2.drawText("${news.iconEmoji} ${news.title}", 35f, y2 + 14f, boldPaint)

                textPaint.textSize = 8f
                textPaint.color = Color.rgb(71, 85, 105)
                canvas2.drawText(news.summary.take(115), 35f, y2 + 28f, textPaint)

                subPaint.textSize = 7.5f
                subPaint.color = Color.rgb(148, 163, 184)
                canvas2.drawText("Fonte: ${news.source} • Categoria: ${news.category}", 35f, y2 + 39f, subPaint)

                y2 += 50f
            }
        }

        // Rodapé da Página 2
        paint.color = Color.rgb(150, 160, 170)
        canvas2.drawLine(25f, 805f, 570f, 805f, paint)
        textPaint.textSize = 8f
        textPaint.color = Color.rgb(100, 110, 120)
        canvas2.drawText("Página 2/2 • Documento exportado via SI Met RADAR. Dados gerados para fins informativos e de planejamento.", 25f, 820f, textPaint)

        document.finishPage(page2)

        return try {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val fileName = "SIMet_BoletimCompleto_${station.id}_${System.currentTimeMillis()}.pdf"
            val file = File(reportsDir, fileName)
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            document.close()
            file
        } catch (_: Exception) {
            document.close()
            null
        }
    }

    fun openOrSharePdf(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Relatório SI-MET Radar - ${pdfFile.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Compartilhar Relatório PDF")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
