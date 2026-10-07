package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.DailyForecastEntity
import com.example.ui.components.Ciiagro24hRechartsLineChart
import com.example.ui.components.DynamicHourlyChart
import com.example.ui.components.OfflineStatusBar
import com.example.ui.components.RainForecast7DaysCard
import com.example.ui.components.WeatherMetricsGrid
import com.example.util.PdfExporter
import com.example.viewmodel.WeatherViewModel

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun DailyForecastScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier,
    onNavigateToRadar: () -> Unit = {}
) {
    val context = LocalContext.current
    val station by viewModel.currentStation.collectAsState()
    val currentCiiagro by viewModel.currentCiiagroRecord.collectAsState()
    val hourlyList by viewModel.hourlyForecasts.collectAsState()
    val dailyList by viewModel.dailyForecasts.collectAsState()
    val prefs by viewModel.userPreferences.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var selectedModel by remember { mutableStateOf("Consenso Multimodelo") }
    var forecastPeriodDays by remember { mutableIntStateOf(7) }

    if (station == null) return

    val current = station!!

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("daily_forecast_screen")
    ) {
        // Top Action Bar
        item {
            OfflineStatusBar(
                isForcedOffline = prefs.isOfflineModeForced,
                lastUpdated = current.lastUpdated,
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshActiveStation() },
                onToggleOffline = { viewModel.toggleForcedOffline() }
            )
        }

        // Hero Weather Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${current.name}, SP",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }

                    Text(
                        current.region,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        "${current.currentTemp.toInt()}°C",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 56.sp
                    )

                    Text(
                        current.weatherCondition,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Mín: ${current.minTemp}°C  •  Máx: ${current.maxTemp}°C",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Radar: ${current.dbzReflectivity} dBZ",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons: Share & PDF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.shareCurrentWeather(context) },
                            modifier = Modifier.weight(1f).testTag("btn_share_daily")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartilhar", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.exportWeatherReportPdf(context) { file ->
                                    if (file != null) {
                                        PdfExporter.openOrSharePdf(context, file)
                                    } else {
                                        Toast.makeText(context, "Erro ao exportar PDF", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f).testTag("btn_pdf_daily")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exportar PDF", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Synoptic Meteorological Bulletin (IPMet Base)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Boletim e Sinopse Diária IPMet UNESP",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        current.synopticSummary,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 24-Hour Evolution Chart (Recharts Interactive Line Chart com dados CIIAGRO)
        item {
            Ciiagro24hRechartsLineChart(
                hourlyForecasts = hourlyList,
                ciiagroRecord = currentCiiagro,
                cityName = current.name,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // 7-Day Rain Forecast & Accumulation Card (Visual Bar Chart & Daily Probabilities)
        item {
            RainForecast7DaysCard(
                dailyForecasts = dailyList,
                cityName = current.name,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Radar Map Shortcut Card for Current Region
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Sensors,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Radar IPMet: Região de ${current.name}",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Veja nuvens de chuva em tempo real, trajetórias (+15m/+30m/+45m) e acumulados em mm.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            viewModel.setExpandedMapOpen(true)
                            onNavigateToRadar()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("button_view_radar_from_forecast")
                    ) {
                        Text("Ver Mapa", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 15-Day Extended Forecast Section with Multi-Model Integration
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (forecastPeriodDays == 7) "Previsão Detalhada para 7 Dias" else "Previsão Estendida para 15 Dias",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = forecastPeriodDays == 7,
                            onClick = { forecastPeriodDays = 7 },
                            label = { Text("7 Dias", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = forecastPeriodDays == 15,
                            onClick = { forecastPeriodDays = 15 },
                            label = { Text("15 Dias", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Meteorological Model Source Selector
                val models = listOf(
                    "Consenso Multimodelo",
                    "IPMet UNESP",
                    "INMET (Brasil)",
                    "CPTEC / INPE",
                    "ECMWF (Europeu)",
                    "NOAA / GFS (EUA)"
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    items(models) { model ->
                        val isSelected = selectedModel == model
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedModel = model },
                            label = { Text(model, fontSize = 11.sp) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                val modelDescription = when (selectedModel) {
                    "IPMet UNESP" -> "Radares Doppler Bauru e Presidente Prudente da UNESP + nowcasting de alta precisão para SP."
                    "INMET (Brasil)" -> "Instituto Nacional de Meteorologia - Estações automáticas de superfície e modelo numérico regional."
                    "CPTEC / INPE" -> "Centro de Previsão de Tempo e Estudos Climáticos - Modelos meteorológicos BRAMS e WRF Brasil."
                    "ECMWF (Europeu)" -> "Modelo europeu de alta resolução, referência global em confiabilidade para até 15 dias."
                    "NOAA / GFS (EUA)" -> "Administração Oceânica e Atmosférica dos EUA - Modelo global operacional GFS."
                    else -> "Integração ponderada do IPMet UNESP com modelos nacionais (INMET/CPTEC) e internacionais (ECMWF/NOAA)."
                }

                Text(
                    text = "📡 Fonte: $modelDescription",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                )
            }
        }

        val displayedDaily = if (forecastPeriodDays == 7) dailyList.take(7) else dailyList
        items(displayedDaily) { item ->
            DailyForecastRow(
                item = item,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Detailed Metrics Grid (UV, Wind, Humidity, Pressure, Sunrise/Sunset)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    "Parâmetros Meteorológicos Detalhados",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                WeatherMetricsGrid(station = current)
            }
        }

        // v5.1: a seção agro (umidade do solo/ATR estimados) saiu daqui; cana e citros com dados reais ficam na aba Agro.
        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun DailyForecastRow(
    item: DailyForecastEntity,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(90.dp)) {
                Text(
                    item.dayOfWeek,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    item.dateText,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val icon: ImageVector = when (item.iconType) {
                    "storm" -> Icons.Default.Thunderstorm
                    "rain" -> Icons.Default.WaterDrop
                    "cloudy" -> Icons.Default.WbCloudy
                    else -> Icons.Default.WbSunny
                }
                val iconColor = when (item.iconType) {
                    "storm" -> Color(0xFFB388FF)
                    "rain" -> Color(0xFF38BDF8)
                    "cloudy" -> Color(0xFF94A3B8)
                    else -> Color(0xFFFFD600)
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    item.condition,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.rainProbability > 25 || item.rainVolumeMm > 0.0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${item.rainProbability}%",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        if (item.rainVolumeMm > 0.0) {
                            Text(
                                "${item.rainVolumeMm}mm",
                                color = Color(0xFF00E5FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    "${item.minTemp.toInt()}° / ${item.maxTemp.toInt()}°",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
