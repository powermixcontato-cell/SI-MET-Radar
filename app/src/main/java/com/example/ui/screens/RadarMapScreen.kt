package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.CitySelectionModal
import com.example.ui.components.PdfExportDialog
import com.example.ui.components.RainMapCard
import com.example.ui.components.WindyWebViewCard
import com.example.viewmodel.WeatherViewModel

/**
 * Tela inicial redesenhada para uma olhada rápida (glanceable):
 * - Topo: Cidade, temperatura grande, ícone do tempo, máx/mín e chance de chuva.
 * - Alertas: chip curto apenas quando houver.
 * - Faixa compacta das próximas horas.
 * - Mapa de chuva interativo.
 * - Rótulos curtos de no máximo 2 a 3 palavras, sem parágrafos longos.
 */
@Composable
fun RadarMapScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier,
    onOpenAlerts: () -> Unit = {},
    onOpenForecast: () -> Unit = {},
    onOpenFloods: () -> Unit = {},
    onOpenOthers: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.selectedState.collectAsStateWithLifecycle()
    val station by viewModel.currentStation.collectAsStateWithLifecycle()
    val hourlyForecasts by viewModel.hourlyForecasts.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val alerts by viewModel.hazardAlerts.collectAsStateWithLifecycle()

    var showPdf by remember { mutableStateOf(false) }
    var showCityModal by remember { mutableStateOf(false) }
    var showWindyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state) { viewModel.refreshHazards(state) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .testTag("radar_map_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1) HERO DO CLIMA (TOPO DA TELA)
        item(key = "hero_weather_top") {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F1D)),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("card_weather_hero_home")
            ) {
                Column(Modifier.padding(16.dp)) {
                    // Linha superior: Cidade com seletor + Ações rápidas
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val cityName = station?.name ?: state.displayName
                        val uf = when {
                            station != null && station!!.region.contains("Rio Grande do Sul", true) -> "RS"
                            station != null && station!!.region.contains("Paraná", true) -> "PR"
                            station != null && station!!.region.contains("São Paulo", true) -> "SP"
                            station != null && station!!.region.length == 2 -> station!!.region.uppercase()
                            else -> state.uf
                        }
                        val cityLabel = if (cityName.contains(uf, ignoreCase = true)) cityName else "$cityName, $uf"

                        Surface(
                            onClick = { showCityModal = true },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.testTag("btn_select_city_home")
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    cityLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 180.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Trocar", tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    viewModel.refreshActiveStation()
                                    viewModel.refreshInmetAlertsNow()
                                    viewModel.refreshHazards(state, force = true)
                                },
                                enabled = !isRefreshing,
                                modifier = Modifier.size(36.dp).testTag("btn_refresh_home")
                            ) {
                                if (isRefreshing) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF00E5FF))
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                                }
                            }
                            IconButton(
                                onClick = { viewModel.shareCurrentWeather(context) },
                                modifier = Modifier.size(36.dp).testTag("btn_share_radar")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { showPdf = true },
                                modifier = Modifier.size(36.dp).testTag("btn_pdf_radar")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // CHIP DE ALERTA (SÓ APARECE SE HOUVER ALERTA!)
                    if (alerts.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        val topSeverity = alerts.maxByOrNull { it.severity.rank }
                        val alertColor = topSeverity?.let { Color(it.severity.argb) } ?: Color(0xFFF59E0B)
                        Surface(
                            onClick = onOpenAlerts,
                            shape = RoundedCornerShape(20.dp),
                            color = alertColor.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, alertColor.copy(alpha = 0.8f)),
                            modifier = Modifier.testTag("chip_alert_home")
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = alertColor, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${alerts.size} alerta${if (alerts.size > 1) "s" else ""}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = alertColor, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // DADOS PRINCIPAIS DO TEMPO (TEMPERATURA GRANDE, ÍCONE, MÁX/MÍN E CHUVA)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val tempText = if (station != null) "${station!!.currentTemp.toInt()}°" else "—"
                            Text(
                                tempText,
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = (-1.5).sp
                            )
                            val conditionText = station?.weatherCondition ?: "Tempo estável"
                            Text(
                                conditionText,
                                fontSize = 14.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val minMax = if (station != null) "↑ ${station!!.maxTemp.toInt()}°  ↓ ${station!!.minTemp.toInt()}°" else "—"
                                Text(
                                    minMax,
                                    fontSize = 13.sp,
                                    color = Color(0xFFCBD5E1),
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("•", color = Color(0xFF64748B), fontSize = 12.sp)
                                Spacer(Modifier.width(8.dp))
                                val rainProb = station?.rainProbability ?: 0
                                Text(
                                    "💧 $rainProb%",
                                    fontSize = 13.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        val iconType = station?.iconType ?: "sunny"
                        Icon(
                            imageVector = weatherIcon(iconType),
                            contentDescription = null,
                            tint = weatherIconColor(iconType),
                            modifier = Modifier.size(68.dp)
                        )
                    }
                }
            }
        }

        // 2) FAIXA COMPACTA DAS PRÓXIMAS HORAS
        item(key = "hourly_compact_strip") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F1D)),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("card_hourly_strip_home")
            ) {
                Column(Modifier.padding(vertical = 10.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Próximas horas",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            "Ver 24h ›",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier
                                .clickable { onOpenForecast() }
                                .padding(4.dp)
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    val hourlyList = hourlyForecasts.take(16)
                    if (hourlyList.isEmpty()) {
                        Text(
                            "Carregando horários...",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(hourlyList, key = { idx, it -> "${it.stationId}_${it.hourText}_$idx" }) { idx, item ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (idx == 0) Color(0xFF1E293B).copy(alpha = 0.5f) else Color.Transparent)
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    val hourLabel = if (idx == 0) "Agora" else item.hourText.take(2) + "h"
                                    Text(
                                        hourLabel,
                                        fontSize = 11.sp,
                                        color = if (idx == 0) Color.White else Color(0xFF94A3B8),
                                        fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Icon(
                                        imageVector = weatherIcon(item.iconType),
                                        contentDescription = null,
                                        tint = weatherIconColor(item.iconType),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${item.temp.toInt()}°",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (item.rainProbability > 0) {
                                        Text(
                                            "${item.rainProbability}%",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    } else {
                                        Spacer(Modifier.height(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3) MAPA DE CHUVA INTERATIVO
        item(key = "rain_map") {
            RainMapCard(state = state, modifier = Modifier.padding(horizontal = 16.dp))
        }

        // 4) ATALHOS RÁPIDOS (CONCISOS: MÁXIMO 2 A 3 PALAVRAS POR ITEM)
        item(key = "quick_actions_grid") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Default.CalendarMonth,
                        title = "Previsão 15 dias",
                        accent = Color(0xFF00E5FF),
                        onClick = onOpenForecast,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        icon = Icons.Default.Air,
                        title = "Satélite e vento",
                        accent = Color(0xFF38BDF8),
                        onClick = { showWindyDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Default.Water,
                        title = "Rios e enchentes",
                        accent = Color(0xFF0EA5E9),
                        onClick = onOpenFloods,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        icon = Icons.Default.Apps,
                        title = "Mais ferramentas",
                        accent = Color(0xFF8B5CF6),
                        onClick = onOpenOthers,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item { Spacer(Modifier.height(14.dp)) }
    }

    // Modal de seleção de cidade em todo o Brasil
    if (showCityModal) {
        CitySelectionModal(
            viewModel = viewModel,
            onDismiss = { showCityModal = false },
            onCitySelected = { place ->
                viewModel.selectCity(place)
                val uf = when {
                    place.admin1?.contains("Paraná", true) == true -> com.example.domain.BrState.PR
                    place.admin1?.contains("Rio Grande do Sul", true) == true -> com.example.domain.BrState.RS
                    else -> com.example.domain.BrState.SP
                }
                viewModel.setSelectedState(uf)
                showCityModal = false
            }
        )
    }

    // Diálogo rápido do Windy quando clicado no atalho "Satélite e vento"
    if (showWindyDialog) {
        Dialog(
            onDismissRequest = { showWindyDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF000000)),
                color = Color(0xFF000000)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Satélite e vento • ${state.uf}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        IconButton(onClick = { showWindyDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                        }
                    }
                    WindyWebViewCard(
                        state = state,
                        focusLat = station?.lat ?: state.centerLat,
                        focusLon = station?.lon ?: state.centerLon,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    if (showPdf) {
        PdfExportDialog(viewModel = viewModel, onDismiss = { showPdf = false })
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0A0F1D),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accent.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun weatherIcon(type: String): ImageVector = when (type.lowercase()) {
    "storm" -> Icons.Default.Thunderstorm
    "rain" -> Icons.Default.WaterDrop
    "cloudy" -> Icons.Default.WbCloudy
    else -> Icons.Default.WbSunny
}

private fun weatherIconColor(type: String): Color = when (type.lowercase()) {
    "rain" -> Color(0xFF38BDF8)
    "storm" -> Color(0xFFA855F7)
    "cloudy" -> Color(0xFF94A3B8)
    else -> Color(0xFFFBBF24)
}

@Composable
internal fun NavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        onClick = onClick,
        shape = com.example.ui.components.SimetCardDefaults.Shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(tag)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                com.example.ui.components.SimetCardHeader(icon = icon, title = title, subtitle = subtitle, accent = accent)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
    }
}
