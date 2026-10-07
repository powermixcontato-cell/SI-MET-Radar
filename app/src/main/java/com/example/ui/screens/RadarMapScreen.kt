package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FloodLevel
import com.example.ui.components.OfflineStatusBar
import com.example.ui.components.PdfExportDialog
import com.example.ui.components.RainForecast7DaysCard
import com.example.ui.components.RainMapCard
import com.example.ui.components.SimetCard
import com.example.ui.components.SimetCardHeader
import com.example.ui.components.StateSelectorBar
import com.example.ui.components.WindyWebViewCard
import com.example.viewmodel.WeatherViewModel

/**
 * Tela inicial (v5.1, limpa): seletor SP/PR/RS + selo de alertas, mapa de chuva (com "Ver trajetória da chuva"),
 * satélite/vento logo abaixo, cidades do estado, previsão 7/15 dias, atalhos para Alagamentos/Enchentes e Outros.
 * O conteúdo antigo desta tela está em Outros → "Radar IPMet & ferramentas" (RadarToolsScreen).
 */
@Composable
fun RadarMapScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier,
    onOpenAlerts: () -> Unit = {},
    onOpenFloods: () -> Unit = {},
    onOpenOthers: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.selectedState.collectAsStateWithLifecycle()
    val stations by viewModel.stationsOfSelectedState.collectAsStateWithLifecycle()
    val currentStation by viewModel.currentStation.collectAsStateWithLifecycle()
    val dailyForecasts by viewModel.dailyForecasts.collectAsStateWithLifecycle()
    val prefs by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val alerts by viewModel.hazardAlerts.collectAsStateWithLifecycle()
    val hazards by viewModel.hazardsByState.collectAsStateWithLifecycle()
    var showPdf by remember { mutableStateOf(false) }

    LaunchedEffect(state) { viewModel.refreshHazards(state) }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("radar_map_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("SI-MET Radar", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Chuva, satélite e previsão • ${state.displayName}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
                IconButton(
                    onClick = { viewModel.refreshActiveStation(); viewModel.refreshInmetAlertsNow(); viewModel.refreshHazards(state, force = true) },
                    enabled = !isRefreshing, modifier = Modifier.testTag("btn_refresh_home")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { viewModel.shareCurrentWeather(context) }, modifier = Modifier.testTag("btn_share_radar")) {
                    Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { showPdf = true }, modifier = Modifier.testTag("btn_pdf_radar")) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Exportar PDF", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            StateSelectorBar(
                selected = state, alerts = alerts,
                onSelect = { viewModel.setSelectedState(it) }, onOpenAlerts = onOpenAlerts,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (prefs.isOfflineModeForced) {
            item {
                OfflineStatusBar(
                    isForcedOffline = true,
                    lastUpdated = currentStation?.lastUpdated ?: 0L,
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshActiveStation() },
                    onToggleOffline = { viewModel.toggleForcedOffline() }
                )
            }
        }
        // 1) Mapa de chuva
        item(key = "rain_map") { RainMapCard(state = state, modifier = Modifier.padding(horizontal = 16.dp)) }
        // 2) Satélite / Windy logo abaixo
        item(key = "windy_sat") {
            WindyWebViewCard(
                state = state,
                focusLat = currentStation?.takeIf { com.example.domain.BrState.ofStationId(it.id) == state }?.lat ?: state.centerLat,
                focusLon = currentStation?.takeIf { com.example.domain.BrState.ofStationId(it.id) == state }?.lon ?: state.centerLon,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        // Cidades do estado (previsão abaixo segue a cidade escolhida)
        item {
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stations, key = { it.id }) { st ->
                    FilterChip(
                        selected = st.id == currentStation?.id,
                        onClick = { viewModel.selectStation(st.id) },
                        label = {
                            Text(if (st.lastUpdated > 0) "${st.name} ${st.currentTemp.toInt()}°C" else st.name, fontSize = 12.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("chip_station_${st.id}")
                    )
                }
            }
        }
        // 3) Previsão 7 / 15 dias
        item {
            if (dailyForecasts.isEmpty()) {
                SimetCard(Modifier.padding(horizontal = 16.dp)) {
                    Text("Previsão de ${currentStation?.name ?: state.displayName}: aguardando a primeira atualização (Open-Meteo).",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                RainForecast7DaysCard(
                    dailyForecasts = dailyForecasts,
                    cityName = currentStation?.name ?: state.displayName,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        // 4) Alagamentos/Enchentes
        item {
            val h = hazards[state]
            val worst = h?.gauges?.maxByOrNull { it.overall.rank }
            val summary = when {
                h == null || (h.loading && h.gauges.isEmpty()) -> "Carregando rios monitorados…"
                worst == null -> "Rios indisponíveis no momento"
                worst.overall.rank >= FloodLevel.ATENCAO.rank -> "${worst.gauge.river} (${worst.gauge.place}): ${worst.overall.label}"
                else -> "${h.gauges.size} rios monitorados • sem indicação de cheia nos próximos 7 dias"
            }
            NavCard(
                icon = Icons.Default.Water, title = "Alagamentos/Enchentes",
                subtitle = summary, accent = Color(0xFF0EA5E9), onClick = onOpenFloods, tag = "card_floods_entry"
            )
        }
        // 5) Outros
        item {
            NavCard(
                icon = Icons.Default.Apps, title = "Outros",
                subtitle = "Radar IPMet & ferramentas, tendências, regiões, Google Maps, notícias e ajustes",
                accent = Color(0xFF8B5CF6), onClick = onOpenOthers, tag = "card_others_entry"
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showPdf) PdfExportDialog(viewModel = viewModel, onDismiss = { showPdf = false })
}

@Composable
internal fun NavCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(tag)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SimetCardHeader(icon = icon, title = title, subtitle = subtitle, accent = accent) }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
    }
}
