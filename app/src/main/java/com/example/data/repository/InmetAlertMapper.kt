package com.example.data.repository

import com.example.data.local.entity.WeatherAlertEntity
import com.example.data.remote.InmetAlert
import com.example.data.remote.InmetConfig
import com.example.data.remote.InmetRssParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Converte avisos do INMET em WeatherAlertEntity (apenas SP e não vencidos). */
object InmetAlertMapper {
    private fun fmt(millis: Long?): String {
        if (millis == null) return "—"
        return SimpleDateFormat("dd/MM HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
        }.format(java.util.Date(millis))
    }

    fun periodText(a: InmetAlert): String = "${fmt(a.startMillis)} – ${fmt(a.endMillis)}"

    /** Ativo = Fim > agora. Futuro = Início > agora. Vencidos são descartados. */
    fun toEntities(alerts: List<InmetAlert>, nowMillis: Long): List<WeatherAlertEntity> =
        alerts.mapNotNull { a ->
            val end = a.endMillis ?: return@mapNotNull null
            if (end <= nowMillis) return@mapNotNull null
            val mesos = InmetRssParser.spMesoRegionsOf(a)
            if (mesos.isEmpty()) return@mapNotNull null
            val isFuture = (a.startMillis ?: 0L) > nowMillis
            val event = a.event ?: a.title
            val prefix = if (isFuture) "Previsto para ${fmt(a.startMillis)}. " else ""
            WeatherAlertEntity(
                id = "inmet_${a.id}",
                regionId = mesos.sorted().joinToString("|"),
                regionName = mesos.sorted().joinToString(", "),
                severity = InmetRssParser.severityToAppLevel(a.severity),
                title = "$event (${a.severity ?: "—"})",
                description = prefix + (a.description ?: "") +
                    "\nPeríodo: ${periodText(a)}\nFonte: ${InmetConfig.SOURCE_LABEL} • ${a.link}",
                radarStationSource = "${InmetConfig.SOURCE_LABEL} • ${a.link}",
                dbzPeak = 0,
                timestamp = a.startMillis ?: nowMillis
            )
        }
}
