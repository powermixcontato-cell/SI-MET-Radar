package com.example.data.model

import com.example.data.local.entity.WeatherAlertEntity
import com.example.data.local.entity.WeatherStationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NewsSeverity {
    INFO,
    ATENCAO,
    ALERTA,
    URGENTE
}

data class WeatherNewsItem(
    val id: String,
    val title: String,
    val summary: String,
    val fullText: String,
    val category: String, // "Frente Fria", "Chuva Intensa", "Rajadas de Vento", "Defesa Civil", "Previsão"
    val region: String,
    val timestamp: Long,
    val source: String, // "Defesa Civil SP", "IPMet UNESP", "INMET / CPTEC"
    val severity: NewsSeverity = NewsSeverity.INFO,
    val iconEmoji: String = "🌦️"
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale("pt", "BR"))
            return sdf.format(Date(timestamp))
        }
}

object WeatherNewsProvider {

    /**
     * Gera boletins e notícias meteorológicas dinâmicas baseadas na estação selecionada,
     * na região geográfica paulista ou nas coordenadas GPS.
     */
    fun getNewsForRegion(
        station: WeatherStationEntity?,
        alerts: List<WeatherAlertEntity>,
        userCoords: Pair<Double, Double>? = null
    ): List<WeatherNewsItem> {
        val stName = station?.name ?: "São Paulo"
        val region = station?.region ?: "Estado de SP"
        val temp = station?.currentTemp?.toInt() ?: 24
        val rain = station?.rainVolumeMm ?: 0.0
        val windSpd = station?.windSpeed ?: 14.0
        val windDir = station?.windDirection ?: "SE"
        val condition = station?.weatherCondition ?: "Parcialmente Nublado"
        val now = System.currentTimeMillis()

        val newsList = mutableListOf<WeatherNewsItem>()

        // 1. Notícia de Alertas Reais Ativos (se houver)
        val severeAlert = alerts.firstOrNull { it.regionName.contains(region, ignoreCase = true) || it.regionName.contains("Estado", ignoreCase = true) }
            ?: alerts.firstOrNull()
        if (severeAlert != null) {
            newsList.add(
                WeatherNewsItem(
                    id = "alert_${severeAlert.id}",
                    title = "ALERTA DEFESA CIVIL: ${severeAlert.title}",
                    summary = "Aviso oficial meteorológico em vigor para $region: ${severeAlert.description.take(120)}...",
                    fullText = "A Defesa Civil do Estado de São Paulo e o INMET mantêm alerta meteorológico vigente para a região de $stName ($region). ${severeAlert.description}. Recomenda-se evitar áreas alagadas, não estacionar veículos sob árvores e atentar-se para rajadas de vento e descargas elétricas.",
                    category = "Defesa Civil",
                    region = region,
                    timestamp = severeAlert.timestamp.coerceAtLeast(now - 3600000L),
                    source = "Defesa Civil SP & INMET",
                    severity = if (severeAlert.severity == "PERIGO" || severeAlert.severity == "GRANDE_PERIGO") NewsSeverity.ALERTA else NewsSeverity.ATENCAO,
                    iconEmoji = "🚨"
                )
            )
        }

        // 2. Boletim de Vento & Correntes Estilo Windy
        val windSeverity = if (windSpd > 35.0) NewsSeverity.ALERTA else if (windSpd > 22.0) NewsSeverity.ATENCAO else NewsSeverity.INFO
        val windEmoji = if (windSpd > 35.0) "🌪️" else "💨"
        newsList.add(
            WeatherNewsItem(
                id = "wind_${station?.id ?: "sp"}",
                title = "Correntes de Vento e Deslocamento Atmosférico em $stName",
                summary = "Ventos de ${String.format(Locale("pt", "BR"), "%.1f", windSpd)} km/h soprando de $windDir com fluxo aerológico ativo em $region.",
                fullText = "A análise de fluxo aerológico (padrão Windy e radar IPMet) indica correntes de ar soprando de quadrante $windDir a ${String.format(Locale("pt", "BR"), "%.1f", windSpd)} km/h sobre $region. Em áreas abertas e planaltos, rajadas convectivas podem ocorrer durante a passagem de instabilidades tropicais. Monitore a camada de vento contínuo em tempo real no mapa.",
                category = "Rajadas de Vento",
                region = region,
                timestamp = now - 1800000L,
                source = "Radar IPMet UNESP & WRF",
                severity = windSeverity,
                iconEmoji = windEmoji
            )
        )

        // 3. Boletim de Chuva e Instabilidade Regional
        val rainSummary = if (rain > 15.0) {
            "Precipitação acumulada expressiva de ${String.format(Locale("pt", "BR"), "%.1f", rain)} mm registrada nas últimas horas."
        } else if (rain > 0.0) {
            "Chuva fraca a moderada com ${String.format(Locale("pt", "BR"), "%.1f", rain)} mm acumulados na microrregião de $stName."
        } else {
            "Sem registro de chuva significativa recente em $stName, com umidade do ar em ${station?.humidity ?: 65}%."
        }

        newsList.add(
            WeatherNewsItem(
                id = "precip_${station?.id ?: "sp"}",
                title = "Panorama de Chuva e Cobertura Radar para $region",
                summary = rainSummary,
                fullText = "Os radares Doppler de Bauru e Presidente Prudente mostram a dinâmica de nuvens convectivas sobre o interior e leste paulista. Em $stName, a condição registrada é de $condition, com temperatura atual de $temp°C. $rainSummary Acompanhe a projeção dos blocos de chuva no mapa interativo com intervalos de 15 minutos.",
                category = "Chuva & Radar",
                region = region,
                timestamp = now - 3600000L,
                source = "IPMet / CIIAGRO",
                severity = if (rain > 25.0) NewsSeverity.ALERTA else NewsSeverity.INFO,
                iconEmoji = "🌧️"
            )
        )

        // 4. Tendência Sinóptica Estadual (Frente Fria / Massa de Ar)
        newsList.add(
            WeatherNewsItem(
                id = "synoptic_${station?.id ?: "sp"}",
                title = "Sinopse Meteorológica do Estado de SP: Dinâmica Regional",
                summary = "Sistemas de baixa pressão e circulação marítima atuam sobre a faixa leste e interior de São Paulo.",
                fullText = "A circulação atmosférica em médios e altos níveis da troposfera favorece o transporte de umidade da Bacia Amazônica e do Oceano Atlântico para o território paulista. A região de $stName ($region) apresenta variação térmica com mínima de ${station?.minTemp ?: 17}°C e máxima de ${station?.maxTemp ?: 29}°C. Para os próximos dias, modelos meteorológicos sugerem manutenção da variabilidade climática típica do clima tropical paulista.",
                category = "Sinopse Sinóptica",
                region = "Estado de São Paulo",
                timestamp = now - 7200000L,
                source = "Centro de Previsão do Tempo (CPTEC/INPE)",
                severity = NewsSeverity.INFO,
                iconEmoji = "🧭"
            )
        )

        return newsList
    }
}
