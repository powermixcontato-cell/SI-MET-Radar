package com.example.domain

import com.example.data.local.entity.WeatherAlertEntity
import com.example.data.remote.ForecastRiskPoint
import com.example.data.repository.InmetAlertMapper
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

enum class HazardCategory(val label: String, val emoji: String) {
    ENCHENTES("Enchentes", "🌊"),
    VENDAVAIS("Ciclones e vendavais", "🌀"),
    GRANIZO("Granizo", "🧊")
}

enum class HazardSeverity(val rank: Int, val label: String, val argb: Long) {
    AMARELO(1, "Amarelo · atenção", 0xFFF2C200),
    LARANJA(2, "Laranja · alerta", 0xFFF57C00),
    VERMELHO(3, "Vermelho · grande perigo", 0xFFD32F2F)
}

/** Natureza do alerta: o app separa claramente aviso oficial de indicador calculado. */
enum class HazardKind(val label: String) {
    OFICIAL("Aviso oficial"),
    INDICADOR_PREVISAO("Indicador de previsão — não é aviso oficial"),
    INDICADOR_RIO("Indicador hidrológico (modelo GloFAS) — não é aviso oficial"),
    OBSERVADO_RIO("Nível observado (ANA) acima de cota oficial")
}

data class HazardAlert(
    val id: String,
    val category: HazardCategory,
    val state: BrState,
    val severity: HazardSeverity,
    val kind: HazardKind,
    val title: String,
    val area: String,
    /** Validade já formatada em horário de Brasília (BRT). */
    val validity: String,
    val detail: String,
    val source: String,
    val sourceUrl: String?
)

object HazardRules {
    private val brt: TimeZone = TimeZone.getTimeZone("America/Sao_Paulo")
    private val ptBr = Locale("pt", "BR")

    // Limiares dos indicadores (documentados na tela de Alertas e no resumo da versão).
    const val GUST_AMARELO = 60.0
    const val GUST_LARANJA = 80.0
    const val GUST_VERMELHO = 100.0
    const val RAIN_DAY_AMARELO = 50.0
    const val RAIN_DAY_LARANJA = 100.0
    const val RAIN_3D_VERMELHO = 150.0
    const val CAPE_FAVORAVEL = 2500.0
    const val CAPE_GRANIZO_FORTE = 2000.0

    private fun norm(s: String) = BrState.normalize(s)

    /** Categorias de um aviso INMET por palavras‑chave do evento/descrição. Avisos sem relação ficam de fora. */
    fun categoriesOfInmet(e: WeatherAlertEntity): Set<HazardCategory> {
        val t = norm(e.title)
        val d = norm(e.description)
        val all = "$t $d"
        val out = mutableSetOf<HazardCategory>()
        if (listOf("chuva", "alagament", "inunda", "enchente", "transbord", "cheia", "acumulado").any { all.contains(it) }) out += HazardCategory.ENCHENTES
        if (listOf("vendaval", "vento", "ciclone", "rajada", "ventania").any { all.contains(it) }) out += HazardCategory.VENDAVAIS
        if (all.contains("granizo")) out += HazardCategory.GRANIZO
        // "Tempestade" do INMET cita chuva, ventos e granizo na descrição; já coberto pelas palavras acima.
        return out
    }

    fun severityOfEntity(e: WeatherAlertEntity): HazardSeverity? = when (e.severity) {
        "ALERTA_VERMELHO" -> HazardSeverity.VERMELHO
        "ALERTA_LARANJA" -> HazardSeverity.LARANJA
        "ALERTA_AMARELO" -> HazardSeverity.AMARELO
        else -> null
    }

    private fun periodOf(e: WeatherAlertEntity): String =
        Regex("Período: ([^\\n]+)").find(e.description)?.groupValues?.get(1)?.trim() ?: "—"

    private fun linkOf(e: WeatherAlertEntity): String? =
        Regex("https?://\\S+").find(e.radarStationSource)?.value

    /** Avisos oficiais do INMET já gravados no banco, filtrados pelo estado. */
    fun fromInmet(entities: List<WeatherAlertEntity>, state: BrState): List<HazardAlert> =
        entities.filter { it.id.startsWith("inmet_") && state in InmetAlertMapper.statesOfEntity(it) }.flatMap { e ->
            val sev = severityOfEntity(e) ?: return@flatMap emptyList()
            val mesos = e.regionId.substringAfter('#', "").split('|').filter { it.isNotBlank() && it in state.mesoRegions }
            val area = if (mesos.isEmpty()) e.regionName else mesos.joinToString(", ") + " (${state.uf})"
            val body = e.description.lines().filterNot { it.startsWith("Período:") || it.startsWith("Fonte:") }.joinToString("\n").trim()
            categoriesOfInmet(e).map { c ->
                HazardAlert(
                    id = "${e.id}#${c.name}", category = c, state = state, severity = sev, kind = HazardKind.OFICIAL,
                    title = e.title, area = area, validity = periodOf(e), detail = body,
                    source = "INMET — Avisos meteorológicos", sourceUrl = linkOf(e) ?: "https://alertas2.inmet.gov.br"
                )
            }
        }

    private fun dayLabel(isoDate: String): String = try {
        val inF = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = brt }
        val out = SimpleDateFormat("EEE dd/MM", ptBr).apply { timeZone = brt }
        out.format(inF.parse(isoDate)!!)
    } catch (_: Exception) { isoDate }

    data class City(val name: String, val lat: Double, val lon: Double)

    private data class Hit(val city: String, val day: String, val sev: HazardSeverity, val text: String)

    private fun aggregate(
        state: BrState, cat: HazardCategory, hits: List<Hit>, title: (HazardSeverity) -> String, rule: String, dates: List<String>
    ): HazardAlert? {
        if (hits.isEmpty()) return null
        val sev = hits.maxBy { it.sev.rank }.sev
        val days = hits.map { it.day }.distinct().sorted()
        val validity = if (days.size == 1) "${dayLabel(days.first())} (00:00–23:59 BRT)"
        else "${dayLabel(days.first())} a ${dayLabel(days.last())} (BRT)"
        val cities = hits.groupBy { it.city }.map { (c, hs) -> c to hs.maxBy { it.sev.rank } }.sortedByDescending { it.second.sev.rank }
        val area = cities.joinToString(", ") { it.first }
        val detail = cities.joinToString("\n") { (c, h) -> "• $c — ${h.text} (${dayLabel(h.day)})" } + "\n\nCritério: $rule"
        return HazardAlert(
            id = "model_${state.uf}_${cat.name}_${dates.firstOrNull() ?: ""}", category = cat, state = state, severity = sev,
            kind = HazardKind.INDICADOR_PREVISAO, title = title(sev), area = area, validity = validity, detail = detail,
            source = "Open-Meteo (modelos de previsão, 3 dias)", sourceUrl = "https://open-meteo.com"
        )
    }

    /** Indicadores de risco calculados da previsão (próximos 3 dias) para as cidades do estado. */
    fun fromForecast(state: BrState, cities: List<City>, points: List<ForecastRiskPoint>): List<HazardAlert> {
        val wind = mutableListOf<Hit>(); val hail = mutableListOf<Hit>(); val rain = mutableListOf<Hit>()
        var dates: List<String> = emptyList()
        cities.zip(points).forEach { (c, p) ->
            dates = p.dates
            p.dates.forEachIndexed { i, d ->
                p.gustMax.getOrNull(i)?.let { g ->
                    val s = when { g >= GUST_VERMELHO -> HazardSeverity.VERMELHO; g >= GUST_LARANJA -> HazardSeverity.LARANJA; g >= GUST_AMARELO -> HazardSeverity.AMARELO; else -> null }
                    if (s != null) wind += Hit(c.name, d, s, "rajadas de até ${g.roundToInt()} km/h")
                }
                val code = p.thunderDays.getOrNull(i)
                val cape = p.capeMax.getOrNull(i)
                val hs = when {
                    (code == 96 || code == 99) && cape != null && cape >= CAPE_GRANIZO_FORTE -> HazardSeverity.LARANJA
                    code == 96 || code == 99 -> HazardSeverity.AMARELO
                    code == 95 && cape != null && cape >= CAPE_FAVORAVEL -> HazardSeverity.AMARELO
                    else -> null
                }
                if (hs != null) hail += Hit(
                    c.name, d, hs,
                    (if (code == 95) "trovoadas com instabilidade forte" else "trovoada com granizo no modelo (código $code)") +
                        (cape?.let { ", CAPE ${it.roundToInt()} J/kg" } ?: "")
                )
                p.rainSum.getOrNull(i)?.let { r ->
                    val s = when { r >= RAIN_DAY_LARANJA -> HazardSeverity.LARANJA; r >= RAIN_DAY_AMARELO -> HazardSeverity.AMARELO; else -> null }
                    if (s != null) rain += Hit(c.name, d, s, "${r.roundToInt()} mm no dia")
                }
            }
            val total = p.rainSum.filterNotNull().sum()
            if (total >= RAIN_3D_VERMELHO && p.dates.isNotEmpty()) rain += Hit(c.name, p.dates.first(), HazardSeverity.VERMELHO, "${total.roundToInt()} mm em 3 dias")
        }
        return listOfNotNull(
            aggregate(state, HazardCategory.ENCHENTES, rain, { "Chuva volumosa prevista" },
                "chuva diária ≥ ${RAIN_DAY_AMARELO.toInt()} mm (amarelo), ≥ ${RAIN_DAY_LARANJA.toInt()} mm (laranja) ou ≥ ${RAIN_3D_VERMELHO.toInt()} mm em 3 dias (vermelho).", dates),
            aggregate(state, HazardCategory.VENDAVAIS, wind, { "Rajadas de vento fortes previstas" },
                "rajada máxima ≥ ${GUST_AMARELO.toInt()} km/h (amarelo), ≥ ${GUST_LARANJA.toInt()} km/h (laranja), ≥ ${GUST_VERMELHO.toInt()} km/h (vermelho).", dates),
            aggregate(state, HazardCategory.GRANIZO, hail, { "Condições para granizo" },
                "código de tempo 96/99 (trovoada com granizo) no modelo → amarelo, ou laranja se CAPE ≥ ${CAPE_GRANIZO_FORTE.toInt()} J/kg; trovoada (95) com CAPE ≥ ${CAPE_FAVORAVEL.toInt()} J/kg → amarelo. A previsão de granizo por modelo é incerta.", dates)
        )
    }

    /** Rios: indicador GloFAS (modelo) e/ou nível ANA acima de cota oficial verificada. */
    fun fromRivers(state: BrState, gauges: List<GaugeStatus>): List<HazardAlert> = gauges.filter { it.gauge.state == state }.flatMap { s ->
        val out = mutableListOf<HazardAlert>()
        val g = s.gauge
        val mSev = when (s.modelLevel) { FloodLevel.MUITO_ALTO -> HazardSeverity.VERMELHO; FloodLevel.ALERTA -> HazardSeverity.LARANJA; FloodLevel.ATENCAO -> HazardSeverity.AMARELO; else -> null }
        if (mSev != null) out += HazardAlert(
            id = "glofas_${g.id}", category = HazardCategory.ENCHENTES, state = state, severity = mSev, kind = HazardKind.INDICADOR_RIO,
            title = "${g.river}: vazão prevista elevada (${s.modelLevel.label})", area = g.place,
            validity = s.peak7Date?.let { "Pico previsto em ${dayLabel(it)} (próximos 7 dias)" } ?: "Próximos 7 dias",
            detail = s.modelReason, source = "GloFAS v4 via Open-Meteo Flood API", sourceUrl = "https://open-meteo.com/en/docs/flood-api"
        )
        val oSev = when (s.observedLevel) { FloodLevel.MUITO_ALTO -> HazardSeverity.VERMELHO; FloodLevel.ALERTA -> HazardSeverity.LARANJA; else -> null }
        if (oSev != null && s.lastLevelCm != null) out += HazardAlert(
            id = "ana_${g.id}", category = HazardCategory.ENCHENTES, state = state, severity = oSev, kind = HazardKind.OBSERVADO_RIO,
            title = "${g.river}: nível ${String.format(ptBr, "%.2f", s.lastLevelCm / 100)} m (${g.anaName})", area = g.place,
            validity = "Leitura de ${s.lastLevelTime ?: "—"} (BRT)",
            detail = (s.observedReason ?: "") + "\n" + (g.official?.source ?: ""),
            source = "ANA — telemetria (estação ${g.anaCode})", sourceUrl = "https://www.snirh.gov.br/hidrotelemetria/"
        )
        out
    }

    fun sort(list: List<HazardAlert>): List<HazardAlert> =
        list.sortedWith(compareByDescending<HazardAlert> { it.severity.rank }
            .thenByDescending { it.kind == HazardKind.OFICIAL || it.kind == HazardKind.OBSERVADO_RIO }.thenBy { it.category.ordinal })
}
