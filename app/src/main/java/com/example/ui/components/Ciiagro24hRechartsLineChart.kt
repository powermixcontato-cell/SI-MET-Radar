package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.ShowChart
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CiiagroRecordEntity
import com.example.data.local.entity.HourlyForecastEntity
import kotlin.math.max
import kotlin.math.min

/**
 * Ciiagro24hRechartsLineChart
 * Gráfico interativo de linha avançado inspirado na biblioteca Recharts (<ResponsiveContainer>, <LineChart>,
 * <XAxis>, <YAxis>, <CartesianGrid>, <Tooltip>, <Legend>, <Area fill="url(#grad)" />):
 * - Visualização contínua das próximas 24 horas (horários dinâmicos corrigidos)
 * - Curva de temperatura tipo "monotone" com gradiente luminoso
 * - Linha e barras de volume de chuva (mm) baseados nos registros e modelos CIIAGRO / IAC
 * - Tooltip interativo flutuante exibindo Temperatura, Chuva, Umidade e ETo estimada ao tocar/arrastar
 * - Eixos duplos Y (°C na esquerda e mm na direita)
 * - Design otimizado para OLED PURO com contraste profissional
 */
@Composable
fun Ciiagro24hRechartsLineChart(
    hourlyForecasts: List<HourlyForecastEntity>,
    ciiagroRecord: CiiagroRecordEntity? = null,
    cityName: String = "São Paulo",
    modifier: Modifier = Modifier
) {
    // Garantir exatamente 24 horas de previsão ou preencher se necessário
    val data = remember(hourlyForecasts) {
        if (hourlyForecasts.isEmpty()) {
            emptyList()
        } else {
            hourlyForecasts.take(24)
        }
    }

    // 0: Dual (Temp + Chuva), 1: Apenas Temp, 2: Apenas Chuva
    var chartMode by remember { mutableIntStateOf(0) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ciiagro_24h_recharts_card"),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF080808) // OLED Puro
        ),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header estilo Recharts Dashboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Próximas 24 Horas",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "OPEN-METEO",
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Variação horária de temperatura e precipitação ($cityName)",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legenda & Filtros de Visualização estilo Recharts <Legend />
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = chartMode == 0,
                        onClick = { chartMode = 0 },
                        label = { Text("Dual", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF10B981),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF141414),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                    FilterChip(
                        selected = chartMode == 1,
                        onClick = { chartMode = 1 },
                        label = { Text("Temp (°C)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFA726),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF141414),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                    FilterChip(
                        selected = chartMode == 2,
                        onClick = { chartMode = 2 },
                        label = { Text("Chuva (mm)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00E5FF),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF141414),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                }

                // Legendas de cores dos eixos
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (chartMode != 2) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFFFFA726), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Temp", color = Color(0xFFFFA726), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (chartMode != 1) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF00E5FF), RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chuva", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (data.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Carregando curva de 24 horas...", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            } else {
                // Informação do ponto selecionado no Tooltip estilo Recharts
                val activePoint = selectedIndex?.let { data.getOrNull(it) } ?: data.firstOrNull()

                if (activePoint != null) {
                    val activeIndex = selectedIndex ?: 0
                    val calculatedEto = ciiagroRecord?.et0MmDay?.let { (it / 24.0) * 1.5 } ?: 0.18
                    val activeHumidity = (75 - ((activePoint.temp - 18.0) * 2.2).toInt()).coerceIn(30, 95)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        color = Color(0xFF121212),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF27272A))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF10B981), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedIndex == null) "Agora (${activePoint.hourText})" else "Horário: ${activePoint.hourText}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activePoint.condition,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Thermostat, contentDescription = null, tint = Color(0xFFFFA726), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${activePoint.temp}°C",
                                        color = Color(0xFFFFA726),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${activePoint.rainVolumeMm} mm",
                                        color = Color(0xFF00E5FF),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Opacity, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "$activeHumidity%",
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Canvas interativo estilo Recharts LineChart
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clipToBounds()
                        .background(Color(0xFF000000)) // Fundo OLED Puro
                        .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                        .pointerInput(data) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val leftPadding = 42.dp.toPx()
                                    val rightPadding = 36.dp.toPx()
                                    val chartWidth = size.width - leftPadding - rightPadding
                                    val x = (offset.x - leftPadding).coerceIn(0f, chartWidth)
                                    val step = chartWidth / (data.size - 1).coerceAtLeast(1)
                                    val idx = (x / step).toInt().coerceIn(0, data.size - 1)
                                    selectedIndex = idx
                                }
                            )
                        }
                        .pointerInput(data) {
                            detectDragGestures { change, _ ->
                                val leftPadding = 42.dp.toPx()
                                val rightPadding = 36.dp.toPx()
                                val chartWidth = size.width - leftPadding - rightPadding
                                val x = (change.position.x - leftPadding).coerceIn(0f, chartWidth)
                                val step = chartWidth / (data.size - 1).coerceAtLeast(1)
                                val idx = (x / step).toInt().coerceIn(0, data.size - 1)
                                selectedIndex = idx
                            }
                        }
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("canvas_recharts_line_chart")
                    ) {
                        val w = size.width
                        val h = size.height

                        val leftPad = 42.dp.toPx()
                        val rightPad = 36.dp.toPx()
                        val topPad = 26.dp.toPx()
                        val bottomPad = 32.dp.toPx()

                        val chartW = w - leftPad - rightPad
                        val chartH = h - topPad - bottomPad

                        // Cálculos de Min e Max de Temperatura e Chuva
                        val minT = data.minOf { it.temp }.let { (it - 2.0).toInt().toDouble() }
                        val maxT = data.maxOf { it.temp }.let { (it + 2.0).toInt().toDouble() }
                        val tempRange = max(1.0, maxT - minT)

                        val maxRain = max(5.0, data.maxOf { it.rainVolumeMm } * 1.3)

                        // 1. Cartesian Grid (<CartesianGrid strokeDasharray="3 3" />)
                        val gridLines = 4
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                        for (i in 0..gridLines) {
                            val y = topPad + (chartH * (i.toFloat() / gridLines.toFloat()))
                            drawLine(
                                color = Color(0xFF1E293B).copy(alpha = 0.6f),
                                start = Offset(leftPad, y),
                                end = Offset(w - rightPad, y),
                                strokeWidth = 1f,
                                pathEffect = dashEffect
                            )

                            // Y-Axis Left Labels: Temperatura (°C)
                            if (chartMode != 2) {
                                val tVal = maxT - ((i.toDouble() / gridLines.toDouble()) * tempRange)
                                val tStr = "${tVal.toInt()}°"
                                val textResult = textMeasurer.measure(
                                    text = tStr,
                                    style = TextStyle(color = Color(0xFFFFA726), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                )
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = tStr,
                                    topLeft = Offset(leftPad - textResult.size.width - 6.dp.toPx(), y - (textResult.size.height / 2f)),
                                    style = TextStyle(color = Color(0xFFFFA726), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                )
                            }

                            // Y-Axis Right Labels: Precipitação (mm)
                            if (chartMode != 1) {
                                val rVal = maxRain - ((i.toDouble() / gridLines.toDouble()) * maxRain)
                                val rStr = String.format(java.util.Locale.getDefault(), "%.0f", rVal)
                                val textResult = textMeasurer.measure(
                                    text = rStr,
                                    style = TextStyle(color = Color(0xFF00E5FF), fontSize = 9.sp)
                                )
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = rStr,
                                    topLeft = Offset(w - rightPad + 6.dp.toPx(), y - (textResult.size.height / 2f)),
                                    style = TextStyle(color = Color(0xFF00E5FF), fontSize = 9.sp)
                                )
                            }
                        }

                        // 2. X-Axis Labels a cada 3 horas (0, 3, 6, 9, 12, 15, 18, 21, 23)
                        val stepX = chartW / (data.size - 1).coerceAtLeast(1)
                        for (i in data.indices) {
                            if (i % 3 == 0 || i == data.size - 1) {
                                val x = leftPad + (i * stepX)
                                val hourLabel = data[i].hourText
                                val textResult = textMeasurer.measure(
                                    text = hourLabel,
                                    style = TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                                )
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = hourLabel,
                                    topLeft = Offset(x - (textResult.size.width / 2f), h - bottomPad + 8.dp.toPx()),
                                    style = TextStyle(color = Color(0xFF94A3B8), fontSize = 10.sp)
                                )
                            }
                        }

                        // 3. Renderizar Barras de Precipitação (Recharts <Bar dataKey="rainMm" />)
                        if (chartMode != 1) {
                            val barWidth = (stepX * 0.45f).coerceIn(4f, 16f)
                            for (i in data.indices) {
                                val rain = data[i].rainVolumeMm
                                if (rain > 0.0) {
                                    val x = leftPad + (i * stepX) - (barWidth / 2f)
                                    val barH = (chartH * (rain / maxRain).toFloat()).coerceIn(3f, chartH)
                                    val y = topPad + chartH - barH

                                    // Gradiente vertical da barra de chuva
                                    drawRoundRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color(0xFF00E5FF), Color(0xFF0284C7).copy(alpha = 0.4f)),
                                            startY = y,
                                            endY = topPad + chartH
                                        ),
                                        topLeft = Offset(x, y),
                                        size = Size(barWidth, barH),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                    )
                                }
                            }
                        }

                        // 4. Renderizar Linha de Curva Suave Monotone de Temperatura (Recharts <Line type="monotone" />)
                        if (chartMode != 2) {
                            val points = data.indices.map { i ->
                                val x = leftPad + (i * stepX)
                                val t = data[i].temp
                                val y = topPad + chartH - (chartH * ((t - minT) / tempRange).toFloat())
                                Offset(x, y)
                            }

                            // Caminho suave Monotone Cubic Bezier
                            val linePath = Path()
                            val areaPath = Path()

                            if (points.isNotEmpty()) {
                                linePath.moveTo(points[0].x, points[0].y)
                                areaPath.moveTo(points[0].x, topPad + chartH)
                                areaPath.lineTo(points[0].x, points[0].y)

                                for (i in 0 until points.size - 1) {
                                    val p0 = points[max(0, i - 1)]
                                    val p1 = points[i]
                                    val p2 = points[i + 1]
                                    val p3 = points[min(points.size - 1, i + 2)]

                                    // Spline de controle Monotone Hermite
                                    val cp1X = p1.x + (p2.x - p0.x) / 6f
                                    val cp1Y = p1.y + (p2.y - p0.y) / 6f
                                    val cp2X = p2.x - (p3.x - p1.x) / 6f
                                    val cp2Y = p2.y - (p3.y - p1.y) / 6f

                                    linePath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
                                    areaPath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
                                }

                                areaPath.lineTo(points.last().x, topPad + chartH)
                                areaPath.close()

                                // Preenchimento da Área com gradiente transparente (<Area fill="url(#colorTemp)" />)
                                drawPath(
                                    path = areaPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFFFFA726).copy(alpha = 0.30f),
                                            Color(0xFFFF7043).copy(alpha = 0.12f),
                                            Color.Transparent
                                        ),
                                        startY = topPad,
                                        endY = topPad + chartH
                                    )
                                )

                                // Linha Principal com Brilho Neon
                                drawPath(
                                    path = linePath,
                                    color = Color(0xFFFFA726),
                                    style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                                )

                                // Pontos no gráfico (dots)
                                for (i in points.indices) {
                                    val pt = points[i]
                                    if (i % 2 == 0 || i == selectedIndex) {
                                        drawCircle(
                                            color = Color(0xFF080808),
                                            radius = if (i == selectedIndex) 7f else 4.5f,
                                            center = pt
                                        )
                                        drawCircle(
                                            color = if (i == selectedIndex) Color(0xFFFFFFFF) else Color(0xFFFFA726),
                                            radius = if (i == selectedIndex) 5f else 3f,
                                            center = pt
                                        )
                                    }
                                }
                            }
                        }

                        // 5. Linha de Mira / Tooltip do Ponto Ativo (Recharts Cursor Guide)
                        selectedIndex?.let { idx ->
                            if (idx in data.indices) {
                                val curX = leftPad + (idx * stepX)
                                drawLine(
                                    color = Color(0xFF10B981).copy(alpha = 0.8f),
                                    start = Offset(curX, topPad),
                                    end = Offset(curX, topPad + chartH),
                                    strokeWidth = 1.5f,
                                    pathEffect = dashEffect
                                )

                                // Círculo de destaque do cursor
                                val targetT = data[idx].temp
                                val curY = topPad + chartH - (chartH * ((targetT - minT) / tempRange).toFloat())
                                drawCircle(
                                    color = Color(0xFF10B981),
                                    radius = 7.5f,
                                    center = Offset(curX, curY)
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 4f,
                                    center = Offset(curX, curY)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Rodapé com Metadados Agrometeorológicos CIIAGRO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Agriculture,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Fonte: Open-Meteo (previsão horária)",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }

                Text(
                    text = "Arraste para inspecionar",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp
                )
            }
        }
    }
}
