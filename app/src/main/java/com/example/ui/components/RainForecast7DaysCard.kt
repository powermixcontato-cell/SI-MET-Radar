package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.DailyForecastEntity
import kotlin.math.max

/**
 * Previsão de Chuvas nos 7 Dias com visualizador de acumulados diários em barras,
 * probabilidade de precipitação, períodos de chuva e alertas para o campo.
 */
@Composable
fun RainForecast7DaysCard(
    dailyForecasts: List<DailyForecastEntity>,
    modifier: Modifier = Modifier,
    cityName: String = "Sua Região",
    /** v5.1: alterna 7 / 15 dias (Open-Meteo forecast_days=16). */
    showRangeToggle: Boolean = true
) {
    if (dailyForecasts.isEmpty()) return

    var rangeDays by rememberSaveable { mutableIntStateOf(7) }
    val next7Days = remember(dailyForecasts, rangeDays) {
        dailyForecasts.take(rangeDays)
    }
    val isLong = next7Days.size > 7

    var selectedDayIndex by remember { mutableIntStateOf(0) }
    val safeIndex = selectedDayIndex.coerceIn(0, next7Days.lastIndex)
    val activeDay = next7Days[safeIndex]

    // Cumulative stats for the 7-day period
    val totalRainMm = remember(next7Days) {
        Math.round(next7Days.sumOf { it.rainVolumeMm } * 10.0) / 10.0
    }
    val rainyDaysCount = remember(next7Days) {
        next7Days.count { it.rainProbability >= 45 || it.rainVolumeMm > 1.0 }
    }
    val maxDayRain = remember(next7Days) {
        next7Days.maxOfOrNull { it.rainVolumeMm } ?: 0.0
    }
    val barScaleMax = max(35.0, maxDayRain * 1.2)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_rain_forecast_7_days"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF0284C7).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = "Chuva 7 Dias",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Previsão de chuva • ${next7Days.size} dias",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Acumulado e probabilidade para $cityName",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp
                        )
                    }
                }

                // Total Rain Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "${totalRainMm}mm",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Total ${next7Days.size} dias",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp
                        )
                    }
                }
            }

            if (showRangeToggle) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.FilterChip(
                        selected = rangeDays == 7, onClick = { rangeDays = 7; selectedDayIndex = 0 },
                        label = { Text("7 dias", fontSize = 12.sp) }, modifier = Modifier.testTag("chip_forecast_7")
                    )
                    androidx.compose.material3.FilterChip(
                        selected = rangeDays == 15, enabled = dailyForecasts.size > 7,
                        onClick = { rangeDays = 15; selectedDayIndex = 0 },
                        label = { Text("15 dias", fontSize = 12.sp) }, modifier = Modifier.testTag("chip_forecast_15")
                    )
                    if (rangeDays == 15) Text(
                        "Dias 8–15: menor confiabilidade",
                        color = Color(0xFF94A3B8), fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Summary Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$rainyDaysCount dias com chuva",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${next7Days.size - rainyDaysCount} dias de tempo firme",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 7-Day Visual Column Bar Chart
            Text(
                text = "Volume Diário de Chuva (Toque no dia para inspecionar):",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLong) 150.dp else 140.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .then(if (isLong) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = if (isLong) Arrangement.spacedBy(2.dp) else Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                next7Days.forEachIndexed { index, day ->
                    val isSelected = index == safeIndex
                    val rainRatio = (day.rainVolumeMm / barScaleMax).coerceIn(0.04, 1.0).toFloat()
                    val barHeightDp = (80 * rainRatio).dp.coerceAtLeast(6.dp)

                    val barBrush = when {
                        day.rainVolumeMm >= 20.0 -> Brush.verticalGradient(
                            listOf(Color(0xFFFF1744), Color(0xFF7C3AED))
                        )
                        day.rainVolumeMm >= 8.0 -> Brush.verticalGradient(
                            listOf(Color(0xFF00E5FF), Color(0xFF0284C7))
                        )
                        day.rainVolumeMm > 0.0 -> Brush.verticalGradient(
                            listOf(Color(0xFF38BDF8), Color(0xFF0369A1))
                        )
                        else -> Brush.verticalGradient(
                            listOf(Color(0xFF64748B), Color(0xFF334155))
                        )
                    }

                    Column(
                        modifier = Modifier
                            .then(if (isLong) Modifier.width(42.dp) else Modifier.weight(1f))
                            .fillMaxHeight()
                            .clickable { selectedDayIndex = index }
                            .padding(horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Rain volume text on top
                        Text(
                            text = if (day.rainVolumeMm > 0.0) "${day.rainVolumeMm.toInt()}mm" else "-",
                            color = if (day.rainVolumeMm > 0.0) Color(0xFF00E5FF) else Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Vertical Bar
                        Box(
                            modifier = Modifier
                                .width(if (isSelected) 22.dp else 16.dp)
                                .height(barHeightDp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(barBrush)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(1.5.dp, Color.White, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    } else Modifier
                                )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Weather icon
                        val icon = getDayIcon(day.iconType)
                        val iconTint = when (day.iconType) {
                            "storm" -> Color(0xFFB388FF)
                            "rain" -> Color(0xFF38BDF8)
                            "cloudy" -> Color(0xFF94A3B8)
                            else -> Color(0xFFFFD600)
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(14.dp)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Probability badge
                        Text(
                            text = "${day.rainProbability}%",
                            color = if (day.rainProbability >= 50) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Day label (Hoje, Seg, etc.)
                        Text(
                            text = if (index == 0) "Hoje" else day.dayOfWeek.take(3),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                        if (isLong) Text(text = day.dateText.take(5), color = Color(0xFF94A3B8), fontSize = 8.sp, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selected Day Breakdown Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Detalhes: ${activeDay.dayOfWeek} (${activeDay.dateText})",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Text(
                            text = "${activeDay.minTemp.toInt()}°C a ${activeDay.maxTemp.toInt()}°C",
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Condição: ${activeDay.condition}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Chuva Prevista: ${activeDay.rainVolumeMm} mm (${activeDay.rainProbability}%)",
                            color = if (activeDay.rainVolumeMm > 0.0) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))


                    // Field Impact Notice
                    val agroNotice = when {
                        activeDay.rainVolumeMm >= 15.0 -> "⚠️ Risco Alto de Interrupção de Colheita e Encharcamento. Não pulverizar para evitar lixiviação."
                        activeDay.rainVolumeMm in 5.0..14.9 -> "💧 Chuva Moderada: Solo úmido, atenção no tráfego de máquinas pesadas em canaviais e citros."
                        activeDay.rainVolumeMm > 0.0 -> "🌦️ Chuva Leve / Garoa: Janela de pulverização possível fora do horário de pancadas."
                        else -> "☀️ Tempo Seco / Firme: Excelente janela para colheita mecanizada, plantio e pulverização agrícola."
                    }
                    val agroColor = if (activeDay.rainVolumeMm >= 15.0) Color(0xFFFF1744) else if (activeDay.rainVolumeMm > 0.0) Color(0xFF38BDF8) else Color(0xFF10B981)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(agroColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = agroNotice,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

private fun getDayIcon(type: String): ImageVector {
    return when (type) {
        "storm" -> Icons.Default.Thunderstorm
        "rain" -> Icons.Default.WaterDrop
        "cloudy" -> Icons.Default.WbCloudy
        else -> Icons.Default.WbSunny
    }
}
