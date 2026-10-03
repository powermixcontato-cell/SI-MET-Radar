package com.example.domain

import com.example.data.repository.AgroDay
import com.example.data.repository.AgroSeries

/**
 * Chuva acumulada nos últimos N dias COMPLETOS (até ontem), a partir da série diária da Open-Meteo
 * (past_days=30). Valores do modelo, não pluviômetro.
 *
 * [days] = quantos dias realmente entraram na conta (pode ser < [requestedDays] se a API devolveu
 * menos histórico); a UI deve usar [days] no rótulo para nunca chamar de "30 dias" um acumulado menor.
 */
data class RainAccumulation(
    val requestedDays: Int,
    val days: Int,
    val rainMm: Double?,
    val et0Mm: Double?,
    val balanceMm: Double?,
    /** Dias com chuva ≥ 1 mm. */
    val rainyDays: Int,
    /** Dia mais chuvoso da janela (null se não houver dado). */
    val wettest: AgroDay?,
    /** Dias sem valor de chuva na janela (a soma considera só os dias com valor). */
    val missingDays: Int,
    /** Série diária da janela (mais antigo → mais recente), para o gráfico de barras. */
    val daily: List<AgroDay>
) {
    val complete: Boolean get() = days == requestedDays && missingDays == 0
    /** Rótulo correto, ex.: "Últimos 30 dias" ou "Últimos 12 dias (histórico disponível)". */
    val label: String get() = when {
        days <= 0 -> "Sem histórico"
        days == requestedDays -> "Últimos $days dias"
        else -> "Últimos $days dias (histórico disponível)"
    }
}

object RainAccumulationCalc {
    const val RAINY_DAY_MM = 1.0

    fun compute(days: List<AgroDay>, todayIndex: Int, window: Int): RainAccumulation {
        if (todayIndex <= 0 || window <= 0 || todayIndex > days.size) {
            return RainAccumulation(window, 0, null, null, null, 0, null, 0, emptyList())
        }
        val slice = days.subList(maxOf(0, todayIndex - window), todayIndex)
        val rains = slice.mapNotNull { it.rainMm }
        val et0s = slice.mapNotNull { it.et0Mm }
        val rain = if (rains.isEmpty()) null else round1(rains.sum())
        // balanço só quando chuva e ET0 estão completos (evita balanço enviesado por dia faltando)
        val et0 = if (et0s.isEmpty()) null else round1(et0s.sum())
        val balance = if (rain != null && et0 != null && rains.size == slice.size && et0s.size == slice.size) round1(rain - et0) else null
        return RainAccumulation(
            requestedDays = window,
            days = slice.size,
            rainMm = rain,
            et0Mm = et0,
            balanceMm = balance,
            rainyDays = rains.count { it >= RAINY_DAY_MM },
            wettest = slice.filter { it.rainMm != null }.maxByOrNull { it.rainMm!! },
            missingDays = slice.size - rains.size,
            daily = slice
        )
    }

    fun compute(series: AgroSeries, window: Int): RainAccumulation = compute(series.days, series.todayIndex, window)

    private fun round1(v: Double) = Math.round(v * 10.0) / 10.0
}
