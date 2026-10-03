package com.example.domain

import com.example.data.repository.AgroDay
import com.example.data.repository.AgroHour
import com.example.data.repository.AgroSeries

/**
 * Índices INDICATIVOS para cana-de-açúcar e citros calculados SOMENTE com a série Open-Meteo
 * (previsão numérica + 7 dias passados do modelo na série horária e 30 na diária). Não são diagnóstico nem recomendação técnica:
 * cada resultado traz a base/fonte do critério para o produtor conferir com o agrônomo.
 */
enum class RiskLevel { BAIXO, MODERADO, ALTO, SEM_DADO }

data class RiskIndicator(
    val id: String,
    val title: String,
    val level: RiskLevel,
    /** Explicação curta com os números usados (ex.: "maior período úmido: 14 h a 23 °C"). */
    val detail: String,
    /** Base / fonte do critério. */
    val basis: String
)

data class WaterBalance(
    val rainPast7: Double?, val et0Past7: Double?, val balancePast7: Double?,
    val rainNext7: Double?, val et0Next7: Double?, val balanceNext7: Double?,
    val daysPast: Int,
    /** Acumulado de até 30 dias completos (ver [daysPast30] para o número real de dias). */
    val rainPast30: Double? = null, val et0Past30: Double? = null, val balancePast30: Double? = null,
    val daysPast30: Int = 0
)

data class SprayHour(val time: String, val ok: Boolean, val reason: String?)
data class SprayWindow(val start: String, val end: String, val hours: Int)
data class SprayOutlook(val hours: List<SprayHour>, val windows: List<SprayWindow>, val evaluated: Int)

data class DryDay(val date: String, val rainMm: Double?, val prob: Int?, val dry: Boolean)
data class HarvestOutlook(val days: List<DryDay>, val consecutiveDryFromToday: Int)

object CanaCitrosIndices {

    // ---- Limiares (com fonte) ----
    /** ANDEF/ESALQ – Manual de Tecnologia de Aplicação: UR mín. 55 %, vento 3–10 km/h, temperatura < 30 °C. */
    const val SPRAY_MAX_TEMP = 30.0
    const val SPRAY_MIN_RH = 55.0
    const val SPRAY_MIN_WIND = 3.0
    const val SPRAY_MAX_WIND = 10.0
    /** Sem chuva na hora e nas 2 h seguintes (critério do app para não lavar o produto). */
    const val SPRAY_RAIN_FREE_HOURS = 2
    const val RAIN_HOUR_MM = 0.2
    const val RAIN_PROB_BLOCK = 50

    /** Proxy de molhamento foliar: hora com UR ≥ 90 % ou chuva ≥ 0,2 mm (o modelo não fornece molhamento). */
    const val WET_RH = 90.0

    /** Dia seco para operação/colheita: chuva prevista < 1 mm e probabilidade < 50 % (critério do app). */
    const val DRY_DAY_MM = 1.0

    /** Geada: temperatura mínima do ar (2 m) ≤ 3 °C já permite geada na relva/folhas (critério usual de alerta). */
    const val FROST_ALERT_TMIN = 3.0
    const val FROST_HIGH_TMIN = 1.0

    /** Umidade do ar: < 30 % estado de atenção; < 20 % alerta (escala usada pelo CGE-SP e Defesa Civil). */
    const val FIRE_RH_ATTENTION = 30.0
    const val FIRE_RH_ALERT = 20.0

    // ---------------------------------------------------------------------------------------

    fun waterBalance(s: AgroSeries): WaterBalance {
        val t = s.todayIndex
        if (t < 0) return WaterBalance(null, null, null, null, null, null, 0)
        val past = s.days.subList(maxOf(0, t - 7), t)
        val next = s.days.subList(t, minOf(s.days.size, t + 7))
        fun sum(list: List<AgroDay>, f: (AgroDay) -> Double?): Double? {
            val v = list.mapNotNull(f)
            return if (v.isEmpty() || v.size < list.size) null else round1(v.sum())
        }
        val rp = sum(past) { it.rainMm }; val ep = sum(past) { it.et0Mm }
        val rn = sum(next) { it.rainMm }; val en = sum(next) { it.et0Mm }
        val acc30 = RainAccumulationCalc.compute(s.days, t, 30)
        return WaterBalance(
            rp, ep, if (rp != null && ep != null) round1(rp - ep) else null,
            rn, en, if (rn != null && en != null) round1(rn - en) else null,
            past.size,
            acc30.rainMm, acc30.et0Mm, acc30.balanceMm, acc30.days
        )
    }

    fun sprayOutlook(s: AgroSeries, horizonHours: Int = 48): SprayOutlook {
        val start = s.nowIndex
        if (start < 0) return SprayOutlook(emptyList(), emptyList(), 0)
        val end = minOf(s.hours.size, start + horizonHours)
        val list = (start until end).map { i ->
            val h = s.hours[i]
            val reason = sprayBlockReason(s.hours, i)
            SprayHour(h.time, reason == null, reason)
        }
        val windows = mutableListOf<SprayWindow>()
        var runStart = -1
        list.forEachIndexed { k, sh ->
            if (sh.ok && runStart < 0) runStart = k
            val closes = runStart >= 0 && (!sh.ok || k == list.lastIndex)
            if (closes) {
                val last = if (sh.ok) k else k - 1
                windows += SprayWindow(list[runStart].time, list[last].time, last - runStart + 1)
                runStart = -1
            }
        }
        return SprayOutlook(list, windows, list.size)
    }

    /** null = hora adequada; senão o principal motivo de bloqueio. */
    fun sprayBlockReason(hours: List<AgroHour>, i: Int): String? {
        val h = hours[i]
        val t = h.temp; val rh = h.rh; val w = h.windKmh
        if (t == null || rh == null || w == null) return "sem dado"
        for (k in i..minOf(hours.lastIndex, i + SPRAY_RAIN_FREE_HOURS)) {
            val r = hours[k].rainMm ?: 0.0
            val p = hours[k].prob ?: 0
            if (r >= RAIN_HOUR_MM || p >= RAIN_PROB_BLOCK) return "chuva prevista"
        }
        if (w > SPRAY_MAX_WIND) return "vento forte"
        if (w < SPRAY_MIN_WIND) return "vento fraco (inversão)"
        if (t >= SPRAY_MAX_TEMP) return "calor"
        if (rh < SPRAY_MIN_RH) return "ar seco"
        return null
    }

    fun harvestOutlook(s: AgroSeries, days: Int = 7): HarvestOutlook {
        val t = s.todayIndex
        if (t < 0) return HarvestOutlook(emptyList(), 0)
        val list = s.days.subList(t, minOf(s.days.size, t + days)).map {
            val dry = it.rainMm != null && it.rainMm < DRY_DAY_MM && (it.prob ?: 0) < RAIN_PROB_BLOCK
            DryDay(it.date, it.rainMm, it.prob, dry)
        }
        return HarvestOutlook(list, list.takeWhile { it.dry }.size)
    }

    /** Maior sequência de horas "úmidas" (proxy de molhamento) e temperatura média nela. */
    data class WetSpell(val hours: Int, val meanTemp: Double?)

    fun longestWetSpell(hours: List<AgroHour>): WetSpell {
        var best = WetSpell(0, null)
        var run = 0; var tSum = 0.0; var tN = 0
        fun close() {
            if (run > best.hours) best = WetSpell(run, if (tN > 0) round1(tSum / tN) else null)
            run = 0; tSum = 0.0; tN = 0
        }
        for (h in hours) {
            val wet = (h.rh ?: 0.0) >= WET_RH || (h.rainMm ?: 0.0) >= RAIN_HOUR_MM
            if (wet) { run++; h.temp?.let { tSum += it; tN++ } } else close()
        }
        close()
        return best
    }

    /** Janela usada nos índices de doença: últimos 7 dias do modelo + próximas 48 h. */
    fun diseaseWindow(s: AgroSeries): List<AgroHour> {
        if (s.nowIndex < 0) return emptyList()
        return s.hours.subList(maxOf(0, s.nowIndex - 7 * 24), minOf(s.hours.size, s.nowIndex + 48))
    }

    fun citrusBlackSpot(s: AgroSeries, month: Int): RiskIndicator {
        val win = diseaseWindow(s)
        val base = "Base: Fundecitrus (pinta preta favorecida por chuva e molhamento prolongado; período crítico set–abr em SP) " +
            "e estudo ESALQ/Fundecitrus: conídios germinam com ≥ 36 h de molhamento, ótimo ≈ 25 °C. " +
            "Molhamento estimado por UR ≥ 90 % ou chuva (Open-Meteo)."
        if (win.isEmpty()) return RiskIndicator("pinta_preta", "Pinta preta (citros)", RiskLevel.SEM_DADO, "Sem série horária.", base)
        val ws = longestWetSpell(win)
        val rain7 = win.take(minOf(win.size, 7 * 24)).sumOf { it.rainMm ?: 0.0 }
        val critical = month in listOf(9, 10, 11, 12, 1, 2, 3, 4)
        val warm = ws.meanTemp != null && ws.meanTemp in 20.0..30.0
        val level = when {
            ws.hours >= 36 && warm -> RiskLevel.ALTO
            (ws.hours >= 12 && ws.meanTemp != null && ws.meanTemp in 15.0..32.0) || rain7 >= 25.0 -> RiskLevel.MODERADO
            else -> RiskLevel.BAIXO
        }
        val detail = "Maior período úmido: ${ws.hours} h" + (ws.meanTemp?.let { " a ${fmt(it)} °C" } ?: "") +
            " • chuva 7 dias: ${fmt(rain7)} mm" + if (critical) " • mês dentro do período crítico" else ""
        return RiskIndicator("pinta_preta", "Pinta preta (citros)", level, detail, base)
    }

    fun psyllidActivity(s: AgroSeries): RiskIndicator {
        val base = "Base: Liu & Tsai (2000) – crescimento populacional ótimo do psilídeo Diaphorina citri entre 25 e 28 °C; " +
            "faixa favorável 18–30 °C (estudo de biologia do inseto em diferentes temperaturas, J. Appl. Entomol.). Risco geral de atividade: faça o monitoramento do Fundecitrus (Alerta Psilídeo, armadilhas amarelas)."
        if (s.nowIndex < 0) return RiskIndicator("psilideo", "Psilídeo / greening (citros)", RiskLevel.SEM_DADO, "Sem série horária.", base)
        val next = s.hours.subList(s.nowIndex, minOf(s.hours.size, s.nowIndex + 48)).mapNotNull { it.temp }
        if (next.isEmpty()) return RiskIndicator("psilideo", "Psilídeo / greening (citros)", RiskLevel.SEM_DADO, "Sem temperatura prevista.", base)
        val mean = round1(next.average())
        val level = when {
            mean in 25.0..28.0 -> RiskLevel.ALTO
            mean in 18.0..30.0 -> RiskLevel.MODERADO
            else -> RiskLevel.BAIXO
        }
        return RiskIndicator("psilideo", "Psilídeo / greening (citros)", level, "Temperatura média prevista 48 h: ${fmt(mean)} °C", base)
    }

    fun sugarcaneRust(s: AgroSeries): RiskIndicator {
        val base = "Base: estudos com Puccinia kuehnii (ferrugem alaranjada) – infecção com ≥ 8 h de molhamento, mais severa a partir de 12 h, " +
            "ótimo ≈ 25 °C (Martins/ESALQ; Summa Phytopathologica). Molhamento estimado por UR ≥ 90 % ou chuva."
        val win = diseaseWindow(s)
        if (win.isEmpty()) return RiskIndicator("ferrugem", "Ferrugem / fungos (cana)", RiskLevel.SEM_DADO, "Sem série horária.", base)
        val ws = longestWetSpell(win)
        val level = when {
            ws.hours >= 12 && ws.meanTemp != null && ws.meanTemp in 20.0..26.0 -> RiskLevel.ALTO
            ws.hours >= 8 && ws.meanTemp != null && ws.meanTemp in 17.0..28.0 -> RiskLevel.MODERADO
            else -> RiskLevel.BAIXO
        }
        return RiskIndicator(
            "ferrugem", "Ferrugem / fungos (cana)", level,
            "Maior período úmido: ${ws.hours} h" + (ws.meanTemp?.let { " a ${fmt(it)} °C" } ?: ""), base
        )
    }

    fun frost(s: AgroSeries): RiskIndicator {
        val base = "Base: critério usual de alerta – mínima do ar a 2 m ≤ 3 °C já permite geada nas folhas/relva (critério agrometeorológico usual). " +
            "Cana e citros jovens são os mais sensíveis."
        val t = s.todayIndex
        val mins = if (t < 0) emptyList() else s.days.subList(t, minOf(s.days.size, t + 7)).mapNotNull { d -> d.tMin?.let { d.date to it } }
        if (mins.isEmpty()) return RiskIndicator("geada", "Geada (cana e citros)", RiskLevel.SEM_DADO, "Sem mínima prevista.", base)
        val (date, tmin) = mins.minBy { it.second }
        val level = when {
            tmin <= FROST_HIGH_TMIN -> RiskLevel.ALTO
            tmin <= FROST_ALERT_TMIN -> RiskLevel.MODERADO
            else -> RiskLevel.BAIXO
        }
        return RiskIndicator("geada", "Geada (cana e citros)", level, "Menor mínima em 7 dias: ${fmt(tmin)} °C (${br(date)})", base)
    }

    fun heatDroughtStress(s: AgroSeries, wb: WaterBalance): RiskIndicator {
        val base = "Base: limiares indicativos do app – máxima ≥ 34 °C (calor) e balanço chuva − ET0 dos últimos 7 dias < −15 mm (déficit). " +
            "ET0 FAO Penman-Monteith calculada pela Open-Meteo."
        val t = s.todayIndex
        val tmax = if (t < 0) null else s.days.subList(t, minOf(s.days.size, t + 7)).mapNotNull { it.tMax }.maxOrNull()
        val bal = wb.balancePast7
        if (tmax == null && bal == null) return RiskIndicator("estresse", "Estresse por calor/seca", RiskLevel.SEM_DADO, "Sem dados.", base)
        val hot = tmax != null && tmax >= 34.0
        val dry = bal != null && bal < -15.0
        val level = when { hot && dry -> RiskLevel.ALTO; hot || dry -> RiskLevel.MODERADO; else -> RiskLevel.BAIXO }
        val detail = listOfNotNull(tmax?.let { "Máxima em 7 dias: ${fmt(it)} °C" }, bal?.let { "Balanço 7 dias: ${fmt(it)} mm" }).joinToString(" • ")
        return RiskIndicator("estresse", "Estresse por calor/seca", level, detail, base)
    }

    fun fireRisk(s: AgroSeries, wb: WaterBalance): RiskIndicator {
        val base = "Base: umidade do ar < 30 % = atenção e < 20 % = alerta (escala usada pelo CGE-SP e Defesa Civil); " +
            "vegetação seca (palha de cana) aumenta o risco quando não choveu nos últimos 7 dias."
        if (s.nowIndex < 0) return RiskIndicator("incendio", "Risco de incêndio (palhada/pomar)", RiskLevel.SEM_DADO, "Sem série horária.", base)
        val minRh = s.hours.subList(s.nowIndex, minOf(s.hours.size, s.nowIndex + 48)).mapNotNull { it.rh }.minOrNull()
            ?: return RiskIndicator("incendio", "Risco de incêndio (palhada/pomar)", RiskLevel.SEM_DADO, "Sem umidade prevista.", base)
        val dryWeek = (wb.rainPast7 ?: Double.MAX_VALUE) < 1.0
        val level = when {
            minRh < FIRE_RH_ALERT || (minRh < FIRE_RH_ATTENTION && dryWeek) -> RiskLevel.ALTO
            minRh < FIRE_RH_ATTENTION -> RiskLevel.MODERADO
            else -> RiskLevel.BAIXO
        }
        val detail = "Menor umidade prevista 48 h: ${fmt(minRh)} %" + (wb.rainPast7?.let { " • chuva 7 dias: ${fmt(it)} mm" } ?: "")
        return RiskIndicator("incendio", "Risco de incêndio (palhada/pomar)", level, detail, base)
    }

    // ---- util ----
    private fun round1(v: Double) = Math.round(v * 10.0) / 10.0
    fun fmt(v: Double): String = String.format(java.util.Locale("pt", "BR"), "%.1f", v)
    /** "2026-10-03" → "03/10"; "2026-10-03T14:00" → "03/10 14h". */
    fun br(iso: String): String {
        val d = iso.take(10).split("-")
        val day = if (d.size == 3) "${d[2]}/${d[1]}" else iso
        return if (iso.length >= 13) "$day ${iso.substring(11, 13)}h" else day
    }
}
