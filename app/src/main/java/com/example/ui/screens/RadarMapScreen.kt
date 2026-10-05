package com.example.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ui.components.ExpandedRadarMapDialog
import com.example.ui.components.GoogleMapsRainPrecisionCard
import com.example.ui.components.HourlyRainInspectorCard
import com.example.ui.components.IpmetRadarCanvas
import com.example.ui.components.OfflineStatusBar
import com.example.ui.components.PdfExportDialog
import com.example.ui.components.RainForecast7DaysCard
import com.example.ui.components.RegionalRainfallCard
import com.example.ui.components.RegionalWeatherNewsCard
import com.example.ui.components.StatePrecipitationForecastMap
import com.example.ui.components.WindyWebViewCard
import com.example.util.PdfExporter
import com.example.viewmodel.WeatherViewModel

@Composable
fun RadarMapScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stations by viewModel.allStations.collectAsState()
    val currentStation by viewModel.currentStation.collectAsState()
    val currentCiiagro by viewModel.currentCiiagroRecord.collectAsState()
    val activeCenter by viewModel.activeRadarCenter.collectAsState()
    val timeStep by viewModel.radarTimeStep.collectAsState()
    val isPlaying by viewModel.isRadarPlaying.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val alerts by viewModel.allAlerts.collectAsState()
    val prefs by viewModel.userPreferences.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val showTrajectories by viewModel.showTrajectories.collectAsState()
    val isExpandedMapOpen by viewModel.isExpandedMapOpen.collectAsState()
    val selectedStormCell by viewModel.selectedStormCell.collectAsState()
    val selectedHourIndex by viewModel.selectedHourIndex.collectAsState()
    val hourlyForecasts by viewModel.hourlyForecasts.collectAsState()
    val dailyForecasts by viewModel.dailyForecasts.collectAsState()
    val userCoordinates by viewModel.userCoordinates.collectAsState()
    val mapsRainPrecisionState by viewModel.mapsRainPrecisionState.collectAsState()
    val liveTimeMillis by viewModel.liveCurrentTime.collectAsState()
    val mapFormat by viewModel.mapFormat.collectAsState()
    val mapBackgroundTheme by viewModel.mapBackgroundTheme.collectAsState()
    val isWindyWebViewEnabled by viewModel.isWindyWebViewEnabled.collectAsState()
    val regionalWeatherNews by viewModel.regionalWeatherNews.collectAsState()

    var showPdfExportDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.focusOnUserLocation(context) { msg, _ ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Permissão de localização não concedida", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("radar_map_screen")
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SI Met RADAR",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "Radar Doppler, Vento Windy & Previsão SP",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.shareCurrentWeather(context) },
                        modifier = Modifier.testTag("btn_share_radar")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartilhar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { showPdfExportDialog = true },
                        modifier = Modifier.testTag("btn_pdf_radar")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Exportar Relatório PDF",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Offline Status Bar
        item {
            OfflineStatusBar(
                isForcedOffline = prefs.isOfflineModeForced,
                lastUpdated = currentStation?.lastUpdated ?: System.currentTimeMillis(),
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshActiveStation() },
                onToggleOffline = { viewModel.toggleForcedOffline() }
            )
        }

        // Live Clock & Real-Time Meteorological Model Sync Banner
        item {
            val sdfFull = remember { java.text.SimpleDateFormat("EEEE, dd/MM/yyyy • HH:mm:ss 'BRT'", java.util.Locale("pt", "BR")) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF091426)),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A5F))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(Color(0xFF00E676), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sdfFull.format(java.util.Date(liveTimeMillis)).replaceFirstChar { it.uppercase() },
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "WRF / NOWCASTING",
                            color = Color(0xFF38BDF8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Active Alert Ticker Banner
        if (alerts.isNotEmpty()) {
            item {
                val topAlert = alerts.first()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (topAlert.severity == "ALERTA_VERMELHO") Color(0xFF450A0A) else Color(0xFF422006)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (topAlert.severity == "ALERTA_VERMELHO") Color(0xFFFF5252) else Color(0xFFFFAB00),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "[${topAlert.regionName}] ${topAlert.title}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = topAlert.description,
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }

        // Search Bar & GPS Focus Action Bar
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C121E)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            placeholder = { Text("Buscar cidade em SP...", fontSize = 12.sp) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearSearch() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Limpar busca", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("input_search_city")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // GPS Focus Button
                        ElevatedButton(
                            onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("button_focus_my_location")
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Meu Local", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Filtered City Search Results Quick Chips
                    if (searchQuery.isNotBlank()) {
                        Text(
                            text = "${searchResults.size} cidades encontradas:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(searchResults) { st ->
                                FilterChip(
                                    selected = currentStation?.id == st.id,
                                    onClick = {
                                        viewModel.selectStation(st.id)
                                        viewModel.clearSearch()
                                    },
                                    label = { Text("${st.name} (${st.rainVolumeMm} mm)", fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    modifier = Modifier.testTag("chip_search_result_${st.id}")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick City Selector Horizontal Row
        item {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stations) { st ->
                    FilterChip(
                        selected = st.id == currentStation?.id,
                        onClick = { viewModel.selectStation(st.id) },
                        label = { Text("${st.name} (${st.currentTemp.toInt()}°C • ${st.rainVolumeMm}mm)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("chip_station_${st.id}")
                    )
                }
            }
        }

        // Fullscreen Radar Map Action Button & Selector de Modo (Nativo vs Windy Web)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isWindyWebViewEnabled,
                        onClick = { viewModel.setWindyWebViewEnabled(false) },
                        label = { Text("Radar & Vento Nativo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chip_mode_native_radar")
                    )

                    FilterChip(
                        selected = isWindyWebViewEnabled,
                        onClick = { viewModel.setWindyWebViewEnabled(true) },
                        label = { Text("Windy Web Ao Vivo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(Icons.Default.Air, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chip_mode_windy_webview")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = { viewModel.setExpandedMapOpen(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("button_open_fullscreen_radar_main")
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Abrir Mapa da Chuva em Tela Cheia", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Mapa Principal: Windy WebView Interativo OU IpmetRadarCanvas Nativo com Correntes de Vento
        if (isWindyWebViewEnabled) {
            item {
                WindyWebViewCard(
                    station = currentStation,
                    userCoordinates = userCoordinates,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    onOpenNativeRadar = { viewModel.setWindyWebViewEnabled(false) }
                )
            }
        } else {
            item {
                IpmetRadarCanvas(
                    stations = stations,
                    activeCenter = activeCenter,
                    timeStep = timeStep,
                    isPlaying = isPlaying,
                    isEnergySaver = prefs.isEnergySaverEnabled,
                    selectedStation = currentStation,
                    stormCells = viewModel.activeStormCells,
                    showTrajectories = showTrajectories,
                    selectedStormCell = selectedStormCell,
                    userCoordinates = userCoordinates,
                    onStationSelected = { viewModel.selectStation(it.id) },
                    onCenterChanged = { viewModel.setRadarCenter(it) },
                    onTimeStepChanged = { viewModel.setRadarTimeStep(it) },
                    onTogglePlay = { viewModel.toggleRadarPlayback() },
                    onSelectStormCell = { viewModel.selectStormCell(it) },
                    onToggleTrajectories = { viewModel.toggleTrajectories() },
                    onOpenExpandedMap = { viewModel.setExpandedMapOpen(true) },
                    mapFormat = mapFormat,
                    onMapFormatChanged = { viewModel.setMapFormat(it) },
                    mapBackgroundTheme = mapBackgroundTheme,
                    onMapBackgroundThemeChanged = { viewModel.setMapBackgroundTheme(it) }
                )
            }
        }

        // Diagnóstico de Precisão de Chuva com Google Maps Grounding & Gemini 3.5 Flash
        item {
            GoogleMapsRainPrecisionCard(
                precisionState = mapsRainPrecisionState,
                userCoordinates = userCoordinates,
                selectedStationName = currentStation?.name,
                onRequestPrecision = { customQuery ->
                    viewModel.requestMapsRainPrecision(customQuery)
                },
                onClearPrecision = {
                    viewModel.clearMapsRainPrecision()
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Hourly Rain Inspector Card (mm for selected hour + projection)
        item {
            HourlyRainInspectorCard(
                hourlyForecasts = hourlyForecasts,
                selectedIndex = selectedHourIndex,
                onSelectHour = { viewModel.selectHourIndex(it) },
                stationName = currentStation?.name ?: "São Paulo"
            )
        }

        // Previsão de Chuva nos 7 Dias (Acumulados e Probabilidade)
        item {
            RainForecast7DaysCard(
                dailyForecasts = dailyForecasts,
                cityName = currentStation?.name ?: "Sua Região",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Notícias do Clima & Alertas Regionais Baseadas na Localização / GPS
        item {
            RegionalWeatherNewsCard(
                newsList = regionalWeatherNews,
                currentRegionName = currentStation?.region ?: "Estado de São Paulo",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Regional Rainfall Accumulation (mm) across São Paulo State
        item {
            RegionalRainfallCard(
                currentStationRegion = currentStation?.region ?: "Estado de SP",
                summaries = viewModel.regionalRainfallSummaries,
                onSelectRegion = { regionName ->
                    val matchingStation = stations.find {
                        it.region.contains(regionName, ignoreCase = true) ||
                        regionName.contains(it.region, ignoreCase = true) ||
                        it.name.contains(regionName.take(6), ignoreCase = true)
                    }
                    matchingStation?.let { viewModel.selectStation(it.id) }
                }
            )
        }

        // Novo Mapa Regional de Chuva e Previsões Climáticas (Visualização Estável)
        item {
            StatePrecipitationForecastMap(
                stations = stations,
                selectedStation = currentStation,
                userCoordinates = userCoordinates,
                onStationSelected = { viewModel.selectStation(it.id) },
                mapFormat = mapFormat,
                onMapFormatChanged = { viewModel.setMapFormat(it) },
                mapBackgroundTheme = mapBackgroundTheme,
                onMapBackgroundThemeChanged = { viewModel.setMapBackgroundTheme(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Information Callout on Radar UNESP
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Sobre a Rede de Radares IPMet SP",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "O IPMet (Centro de Meteorologia da UNESP) opera radares Doppler cobrindo todo o estado de São Paulo. A tela exibe onde está a chuva e os vetores da trajetória projetada em +15 min, +30 min e +45 min, além da medição horária em milímetros (mm) para alertas de enxurrada e granizo.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Fullscreen Expanded Radar Map Dialog
    if (isExpandedMapOpen) {
        ExpandedRadarMapDialog(
            stations = stations,
            activeCenter = activeCenter,
            timeStep = timeStep,
            isPlaying = isPlaying,
            isEnergySaver = prefs.isEnergySaverEnabled,
            selectedStation = currentStation,
            stormCells = viewModel.activeStormCells,
            showTrajectories = showTrajectories,
            selectedStormCell = selectedStormCell,
            userCoordinates = userCoordinates,
            searchQuery = searchQuery,
            searchResults = searchResults,
            onStationSelected = { viewModel.selectStation(it.id) },
            onCenterChanged = { viewModel.setRadarCenter(it) },
            onTimeStepChanged = { viewModel.setRadarTimeStep(it) },
            onTogglePlay = { viewModel.toggleRadarPlayback() },
            onToggleTrajectories = { viewModel.toggleTrajectories() },
            onSelectStormCell = { viewModel.selectStormCell(it) },
            onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
            onLocateUser = {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            mapFormat = mapFormat,
            onMapFormatChanged = { viewModel.setMapFormat(it) },
            mapBackgroundTheme = mapBackgroundTheme,
            onMapBackgroundThemeChanged = { viewModel.setMapBackgroundTheme(it) },
            onDismiss = { viewModel.setExpandedMapOpen(false) }
        )
    }

    // Modal de Exportação Avançada em PDF com Funções e Notícias Regionais
    if (showPdfExportDialog) {
        PdfExportDialog(
            viewModel = viewModel,
            onDismiss = { showPdfExportDialog = false }
        )
    }
}

