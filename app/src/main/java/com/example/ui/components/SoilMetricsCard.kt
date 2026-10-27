package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.AgroSeries
import com.example.data.repository.SoilData
import java.util.Locale
import kotlin.math.roundToInt

private val SoilAmber = Color(0xFFD97706)
private val SoilBrown = Color(0xFFB45309)

@Composable
fun SoilMetricsCard(
    series: AgroSeries?,
    modifier: Modifier = Modifier
) {
    val soil = series?.currentSoil

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("soil_metrics_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SoilAmber.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(14.dp)) {
            // Cabeçalho
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(SoilAmber, CircleShape))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Umidade e Temperatura do Solo",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Medições por profundidade de perfil (Open-Meteo)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.Grass,
                    contentDescription = null,
                    tint = SoilAmber,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            if (soil == null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF141416),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Aguardando leitura de perfil de solo da Open-Meteo para este ponto.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                // Seção 1: Umidade Volumétrica do Solo
                Text(
                    "UMIDADE DO SOLO (CONTEÚDO VOLUMÉTRICO DE ÁGUA)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )
                Spacer(Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SoilMoistureRow("0 a 1 cm (Superfície)", soil.moist0to1cm)
                    SoilMoistureRow("1 a 3 cm (Camada de sementeira)", soil.moist1to3cm)
                    SoilMoistureRow("3 a 9 cm (Zona radicular inicial)", soil.moist3to9cm)
                    SoilMoistureRow("9 a 27 cm (Zona radicular principal)", soil.moist9to27cm)
                    SoilMoistureRow("27 a 81 cm (Perfil profundo / reserva)", soil.moist27to81cm)
                }

                Spacer(Modifier.height(14.dp))

                // Seção 2: Temperatura do Solo
                Text(
                    "TEMPERATURA DO SOLO (°C)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFBBF24)
                )
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SoilTempBox("0 cm (Superfície)", soil.temp0cm, Modifier.weight(1f))
                    SoilTempBox("6 cm (Sementeira)", soil.temp6cm, Modifier.weight(1f))
                    SoilTempBox("18 cm (Raiz)", soil.temp18cm, Modifier.weight(1f))
                    SoilTempBox("54 cm (Subsolo)", soil.temp54cm, Modifier.weight(1f))
                }

                Spacer(Modifier.height(10.dp))
            }

            // Nota de fonte visível
            Text(
                "Fonte: Open-Meteo (modelos de superfície do solo ECMWF / IFS Land Surface Model). " +
                "Parâmetros: soil_moisture_0_to_1cm a 27_to_81cm (m³/m³) e soil_temperature_0cm a 54cm (°C).",
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SoilMoistureRow(label: String, moistureVal: Double?) {
    val pct = moistureVal?.let { it * 100.0 }
    val (statusLabel, statusColor) = when {
        pct == null -> "—" to Color(0xFF94A3B8)
        pct < 15.0 -> "Seco (< 15%)" to Color(0xFFF97316)
        pct < 30.0 -> "Moderado (15-30%)" to Color(0xFFFBBF24)
        pct < 45.0 -> "Adequado (30-45%)" to Color(0xFF10B981)
        else -> "Saturado (> 45%)" to Color(0xFF38BDF8)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141416), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 11.sp, color = Color(0xFFCBD5E1))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pct?.let { "${String.format(Locale.US, "%.1f", it)}%  (${String.format(Locale.US, "%.3f", moistureVal)} m³/m³)" } ?: "—",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    statusLabel,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val progress = pct?.let { (it / 60.0).coerceIn(0.0, 1.0).toFloat() } ?: 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = statusColor,
            trackColor = Color(0xFF27272A),
        )
    }
}

@Composable
private fun SoilTempBox(label: String, temp: Double?, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF18181B),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF27272A)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 9.sp, color = Color(0xFF94A3B8), maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Thermostat, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                Text(
                    temp?.let { "${String.format(Locale.US, "%.1f", it)}°" } ?: "—",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC)
                )
            }
        }
    }
}
