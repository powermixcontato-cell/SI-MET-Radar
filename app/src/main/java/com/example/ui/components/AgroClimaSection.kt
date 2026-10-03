package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CiiagroRecordEntity
import com.example.data.local.entity.WeatherStationEntity
import kotlin.math.max
import kotlin.math.min

/**
 * AgroClima SP - Agricultural Weather Parameters & Crop Intelligence
 * Focused heavily on São Paulo agro-powerhouses:
 * - CANA-DE-AÇÚCAR (Colheita mecanizada, tráfego de transbordos, ATR/sacarose, risco de fogo em palhada)
 * - CITRUS / CITRICULTURA (Dispersão do psilídeo do Greening/HLB, demanda de irrigação, cancro cítrico)
 * - REDE CIIAGRO / IAC (Previsões e dados agrometeorológicos ao vivo)
 */
@Composable
fun AgroClimaSection(
    station: WeatherStationEntity,
    ciiagroRecord: CiiagroRecordEntity? = null,
    modifier: Modifier = Modifier
) {
    var selectedCulture by remember { mutableStateOf("Cana-de-Açúcar") }

    val cultureOptions = listOf(
        "Cana-de-Açúcar",
        "Citrus (Laranja/Limão)",
        "🌱 Dados Agro (Open-Meteo)",
        "Café Paulista",
        "Soja / Grãos"
    )

    // Agrometeorological calculations based on station weather
    val tempMean = station.currentTemp.coerceIn(10.0, 45.0)
    val rh = station.humidity.coerceIn(15, 100)
    val windKmH = station.windSpeed.coerceIn(0.0, 100.0)
    val radiationFactor = when {
        station.weatherCondition.contains("Ensolarado", ignoreCase = true) || station.weatherCondition.contains("Limpo", ignoreCase = true) -> 1.3
        station.weatherCondition.contains("Nublado", ignoreCase = true) -> 0.8
        station.weatherCondition.contains("Chuv", ignoreCase = true) -> 0.5
        else -> 1.0
    }
    // 1. Evapotranspiration reference (ET0 - Hargreaves / Penman simplified approx mm/day)
    val et0 = max(1.5, (0.0023 * (tempMean + 17.8) * 4.2 * (100 - rh) / 50.0 * radiationFactor) + (windKmH * 0.04))
    val et0Formatted = String.format(java.util.Locale("pt", "BR"), "%.1f", et0)

    // 2. Soil Moisture & Water Balance estimate (0-100% capacity)
    val estimatedSoilMoisture = min(98, max(20, (rh * 0.55 + station.rainVolumeMm * 4.2 - et0 * 1.5).toInt()))
    val soilStatus = when {
        estimatedSoilMoisture > 75 -> "Saturado / Solo Encharcado"
        estimatedSoilMoisture >= 45 -> "Capacidade de Campo Ideal"
        estimatedSoilMoisture >= 30 -> "Déficit Leve (Monitorar)"
        else -> "Déficit Hídrico Crítico"
    }
    val soilColor = when {
        estimatedSoilMoisture > 75 -> Color(0xFF0284C7)
        estimatedSoilMoisture >= 45 -> Color(0xFF10B981)
        estimatedSoilMoisture >= 30 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    // 3. Spraying window condition (Janela de Pulverização Agrícola)
    val isWindGood = windKmH in 3.0..12.0
    val isTempGood = tempMean <= 30.0
    val isRhGood = rh >= 50
    val isRainFree = station.rainProbability < 40 && station.rainVolumeMm < 1.0

    val sprayWindowFavorable = isWindGood && isTempGood && isRhGood && isRainFree
    val sprayStatus = when {
        sprayWindowFavorable -> "FAVORÁVEL PARA PULVERIZAÇÃO"
        !isRainFree -> "DESFAVORÁVEL: Risco de Chuva"
        windKmH > 15.0 -> "DESFAVORÁVEL: Vento Forte (>15 km/h) Risco de Deriva"
        windKmH < 3.0 -> "ATENÇÃO: Ar Calmo / Inversão Térmica"
        tempMean > 30.0 -> "DESFAVORÁVEL: Alta Evaporação de Gotas (>30°C)"
        rh < 50 -> "ATENÇÃO: Baixa Umidade (<50%)"
        else -> "CONDIÇÃO MODERADA"
    }
    val sprayColor = when {
        sprayWindowFavorable -> Color(0xFF10B981)
        sprayStatus.startsWith("ATENÇÃO") || sprayStatus.contains("MODERADA") -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    // Specialized Sugarcane Calculations
    val sugarcaneTrafficability = when {
        station.rainVolumeMm >= 12.0 || estimatedSoilMoisture > 75 -> "Interrompida: Risco de Atolamento / Danos à Soqueira"
        estimatedSoilMoisture in 45..75 && station.rainVolumeMm in 1.0..11.9 -> "Atenção: Monitorar Umidade em Baixadas"
        else -> "Excelente: Solo Firme para Colhedoras e Transbordos"
    }
    val sugarcaneTrafficColor = when {
        station.rainVolumeMm >= 12.0 || estimatedSoilMoisture > 75 -> Color(0xFFEF4444)
        estimatedSoilMoisture in 45..75 && station.rainVolumeMm in 1.0..11.9 -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val sugarcaneFireRisk = when {
        rh < 30 && tempMean > 31.0 && windKmH > 16.0 -> "ALERTA CRÍTICO: Risco Extremo de Incêndio em Palhada"
        rh < 45 && tempMean > 28.0 -> "Risco Moderado de Fogo na Palha Seca"
        else -> "Risco Baixo de Incêndio"
    }
    val sugarcaneFireColor = when {
        rh < 30 && tempMean > 31.0 && windKmH > 16.0 -> Color(0xFFFF1744)
        rh < 45 && tempMean > 28.0 -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    // Specialized Citrus Calculations
    // Psyllid (Diaphorina citri - Greening / HLB vector) flight condition: 24-32°C, wind < 12 km/h
    val psyllidFlightRisk = when {
        tempMean in 24.0..33.0 && windKmH < 12.0 && station.rainVolumeMm < 1.0 -> "ALTO: Clima Ideal para Dispersão e Voo do Psilídeo"
        windKmH > 18.0 || station.rainVolumeMm >= 5.0 -> "BAIXO: Vento ou Chuva Inibem Voo do Vetor"
        else -> "MODERADO: Monitorar Armadilhas e Bordaduras"
    }
    val psyllidColor = when {
        psyllidFlightRisk.startsWith("ALTO") -> Color(0xFFFF1744)
        psyllidFlightRisk.startsWith("MODERADO") -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    // Citrus Irrigation Net Demand (Lâmina Líquida) = ET0 * Kc (Kc citros approx 0.75)
    val citrusWaterDemandMm = max(0.0, Math.round((et0 * 0.75 - (station.rainVolumeMm * 0.7)) * 10.0) / 10.0)

    // Citrus Fungal Risk (Cancro Cítrico e Mancha Preta): high humidity (>78%) + warm (>23°C)
    val citrusFungalRisk = when {
        rh >= 78 && tempMean >= 23.0 && station.rainVolumeMm > 0.0 -> "ALERTA: Condições Favoráveis para Cancro e Mancha Preta"
        rh >= 75 && tempMean >= 22.0 -> "Atenção ao Período de Molhamento Foliar"
        else -> "Baixo Risco de Doenças Fúngicas"
    }
    val citrusFungalColor = when {
        citrusFungalRisk.startsWith("ALERTA") -> Color(0xFFFF1744)
        citrusFungalRisk.startsWith("Atenção") -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("agroclima_section_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Agriculture,
                            contentDescription = "AgroClima SP",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AgroClima São Paulo",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Inteligência Meteorológica para Cana e Citricultura",
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Open-Meteo",
                        color = Color(0xFF10B981),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selector of Agro Culturas de SP
            Text(
                text = "Selecione o Foco da Cultura Agrícola:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(cultureOptions) { cult ->
                    val isSelected = selectedCulture == cult
                    val isFocusCulture = cult.contains("Cana") || cult.contains("Citrus")
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCulture = cult },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(cult, fontSize = 11.sp, fontWeight = if (isFocusCulture) FontWeight.Bold else FontWeight.Normal)
                            }
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (cult.contains("Cana")) Color(0xFF059669) else if (cult.contains("Citrus")) Color(0xFFEA580C) else MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SPECIALIZED SECTION: CANA-DE-AÇÚCAR
            AnimatedVisibility(visible = selectedCulture == "Cana-de-Açúcar") {
                Column {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF059669).copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PrecisionManufacturing,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Painel Operacional • Cana-de-Açúcar",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = sugarcaneTrafficColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (sugarcaneTrafficColor == Color(0xFF10B981)) "FROTA LIBERADA" else "FROTA EM ALERTA",
                                        color = sugarcaneTrafficColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 1. Trafegabilidade do Solo e Colhedoras
                            Text(
                                text = "Trafegabilidade & Colheita Mecanizada (Transbordo):",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = sugarcaneTrafficability,
                                color = sugarcaneTrafficColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // 2. Risco de Incêndio / Fogo em Palhada de Cana
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = sugarcaneFireColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Risco de Queima em Palhada Seca:",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = sugarcaneFireRisk,
                                    color = sugarcaneFireColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 3. Maturação e Síntese de Açúcar (ATR / Pol)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.WbSunny,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Síntese de Sacarose (ATR / Pol):",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                                val atrStatus = if (tempMean > 24.0 && station.rainVolumeMm < 5.0) "Alta Acumulação (+1.8 kg ATR/t)" else "Desenvolvimento Vegetativo"
                                Text(
                                    text = atrStatus,
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Advisory note
                            Text(
                                text = "💡 Dica Usineira: Com umidade de solo em $estimatedSoilMoisture%, preserve a soqueira evitando tráfego de carretas pesadas fora das linhas de tiro.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // SPECIALIZED SECTION: CITRUS / CITRICULTURA
            AnimatedVisibility(visible = selectedCulture.contains("Citrus")) {
                Column {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFEA580C).copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEA580C).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.BugReport,
                                        contentDescription = null,
                                        tint = Color(0xFFEA580C),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Painel Fitossanitário • Citrus (Laranja/Limão)",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = psyllidColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "VETOR HLB",
                                        color = psyllidColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 1. Alerta Greening (HLB) - Psilídeo Diaphorina citri
                            Text(
                                text = "Dispersão do Psilídeo (Vetor Greening / HLB):",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = psyllidFlightRisk,
                                color = psyllidColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // 2. Demanda de Irrigação (Pomares Irrigados)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.WaterDrop,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Lâmina de Irrigação Recomendada:",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = if (citrusWaterDemandMm > 0.0) "$citrusWaterDemandMm mm/dia (Gotejo)" else "0.0 mm (Chuva Suficiente)",
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 3. Cancro Cítrico e Mancha Preta
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = citrusFungalColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Cancro Cítrico & Mancha Preta:",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = citrusFungalRisk,
                                    color = citrusFungalColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Advisory note
                            Text(
                                text = "🍊 Manejo de Pomar: Em períodos com risco de psilídeo alto, priorize o controle conjunto nas bordaduras do pomar com produtos de rápida ação de choque.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // SPECIALIZED SECTION: REDE CIIAGRO / IAC
            AnimatedVisibility(visible = selectedCulture.contains("Open-Meteo")) {
                Column {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF047857).copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Agriculture, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Dados agrometeorológicos – Open-Meteo (${station.name})",
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ET0 FAO (hoje):", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                Text(
                                    text = if (ciiagroRecord != null) "${ciiagroRecord.et0MmDay} mm/dia" else "—",
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Radiação Solar:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                Text(
                                    text = if (ciiagroRecord != null) "${ciiagroRecord.solarRadiationMj} MJ/m²" else "—",
                                    color = Color(0xFFF59E0B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Balanço Hídrico do Solo:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                Text(
                                    text = ciiagroRecord?.soilWaterDeficitRisk ?: "—",
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = ciiagroRecord?.cropManagementRecommendation
                                    ?: "Dados indisponíveis (aguardando Open-Meteo).",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Grid of 4 Fundamental Agricultural Parameters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ET0 Card
                AgroMetricBox(
                    icon = Icons.Default.Opacity,
                    iconTint = Color(0xFF38BDF8),
                    title = "Evapotranspiração (ET0)",
                    value = "$et0Formatted mm/dia",
                    desc = if (et0 > 4.5) "Demanda hídrica alta" else "Demanda hídrica moderada",
                    modifier = Modifier.weight(1f)
                )

                // Soil Moisture Card
                AgroMetricBox(
                    icon = Icons.Default.Park,
                    iconTint = soilColor,
                    title = "Umidade Estimada Solo",
                    value = "$estimatedSoilMoisture%",
                    desc = soilStatus,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Spray Window Card
                AgroMetricBox(
                    icon = Icons.Default.Speed,
                    iconTint = sprayColor,
                    title = "Pulverização Agrícola",
                    value = if (sprayWindowFavorable) "Favorável" else "Atenção",
                    desc = sprayStatus,
                    modifier = Modifier.weight(1f)
                )

                // Thermal Stress / Frost Card
                AgroMetricBox(
                    icon = Icons.Default.Thermostat,
                    iconTint = Color(0xFFF59E0B),
                    title = "Temp. & Graus-Dia",
                    value = "${station.currentTemp.toInt()}°C",
                    desc = "Vento ${station.windSpeed} km/h",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Spray window condition details badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = sprayColor.copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(1.dp, sprayColor.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (sprayWindowFavorable) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = sprayColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = sprayStatus,
                            color = sprayColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Vento: ${station.windSpeed} km/h • Umidade: ${station.humidity}% • Temp: ${station.currentTemp}°C",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgroMetricBox(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    desc: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 2,
                lineHeight = 13.sp
            )
        }
    }
}
