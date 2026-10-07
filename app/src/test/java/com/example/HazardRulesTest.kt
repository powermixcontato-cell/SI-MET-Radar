package com.example

import com.example.data.local.entity.WeatherAlertEntity
import com.example.data.remote.AnaReading
import com.example.data.remote.ForecastRiskPoint
import com.example.data.remote.GlofasSeries
import com.example.domain.BrState
import com.example.domain.FloodGauges
import com.example.domain.FloodLevel
import com.example.domain.HazardCategory
import com.example.domain.HazardKind
import com.example.domain.HazardRules
import com.example.domain.HazardSeverity
import com.example.domain.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v5.1: regras de alertas (3 categorias) e avaliação de rios — sem rede. */
class HazardRulesTest {

    private fun inmet(id: String, title: String, sev: String, region: String, desc: String = "") = WeatherAlertEntity(
        id = "inmet_$id", regionId = region, regionName = "x", severity = sev, title = title,
        description = "$desc\nPeríodo: 07/10 10:00 – 08/10 10:00 (BRT)\nFonte: INMET • https://alertas2.inmet.gov.br/1",
        radarStationSource = "INMET • https://alertas2.inmet.gov.br/1", dbzPeak = 0, timestamp = 0L
    )

    @Test
    fun inmetAlertsAreClassifiedAndFilteredByState() {
        val list = listOf(
            inmet("1", "Tempestade (Perigo)", "ALERTA_LARANJA", "PR,RS#Sudoeste Paranaense|Noroeste Rio-grandense",
                "Chuva entre 30 e 60 mm/h, ventos intensos (60-100 km/h) e queda de granizo."),
            inmet("2", "Vendaval (Perigo Potencial)", "ALERTA_AMARELO", "SP#Campinas"),
            inmet("3", "Baixa umidade (Perigo Potencial)", "ALERTA_AMARELO", "RS#Sudoeste Rio-grandense")
        )
        val rs = HazardRules.fromInmet(list, BrState.RS)
        assertEquals(setOf(HazardCategory.ENCHENTES, HazardCategory.VENDAVAIS, HazardCategory.GRANIZO), rs.map { it.category }.toSet())
        assertTrue(rs.all { it.kind == HazardKind.OFICIAL && it.severity == HazardSeverity.LARANJA })
        assertEquals("Noroeste Rio-grandense (RS)", rs.first().area)
        assertEquals("07/10 10:00 – 08/10 10:00 (BRT)", rs.first().validity)
        // baixa umidade não entra em nenhuma das 3 categorias
        assertTrue(rs.none { it.id.startsWith("inmet_3") })
        val sp = HazardRules.fromInmet(list, BrState.SP)
        assertEquals(listOf(HazardCategory.VENDAVAIS), sp.map { it.category })
    }

    @Test
    fun forecastIndicatorsUseDocumentedThresholds() {
        val p = ForecastRiskPoint(
            lat = -30.0, lon = -51.0, dates = listOf("2026-10-07", "2026-10-08", "2026-10-09"),
            gustMax = listOf(40.0, 85.0, 30.0), rainSum = listOf(10.0, 60.0, 5.0), dailyCode = listOf(3, 95, 3),
            capeMax = listOf(500.0, 2600.0, 100.0), thunderDays = listOf(null, 95, null)
        )
        val out = HazardRules.fromForecast(BrState.RS, listOf(HazardRules.City("Porto Alegre", -30.0, -51.0)), listOf(p))
        val byCat = out.associateBy { it.category }
        assertEquals(HazardSeverity.LARANJA, byCat[HazardCategory.VENDAVAIS]?.severity)
        assertEquals(HazardSeverity.AMARELO, byCat[HazardCategory.ENCHENTES]?.severity)
        assertEquals(HazardSeverity.AMARELO, byCat[HazardCategory.GRANIZO]?.severity)
        assertTrue(out.all { it.kind == HazardKind.INDICADOR_PREVISAO })
    }

    @Test
    fun calmForecastProducesNoAlerts() {
        val p = ForecastRiskPoint(-25.0, -49.0, listOf("2026-10-07"), listOf(20.0), listOf(1.0), listOf(1), listOf(100.0), listOf(null))
        assertTrue(HazardRules.fromForecast(BrState.PR, listOf(HazardRules.City("Curitiba", -25.0, -49.0)), listOf(p)).isEmpty())
    }

    @Test
    fun guaibaObservedAboveOfficialFloodStage() {
        val g = FloodGauges.all.first { it.id == "rs_guaiba" }
        val ana = listOf(AnaReading("2026-10-06 00:00:00", 280.0), AnaReading("2026-10-07 00:00:00", 310.0))
        val s = FloodGauges.evaluate(g, null, "offline", ana, null)
        assertEquals(FloodLevel.MUITO_ALTO, s.observedLevel)
        assertEquals(30.0, s.delta24hCm!!, 0.01)
        assertEquals(FloodLevel.SEM_DADO, s.modelLevel)
        val alerts = HazardRules.fromRivers(BrState.RS, listOf(s))
        assertEquals(1, alerts.size)
        assertEquals(HazardKind.OBSERVADO_RIO, alerts[0].kind)
        assertEquals(HazardSeverity.VERMELHO, alerts[0].severity)
    }

    @Test
    fun glofasPeakComparedWithReanalysisThresholds() {
        val g = FloodGauges.all.first { it.id == "rs_taquari_lajeado" }
        val dates = (1..10).map { "2026-10-%02d".format(it) }
        val mean = listOf(500.0, 600.0, 700.0, 800.0, 1000.0, 3000.0, 6000.0, 5000.0, 4000.0, 3000.0)
        val s = GlofasSeries(-29.475, -51.975, dates, mean, mean, mean.map { it * 1.3 }, todayIndex = 3)
        val st = FloodGauges.evaluate(g, s, null, emptyList(), null)
        assertEquals(800.0, st.todayQ!!, 0.01)
        assertEquals(6000.0, st.peak7Mean!!, 0.01)
        assertEquals(FloodLevel.ALERTA, st.modelLevel) // q5 ≈ 5714 ≤ 6000 < q20 ≈ 7378
        assertEquals(Trend.SUBINDO, st.trend)
    }
}
