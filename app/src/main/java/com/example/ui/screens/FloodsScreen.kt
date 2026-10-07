package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.BrState
import com.example.domain.FloodLevel
import com.example.domain.FloodThresholds
import com.example.domain.GaugeStatus
import com.example.ui.components.SimetCard
import com.example.ui.components.SimetCardHeader
import com.example.viewmodel.WeatherViewModel
import java.util.Locale

fun floodLevelColor(l: FloodLevel): Color = when (l) {
    FloodLevel.MUITO_ALTO -> Color(0xFFD32F2F)
    FloodLevel.ALERTA -> Color(0xFFF57C00)
    FloodLevel.ATENCAO -> Color(0xFFF2C200)
    FloodLevel.NORMAL -> Color(0xFF10B981)
    FloodLevel.SEM_DADO -> Color(0xFF64748B)
}

/** Fontes/links do módulo (conferidos em 07/10/2026). */
object FloodLinks {
    data class L(val label: String, val url: String)
    val common = listOf(
        L("ANA – HidroTelemetria (níveis em tempo real)", "https://www.snirh.gov.br/hidrotelemetria/"),
        L("SGB – Sistema de Alerta Hidrológico (SACE)", "https://www.sgb.gov.br/sace/"),
        L("CEMADEN – monitoramento de desastres", "https://www.gov.br/cemaden/pt-br"),
        L("GloFAS – Copernicus Emergency Management Service", "https://global-flood.emergency.copernicus.eu/"),
        L("Open-Meteo Flood API (dados GloFAS usados no app)", "https://open-meteo.com/en/docs/flood-api")
    )
    fun forState(s: BrState): List<L> = when (s) {
        BrState.RS -> listOf(
            L("Defesa Civil RS", "https://www.defesacivil.rs.gov.br"),
            L("Defesa Civil RS – nível do Guaíba", "https://www.defesacivil.rs.gov.br/nivel-no"),
            L("Governo RS – cotas do Guaíba (alerta 2,55 m / inundação 3,00 m)", "https://www.estado.rs.gov.br/estado-atualiza-cota-de-inundacao-do-guaiba-na-usina-do-gasometro")
        )
        BrState.PR -> listOf(L("Defesa Civil PR", "https://www.defesacivil.pr.gov.br"))
        BrState.SP -> listOf(L("Defesa Civil SP", "https://www.defesacivil.sp.gov.br"))
    } + common

    fun history(s: BrState): List<Pair<String, String>> = when (s) {
        BrState.RS -> listOf(
            "Maio/2024: o Guaíba chegou a 5,35 m no Cais Mauá em 05/05 (recorde anterior: 4,76 m em 1941)." to
                "https://www.scielo.br/j/ea/a/LyHVHKHzm67CwpvcWPKPwTm/",
            "Balanço da Defesa Civil RS em 30/05/2024: 169 mortes, 44 desaparecidos, 473 municípios e 2,35 milhões de pessoas afetadas." to
                "https://www.estado.rs.gov.br/defesa-civil-atualiza-balanco-das-enchentes-no-rs-30-5-9h",
            "Taquari: cota de inundação de 19 m em Estrela (ANA), superada novamente em junho/2025." to
                "https://noticias.uol.com.br/cotidiano/ultimas-noticias/2025/06/30/moradores-sao-levados-a-abrigos-apos-rio-atingir-cota-de-inundacao-no-rs.htm"
        )
        BrState.PR -> listOf(
            "União da Vitória (Rio Iguaçu): enchente de 1983 com 10,42 m e 18 mortes." to
                "https://www.defesacivil.pr.gov.br/sites/defesa-civil/arquivos_restritos/files/documento/2019-05/enchente_de_uniao_da_vitoria_de_1983.pdf",
            "Grandes cheias em 1983, 1992, 2014 e 2023 (≈8,1 m em 2014 e 2023)." to
                "https://g1.globo.com/pr/campos-gerais-sul/noticia/2023/10/18/quatro-enchentes-em-quarenta-anos-entenda-porque-o-rio-iguacu-alaga-tanto-uniao-da-vitoria.ghtml"
        )
        BrState.SP -> emptyList()
    }
}

@Composable
fun FloodsScreen(viewModel: WeatherViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by viewModel.selectedState.collectAsState()
    val hazards by viewModel.hazardsByState.collectAsState()
    val h = hazards[state]
    LaunchedEffect(state) { viewModel.refreshHazards(state) }

    fun open(url: String) = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) { }

    LazyColumn(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("floods_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Alagamentos/Enchentes", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text("Rios de ${state.displayName}: vazão prevista e nível observado", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { viewModel.refreshHazards(state, force = true) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(BrState.RS, BrState.PR, BrState.SP).forEach { st ->
                    FilterChip(selected = st == state, onClick = { viewModel.setSelectedState(st) }, label = { Text(st.uf) })
                }
            }
        }
        item {
            Text(
                "Vazão: modelo hidrológico GloFAS v4 (Copernicus) via Open-Meteo, média do conjunto de previsões, comparada às cheias " +
                    "típicas de 2/5/20 anos da própria reanálise do modelo. Nível: telemetria da ANA. Indicadores do modelo NÃO são avisos oficiais; " +
                    "siga a Defesa Civil.",
                fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        when {
            h == null || (h.loading && h.gauges.isEmpty()) -> item {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            }
            h.gauges.isEmpty() -> item {
                SimetCard(Modifier.padding(horizontal = 16.dp)) { Text("Dados de rios indisponíveis agora. Tente novamente com conexão.", fontSize = 12.sp) }
            }
            else -> items(h.gauges.sortedByDescending { it.overall.rank }, key = { it.gauge.id }) { g ->
                GaugeCard(g, Modifier.padding(horizontal = 16.dp))
            }
        }
        val hist = FloodLinks.history(state)
        if (hist.isNotEmpty()) item {
            SimetCard(Modifier.padding(horizontal = 16.dp)) {
                SimetCardHeader(Icons.Default.History, "Histórico", "Eventos marcantes (fontes públicas)")
                Spacer(Modifier.height(6.dp))
                hist.forEach { (t, url) ->
                    Text("• $t", fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Fonte", fontSize = 11.sp, color = Color(0xFF38BDF8), textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { open(url) }.padding(bottom = 6.dp))
                }
            }
        }
        item {
            SimetCard(Modifier.padding(horizontal = 16.dp)) {
                SimetCardHeader(Icons.Default.Link, "Fontes oficiais e dados", null)
                Spacer(Modifier.height(6.dp))
                FloodLinks.forState(state).forEach { l ->
                    Text(l.label, fontSize = 12.sp, color = Color(0xFF38BDF8), textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { open(l.url) }.padding(vertical = 3.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Atribuição: vazões GloFAS © Copernicus Emergency Management Service (dados via Open-Meteo, CC BY 4.0). " +
                        "Níveis: Agência Nacional de Águas e Saneamento Básico (ANA), Rede Hidrometeorológica Nacional.",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun fmtQ(v: Double?): String = when {
    v == null -> "—"
    v >= 100 -> String.format(Locale("pt", "BR"), "%,.0f", v)
    else -> String.format(Locale("pt", "BR"), "%.1f", v)
}

@Composable
fun GaugeCard(s: GaugeStatus, modifier: Modifier = Modifier) {
    val g = s.gauge
    val color = floodLevelColor(s.overall)
    SimetCard(modifier.testTag("gauge_${g.id}"), accent = color) {
        SimetCardHeader(Icons.Default.Water, "${g.river} • ${g.place}", "Ponto GloFAS ${String.format(Locale.US, "%.3f, %.3f", g.glofasLat, g.glofasLon)}", accent = color) {
            Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.2f)) {
                Text(s.overall.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        // Modelo
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Metric("Hoje", "${fmtQ(s.todayQ)} m³/s")
            Metric("Pico 7 d", "${fmtQ(s.peak7Mean)} m³/s")
            Metric("Tendência 3 d", s.trend?.let { "${it.arrow} ${it.label}" } ?: "—")
        }
        Spacer(Modifier.height(6.dp))
        s.glofas?.let { DischargeChart(s, Modifier.fillMaxWidth().height(110.dp)) }
        Spacer(Modifier.height(4.dp))
        Text("Modelo: ${s.modelLevel.label}. ${s.modelReason}", fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Observado
        if (g.anaCode != null) {
            Spacer(Modifier.height(8.dp))
            val lvl = s.lastLevelCm?.let { String.format(Locale("pt", "BR"), "%.2f m", it / 100) } ?: "—"
            val d24 = s.delta24hCm?.let { String.format(Locale("pt", "BR"), "%+.0f cm em 24 h", it) } ?: "variação 24 h indisponível"
            Text("Observado (ANA ${g.anaName}, ${g.anaCode}): $lvl • $d24", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            s.lastLevelTime?.let { Text("Leitura: $it (BRT)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            s.observedReason?.let { Text(it, fontSize = 11.sp, color = if (s.observedLevel.rank >= FloodLevel.ALERTA.rank) floodLevelColor(s.observedLevel) else MaterialTheme.colorScheme.onSurfaceVariant) }
            g.official?.let { Text("Cota oficial: ${it.source}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        g.note?.let { Spacer(Modifier.height(4.dp)); Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** 14 dias passados + 30 previstos: linha = controle/reanálise, azul = média do conjunto, tracejado = membro máximo; limiares 2/5/20 anos. */
@Composable
private fun DischargeChart(s: GaugeStatus, modifier: Modifier) {
    val series = s.glofas ?: return
    val th = FloodThresholds.byGauge[s.gauge.id]
    val values = (series.discharge + series.ensembleMean + series.ensembleMax).filterNotNull()
    if (values.isEmpty()) return
    val maxV = maxOf(values.max(), th?.q2 ?: 0.0) * 1.08
    val lineColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val legend = remember(th) { th != null }
    Column {
        Canvas(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))) {
            val n = series.dates.size
            if (n < 2) return@Canvas
            fun x(i: Int) = size.width * i / (n - 1)
            fun y(v: Double) = (size.height * (1 - v / maxV)).toFloat()
            th?.let {
                listOf(it.q2 to Color(0xFFF2C200), it.q5 to Color(0xFFF57C00), it.q20 to Color(0xFFD32F2F)).forEach { (q, c) ->
                    if (q <= maxV) drawLine(c.copy(alpha = 0.8f), Offset(0f, y(q)), Offset(size.width, y(q)), strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
            }
            if (series.todayIndex in 0 until n) drawLine(gridColor, Offset(x(series.todayIndex), 0f), Offset(x(series.todayIndex), size.height), strokeWidth = 2f)
            fun path(list: List<Double?>): Path {
                val p = Path(); var started = false
                list.forEachIndexed { i, v -> if (v != null) { if (!started) { p.moveTo(x(i), y(v)); started = true } else p.lineTo(x(i), y(v)) } }
                return p
            }
            drawPath(path(series.ensembleMax), Color(0xFF38BDF8).copy(alpha = 0.5f), style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
            drawPath(path(series.ensembleMean), Color(0xFF38BDF8), style = Stroke(4f))
            drawPath(path(series.discharge.take(series.todayIndex + 1)), lineColor, style = Stroke(3f))
        }
        Text(
            "Últimos 14 d e próximos 30 d • linha clara: reanálise/controle • azul: média do conjunto • tracejado azul: membro mais alto" +
                if (legend) " • amarelo/laranja/vermelho: cheias de 2/5/20 anos" else "",
            fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(0.dp))
    }
}
