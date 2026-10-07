package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.repository.InmetAlertMapper
import com.example.domain.BrState
import com.example.util.PdfExporter
import com.example.viewmodel.WeatherViewModel

/**
 * Aba Agro (v5.1): foco em cana-de-açúcar e citros, só com dados reais (Open-Meteo + avisos INMET do estado).
 * Saíram a "inteligência agro" com umidade do solo/ATR estimados e o gráfico de umidade inferida (não eram medições).
 * Reaproveita o conteúdo de Cana & Citros (balanço hídrico, chuva acumulada, pulverização, colheita, riscos, geada).
 */
@Composable
fun AgroClimaScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.selectedState.collectAsStateWithLifecycle()
    val station by viewModel.currentStation.collectAsStateWithLifecycle()
    val series by viewModel.currentAgroSeries.collectAsStateWithLifecycle()
    val alerts by viewModel.allAlerts.collectAsStateWithLifecycle()
    val stations by viewModel.stationsOfSelectedState.collectAsStateWithLifecycle()
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val loadState by viewModel.currentAgroLoadState.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedStationId.collectAsStateWithLifecycle()
    LaunchedEffect(selectedId) { viewModel.ensureAgroSeriesLoaded() }

    val stateAlerts = remember(alerts, state) {
        alerts.filter { it.id.startsWith("inmet_") && state in InmetAlertMapper.statesOfEntity(it) }
    }

    CanaCitrosContent(
        station = station,
        series = series,
        alerts = stateAlerts,
        stations = stations,
        isRefreshing = refreshing,
        loadState = loadState,
        month = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Sao_Paulo")).get(java.util.Calendar.MONTH) + 1,
        onSelectStation = { viewModel.selectStation(it) },
        onRefresh = { viewModel.refreshActiveStation() },
        onRetry = { viewModel.ensureAgroSeriesLoaded() },
        modifier = modifier,
        alertsScopeLabel = state.uf,
        stateSelector = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BrState.entries.forEach { st ->
                    FilterChip(selected = st == state, onClick = { viewModel.setSelectedState(st) }, label = { Text(st.uf) })
                }
            }
        },
        onExportPdf = {
            Toast.makeText(context, "Gerando boletim agro em PDF…", Toast.LENGTH_SHORT).show()
            viewModel.exportAgroReportPdf(context) { file ->
                if (file != null) PdfExporter.openOrSharePdf(context, file)
                else Toast.makeText(context, "Não foi possível gerar o PDF (aguarde os dados da Open-Meteo).", Toast.LENGTH_LONG).show()
            }
        }
    )
}
