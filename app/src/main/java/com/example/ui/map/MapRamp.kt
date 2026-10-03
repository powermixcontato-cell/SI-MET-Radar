package com.example.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

/**
 * Escala de cores contínua (valor → cor) com faixa transparente abaixo de [visibleFrom].
 * Uma única definição por grandeza, usada pelo campo, pelas pílulas de valor e pela legenda.
 */
class MapRamp(
    val stops: List<Pair<Float, Color>>,
    val unit: String,
    /** Abaixo disso o campo fica transparente (ex.: chuva < 0,2 mm). null = sempre visível. */
    val visibleFrom: Float? = null,
    /** Valores da legenda (posições igualmente espaçadas na barra). */
    val legendTicks: List<Float>,
    val tickFormatter: (Float) -> String = { v -> if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v) }
) {
    fun color(value: Float): Color {
        if (value.isNaN()) return Color.Transparent
        val first = stops.first(); val last = stops.last()
        if (value <= first.first) return first.second
        if (value >= last.first) return last.second
        for (i in 0 until stops.size - 1) {
            val (v0, c0) = stops[i]; val (v1, c1) = stops[i + 1]
            if (value in v0..v1) return lerp(c0, c1, if (v1 > v0) (value - v0) / (v1 - v0) else 0f)
        }
        return last.second
    }

    /** Opacidade do campo para o valor (0 abaixo de visibleFrom, rampa suave até o 1º stop). */
    fun fieldAlpha(value: Float): Float {
        if (value.isNaN()) return 0f
        val vf = visibleFrom ?: return 1f
        if (value < vf) return 0f
        val full = stops.first().first.coerceAtLeast(vf)
        return if (full <= vf) 1f else ((value - vf) / (full - vf)).coerceIn(0.35f, 1f)
    }

    fun argb(value: Float, alpha: Float): Int {
        val a = (fieldAlpha(value) * alpha).coerceIn(0f, 1f)
        if (a <= 0f) return 0
        return color(value).copy(alpha = a).toArgb()
    }

    /** Cor amostrada na posição 0..1 da barra de legenda (interpolação entre ticks). */
    fun legendColorAt(t: Float): Color {
        val n = legendTicks.size
        if (n < 2) return color(legendTicks.firstOrNull() ?: 0f)
        val pos = (t.coerceIn(0f, 1f) * (n - 1))
        val i = pos.toInt().coerceAtMost(n - 2)
        val f = pos - i
        val v = legendTicks[i] + (legendTicks[i + 1] - legendTicks[i]) * f
        return color(v)
    }

    companion object {
        /** Chuva acumulada no dia (mm). Mesmas cores de PrecipColorRamp (verde → amarelo → vermelho → roxo). */
        val RainMm = MapRamp(
            stops = listOf(
                0.5f to Color(0xFFA6F28F), 2f to Color(0xFF3DBA3D), 5f to Color(0xFF1E9E3A),
                10f to Color(0xFFFFE433), 20f to Color(0xFFFFA000), 30f to Color(0xFFFF5A00),
                50f to Color(0xFFE8141E), 80f to Color(0xFFB0006E), 120f to Color(0xFF8A2BE2)
            ),
            unit = "mm", visibleFrom = 0.2f,
            legendTicks = listOf(0.5f, 2f, 5f, 10f, 20f, 50f, 120f)
        )

        /** Refletividade ESTIMADA (dBZ) a partir da taxa de chuva prevista — mesma escala do radar. */
        val Dbz = MapRamp(
            stops = com.example.ui.components.PrecipStops,
            unit = "dBZ", visibleFrom = 8f,
            legendTicks = listOf(10f, 20f, 30f, 40f, 50f, 65f)
        )

        val TempC = MapRamp(
            stops = listOf(
                5f to Color(0xFF6D28D9), 10f to Color(0xFF2563EB), 15f to Color(0xFF06B6D4),
                20f to Color(0xFF22C55E), 25f to Color(0xFFFACC15), 30f to Color(0xFFF97316),
                35f to Color(0xFFDC2626), 40f to Color(0xFF7F1D1D)
            ),
            unit = "°C", legendTicks = listOf(10f, 15f, 20f, 25f, 30f, 35f)
        )

        val ProbPct = MapRamp(
            stops = listOf(
                10f to Color(0xFFC4B5FD), 30f to Color(0xFFA78BFA), 50f to Color(0xFF7C3AED),
                70f to Color(0xFFC026D3), 90f to Color(0xFFDB2777)
            ),
            unit = "%", visibleFrom = 5f, legendTicks = listOf(10f, 30f, 50f, 70f, 90f)
        )

        val HumidityPct = MapRamp(
            stops = listOf(
                20f to Color(0xFFB45309), 35f to Color(0xFFF59E0B), 50f to Color(0xFFFDE68A),
                65f to Color(0xFF86EFAC), 80f to Color(0xFF14B8A6), 95f to Color(0xFF1D4ED8)
            ),
            unit = "%", legendTicks = listOf(20f, 35f, 50f, 65f, 80f, 95f)
        )
    }
}
