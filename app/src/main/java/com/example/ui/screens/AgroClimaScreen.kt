package com.example.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AgroSmartIntelligenceSection
import com.example.ui.components.AgroTempHumidityVariationChart
import com.example.ui.components.Ciiagro24hRechartsLineChart
import com.example.ui.components.OfflineStatusBar
import com.example.ui.components.StatePrecipitationForecastMap
import com.example.util.PdfExporter
import com.example.viewmodel.WeatherViewModel

/**
 * AgroClimaScreen - Clean Agronomic Dashboard
 * - Interface limpa, flat e moderna, sem balões inflados ou cards gigantes
 * - Horário ao vivo em tempo real sincronizado com os modelos
 * - Seleção ágil de lavoura/polo agrícola
 * - Diagnósticos objetivos para Cana e Citrus
 * - Gráficos integrados de 24h e 7 dias
 * - Mapa do Estado de SP com formatos dinâmicos
 */
@Composable
fun AgroClimaScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentStation by viewModel.currentStation.collectAsState()
    val currentCiiagro by viewModel.currentCiiagroRecord.collectAsState()
    val allStations by viewModel.allStations.collectAsState()
    val dailyForecasts by viewModel.dailyForecasts.collectAsState()
    val hourlyForecasts by viewModel.hourlyForecasts.collectAsState()
    val prefs by viewModel.userPreferences.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val userCoordinates by viewModel.userCoordinates.collectAsState()
    val liveTimeMillis by viewModel.liveCurrentTime.collectAsState()
    val mapFormat by viewModel.mapFormat.collectAsState()
    val mapBackgroundTheme by viewModel.mapBackgroundTheme.collectAsState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.focusOnUserLocation(context) { msg, _ ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Permissão de GPS necessária para detectar sua lavoura", Toast.LENGTH_SHORT).show()
        }
    }

    val current = currentStation ?: allStations.firstOrNull()

    fun exportAgroPdf() {
        Toast.makeText(context, "Gerando Boletim Agro em PDF...", Toast.LENGTH_SHORT).show()
        viewModel.exportAgroReportPdf(context) { file ->
            if (file != null) {
                PdfExporter.openOrSharePdf(context, file)
            } else {
                Toast.makeText(context, "Erro ao gerar boletim PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val sdfLiveClock = remember { java.text.SimpleDateFormat("dd/MM/yyyy • HH:mm:ss", java.util.Locale("pt", "BR")) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("agro_clima_screen")
    ) {
        // 1. Top Header com Live Clock e Ações Rápidas (Clean)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFF10B981).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Agriculture,
                                contentDescription = "AgroClima",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AgroClima SP",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(Color(0xFF00E676), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${sdfLiveClock.format(java.util.Date(liveTimeMillis))} BRT • Ao Vivo",
                            color = Color(0xFF00E676),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { exportAgroPdf() },
                        modifier = Modifier.testTag("btn_pdf_agro_topbar")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "PDF Agro",
                            tint = Color(0xFF10B981)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.shareCurrentWeather(context) },
                        modifier = Modifier.testTag("btn_share_agro")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartilhar",
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
                lastUpdated = current?.lastUpdated ?: System.currentTimeMillis(),
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshActiveStation() },
                onToggleOffline = { viewModel.toggleForcedOffline() }
            )
        }

        // 2. Barra de Localização Limpa (Sem card grande)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${current?.name ?: "Bauru"}, ${current?.region ?: "SP"}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${current?.currentTemp?.toInt() ?: 24}°C",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    modifier = Modifier.testTag("btn_locate_crop_gps")
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Meu GPS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        }

        // 3. Quick Chips de Polos Agrícolas (Scroll horizontal leve)
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                val agroHubIds = listOf("barretos", "bauru", "ribeirao_preto", "araraquara", "presidente_prudente", "sao_jose_rio_preto", "piracicaba", "campinas")
                val agroHubs = allStations.filter { it.id in agroHubIds }
                items(if (agroHubs.isNotEmpty()) agroHubs else allStations.take(6)) { st ->
                    FilterChip(
                        selected = current?.id == st.id,
                        onClick = { viewModel.selectStation(st.id) },
                        label = { Text("${st.name} ${st.currentTemp.toInt()}°C", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF10B981),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // 4. Módulo de Inteligência Agronômica (Clean, Flat, sem cartões duplicados)
        if (current != null) {
            item {
                AgroSmartIntelligenceSection(
                    station = current,
                    ciiagroRecord = currentCiiagro,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // 5. Gráfico de Linha de 24h com dados agrometeorológicos (CIIAGRO)
        if (current != null && hourlyForecasts.isNotEmpty()) {
            item {
                Ciiagro24hRechartsLineChart(
                    hourlyForecasts = hourlyForecasts,
                    ciiagroRecord = currentCiiagro,
                    cityName = current.name,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // 6. Gráfico de Variação de Umidade e Temperatura dos Próximos 7 Dias
        if (current != null && dailyForecasts.isNotEmpty()) {
            item {
                AgroTempHumidityVariationChart(
                    dailyForecasts = dailyForecasts,
                    cityName = current.name,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // 7. Mapa de Chuva e Previsões Climáticas do Estado de SP
        item {
            StatePrecipitationForecastMap(
                stations = allStations,
                selectedStation = current,
                userCoordinates = userCoordinates,
                onStationSelected = { viewModel.selectStation(it.id) },
                mapFormat = mapFormat,
                onMapFormatChanged = { viewModel.setMapFormat(it) },
                mapBackgroundTheme = mapBackgroundTheme,
                onMapBackgroundThemeChanged = { viewModel.setMapBackgroundTheme(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // 8. Botão Minimalista de Exportação de Relatório Técnico
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { exportAgroPdf() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_export_agro_pdf_card")
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Exportar Boletim Técnico Oficial em PDF",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
