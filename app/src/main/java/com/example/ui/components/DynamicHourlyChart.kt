package com.example.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.example.data.local.entity.HourlyForecastEntity
import com.example.util.safeDrawText

@Composable
fun DynamicHourlyChart(
    hourlyList: List<HourlyForecastEntity>,
    modifier: Modifier = Modifier
) {
    if (hourlyList.isEmpty()) return

    var selectedIndex by remember { mutableStateOf(0) }
    val textMeasurer = rememberTextMeasurer()
    val minTemp = hourlyList.minOfOrNull { it.temp } ?: 15.0
    val maxTemp = (hourlyList.maxOfOrNull { it.temp } ?: 30.0).coerceAtLeast(minTemp + 4.0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("dynamic_hourly_chart_container")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Evolução Horária (24 Horas)",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            val sel = hourlyList.getOrNull(selectedIndex) ?: hourlyList.first()
            Text(
                "${sel.hourText}: ${sel.temp}°C | ${sel.rainProbability}% Chuva",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary
            val rainBarColor = Color(0xFF38BDF8).copy(alpha = 0.6f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .pointerInput(hourlyList) {
                        detectTapGestures { offset ->
                            val itemWidth = size.width / hourlyList.size
                            val idx = (offset.x / itemWidth).toInt().coerceIn(0, hourlyList.size - 1)
                            selectedIndex = idx
                        }
                    }
                    .testTag("canvas_hourly_curve")
            ) {
                val w = size.width
                val h = size.height
                val chartHeight = h - 30f // reserve 30px for labels
                val count = hourlyList.size
                val stepX = w / count

                // 1. Draw Rain Probability Bars in the background
                hourlyList.forEachIndexed { i, item ->
                    val barHeight = (item.rainProbability / 100f) * (chartHeight * 0.5f)
                    val barLeft = i * stepX + stepX * 0.2f
                    val barWidth = stepX * 0.6f
                    drawRect(
                        color = rainBarColor,
                        topLeft = Offset(barLeft, chartHeight - barHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                    )
                }

                // 2. Draw Temperature Curve
                val points = hourlyList.mapIndexed { i, item ->
                    val x = i * stepX + stepX / 2f
                    val normY = (item.temp - minTemp) / (maxTemp - minTemp)
                    val y = chartHeight - (normY * (chartHeight - 40f) + 20f).toFloat()
                    Offset(x, y)
                }

                val curvePath = Path()
                points.forEachIndexed { i, pt ->
                    if (i == 0) curvePath.moveTo(pt.x, pt.y)
                    else {
                        val prev = points[i - 1]
                        val midX = (prev.x + pt.x) / 2f
                        curvePath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                    }
                }

                // Fill gradient under temperature line
                val fillPath = Path().apply {
                    addPath(curvePath)
                    lineTo(points.last().x, chartHeight)
                    lineTo(points.first().x, chartHeight)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.25f), Color.Transparent),
                        startY = 0f,
                        endY = chartHeight
                    )
                )

                // Draw curve stroke
                drawPath(
                    path = curvePath,
                    color = primaryColor,
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )

                // 3. Draw Points and Labels
                points.forEachIndexed { i, pt ->
                    val item = hourlyList[i]
                    val isSelected = i == selectedIndex

                    if (isSelected) {
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.3f),
                            radius = 10f,
                            center = pt
                        )
                        drawLine(
                            color = primaryColor.copy(alpha = 0.5f),
                            start = Offset(pt.x, 0f),
                            end = Offset(pt.x, chartHeight),
                            strokeWidth = 1.5f
                        )
                    }

                    drawCircle(
                        color = if (isSelected) Color.White else primaryColor,
                        radius = if (isSelected) 5f else 3.5f,
                        center = pt
                    )

                    // Temp text above point
                    safeDrawText(
                        textMeasurer = textMeasurer,
                        text = "${item.temp.toInt()}°",
                        topLeft = Offset(pt.x - 12f, pt.y - 20f),
                        style = TextStyle(
                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )

                    // Hour label below
                    safeDrawText(
                        textMeasurer = textMeasurer,
                        text = item.hourText,
                        topLeft = Offset(pt.x - 14f, chartHeight + 6f),
                        style = TextStyle(
                            color = if (isSelected) primaryColor else Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }
        }
    }
}
