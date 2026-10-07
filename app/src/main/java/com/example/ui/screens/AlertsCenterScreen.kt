package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.BrState
import com.example.domain.HazardAlert
import com.example.domain.HazardCategory
import com.example.domain.HazardKind
import com.example.domain.HazardRules
import com.example.ui.components.SimetCard
import com.example.ui.components.SimetCardHeader
import com.example.viewmodel.WeatherViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Central de alertas (v5.1) com 3 categorias — Enchentes, Ciclones e vendavais, Granizo — para o estado selecionado.
 * Fontes: avisos oficiais do INMET; indicadores calculados da previsão Open-Meteo; rios (GloFAS e telemetria ANA).
 * Indicadores calculados são sempre rotulados como "não é aviso oficial". Nada é inventado: sem dado → "Sem alertas ativos".
 */
@Composable
fun AlertsCenterScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier,
    onOpenFloods: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.selectedState.collectAsStateWithLifecycle()
    val alerts by viewModel.hazardAlerts.collectAsStateWithLifecycle()
    val hazards by viewModel.hazardsByState.collectAsStateWithLifecycle()
    val inmetError by viewModel.inmetAlertsError.collectAsStateWithLifecycle()
    val inmetUpdated by viewModel.inmetAlertsUpdatedAt.collectAsStateWithLifecycle()
    val notifyOn by viewModel.alertNotificationsEnabled.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state) { viewModel.refreshHazards(state) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setAlertNotificationsEnabled(context, granted)
    }

    val shown = alerts.filter { filter == null || it.category.name == filter }
    val brt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("America/Sao_Paulo") } }
    val h = hazards[state]

    LazyColumn(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("alerts_center_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Alertas • ${state.displayName}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text("Enchentes • Ciclones e vendavais • Granizo", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { viewModel.refreshInmetAlertsNow(); viewModel.refreshHazards(state, force = true) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BrState.entries.forEach { st ->
                    FilterChip(selected = st == state, onClick = { viewModel.setSelectedState(st) }, label = { Text(st.uf) })
                }
            }
        }
        item {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Todos (${alerts.size})") }) }
                items(HazardCategory.entries) { c ->
                    val n = alerts.count { it.category == c }
                    FilterChip(selected = filter == c.name, onClick = { filter = c.name }, label = { Text("${c.emoji} ${c.label} ($n)") })
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                val inmetLine = when {
                    inmetError != null -> "INMET: avisos indisponíveis agora (sem conexão ou serviço fora do ar). Os demais itens continuam."
                    inmetUpdated > 0 -> "INMET: avisos consultados às ${brt.format(java.util.Date(inmetUpdated))} BRT."
                    else -> "INMET: consultando avisos…"
                }
                Text(inmetLine, fontSize = 11.sp, color = if (inmetError != null) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant)
                val modelLine = when {
                    h == null || (h.loading && h.updatedAt == 0L) -> "Indicadores (previsão e rios): carregando…"
                    h.forecastError != null || h.glofasError != null ->
                        "Indicadores: parte indisponível (${listOfNotNull(h.forecastError?.let { "previsão: $it" }, h.glofasError?.let { "rios: $it" }).joinToString("; ")})."
                    else -> "Indicadores calculados às ${brt.format(java.util.Date(h.updatedAt))} BRT."
                }
                Text(modelLine, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (shown.isEmpty()) {
            item {
                SimetCard(Modifier.padding(horizontal = 16.dp), accent = Color(0xFF10B981)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Sem alertas ativos", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Nenhum aviso oficial do INMET nem indicador de risco para ${state.displayName}" +
                                    (filter?.let { f -> " na categoria " + HazardCategory.valueOf(f).label } ?: "") + " neste momento.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(shown, key = { it.id }) { a -> HazardAlertCard(a, Modifier.padding(horizontal = 16.dp)) }
        }
        item {
            NavCard(
                icon = Icons.Default.Info, title = "Rios e enchentes em detalhe",
                subtitle = "Vazão prevista (GloFAS) e nível observado (ANA) por rio/cidade",
                accent = Color(0xFF0EA5E9), onClick = onOpenFloods, tag = "alerts_open_floods"
            )
        }
        item {
            SimetCard(Modifier.padding(horizontal = 16.dp)) {
                SimetCardHeader(Icons.Default.NotificationsActive, "Notificações (opcional)",
                    "Avisos oficiais novos de Perigo (laranja) ou Grande Perigo (vermelho) do INMET para ${state.uf}, verificados a cada hora")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (notifyOn) "Ativadas" else "Desativadas", modifier = Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = notifyOn, onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else viewModel.setAlertNotificationsEnabled(context, on)
                    }, modifier = Modifier.testTag("switch_alert_notifications"))
                }
            }
        }
        item {
            SimetCard(Modifier.padding(horizontal = 16.dp)) {
                SimetCardHeader(Icons.Default.Info, "Como os alertas funcionam", "Fontes e critérios")
                Spacer(Modifier.height(8.dp))
                Text(
                    "• Aviso oficial: avisos do INMET (alertas2.inmet.gov.br) que citam mesorregiões de SP, PR ou RS. Cor = severidade do INMET " +
                        "(Perigo Potencial = amarelo, Perigo = laranja, Grande Perigo = vermelho). Categoria pelo tipo do aviso (chuvas/acumulados → Enchentes; " +
                        "vendaval/ventos/ciclone → Ciclones e vendavais; granizo → Granizo; tempestade pode entrar em mais de uma).\n" +
                        "• Indicador de previsão (não é aviso oficial): Open-Meteo, próximos 3 dias, cidades do estado. Rajadas ≥ ${HazardRules.GUST_AMARELO.toInt()}/" +
                        "${HazardRules.GUST_LARANJA.toInt()}/${HazardRules.GUST_VERMELHO.toInt()} km/h; chuva ≥ ${HazardRules.RAIN_DAY_AMARELO.toInt()}/" +
                        "${HazardRules.RAIN_DAY_LARANJA.toInt()} mm/dia ou ≥ ${HazardRules.RAIN_3D_VERMELHO.toInt()} mm em 3 dias; granizo quando o modelo indica " +
                        "trovoada com granizo (códigos 96/99; laranja se CAPE ≥ ${HazardRules.CAPE_GRANIZO_FORTE.toInt()} J/kg) ou trovoada com CAPE ≥ ${HazardRules.CAPE_FAVORAVEL.toInt()} J/kg.\n" +
                        "• Rios: vazão prevista pelo GloFAS (Copernicus/Open-Meteo) comparada às cheias típicas de 2, 5 e 20 anos calculadas da própria reanálise; " +
                        "nível observado da ANA comparado a cotas oficiais só onde foram conferidas (Guaíba/Cais Mauá e Taquari/Estrela).\n" +
                        "Em emergência siga sempre a Defesa Civil (199) e os avisos oficiais.",
                    fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun HazardAlertCard(a: HazardAlert, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val sevColor = Color(a.severity.argb)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, sevColor.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth().clickable { open = !open }.testTag("hazard_${a.id}")
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(sevColor))
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${a.category.emoji} ${a.category.label}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = sevColor)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = sevColor.copy(alpha = 0.18f)) {
                        Text(a.severity.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(a.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    a.kind.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    color = if (a.kind == HazardKind.OFICIAL || a.kind == HazardKind.OBSERVADO_RIO) Color(0xFF38BDF8) else Color(0xFFF59E0B)
                )
                Spacer(Modifier.height(4.dp))
                Text("Área: ${a.area}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Validade: ${a.validity}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Fonte: ${a.source}", fontSize = 11.sp, color = Color(0xFF38BDF8),
                    textDecoration = if (a.sourceUrl != null) TextDecoration.Underline else null,
                    modifier = Modifier.clickable(enabled = a.sourceUrl != null) {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.sourceUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
                    }
                )
                if (open && a.detail.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(a.detail, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (a.detail.isNotBlank()) {
                    Text("Toque para ver detalhes", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
