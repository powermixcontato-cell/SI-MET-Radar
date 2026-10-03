package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.OpenMeteoConfig
import com.example.data.repository.AgroSeries
import com.example.domain.CanaCitrosIndices.br
import com.example.domain.CanaCitrosIndices.fmt
import com.example.domain.RainAccumulationCalc

/**
 * Chuva acumulada nos últimos 7 / 15 / 30 dias (série diária Open-Meteo com past_days=30).
 * O rótulo usa o número REAL de dias com dado (ex.: "Últimos 12 dias (histórico disponível)").
 */
@Composable
fun RainAccumulationCard(
    series: AgroSeries?,
    modifier: Modifier = Modifier,
    initialWindow: Int = 30
) {
    var window by rememberSaveable { mutableIntStateOf(initialWindow) }
    val acc = remember(series, window) { series?.let { RainAccumulationCalc.compute(it, window) } }
    val accent = Color(0xFF38BDF8)
    Card(
        modifier = modifier.fillMaxWidth().testTag("card_rain_accumulation"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(accent, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Chuva acumulada (estimativa do modelo)",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OpenMeteoConfig.ACCUMULATION_WINDOWS.forEach { d ->
                    FilterChip(
                        selected = window == d,
                        onClick = { window = d },
                        label = { Text("$d dias", fontSize = 11.sp) },
                        modifier = Modifier.height(32.dp).testTag("chip_acc_$d")
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            if (acc == null || acc.days == 0 || acc.rainMm == null) {
                Text(
                    "Sem histórico de chuva disponível para esta cidade ainda.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            Text(acc.label + " (até ontem)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("acc_label"))
            Text(
                "${fmt(acc.rainMm)} mm",
                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = accent,
                modifier = Modifier.testTag("acc_total")
            )
            Text(
                listOfNotNull(
                    "${acc.rainyDays} dia(s) com chuva ≥ 1 mm",
                    acc.wettest?.let { w -> w.rainMm?.takeIf { it > 0.0 }?.let { "maior: ${fmt(it)} mm em ${br(w.date)}" } },
                    acc.et0Mm?.let { "ET0: ${fmt(it)} mm" },
                    acc.balanceMm?.let { "balanço: ${if (it > 0) "+" else ""}${fmt(it)} mm" }
                ).joinToString(" • "),
                fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurface
            )
            if (acc.missingDays > 0) {
                Text("${acc.missingDays} dia(s) sem dado na janela (não somados).", fontSize = 10.sp, color = Color(0xFFF59E0B))
            }
            Spacer(Modifier.height(8.dp))
            // barras diárias (mais antigo → ontem)
            val maxMm = (acc.daily.maxOfOrNull { it.rainMm ?: 0.0 } ?: 0.0).coerceAtLeast(5.0)
            val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            Canvas(
                Modifier.fillMaxWidth().height(64.dp)
                    .semantics { contentDescription = "Gráfico de chuva diária, ${acc.label}: total ${fmt(acc.rainMm)} mm" }
            ) {
                val n = acc.daily.size.coerceAtLeast(1)
                val cw = size.width / n
                drawLine(grid, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
                acc.daily.forEachIndexed { i, d ->
                    val mm = d.rainMm ?: return@forEachIndexed
                    val bh = (mm / maxMm).toFloat() * (size.height - 2f)
                    if (bh > 0.5f) {
                        drawRoundRect(
                            accent, Offset(i * cw + cw * 0.15f, size.height - bh),
                            Size((cw * 0.7f).coerceAtLeast(1f), bh), CornerRadius(2f)
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(acc.daily.firstOrNull()?.let { br(it.date) } ?: "", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("máx. da escala ${fmt(maxMm)} mm", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(acc.daily.lastOrNull()?.let { br(it.date) } ?: "", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Fonte: Open-Meteo (modelo/reanálise para o ponto da cidade; não é pluviômetro). Dia de hoje não incluído.",
                fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
