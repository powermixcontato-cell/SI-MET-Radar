package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.WeatherStationEntity
import com.example.viewmodel.StormCellTrajectory

@Composable
fun ExpandedRadarMapDialog(
    stations: List<WeatherStationEntity>,
    activeCenter: String,
    timeStep: Int,
    isPlaying: Boolean,
    isEnergySaver: Boolean,
    selectedStation: WeatherStationEntity?,
    stormCells: List<StormCellTrajectory>,
    showTrajectories: Boolean,
    selectedStormCell: StormCellTrajectory?,
    userCoordinates: Pair<Double, Double>? = null,
    searchQuery: String,
    searchResults: List<WeatherStationEntity>,
    onStationSelected: (WeatherStationEntity) -> Unit,
    onCenterChanged: (String) -> Unit,
    onTimeStepChanged: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onToggleTrajectories: () -> Unit,
    onSelectStormCell: (StormCellTrajectory?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onLocateUser: () -> Unit,
    mapFormat: Int = 0,
    onMapFormatChanged: (Int) -> Unit = {},
    mapBackgroundTheme: Int = 0,
    onMapBackgroundThemeChanged: (Int) -> Unit = {},
    onDismiss: () -> Unit
) {
    var showSearchBar by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("dialog_expanded_radar"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar inside Dialog
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("button_close_expanded_radar")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar Mapa Expandido",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column {
                            Text(
                                text = "Mapa de chuva prevista (Open-Meteo)",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Chuva e Trajetórias em Tempo Real (SP)",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // GPS Locate Button
                        IconButton(
                            onClick = onLocateUser,
                            modifier = Modifier.testTag("button_expanded_gps_locate")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Focar no Meu Local",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Search Toggle
                        IconButton(
                            onClick = { showSearchBar = !showSearchBar },
                            modifier = Modifier.testTag("button_expanded_toggle_search")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar Cidades",
                                tint = if (showSearchBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Botão de trajetórias removido (sem células de tempestade reais).
                    }
                }

                // Optional Search Bar
                if (showSearchBar) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("Buscar cidade em SP (ex: Bauru, Campinas, Santos)...", fontSize = 12.sp) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Limpar", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("input_expanded_search_city")
                        )

                        // Search Results chips if searching
                        if (searchQuery.isNotBlank()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(searchResults) { st ->
                                    FilterChip(
                                        selected = selectedStation?.id == st.id,
                                        onClick = {
                                            onStationSelected(st)
                                            showSearchBar = false
                                        },
                                        label = { Text("${st.name} (${st.rainVolumeMm} mm)", fontSize = 11.sp) },
                                        leadingIcon = {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Storm Cell Selection Filter Chips
                if (stormCells.isNotEmpty()) Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Células:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(stormCells) { cell ->
                            val isSel = selectedStormCell?.id == cell.id
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    if (isSel) onSelectStormCell(null) else onSelectStormCell(cell)
                                },
                                label = {
                                    Text(
                                        text = "${cell.name.take(16)} (${cell.dbzPeak} dBZ)",
                                        fontSize = 11.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Thunderstorm,
                                        contentDescription = null,
                                        tint = if (cell.dbzPeak >= 50) Color(0xFFFF1744) else Color(0xFFFF9100),
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }

                // Main Radar Canvas (Takes main area)
                Box(modifier = Modifier.weight(1f)) {
                    IpmetRadarCanvas(
                        stations = stations,
                        activeCenter = activeCenter,
                        timeStep = timeStep,
                        isPlaying = isPlaying,
                        isEnergySaver = isEnergySaver,
                        selectedStation = selectedStation,
                        stormCells = stormCells,
                        showTrajectories = showTrajectories,
                        selectedStormCell = selectedStormCell,
                        userCoordinates = userCoordinates,
                        onStationSelected = onStationSelected,
                        onCenterChanged = onCenterChanged,
                        onTimeStepChanged = onTimeStepChanged,
                        onTogglePlay = onTogglePlay,
                        onSelectStormCell = onSelectStormCell,
                        onToggleTrajectories = onToggleTrajectories,
                        onOpenExpandedMap = {}, // already expanded
                        isExpandedMode = true,
                        mapFormat = mapFormat,
                        onMapFormatChanged = onMapFormatChanged,
                        mapBackgroundTheme = mapBackgroundTheme,
                        onMapBackgroundThemeChanged = onMapBackgroundThemeChanged,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Selected Storm Cell Trajectory Info Card
                selectedStormCell?.let { cell ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("card_expanded_storm_cell_info"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Thunderstorm,
                                        contentDescription = null,
                                        tint = Color(0xFFFF1744),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = cell.name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                IconButton(
                                    onClick = { onSelectStormCell(null) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Fechar", modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Deslocamento: ${cell.headingCompass} a ${cell.speedKmH.toInt()} km/h",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Taxa: ${cell.rainRateMmH} mm/h (${cell.dbzPeak} dBZ)",
                                    color = Color(0xFFFF9100),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Trajetória prevista: ${cell.eta15m} • ${cell.eta30m} • ${cell.eta45m}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
