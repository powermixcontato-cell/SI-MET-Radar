package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.DailyForecastEntity
import com.example.util.safeDrawText
import kotlin.math.max
import kotlin.math.min

/**
 * Gráfico com visualização de dados para produtores rurais:
 * - Variação de Umidade Relativa do Ar (%) e Temperatura (°C) nos próximos 7 dias.
 * - Eixos duplos coordenados, curvas de evolução suave, zonas de conforto agronômico
 *   (faixa de pulverização de defensivos e risco de estresse térmico/desidratação).
 */
@Composable
fun AgroTempHumidityVariationChart(
    dailyForecasts: List<DailyForecastEntity>,
    cityName: String,
    modifier: Modifier = Modifier
) {
    if (dailyForecasts.isEmpty()) return

    val next7Days = remember(dailyForecasts) { dailyForecasts.take(7) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var activeMetricView by remember { mutableIntStateOf(0) } // 0: Ambos (Duplo Eixo), 1: Apenas Umidade, 2: Apenas Temperatura
    val textMeasurer = rememberTextMeasurer()

    val safeIndex = selectedIndex.coerceIn(0, next7Days.lastIndex)
    val selectedDay = next7Days[safeIndex]

    // Estimate relative humidity from rain probability and rain volume (realistic rural microclimate proxy)
    val estimatedHumidityList = remember(next7Days) {
        next7Days.map { d ->
            val base = 48 + (d.rainProbability * 0.45) + min(18.0, d.rainVolumeMm * 1.5)
            base.toInt().coerceIn(28, 96)
        }
    }

    val minTemp = remember(next7Days) { (next7Days.minOfOrNull { it.minTemp } ?: 14.0) - 2.0 }
    val maxTemp = remember(next7Days) { (next7Days.maxOfOrNull { it.maxTemp } ?: 34.0) + 2.0 }

    val minHumidity = 20
    val maxHumidity = 100

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("agro_temp_humidity_variation_chart"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Thermostat,
                            contentDescription = "Variação Agro",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Variação de Umidade e Temperatura",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Evolução nos próximos 7 dias para $cityName",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "Agro 7D",
                        color = Color(0xFF10B981),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode Selector Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = activeMetricView == 0,
                        onClick = { activeMetricView = 0 },
                        label = { Text("Umidade & Temp (Combinado)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = activeMetricView == 1,
                        onClick = { activeMetricView = 1 },
                        label = { Text("Foco em Umidade (%)", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Opacity, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = activeMetricView == 2,
                        onClick = { activeMetricView = 2 },
                        label = { Text("Foco em Temperatura (°C)", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Thermostat, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF97316),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF0B132B), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(184.dp)
                        .pointerInput(next7Days) {
                            detectTapGestures { offset ->
                                val stepW = size.width / next7Days.size
                                val idx = (offset.x / stepW).toInt().coerceIn(0, next7Days.size - 1)
                                selectedIndex = idx
                            }
                        }
                        .testTag("canvas_agro_temp_humidity")
                ) {
                    val w = size.width
                    val h = size.height
                    val bottomPadding = 30f
                    val topPadding = 20f
                    val chartH = h - bottomPadding - topPadding
                    val count = next7Days.size
                    val stepX = w / count

                    // 1. Draw Ideal Spraying Window (Umidade 50-70% & Temp <= 30°C) as subtle background band
                    val yHumid70 = topPadding + chartH * (1f - (70f - minHumidity) / (maxHumidity - minHumidity))
                    val yHumid50 = topPadding + chartH * (1f - (50f - minHumidity) / (maxHumidity - minHumidity))
                    drawRoundRect(
                        color = Color(0xFF10B981).copy(alpha = 0.08f),
                        topLeft = Offset(0f, yHumid70),
                        size = Size(w, (yHumid50 - yHumid70).coerceAtLeast(10f)),
                        cornerRadius = CornerRadius(4f, 4f)
                    )

                    // Horizontal Grid Lines
                    val gridLines = listOf(0.25f, 0.5f, 0.75f)
                    gridLines.forEach { frac ->
                        val y = topPadding + chartH * frac
                        drawLine(
                            color = Color(0xFF334155).copy(alpha = 0.5f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }

                    // 2. Plot Humidity Bars or Filled Area
                    if (activeMetricView == 0 || activeMetricView == 1) {
                        val humidPoints = next7Days.mapIndexed { i, _ ->
                            val hum = estimatedHumidityList[i]
                            val x = i * stepX + stepX / 2f
                            val normH = (hum - minHumidity).toFloat() / (maxHumidity - minHumidity)
                            val y = topPadding + chartH * (1f - normH)
                            Offset(x, y)
                        }

                        // Gradient fill under humidity curve
                        val humidFillPath = Path()
                        humidPoints.forEachIndexed { i, pt ->
                            if (i == 0) {
                                humidFillPath.moveTo(pt.x, pt.y)
                            } else {
                                val prev = humidPoints[i - 1]
                                val midX = (prev.x + pt.x) / 2f
                                humidFillPath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                            }
                        }
                        humidFillPath.lineTo(humidPoints.last().x, topPadding + chartH)
                        humidFillPath.lineTo(humidPoints.first().x, topPadding + chartH)
                        humidFillPath.close()

                        drawPath(
                            path = humidFillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF0284C7).copy(alpha = 0.35f), Color(0xFF0284C7).copy(alpha = 0.02f)),
                                startY = topPadding,
                                endY = topPadding + chartH
                            )
                        )

                        // Humidity Line
                        val humidLinePath = Path()
                        humidPoints.forEachIndexed { i, pt ->
                            if (i == 0) humidLinePath.moveTo(pt.x, pt.y)
                            else {
                                val prev = humidPoints[i - 1]
                                val midX = (prev.x + pt.x) / 2f
                                humidLinePath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                            }
                        }
                        drawPath(
                            path = humidLinePath,
                            color = Color(0xFF38BDF8),
                            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                        )

                        // Nodes
                        humidPoints.forEachIndexed { i, pt ->
                            val hum = estimatedHumidityList[i]
                            drawCircle(color = Color(0xFF0C4A6E), radius = 6f, center = pt)
                            drawCircle(color = Color(0xFF38BDF8), radius = 3.5f, center = pt)

                            // Label
                            safeDrawText(
                                textMeasurer = textMeasurer,
                                text = "$hum%",
                                topLeft = Offset(pt.x - 12f, pt.y - 18f),
                                style = TextStyle(color = Color(0xFF7DD3FC), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    // 3. Plot Temperature Curves (Max and Min)
                    if (activeMetricView == 0 || activeMetricView == 2) {
                        val maxTempPoints = next7Days.mapIndexed { i, day ->
                            val x = i * stepX + stepX / 2f
                            val normT = (day.maxTemp - minTemp).toFloat() / (maxTemp - minTemp).toFloat()
                            val y = topPadding + chartH * (1f - normT)
                            Offset(x, y)
                        }

                        val minTempPoints = next7Days.mapIndexed { i, day ->
                            val x = i * stepX + stepX / 2f
                            val normT = (day.minTemp - minTemp).toFloat() / (maxTemp - minTemp).toFloat()
                            val y = topPadding + chartH * (1f - normT)
                            Offset(x, y)
                        }

                        // Max Temp Line
                        val maxTempPath = Path()
                        maxTempPoints.forEachIndexed { i, pt ->
                            if (i == 0) maxTempPath.moveTo(pt.x, pt.y)
                            else {
                                val prev = maxTempPoints[i - 1]
                                val midX = (prev.x + pt.x) / 2f
                                maxTempPath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                            }
                        }
                        drawPath(
                            path = maxTempPath,
                            color = Color(0xFFF97316),
                            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                        )

                        maxTempPoints.forEachIndexed { i, pt ->
                            val temp = next7Days[i].maxTemp.toInt()
                            drawCircle(color = Color(0xFF7C2D12), radius = 6f, center = pt)
                            drawCircle(color = Color(0xFFF97316), radius = 3.5f, center = pt)

                            safeDrawText(
                                textMeasurer = textMeasurer,
                                text = "${temp}°",
                                topLeft = Offset(pt.x - 8f, pt.y - 18f),
                                style = TextStyle(color = Color(0xFFFDBA74), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        }

                        // Min Temp Line (Dashed)
                        val minTempPath = Path()
                        minTempPoints.forEachIndexed { i, pt ->
                            if (i == 0) minTempPath.moveTo(pt.x, pt.y)
                            else {
                                val prev = minTempPoints[i - 1]
                                val midX = (prev.x + pt.x) / 2f
                                minTempPath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                            }
                        }
                        drawPath(
                            path = minTempPath,
                            color = Color(0xFFFBBF24),
                            style = Stroke(
                                width = 2f,
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                            )
                        )
                    }

                    // 4. Highlight Selected Day Vertical Cursor
                    val selCenterX = safeIndex * stepX + stepX / 2f
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(selCenterX, topPadding),
                        end = Offset(selCenterX, topPadding + chartH),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )

                    // 5. Day of Week Labels at bottom
                    next7Days.forEachIndexed { i, day ->
                        val x = i * stepX + stepX / 2f
                        val isSel = i == safeIndex
                        safeDrawText(
                            textMeasurer = textMeasurer,
                            text = day.dayOfWeek,
                            topLeft = Offset(x - 12f, h - 22f),
                            style = TextStyle(
                                color = if (isSel) Color.White else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                        safeDrawText(
                            textMeasurer = textMeasurer,
                            text = day.dateText,
                            topLeft = Offset(x - 14f, h - 10f),
                            style = TextStyle(
                                color = if (isSel) Color(0xFF38BDF8) else Color(0xFF64748B),
                                fontSize = 9.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legend Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF38BDF8), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Umidade do Ar (%)", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFF97316), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Temp. Máxima (°C)", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFFBBF24), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Temp. Mínima (°C)", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selected Day Agronomic Diagnostic Card
            val activeHumid = estimatedHumidityList[safeIndex]
            val sprayRecommendation = when {
                activeHumid < 45 -> "Crítico: Baixa umidade (<45%). Alto risco de evaporação de gotas e queima de defensivos. Evite pulverizar entre 10h e 16h."
                activeHumid in 50..70 && selectedDay.maxTemp <= 30.0 -> "Excelente: Janela ideal para aplicação de defensivos e foliares (umidade entre 50-70% e temperatura amena)."
                activeHumid > 85 -> "Atenção: Alta umidade (>85%). Monitorar tempo de secagem das folhas e risco de fungos/bactérias."
                else -> "Condição Regular: Pulverização permitida nas primeiras horas da manhã ou final da tarde."
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedDay.dayOfWeek} (${selectedDay.dateText}) - Diagnóstico para o Produtor",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("${selectedDay.rainProbability}%", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Umidade Estimada", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            Text("$activeHumid%", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column {
                            Text("Variação Térmica", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            Text("${selectedDay.minTemp.toInt()}°C a ${selectedDay.maxTemp.toInt()}°C", color = Color(0xFFF97316), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column {
                            Text("Chuva Prevista", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            Text("${selectedDay.rainVolumeMm} mm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = if (activeHumid in 50..70) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sprayRecommendation,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
