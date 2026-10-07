package com.example.util

import android.content.Context
import com.example.data.local.entity.DailyForecastEntity
import com.example.data.local.entity.WeatherAlertEntity
import com.example.data.local.entity.WeatherStationEntity
import com.example.data.repository.AgroSeries
import com.example.domain.BrState
import com.example.domain.CanaCitrosIndices
import com.example.domain.GaugeStatus
import com.example.domain.HazardAlert
import com.example.domain.RiskIndicator
import com.example.domain.RiskLevel
import java.io.File
import java.util.Locale

/** Opções do PDF da tela inicial (v5.1). */
data class MainReportOptions(
    val includeRainMap: Boolean = true,
    val include15Days: Boolean = true,
    val includeAlerts: Boolean = true,
    val includeRivers: Boolean = true
)

/** Relatórios PDF da v5.1 (tela inicial e agro). Só usam dados já obtidos; sem dado → texto "indisponível". */
object SimetReports {
    private val pt = Locale("pt", "BR")
    private fun f1(v: Double) = String.format(pt, "%.1f", v)
    private fun reportFile(context: Context, prefix: String, state: BrState) =
        File(File(context.cacheDir, "reports"), "SIMet_${prefix}_${state.uf}_${System.currentTimeMillis()}.pdf")

    private fun forecastRows(days: List<DailyForecastEntity>): List<List<String>> = days.map {
        listOf("${it.dayOfWeek} ${it.dateText}", it.condition, "${it.minTemp.toInt()}° / ${it.maxTemp.toInt()}°", "${f1(it.rainVolumeMm)} mm", "${it.rainProbability}%")
    }

    /** Executar fora da thread principal (baixa a imagem do radar). */
    fun mainReport(
        context: Context,
        state: BrState,
        station: WeatherStationEntity?,
        daily: List<DailyForecastEntity>,
        alerts: List<HazardAlert>,
        gauges: List<GaugeStatus>,
        options: MainReportOptions
    ): File? {
        val b = PdfReportBuilder("Boletim do tempo • ${state.displayName}", "Cidade de referência: ${station?.name ?: "—"}")
        val sources = linkedSetOf<String>()

        if (options.includeRainMap) {
            b.section("Mapa de chuva (radar)")
            val snap = try { RainMapSnapshot.fetch(state) } catch (_: Exception) { null }
            if (snap != null) {
                b.image(snap.bitmap, "Último quadro de radar disponível: ${PdfReportBuilder.brt(snap.frameTimeSec * 1000)}. " +
                    "Composição de radares RainViewer sobre mapa base Esri (World Dark Gray).")
                sources += "Radar: RainViewer (rainviewer.com), composição de radares meteorológicos."
                sources += "Mapa base: Esri, HERE, Garmin, © colaboradores OpenStreetMap."
            } else {
                b.paragraph("Mapa de chuva indisponível no momento da geração (sem conexão ou serviço fora do ar).", "small")
            }
        }

        b.section("Previsão de 7 dias • ${station?.name ?: state.displayName}")
        if (daily.isEmpty()) b.paragraph("Previsão ainda não carregada para esta cidade.", "small")
        else {
            b.table(listOf("Dia", "Condição", "Mín / Máx", "Chuva", "Prob."), forecastRows(daily.take(7)), listOf(1.1f, 2.4f, 1f, 0.9f, 0.6f))
            b.paragraph("Total previsto em 7 dias: ${f1(daily.take(7).sumOf { it.rainVolumeMm })} mm.", "bold")
            sources += "Previsão: Open-Meteo (modelos numéricos, forecast_days=16), open-meteo.com — CC BY 4.0."
        }
        if (options.include15Days && daily.size > 7) {
            b.section("Tendência dias 8 a 15")
            b.table(listOf("Dia", "Condição", "Mín / Máx", "Chuva", "Prob."), forecastRows(daily.drop(7).take(8)), listOf(1.1f, 2.4f, 1f, 0.9f, 0.6f))
            b.paragraph("Previsões além de 7 dias têm confiabilidade menor; use como tendência.", "small")
        }

        if (options.includeAlerts) {
            b.section("Alertas ativos • ${state.uf}")
            if (alerts.isEmpty()) b.paragraph("Sem alertas ativos.")
            alerts.forEach { a ->
                val c = when (a.severity) {
                    com.example.domain.HazardSeverity.AMARELO -> 0xFFB45309.toInt() // âmbar escuro (amarelo puro some no papel)
                    else -> a.severity.argb.toInt()
                }
                b.bullet("[${a.severity.label}] ${a.category.label}: ${a.title} — ${a.kind.label}", c)
                b.paragraph("Área: ${a.area} • Validade: ${a.validity} • Fonte: ${a.source}", "small", indent = 12f)
                sources += a.source + (a.sourceUrl?.let { " ($it)" } ?: "")
            }
        }

        if (options.includeRivers && gauges.isNotEmpty()) {
            b.section("Rios monitorados • ${state.uf}")
            b.table(
                listOf("Rio / local", "Situação", "Vazão hoje", "Pico 7 d", "Nível ANA"),
                gauges.map { g ->
                    listOf(
                        "${g.gauge.river} — ${g.gauge.place}", g.overall.label,
                        g.todayQ?.let { "${it.toInt()} m³/s" } ?: "—",
                        g.peak7Mean?.let { "${it.toInt()} m³/s" } ?: "—",
                        g.lastLevelCm?.let { String.format(pt, "%.2f m", it / 100) } ?: "—"
                    )
                },
                listOf(2.6f, 1.1f, 1f, 1f, 0.9f)
            )
            b.paragraph("Situação = maior entre o indicador do modelo GloFAS (vazão prevista × cheias de 2/5/20 anos da reanálise) e o nível observado da ANA " +
                "comparado às cotas oficiais conferidas (Guaíba/Cais Mauá e Taquari/Estrela). Indicadores do modelo não são avisos oficiais.", "small")
            sources += "Vazões: GloFAS v4 © Copernicus Emergency Management Service, via Open-Meteo Flood API."
            if (gauges.any { it.lastLevelCm != null }) sources += "Níveis: ANA — Rede Hidrometeorológica Nacional (telemetria)."
        }

        b.section("Fontes")
        sources.forEach { b.bullet(it) }
        b.paragraph("Horários em Brasília (BRT). Gerado pelo app SI-MET Radar v5.1.", "small")
        return b.write(reportFile(context, "Boletim", state))
    }

    private fun levelText(l: RiskLevel) = when (l) {
        RiskLevel.ALTO -> "ALTO"; RiskLevel.MODERADO -> "MODERADO"; RiskLevel.BAIXO -> "BAIXO"; RiskLevel.SEM_DADO -> "SEM DADO"
    }
    private fun levelColor(l: RiskLevel): Int = when (l) {
        RiskLevel.ALTO -> 0xFFDC2626.toInt(); RiskLevel.MODERADO -> 0xFFD97706.toInt(); RiskLevel.BAIXO -> 0xFF16A34A.toInt(); RiskLevel.SEM_DADO -> 0xFF64748B.toInt()
    }
    private fun risks(b: PdfReportBuilder, list: List<RiskIndicator>) = list.forEach { r ->
        b.bullet("${r.title}: ${levelText(r.level)} (estimativa)", levelColor(r.level))
        b.paragraph(r.detail, indent = 12f)
        b.paragraph(r.basis, "small", indent = 12f)
    }

    fun agroReport(
        context: Context,
        state: BrState,
        station: WeatherStationEntity?,
        daily: List<DailyForecastEntity>,
        series: AgroSeries?,
        inmetAlerts: List<WeatherAlertEntity>,
        month: Int
    ): File? {
        val b = PdfReportBuilder("Boletim agro • Cana e citros • ${state.displayName}", "Cidade: ${station?.name ?: "—"}")
        b.section("Previsão de 7 dias")
        if (daily.isEmpty()) b.paragraph("Previsão ainda não carregada.", "small")
        else b.table(listOf("Dia", "Condição", "Mín / Máx", "Chuva", "Prob."), forecastRows(daily.take(7)), listOf(1.1f, 2.4f, 1f, 0.9f, 0.6f))

        if (series == null) {
            b.section("Índices de cana e citros")
            b.paragraph("Série agrometeorológica (Open-Meteo, últimos 30 dias + previsão) indisponível no momento da geração. " +
                "Os índices não são estimados sem dado.", "small")
        } else {
            val wb = CanaCitrosIndices.waterBalance(series)
            b.section("Balanço hídrico (chuva − ET0)")
            fun line(label: String, r: Double?, e: Double?, bal: Double?) =
                b.bullet("$label: chuva ${r?.let { "${f1(it)} mm" } ?: "—"}, ET0 ${e?.let { "${f1(it)} mm" } ?: "—"}, balanço ${bal?.let { (if (it > 0) "+" else "") + f1(it) + " mm" } ?: "—"}")
            line("Últimos ${wb.daysPast} dias", wb.rainPast7, wb.et0Past7, wb.balancePast7)
            if (wb.daysPast30 > 0) line("Últimos ${wb.daysPast30} dias", wb.rainPast30, wb.et0Past30, wb.balancePast30)
            line("Próximos 7 dias (previsão)", wb.rainNext7, wb.et0Next7, wb.balanceNext7)
            b.paragraph("ET0 = evapotranspiração de referência FAO-56 (Open-Meteo). Não considera solo, irrigação nem coeficiente da cultura.", "small")

            val harvest = CanaCitrosIndices.harvestOutlook(series)
            b.section("Cana-de-açúcar")
            b.paragraph("Colheita/operações: ${if (harvest.consecutiveDryFromToday > 0) "${harvest.consecutiveDryFromToday} dia(s) seco(s) seguidos a partir de hoje" else "chuva prevista hoje"}.", "bold")
            b.table(listOf("Data", "Chuva", "Prob.", "Dia seco?"), harvest.days.map {
                listOf(CanaCitrosIndices.br(it.date).take(5), it.rainMm?.let { r -> "${f1(r)} mm" } ?: "—", it.prob?.let { p -> "$p%" } ?: "—", if (it.dry) "sim" else "não")
            })
            risks(b, listOf(CanaCitrosIndices.sugarcaneRust(series), CanaCitrosIndices.heatDroughtStress(series, wb), CanaCitrosIndices.fireRisk(series, wb)))

            b.section("Citros")
            risks(b, listOf(CanaCitrosIndices.citrusBlackSpot(series, month), CanaCitrosIndices.psyllidActivity(series)))
            b.paragraph("Para manejo de pragas e doenças dos citros consulte o Fundecitrus (fundecitrus.com.br).", "small")

            b.section("Cana e citros — geada e pulverização")
            risks(b, listOf(CanaCitrosIndices.frost(series)))
            val spray = CanaCitrosIndices.sprayOutlook(series)
            val good = spray.windows.filter { it.hours >= 2 }
            if (good.isEmpty()) b.bullet("Pulverização: sem janela adequada de 2 h ou mais nas próximas ${spray.evaluated} h.")
            else good.take(5).forEach { w -> b.bullet("Janela de pulverização: ${CanaCitrosIndices.br(w.start)} → ${CanaCitrosIndices.br(w.end).takeLast(5)} (${w.hours} h)") }
            b.paragraph("Critério: temperatura < 30 °C, umidade ≥ 55 %, vento 3–10 km/h e sem chuva na hora e nas 2 h seguintes (recomendação geral; confira a bula).", "small")
            b.paragraph("Série Open-Meteo obtida em ${PdfReportBuilder.brt(series.fetchedAt)}.", "small")
        }

        b.section("Avisos oficiais INMET • ${state.uf}")
        if (inmetAlerts.isEmpty()) b.paragraph("Sem avisos ativos do INMET carregados para ${state.uf}.")
        inmetAlerts.take(8).forEach { a -> b.bullet("${a.title} — ${a.regionName}") }

        b.section("Fontes")
        b.bullet("Previsão e série agrometeorológica: Open-Meteo (open-meteo.com), CC BY 4.0.")
        b.bullet("Avisos: INMET — Instituto Nacional de Meteorologia (alertas2.inmet.gov.br).")
        b.paragraph("Todos os índices são estimativas indicativas a partir de previsão numérica para o ponto da cidade. " +
            "Não substituem medição no talhão nem a recomendação do engenheiro agrônomo. Horários em BRT.", "small")
        return b.write(reportFile(context, "Agro", state))
    }
}
