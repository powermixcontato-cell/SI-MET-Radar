package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.WeatherAlertEntity
import com.example.data.local.entity.WeatherStationEntity
import com.example.data.repository.AgroLoadState
import com.example.data.repository.AgroSeries
import com.example.ui.components.RainAccumulationCard
import com.example.domain.CanaCitrosIndices
import com.example.domain.CanaCitrosIndices.br
import com.example.domain.CanaCitrosIndices.fmt
import com.example.domain.RiskIndicator
import com.example.domain.RiskLevel
import com.example.ui.map.CityValueChip
import com.example.viewmodel.WeatherViewModel

private val CanaGreen = Color(0xFF16A34A)
private val CitrusOrange = Color(0xFFF59E0B)

@Composable
fun CanaCitrosScreen(viewModel: WeatherViewModel) {
    val station by viewModel.currentStation.collectAsState()
    val series by viewModel.currentAgroSeries.collectAsState()
    val alerts by viewModel.allAlerts.collectAsState()
    val stations by viewModel.allStations.collectAsState()
    val refreshing by viewModel.isRefreshing.collectAsState()
    val loadState by viewModel.currentAgroLoadState.collectAsState()
    val selectedId by viewModel.selectedStationId.collectAsState()
    // Carrega a série ao abrir a aba e ao trocar de cidade (antes só aparecia após "Atualizar")
    LaunchedEffect(selectedId) { viewModel.ensureAgroSeriesLoaded() }
    CanaCitrosContent(
        station = station,
        series = series,
        alerts = alerts,
        stations = stations,
        isRefreshing = refreshing,
        loadState = loadState,
        month = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Sao_Paulo")).get(java.util.Calendar.MONTH) + 1,
        onSelectStation = { viewModel.selectStation(it) },
        onRefresh = { viewModel.refreshActiveStation() },
        onRetry = { viewModel.ensureAgroSeriesLoaded() }
    )
}

@Composable
fun CanaCitrosContent(
    station: WeatherStationEntity?,
    series: AgroSeries?,
    alerts: List<WeatherAlertEntity>,
    stations: List<WeatherStationEntity>,
    isRefreshing: Boolean,
    month: Int,
    onSelectStation: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    loadState: AgroLoadState = AgroLoadState.Idle,
    onRetry: () -> Unit = onRefresh,
    /** v5.1 (aba Agro): seletor de estado e botão de PDF no cabeçalho. */
    stateSelector: (@Composable () -> Unit)? = null,
    onExportPdf: (() -> Unit)? = null,
    alertsScopeLabel: String = "SP"
) {
    val activeAlerts = remember(alerts) {
        alerts.filter { !it.isAcknowledged }.sortedBy { severityRank(it.severity) }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("cana_citros_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Cabeçalho
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🌱🍊", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Cana & Citros", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            station?.let { "${it.name} • ${it.region}" } ?: "Selecione uma cidade",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (onExportPdf != null) {
                        IconButton(onClick = onExportPdf, modifier = Modifier.size(40.dp).testTag("btn_pdf_agro_topbar")) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Boletim agro em PDF", tint = CanaGreen)
                        }
                    }
                    IconButton(onClick = onRefresh, enabled = !isRefreshing, modifier = Modifier.size(40.dp).testTag("btn_cana_citros_refresh")) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                stateSelector?.let { it(); Spacer(Modifier.height(6.dp)) }
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(stations, key = { it.id }) { st ->
                            CityValueChip(
                                name = st.name, value = null, valueColor = Color.Transparent,
                                selected = st.id == station?.id, onClick = { onSelectStation(st.id) }
                            )
                        }
                    }
                }
                Text(
                    series?.let { "Dados: Open-Meteo (modelo, últimos 30 dias + previsão) • atualizado ${hhmm(it.fetchedAt)}" }
                        ?: "Dados: Open-Meteo (modelo) • aguardando atualização",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Alertas INMET no topo
        item { AlertsSection(activeAlerts, alertsScopeLabel) }

        if (series == null || station == null) {
            item {
                when {
                    station == null || loadState is AgroLoadState.Idle || loadState is AgroLoadState.Loading ->
                        SectionCard("Carregando dados da Open-Meteo…", Color(0xFF0EA5E9), Modifier.testTag("cana_citros_loading")) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Buscando a série horária e diária (últimos 30 dias + previsão)" + (station?.let { " para ${it.name}" } ?: "") + ".",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    else ->
                        SectionCard("Não foi possível carregar os dados", Color(0xFFEF4444), Modifier.testTag("cana_citros_error")) {
                            Text(
                                (loadState as? AgroLoadState.Error)?.message ?: "A Open-Meteo não devolveu a série desta cidade.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Os índices de cana e citros usam somente dados reais da Open-Meteo; nada é mostrado sem dado.",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(onClick = onRetry, enabled = !isRefreshing, modifier = Modifier.height(40.dp).testTag("btn_cana_citros_retry")) {
                                Text("Tentar novamente", fontSize = 12.sp)
                            }
                        }
                }
            }
            return@LazyColumn
        }
        if (loadState is AgroLoadState.Error) {
            item {
                Text(
                    "Falha na última atualização: ${loadState.message} Exibindo a última série carregada.",
                    fontSize = 11.sp, color = Color(0xFFF59E0B), modifier = Modifier.testTag("cana_citros_stale")
                )
            }
        }

        val wb = CanaCitrosIndices.waterBalance(series)
        val spray = CanaCitrosIndices.sprayOutlook(series)
        val harvest = CanaCitrosIndices.harvestOutlook(series)
        val citrus = listOf(CanaCitrosIndices.citrusBlackSpot(series, month), CanaCitrosIndices.psyllidActivity(series))
        val cana = listOf(CanaCitrosIndices.sugarcaneRust(series), CanaCitrosIndices.heatDroughtStress(series, wb), CanaCitrosIndices.fireRisk(series, wb))
        val both = listOf(CanaCitrosIndices.frost(series))

        item { WaterBalanceCard(wb) }
        item { RainAccumulationCard(series) }
        item { SprayCard(spray) }
        item { HarvestCard(harvest) }
        item { RiskCard("Citros (laranja) — riscos indicativos", CitrusOrange, citrus, "Monitore o pomar e siga o Fundecitrus (fundecitrus.com.br).") }
        item { RiskCard("Cana-de-açúcar — riscos indicativos", CanaGreen, cana, null) }
        item { RiskCard("Cana e citros — geada", Color(0xFF38BDF8), both, null) }
        item {
            Text(
                "Todos os índices são ESTIMATIVAS indicativas calculadas a partir de previsão numérica (Open-Meteo) para o ponto da cidade. " +
                    "Não são diagnóstico, não substituem medição no talhão nem a recomendação do engenheiro agrônomo.",
                fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, accent: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(accent, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun AlertsSection(alerts: List<WeatherAlertEntity>, scope: String = "SP") {
    if (alerts.isEmpty()) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
            Text(
                "Nenhum aviso do INMET para $scope carregado no app (detalhes na aba Alertas)",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("cana_citros_alerts")) {
        alerts.take(3).forEach { a ->
            val c = severityColor(a.severity)
            Row(
                Modifier.fillMaxWidth().background(c.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                    .border(1.dp, c.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = c, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, lineHeight = 14.sp)
                    Text(a.regionName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, lineHeight = 14.sp)
                }
            }
        }
        Text(
            "Fonte: INMET (avisos para $scope)" + if (alerts.size > 3) " • +${alerts.size - 3} na aba Alertas" else "",
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WaterBalanceCard(wb: com.example.domain.WaterBalance) {
    SectionCard("Balanço hídrico (estimativa)", Color(0xFF0EA5E9), Modifier.testTag("card_water_balance")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BalanceColumn("Últimos ${wb.daysPast} dias", wb.rainPast7, wb.et0Past7, wb.balancePast7, Modifier.weight(1f))
            BalanceColumn(
                when {
                    wb.daysPast30 >= 30 -> "Últimos 30 dias"
                    wb.daysPast30 > 0 -> "Últimos ${wb.daysPast30} dias (de 30; histórico disponível)"
                    else -> "Últimos 30 dias"
                },
                wb.rainPast30, wb.et0Past30, wb.balancePast30, Modifier.weight(1f).testTag("balance_30d"),
                deficitThreshold = -45.0
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            BalanceColumn("Próximos 7 dias (previsão)", wb.rainNext7, wb.et0Next7, wb.balanceNext7, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Balanço = chuva − ET0 (evapotranspiração de referência FAO-56 Penman-Monteith, Open-Meteo). " +
                "Negativo = a atmosfera demandou mais água do que choveu. Não considera solo, irrigação nem a cultura (Kc).",
            fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BalanceColumn(title: String, rain: Double?, et0: Double?, bal: Double?, modifier: Modifier, deficitThreshold: Double = -15.0) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(10.dp)).padding(8.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("Chuva: ${rain?.let { "${fmt(it)} mm" } ?: "—"}", fontSize = 12.sp, color = Color(0xFF38BDF8))
        Text("ET0: ${et0?.let { "${fmt(it)} mm" } ?: "—"}", fontSize = 12.sp, color = Color(0xFFF59E0B))
        val c = when { bal == null -> MaterialTheme.colorScheme.onSurface; bal < deficitThreshold -> Color(0xFFEF4444); bal < 0 -> Color(0xFFF59E0B); else -> Color(0xFF22C55E) }
        Text(
            bal?.let { (if (it > 0) "+" else "") + "${fmt(it)} mm" } ?: "—",
            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c
        )
        Text(
            when { bal == null -> "sem dado"; bal < deficitThreshold -> "déficit relevante"; bal < 0 -> "déficit leve"; else -> "sem déficit" },
            fontSize = 10.sp, color = c
        )
    }
}

@Composable
private fun SprayCard(o: com.example.domain.SprayOutlook) {
    SectionCard("Janela de pulverização — próximas ${o.evaluated} h", Color(0xFF22C55E), Modifier.testTag("card_spray_window")) {
        // faixa de 48 h: verde = adequado, vermelho = inadequado, cinza = sem dado
        val ok = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val n = o.hours.size.coerceAtLeast(1)
            val cw = size.width / n
            o.hours.forEachIndexed { i, h ->
                val c = when { h.ok -> Color(0xFF22C55E); h.reason == "sem dado" -> Color(0xFF64748B); else -> Color(0xFFEF4444).copy(alpha = 0.55f) }
                drawRoundRect(c, Offset(i * cw + 0.5f, 0f), Size(cw - 1f, size.height), CornerRadius(2f))
                if (h.time.endsWith("00:00")) drawRect(ok, Offset(i * cw, 0f), Size(1.5f, size.height))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(o.hours.firstOrNull()?.let { br(it.time) } ?: "", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(o.hours.lastOrNull()?.let { br(it.time) } ?: "", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        val good = o.windows.filter { it.hours >= 2 }
        if (good.isEmpty()) {
            val reasons = o.hours.mapNotNull { it.reason }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(2)
            Text(
                "Sem janela adequada de 2 h ou mais." + if (reasons.isNotEmpty()) " Principais limitações: " + reasons.joinToString { "${it.key} (${it.value} h)" } else "",
                fontSize = 12.sp, color = Color(0xFFEF4444)
            )
        } else {
            good.take(4).forEach { w ->
                Text("✓ ${br(w.start)} → ${br(w.end).takeLast(3)} (${w.hours} h)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF22C55E))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Critério (recomendação geral, Manual de Tecnologia de Aplicação ANDEF/ESALQ; Embrapa recomenda as horas mais frescas): " +
                "temperatura < 30 °C, umidade ≥ 55 %, vento 3–10 km/h e sem chuva prevista na hora e nas 2 h seguintes. Confira a bula do produto.",
            fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HarvestCard(h: com.example.domain.HarvestOutlook) {
    SectionCard("Cana: colheita e operações — 7 dias", CanaGreen, Modifier.testTag("card_harvest_window")) {
        Text(
            if (h.consecutiveDryFromToday > 0) "${h.consecutiveDryFromToday} dia(s) seco(s) seguidos a partir de hoje" else "Hoje tem chuva prevista",
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (h.consecutiveDryFromToday >= 3) CanaGreen else Color(0xFFF59E0B)
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            h.days.forEach { d ->
                Column(
                    Modifier.weight(1f)
                        .background((if (d.dry) CanaGreen else Color(0xFF38BDF8)).copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(br(d.date).take(5), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (d.dry) "☀" else "🌧", fontSize = 13.sp)
                    Text(d.rainMm?.let { fmt(it) } ?: "—", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("mm", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Dia seco = chuva prevista < 1 mm e probabilidade < 50 % (critério indicativo do app para tráfego de máquinas e colheita).",
            fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RiskCard(title: String, accent: Color, items: List<RiskIndicator>, footer: String?) {
    SectionCard(title, accent) {
        items.forEachIndexed { i, r ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Column(Modifier.testTag("risk_${r.id}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    LevelPill(r.level)
                }
                Text(r.detail, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(r.basis, fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        footer?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = accent)
        }
    }
}

@Composable
private fun LevelPill(level: RiskLevel) {
    val (txt, c) = when (level) {
        RiskLevel.ALTO -> "ALTO" to Color(0xFFEF4444)
        RiskLevel.MODERADO -> "MODERADO" to Color(0xFFF59E0B)
        RiskLevel.BAIXO -> "BAIXO" to Color(0xFF22C55E)
        RiskLevel.SEM_DADO -> "SEM DADO" to Color(0xFF64748B)
    }
    Surface(shape = RoundedCornerShape(50), color = c.copy(alpha = 0.18f), border = androidx.compose.foundation.BorderStroke(1.dp, c)) {
        Text("$txt (estimativa)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

private fun severityRank(s: String) = when (s) { "ALERTA_VERMELHO" -> 0; "ALERTA_LARANJA" -> 1; "ALERTA_AMARELO" -> 2; else -> 3 }
private fun severityColor(s: String) = when (s) {
    "ALERTA_VERMELHO" -> Color(0xFFEF4444); "ALERTA_LARANJA" -> Color(0xFFF97316); "ALERTA_AMARELO" -> Color(0xFFEAB308); else -> Color(0xFF38BDF8)
}
private fun hhmm(t: Long): String = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(t))
