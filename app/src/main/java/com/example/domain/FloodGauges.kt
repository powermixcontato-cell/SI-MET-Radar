package com.example.domain

import com.example.data.remote.AnaReading
import com.example.data.remote.GlofasSeries
import kotlin.math.roundToInt

/** Cota oficial verificada (m), com a fonte pública onde foi conferida. */
data class OfficialStage(val alertCm: Double?, val floodCm: Double?, val source: String)

/**
 * Ponto de monitoramento de rio. O ponto GloFAS é a célula de 0,05° com maior vazão perto da cidade
 * (conferido com a Open-Meteo Flood API em 07/10/2026). Estação ANA = telemetria da Rede Hidrometeorológica.
 */
data class FloodGauge(
    val id: String,
    val state: BrState,
    val river: String,
    val place: String,
    val glofasLat: Double,
    val glofasLon: Double,
    val anaCode: String? = null,
    val anaName: String? = null,
    val official: OfficialStage? = null,
    val note: String? = null
)

enum class FloodLevel(val rank: Int, val label: String) {
    SEM_DADO(-1, "Sem dado"),
    NORMAL(0, "Normal"),
    ATENCAO(1, "Atenção"),
    ALERTA(2, "Alerta"),
    MUITO_ALTO(3, "Risco muito alto")
}

enum class Trend(val label: String, val arrow: String) { SUBINDO("subindo", "↑"), ESTAVEL("estável", "→"), DESCENDO("descendo", "↓") }

data class GaugeStatus(
    val gauge: FloodGauge,
    val glofas: GlofasSeries?,
    val glofasError: String?,
    val ana: List<AnaReading>,
    val anaError: String?,
    /** Vazão de hoje (m³/s, controle GloFAS). */
    val todayQ: Double?,
    /** Pico previsto 7 dias (média do conjunto). */
    val peak7Mean: Double?,
    val peak7Date: String?,
    /** Pico previsto 7 dias no membro mais alto do conjunto (cenário pessimista). */
    val peak7Max: Double?,
    val peak30Mean: Double?,
    val trend: Trend?,
    val modelLevel: FloodLevel,
    val modelReason: String,
    val observedLevel: FloodLevel,
    val observedReason: String?,
    val lastLevelCm: Double?,
    val lastLevelTime: String?,
    val delta24hCm: Double?
) {
    val overall: FloodLevel get() = if (observedLevel.rank > modelLevel.rank) observedLevel else modelLevel
}

object FloodGauges {

    val all: List<FloodGauge> = listOf(
        // ---------------- Rio Grande do Sul ----------------
        FloodGauge(
            "rs_guaiba", BrState.RS, "Lago Guaíba", "Porto Alegre (Cais Mauá)", -30.175, -51.325,
            anaCode = "87450004", anaName = "Cais Mauá C6",
            official = OfficialStage(255.0, 300.0, "Governo do RS: cota de alerta 2,55 m e de inundação 3,00 m no Cais Mauá (estado.rs.gov.br)"),
            note = "Recorde: 5,35 m em 05/05/2024 (enchente de 2024)."
        ),
        FloodGauge(
            "rs_taquari_lajeado", BrState.RS, "Rio Taquari", "Lajeado / Estrela", -29.475, -51.975,
            anaCode = "86879300", anaName = "Estrela",
            official = OfficialStage(null, 1900.0, "Cota de inundação de 19 m em Estrela (Defesa Civil RS / ANA, citada pela imprensa em 2025)")
        ),
        FloodGauge("rs_taquari_mucum", BrState.RS, "Rio Taquari", "Muçum / Encantado", -29.225, -51.925, anaCode = "86510000", anaName = "Muçum"),
        FloodGauge("rs_jacui_cachoeira", BrState.RS, "Rio Jacuí", "Cachoeira do Sul / Rio Pardo", -29.975, -52.725, anaCode = "85643990", anaName = "Cachoeira do Sul"),
        FloodGauge("rs_sinos", BrState.RS, "Rio dos Sinos", "São Leopoldo / Canoas", -29.925, -51.275, anaCode = "87382000", anaName = "São Leopoldo"),
        FloodGauge("rs_cai", BrState.RS, "Rio Caí", "Montenegro", -29.825, -51.425, anaCode = "87270000", anaName = "Passo Montenegro"),
        FloodGauge("rs_uruguai_uruguaiana", BrState.RS, "Rio Uruguai", "Uruguaiana", -29.675, -57.075, anaCode = "77150000", anaName = "Uruguaiana"),
        // ---------------- Paraná ----------------
        FloodGauge(
            "pr_iguacu_uniao", BrState.PR, "Rio Iguaçu", "União da Vitória", -26.225, -51.075,
            anaCode = "65310001", anaName = "UHE G. B. Munhoz – União da Vitória",
            note = "Cheias históricas: 1983 (10,42 m), 1992, 2014 e 2023 (régua municipal; a estação ANA usa outra referência)."
        ),
        FloodGauge("pr_iguacu_curitiba", BrState.PR, "Rio Iguaçu", "Curitiba / Araucária / Balsa Nova", -25.625, -49.375, anaCode = "65028000", anaName = "Balsa Nova"),
        FloodGauge("pr_iguacu_foz", BrState.PR, "Rio Iguaçu", "Foz do Iguaçu", -25.575, -54.425),
        FloodGauge("pr_tibagi", BrState.PR, "Rio Tibagi", "Londrina / Jataizinho", -23.225, -51.025),
        FloodGauge("pr_ivai", BrState.PR, "Rio Ivaí", "Médio Ivaí (Ivaiporã)", -24.075, -51.625),
        FloodGauge("pr_parana_guaira", BrState.PR, "Rio Paraná", "Guaíra", -24.125, -54.375),
        // ---------------- São Paulo ----------------
        FloodGauge("sp_ribeira_registro", BrState.SP, "Rio Ribeira de Iguape", "Registro", -24.475, -47.875, anaCode = "81683000", anaName = "Registro"),
        FloodGauge("sp_tiete_sp", BrState.SP, "Rio Tietê", "São Paulo / Osasco", -23.525, -46.725),
        FloodGauge("sp_piracicaba", BrState.SP, "Rio Piracicaba", "Piracicaba", -22.725, -47.775),
        FloodGauge("sp_paraiba_sjc", BrState.SP, "Rio Paraíba do Sul", "São José dos Campos", -23.125, -45.825)
    )

    fun forState(state: BrState): List<FloodGauge> = all.filter { it.state == state }

    private fun fmtQ(v: Double): String = if (v >= 100) "${v.roundToInt()}" else String.format(java.util.Locale("pt", "BR"), "%.1f", v)

    /** Avalia a série GloFAS + leituras ANA do ponto. Nada é estimado sem dado: sem série → SEM_DADO. */
    fun evaluate(g: FloodGauge, s: GlofasSeries?, glofasError: String?, ana: List<AnaReading>, anaError: String?): GaugeStatus {
        var todayQ: Double? = null; var peak7: Double? = null; var peak7Date: String? = null
        var peak7Max: Double? = null; var peak30: Double? = null; var trend: Trend? = null
        var level = FloodLevel.SEM_DADO
        var reason = glofasError?.let { "GloFAS indisponível ($it)" } ?: "GloFAS sem dados para este ponto"
        if (s != null && s.todayIndex >= 0) {
            val t = s.todayIndex
            todayQ = s.discharge.getOrNull(t) ?: s.ensembleMean.getOrNull(t)
            val r7 = (t until minOf(t + 7, s.dates.size))
            val means7 = r7.mapNotNull { i -> s.ensembleMean.getOrNull(i)?.let { i to it } }
            means7.maxByOrNull { it.second }?.let { peak7 = it.second; peak7Date = s.dates[it.first] }
            peak7Max = r7.mapNotNull { s.ensembleMax.getOrNull(it) }.maxOrNull()
            peak30 = (t until s.dates.size).mapNotNull { s.ensembleMean.getOrNull(it) }.maxOrNull()
            val in3 = s.ensembleMean.getOrNull(minOf(t + 3, s.dates.lastIndex))
            if (todayQ != null && in3 != null && todayQ > 0) {
                val ratio = in3 / todayQ
                trend = when { ratio > 1.10 -> Trend.SUBINDO; ratio < 0.90 -> Trend.DESCENDO; else -> Trend.ESTAVEL }
            }
            val th = FloodThresholds.byGauge[g.id]
            val p = peak7
            if (p == null) {
                level = FloodLevel.SEM_DADO; reason = "Previsão GloFAS sem valores para os próximos 7 dias"
            } else if (th == null) {
                level = FloodLevel.NORMAL
                reason = "Pico previsto (7 d): ${fmtQ(p)} m³/s. Sem limiar de referência calculado para este ponto."
            } else {
                level = when {
                    p >= th.q20 -> FloodLevel.MUITO_ALTO
                    p >= th.q5 -> FloodLevel.ALERTA
                    p >= th.q2 -> FloodLevel.ATENCAO
                    else -> FloodLevel.NORMAL
                }
                val extra = if (level == FloodLevel.NORMAL && (peak7Max ?: 0.0) >= th.q2)
                    " O membro mais alto do conjunto chega a ${fmtQ(peak7Max!!)} m³/s (cenário pessimista, acima da cheia de 2 anos)." else ""
                reason = "Pico previsto (7 d, média do conjunto): ${fmtQ(p)} m³/s • cheia típica de 2 anos ≈ ${fmtQ(th.q2)}, " +
                    "5 anos ≈ ${fmtQ(th.q5)}, 20 anos ≈ ${fmtQ(th.q20)} m³/s (reanálise GloFAS ${th.period}).$extra"
            }
        }
        // Observado (ANA)
        val last = ana.lastOrNull()
        var obs = FloodLevel.SEM_DADO
        var obsReason: String? = when {
            g.anaCode == null -> null
            anaError != null -> "Telemetria ANA indisponível ($anaError)"
            last == null -> "Telemetria ANA sem leituras recentes"
            else -> null
        }
        var delta: Double? = null
        if (last != null) {
            val ref = ana.lastOrNull { it.dateTime < last.dateTime && hoursBetween(it.dateTime, last.dateTime) >= 23.0 }
            delta = ref?.let { last.levelCm - it.levelCm }
            val off = g.official
            obs = FloodLevel.NORMAL
            if (off != null) {
                fun m(cm: Double) = String.format(java.util.Locale("pt", "BR"), "%.2f m", cm / 100)
                when {
                    off.floodCm != null && last.levelCm >= off.floodCm -> { obs = FloodLevel.MUITO_ALTO; obsReason = "Nível acima da cota de inundação (${m(off.floodCm)})." }
                    off.alertCm != null && last.levelCm >= off.alertCm -> { obs = FloodLevel.ALERTA; obsReason = "Nível acima da cota de alerta (${m(off.alertCm)})." }
                    else -> obsReason = "Abaixo das cotas oficiais (" +
                        listOfNotNull(off.alertCm?.let { "alerta ${m(it)}" }, off.floodCm?.let { "inundação ${m(it)}" }).joinToString(", ") + ")."
                }
            } else obsReason = "Sem cota oficial conferida para esta estação: veja a tendência."
        }
        return GaugeStatus(
            gauge = g, glofas = s, glofasError = glofasError, ana = ana, anaError = anaError,
            todayQ = todayQ, peak7Mean = peak7, peak7Date = peak7Date, peak7Max = peak7Max, peak30Mean = peak30,
            trend = trend, modelLevel = level, modelReason = reason,
            observedLevel = obs, observedReason = obsReason,
            lastLevelCm = last?.levelCm, lastLevelTime = last?.dateTime, delta24hCm = delta
        )
    }

    /** "yyyy-MM-dd HH:mm:ss" → horas entre dois instantes (mesmo fuso). */
    fun hoursBetween(a: String, b: String): Double {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        return try { ((f.parse(b.trim())!!.time - f.parse(a.trim())!!.time) / 3_600_000.0) } catch (_: Exception) { 0.0 }
    }
}
