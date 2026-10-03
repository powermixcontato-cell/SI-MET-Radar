package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.log10
import kotlin.math.pow

/**
 * Escala de cores contínua de precipitação (estilo mapa de chuva moderno).
 * Paradas em refletividade (dBZ) → mm/h aproximado (Marshall-Palmer Z = 200 R^1.6).
 * Usada apenas para o VISUAL da chuva; não altera nenhum dado.
 */
val PrecipStops: List<Pair<Float, Color>> = listOf(
    10f to Color(0xFFA6F28F),  // ~0,2 mm/h  garoa – verde-claro
    20f to Color(0xFF3DBA3D),  // ~0,6 mm/h  fraca – verde
    30f to Color(0xFF1E7D1E),  // ~2,7 mm/h  moderada – verde-escuro
    35f to Color(0xFFFFF033),  // ~5,6 mm/h  amarelo
    40f to Color(0xFFFFB000),  // ~11 mm/h   laranja-claro
    45f to Color(0xFFFF6A00),  // ~24 mm/h   laranja forte
    50f to Color(0xFFE8141E),  // ~49 mm/h   vermelho
    55f to Color(0xFFB0006E),  // ~100 mm/h  magenta / granizo
    65f to Color(0xFF8A2BE2)   // 65+        roxo – extrema
)

/** Lista de cores pronta para Brush.horizontalGradient (legenda). */
val PrecipLegendColors: List<Color> = PrecipStops.map { it.second }

/** Color stops (posição 0..1 proporcional ao dBZ) para a barra de legenda. */
val PrecipLegendStops: Array<Pair<Float, Color>> = run {
    val min = PrecipStops.first().first
    val max = PrecipStops.last().first
    PrecipStops.map { (dbz, c) -> ((dbz - min) / (max - min)) to c }.toTypedArray()
}

/** Interpola linearmente a cor da precipitação para um valor de dBZ. */
fun precipColor(dbz: Float, alpha: Float = 0.7f): Color {
    val first = PrecipStops.first()
    val last = PrecipStops.last()
    val base = when {
        dbz <= first.first -> first.second
        dbz >= last.first -> last.second
        else -> {
            var result = last.second
            for (i in 0 until PrecipStops.size - 1) {
                val (d0, c0) = PrecipStops[i]
                val (d1, c1) = PrecipStops[i + 1]
                if (dbz >= d0 && dbz <= d1) {
                    val t = if (d1 > d0) (dbz - d0) / (d1 - d0) else 0f
                    result = lerp(c0, c1, t)
                    break
                }
            }
            result
        }
    }
    return base.copy(alpha = alpha.coerceIn(0f, 1f))
}

/** Converte chuva (mm, tratada como mm/h) em dBZ equivalente via Marshall-Palmer. */
fun mmToDbz(mm: Double): Float =
    if (mm <= 0.0) 0f else (10 * log10(200 * mm.pow(1.6))).toFloat()
