package com.example.data.repository

import com.example.data.local.entity.WeatherAlertEntity
import com.example.data.remote.InmetAlert
import com.example.data.remote.InmetConfig
import com.example.data.remote.InmetRssParser
import com.example.domain.BrState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Converte avisos do INMET em WeatherAlertEntity (apenas SP, PR e RS, não vencidos).
 * regionId = "UF1,UF2#meso1|meso2" (ex.: "PR,RS#Sudoeste Paranaense|Noroeste Rio-grandense"),
 * usado para filtrar por estado sem mudar o esquema do banco.
 */
object InmetAlertMapper {
    private fun fmt(millis: Long?): String {
        if (millis == null) return "—"
        return SimpleDateFormat("dd/MM HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
        }.format(java.util.Date(millis))
    }

    fun periodText(a: InmetAlert): String = "${fmt(a.startMillis)} – ${fmt(a.endMillis)} (BRT)"

    /** Estados (SP/PR/RS) e mesorregiões citados na "Área" do aviso. */
    fun statesOf(a: InmetAlert): Map<BrState, Set<String>> {
        val area = a.areas.joinToString(", ")
        return BrState.entries.associateWith { it.matchMesoRegions(area) }.filterValues { it.isNotEmpty() }
    }

    /** Estados codificados no regionId de um aviso INMET salvo. */
    fun statesOfEntity(e: WeatherAlertEntity): Set<BrState> {
        if (!e.id.startsWith("inmet_")) return emptySet()
        val head = e.regionId.substringBefore('#', missingDelimiterValue = "")
        if (head.isBlank()) return setOf(BrState.SP) // formato antigo (v5.0): só SP
        return head.split(',').mapNotNull { uf -> BrState.entries.firstOrNull { it.uf == uf.trim() } }.toSet()
    }

    /** Ativo = Fim > agora. Futuro = Início > agora. Vencidos são descartados. */
    fun toEntities(alerts: List<InmetAlert>, nowMillis: Long): List<WeatherAlertEntity> =
        alerts.mapNotNull { a ->
            val end = a.endMillis ?: return@mapNotNull null
            if (end <= nowMillis) return@mapNotNull null
            val byState = statesOf(a)
            if (byState.isEmpty()) return@mapNotNull null
            val mesos = byState.values.flatten().sorted()
            val ufs = byState.keys.sortedBy { it.ordinal }.joinToString(",") { it.uf }
            val isFuture = (a.startMillis ?: 0L) > nowMillis
            val event = a.event ?: a.title
            val prefix = if (isFuture) "Previsto para ${fmt(a.startMillis)}. " else ""
            WeatherAlertEntity(
                id = "inmet_${a.id}",
                regionId = "$ufs#" + mesos.joinToString("|"),
                regionName = mesos.joinToString(", ") + " ($ufs)",
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
