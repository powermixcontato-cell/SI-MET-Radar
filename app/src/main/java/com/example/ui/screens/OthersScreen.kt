package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.WeatherViewModel

/** Rotas internas da aba "Outros" (v5.1). Nada foi apagado da v5.0: as telas foram movidas para cá. */
enum class OthersRoute(val title: String) {
    RADAR_TOOLS("Radar IPMet & ferramentas"),
    FLOODS("Alagamentos/Enchentes"),
    TRENDS("Tendências & histórico"),
    REGIONS("Regiões e alertas (v5.0)"),
    SETTINGS("Ajustes")
}

const val IPMET_ANIMATION_URL = "https://www.ipmetradar.com.br/2animRadar.php"

@Composable
fun OthersScreen(
    viewModel: WeatherViewModel,
    route: OthersRoute?,
    onRouteChange: (OthersRoute?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (route != null) {
        BackHandler { onRouteChange(null) }
        Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onRouteChange(null) }, modifier = Modifier.testTag("others_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                }
                Text("Outros • ${route.title}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.weight(1f)) {
                when (route) {
                    OthersRoute.RADAR_TOOLS -> RadarToolsScreen(viewModel = viewModel)
                    OthersRoute.FLOODS -> FloodsScreen(viewModel = viewModel)
                    OthersRoute.TRENDS -> HistoricalTrendsScreen(viewModel = viewModel)
                    OthersRoute.REGIONS -> AlertsAndRegionsScreen(viewModel = viewModel)
                    OthersRoute.SETTINGS -> SettingsAndApiScreen(viewModel = viewModel)
                }
            }
        }
        return
    }

    LazyColumn(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("others_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(start = 16.dp, top = 10.dp)) {
                Text("Outros", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                Text("Demais recursos do app", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            NavCard(Icons.Default.Sensors, OthersRoute.RADAR_TOOLS.title,
                "Antiga tela inicial: radar nativo (ilustrativo), busca/GPS, inspetor horário, Google Maps, notícias, chuva regional e mapa do estado",
                Color(0xFF0284C7), { onRouteChange(OthersRoute.RADAR_TOOLS) }, "others_radar_tools")
        }
        item {
            NavCard(Icons.Default.Water, OthersRoute.FLOODS.title, "Rios do RS, PR e SP: vazão prevista (GloFAS) e nível observado (ANA)",
                Color(0xFF0EA5E9), { onRouteChange(OthersRoute.FLOODS) }, "others_floods")
        }
        item {
            NavCard(Icons.Default.Movie, "Animação oficial do radar IPMet",
                "Abre ipmetradar.com.br (quadros reais dos radares de Bauru e Presidente Prudente)",
                Color(0xFF22C55E), {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(IPMET_ANIMATION_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
                }, "others_ipmet_official")
        }
        item {
            NavCard(Icons.Default.ShowChart, OthersRoute.TRENDS.title, "Comparativos mensais e climatologia",
                Color(0xFF8B5CF6), { onRouteChange(OthersRoute.TRENDS) }, "others_trends")
        }
        item {
            NavCard(Icons.Default.NotificationsActive, OthersRoute.REGIONS.title, "Inscrições por região e lista de avisos da v5.0",
                Color(0xFFF59E0B), { onRouteChange(OthersRoute.REGIONS) }, "others_regions")
        }
        item {
            NavCard(Icons.Default.Tune, OthersRoute.SETTINGS.title, "Tema, modo offline, economia de energia, chaves de API e cache",
                Color(0xFF64748B), { onRouteChange(OthersRoute.SETTINGS) }, "others_settings")
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
