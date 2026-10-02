package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.WeatherStationEntity

@Composable
fun WeatherMetricsGrid(
    station: WeatherStationEntity,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weather_metrics_grid"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Umidade & Vento
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                icon = Icons.Default.WaterDrop,
                iconTint = Color(0xFF38BDF8),
                title = "Umidade do Ar",
                value = "${station.humidity}%",
                subtitle = if (station.humidity > 80) "Alta saturação" else "Faixa confortável",
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                icon = Icons.Default.Air,
                iconTint = Color(0xFF00E5FF),
                title = "Vento & Rajadas",
                value = "${station.windSpeed.toInt()} km/h",
                subtitle = station.windDirection,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Pressão Atmosférica & Índice UV
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                icon = Icons.Default.Compress,
                iconTint = Color(0xFFB388FF),
                title = "Pressão Atmosférica",
                value = "${station.pressure} hPa",
                subtitle = if (station.pressure < 1013) "Baixa pressão (Instável)" else "Estável",
                modifier = Modifier.weight(1f)
            )

            val uvDesc = when {
                station.uvIndex >= 11 -> "Extremo (Proteção máx)"
                station.uvIndex >= 8 -> "Muito Alto"
                station.uvIndex >= 6 -> "Alto"
                station.uvIndex >= 3 -> "Moderado"
                else -> "Baixo"
            }
            MetricCard(
                icon = Icons.Default.WbSunny,
                iconTint = Color(0xFFFFD600),
                title = "Índice Ultravioleta",
                value = "UV ${station.uvIndex}",
                subtitle = uvDesc,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 3: Nascer / Pôr do Sol & Qualidade do Ar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                icon = Icons.Default.WbTwilight,
                iconTint = Color(0xFFFFAB00),
                title = "Horários Solares",
                value = "${station.sunrise} / ${station.sunset}",
                subtitle = "Nascer / Pôr do Sol",
                modifier = Modifier.weight(1f)
            )

            val aqiDesc = when {
                station.aqi <= 30 -> "Boa (Ar limpo)"
                station.aqi <= 50 -> "Moderada"
                else -> "Inadequada"
            }
            MetricCard(
                icon = Icons.Default.Thermostat,
                iconTint = Color(0xFF00E676),
                title = "Qualidade do Ar (AQI)",
                value = "${station.aqi} AQI",
                subtitle = aqiDesc,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}
