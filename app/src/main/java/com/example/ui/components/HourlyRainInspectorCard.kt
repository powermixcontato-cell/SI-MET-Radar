package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.HourlyForecastEntity

@Composable
fun HourlyRainInspectorCard(
    hourlyForecasts: List<HourlyForecastEntity>,
    selectedIndex: Int,
    onSelectHour: (Int) -> Unit,
    stationName: String,
    modifier: Modifier = Modifier
) {
    if (hourlyForecasts.isEmpty()) return

    val currentSelected = hourlyForecasts.getOrNull(selectedIndex) ?: hourlyForecasts.first()

    // Rain intensity category based on mm
    val (categoryLabel, categoryColor, categoryDesc) = when {
        currentSelected.rainVolumeMm >= 20.0 -> Triple("Chuva Torrencial / Tempestade", Color(0xFFFF1744), "Risco crítico de alagamentos e transbordamentos imediatos.")
        currentSelected.rainVolumeMm >= 10.0 -> Triple("Chuva Forte", Color(0xFFFF9100), "Precipitação densa com redução drástica de visibilidade.")
        currentSelected.rainVolumeMm >= 3.0 -> Triple("Chuva Moderada", Color(0xFFFFD600), "Pancadas constantes típicas de instabilidades de verão.")
        currentSelected.rainVolumeMm > 0.0 -> Triple("Chuva Leve / Garoa", Color(0xFF00E5FF), "Precipitação fina sem risco de acúmulo severo.")
        else -> Triple("Sem Chuva Prevista", Color(0xFF00E676), "Período seco favorável para deslocamentos.")
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("card_hourly_rain_inspector"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
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
                            .size(34.dp)
                            .background(Color(0xFF0284C7).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Milímetros por Hora (mm)",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Previsão detalhada em $stationName",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                val sdfLiveHour = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "BR")) }
                val displayTopTime = if (selectedIndex == 0) "AGORA (${sdfLiveHour.format(java.util.Date())})" else currentSelected.hourText

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = categoryColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, categoryColor)
                ) {
                    Text(
                        text = "$displayTopTime • ${currentSelected.rainVolumeMm} mm",
                        color = categoryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Horizontal Hour Scrubber
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                hourlyForecasts.forEachIndexed { idx, item ->
                    val isSelected = idx == selectedIndex
                    val itemHasRain = item.rainVolumeMm > 0.0
                    val isLiveNow = idx == 0

                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else if (isLiveNow) Color(0xFF00E676).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = if (isSelected) 2.dp else if (isLiveNow) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else if (isLiveNow) Color(0xFF00E676) else if (itemHasRain) Color(0xFF0284C7) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectHour(idx) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("chip_hour_${item.hourText.replace(":", "_")}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isLiveNow) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF00E676), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "AGORA",
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF00E676),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            Text(
                                text = item.hourText,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected || isLiveNow) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Icon(
                                imageVector = if (item.rainVolumeMm > 10.0) Icons.Default.Thunderstorm else if (itemHasRain) Icons.Default.WaterDrop else Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else if (itemHasRain) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${item.rainVolumeMm} mm",
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else if (itemHasRain) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Detailed Metric Box for the selected hour
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Janela das ${currentSelected.hourText}",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = categoryLabel,
                                color = categoryColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${currentSelected.rainVolumeMm} mm/h",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Probabilidade: ${currentSelected.rainProbability}%",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = categoryDesc,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
