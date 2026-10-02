package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ClimateTrendEntity
import com.example.util.safeDrawText
import java.util.Locale

@Composable
fun HistoricalComparisonView(
    trends: List<ClimateTrendEntity>,
    modifier: Modifier = Modifier
) {
    if (trends.isEmpty()) return

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 = Comparativo Geral, 1 = Temperatura, 2 = Chuva
    var selectedMonthIndex by remember { mutableIntStateOf(8) } // Setembro default

    val currentMonth = trends.getOrNull(selectedMonthIndex) ?: trends.first()
    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("historical_comparison_view")
    ) {
        // Tab Selector: Mode
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Análise de Tendência", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Temperaturas (12m)", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTabIndex == 2,
                onClick = { selectedTabIndex = 2 },
                text = { Text("Precipitação (12m)", fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Month Selector Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(trends) { index, item ->
                FilterChip(
                    selected = index == selectedMonthIndex,
                    onClick = { selectedMonthIndex = index },
                    label = { Text(item.monthName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("chip_month_${item.monthName}")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Month Comparison Highlight Card
        val deltaTemp = currentMonth.avgTempCurrent - currentMonth.avgTempHistorical
        val deltaRain = currentMonth.rainCurrentMm - currentMonth.rainHistoricalMm
        val isTempWarmer = deltaTemp >= 0
        val isRainAbove = deltaRain >= 0

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Comparação: ${currentMonth.monthName} 2026 vs Normal Histórica (1991-2020)",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isTempWarmer) Color(0xFFFF5252).copy(alpha = 0.2f) else Color(0xFF00E676).copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isTempWarmer) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = if (isTempWarmer) Color(0xFFFF5252) else Color(0xFF00E676),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val sign = if (deltaTemp >= 0) "+" else ""
                            Text(
                                "$sign${String.format(Locale.US, "%.1f", deltaTemp)}°C",
                                color = if (isTempWarmer) Color(0xFFFF5252) else Color(0xFF00E676),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Temperature Column
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Temperatura Média", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Text(
                            "${currentMonth.avgTempCurrent}°C",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        Text(
                            "Normal: ${currentMonth.avgTempHistorical}°C",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    // Rain Column
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chuva Acumulada", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Text(
                            "${currentMonth.rainCurrentMm.toInt()} mm",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        val rainSign = if (deltaRain >= 0) "+${deltaRain.toInt()}" else "${deltaRain.toInt()}"
                        Text(
                            "Normal: ${currentMonth.rainHistoricalMm.toInt()} mm ($rainSign mm)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    // Rainy days
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dias com Chuva", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Text(
                            "${currentMonth.rainyDaysCurrent} dias",
                            color = Color(0xFF00E676),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        Text(
                            "Normal: ${currentMonth.rainyDaysHistorical} dias",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dynamic 12-Month Comparison Bar / Line Chart
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (selectedTabIndex == 1) "Temperaturas: 2026 (Ciano) vs Normal Histórica (Cinza)"
                    else if (selectedTabIndex == 2) "Precipitação: 2026 (Azul) vs Normal Histórica (Cinza)"
                    else "Comparativo de Tendência Climática Mensal (São Paulo)",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val histColor = Color(0xFF64748B)
                    val rainColor = Color(0xFF38BDF8)

                    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                        val w = size.width
                        val h = size.height
                        val chartHeight = h - 25f
                        val stepX = w / trends.size

                        if (selectedTabIndex == 2) {
                            // Rain comparison double bars
                            val maxRain = (trends.maxOfOrNull { maxOf(it.rainCurrentMm, it.rainHistoricalMm) } ?: 300.0).toFloat()

                            trends.forEachIndexed { i, item ->
                                val x = i * stepX
                                val barW = stepX * 0.35f

                                // Historical bar
                                val hHist = (item.rainHistoricalMm.toFloat() / maxRain) * (chartHeight - 20f)
                                drawRect(
                                    color = histColor.copy(alpha = 0.5f),
                                    topLeft = Offset(x + stepX * 0.1f, chartHeight - hHist),
                                    size = Size(barW, hHist)
                                )

                                // Current bar
                                val hCur = (item.rainCurrentMm.toFloat() / maxRain) * (chartHeight - 20f)
                                drawRect(
                                    color = if (i == selectedMonthIndex) primaryColor else rainColor,
                                    topLeft = Offset(x + stepX * 0.1f + barW + 2f, chartHeight - hCur),
                                    size = Size(barW, hCur)
                                )

                                // Month Label
                                safeDrawText(
                                    textMeasurer = textMeasurer,
                                    text = item.monthName,
                                    topLeft = Offset(x + stepX * 0.15f, chartHeight + 4f),
                                    style = TextStyle(
                                        color = if (i == selectedMonthIndex) primaryColor else Color(0xFF94A3B8),
                                        fontSize = 9.sp,
                                        fontWeight = if (i == selectedMonthIndex) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        } else {
                            // Temperature comparison (or General Trend)
                            val minT = 15f
                            val maxT = 30f

                            trends.forEachIndexed { i, item ->
                                val x = i * stepX + stepX / 2f

                                val yCur = chartHeight - ((item.avgTempCurrent.toFloat() - minT) / (maxT - minT)) * (chartHeight - 30f)
                                val yHist = chartHeight - ((item.avgTempHistorical.toFloat() - minT) / (maxT - minT)) * (chartHeight - 30f)

                                // Draw connection line between current and historical to highlight anomaly
                                drawLine(
                                    color = if (item.avgTempCurrent >= item.avgTempHistorical) Color(0xFFFF5252).copy(alpha = 0.5f) else Color(0xFF00E676).copy(alpha = 0.5f),
                                    start = Offset(x, yHist),
                                    end = Offset(x, yCur),
                                    strokeWidth = 3f
                                )

                                // Historical Dot
                                drawCircle(
                                    color = histColor,
                                    radius = 3.5f,
                                    center = Offset(x, yHist)
                                )

                                // Current Dot
                                val isSelected = i == selectedMonthIndex
                                if (isSelected) {
                                    drawCircle(
                                        color = primaryColor.copy(alpha = 0.3f),
                                        radius = 10f,
                                        center = Offset(x, yCur)
                                    )
                                }
                                drawCircle(
                                    color = if (isSelected) Color.White else primaryColor,
                                    radius = if (isSelected) 5f else 4f,
                                    center = Offset(x, yCur)
                                )

                                // Month Label
                                safeDrawText(
                                    textMeasurer = textMeasurer,
                                    text = item.monthName,
                                    topLeft = Offset(x - 8f, chartHeight + 4f),
                                    style = TextStyle(
                                        color = if (isSelected) primaryColor else Color(0xFF94A3B8),
                                        fontSize = 9.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                }

                // Legend row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Período Atual (2026)", color = Color(0xFF94A3B8), fontSize = 10.sp)

                    Spacer(modifier = Modifier.width(16.dp))

                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF64748B), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Normal Histórica (1991-2020)", color = Color(0xFF94A3B8), fontSize = 10.sp)
                }
            }
        }
    }
}
