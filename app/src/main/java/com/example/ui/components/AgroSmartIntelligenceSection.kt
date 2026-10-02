package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDamage
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
 * AgroSmartIntelligenceSection
 * Layout limpo, direto e profissional (Clean Agronomic Dashboard)
 * Sem balões ou cards inflados, com foco em métricas de alto impacto:
 * - Cana-de-Açúcar (Trafegabilidade, Maturação/ATR, Risco de Fogo)
 * - Citrus (Dormência Floral, Cancro/Pinta Preta, Psilídeo do Greening, Irrigação)
 * - Balanço Hídrico & ETo (Evapotranspiração diária e umidade do solo)
 * - Janela de Pulverização (Vento, temperatura, UR e deriva)
 * - Rede CIIAGRO / IAC (Agrometeorologia oficial)
 */
@Composable
fun AgroSmartIntelligenceSection(
    station: WeatherStationEntity,
    ciiagroRecord: CiiagroRecordEntity? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        "🎋 Cana",
        "🍊 Citrus",
        "💧 Balanço Hídrico",
        "🚜 Pulverização",
        "🌱 CIIAGRO / IAC"
    )

    // Cálculos agrometeorológicos
    val tempMean = station.currentTemp.coerceIn(10.0, 45.0)
    val rh = station.humidity.coerceIn(15, 100)
    val windKmH = station.windSpeed.coerceIn(0.0, 100.0)

    val radiationFactor = when {
        station.weatherCondition.contains("Ensolarado", ignoreCase = true) ||
                station.weatherCondition.contains("Limpo", ignoreCase = true) -> 1.3
        station.weatherCondition.contains("Nublado", ignoreCase = true) -> 0.8
        station.weatherCondition.contains("Chuv", ignoreCase = true) -> 0.5
        else -> 1.0
    }
    val et0 = max(1.5, (0.0023 * (tempMean + 17.8) * 4.2 * (100 - rh) / 50.0 * radiationFactor) + (windKmH * 0.04))
    val et0Formatted = String.format(java.util.Locale.US, "%.1f", et0)

    val estimatedSoilMoisture = min(98, max(20, (rh * 0.55 + station.rainVolumeMm * 4.2 - et0 * 1.5).toInt()))
    val soilStatus = when {
        estimatedSoilMoisture > 75 -> "Solo Saturado (Encharcado)"
        estimatedSoilMoisture >= 45 -> "Capacidade de Campo Ideal"
        estimatedSoilMoisture >= 30 -> "Déficit Leve"
        else -> "Déficit Hídrico Severo"
    }

    // Cana
    val caneTraffic = when {
        station.rainVolumeMm >= 12.0 || estimatedSoilMoisture > 75 ->
            Pair("INTERROMPIDA", "Solo saturado. Risco de atolamento e compactação.")
        estimatedSoilMoisture in 45..75 && station.rainVolumeMm in 1.0..11.9 ->
            Pair("ATENÇÃO", "Colheita viável em platôs altos. Evitar baixadas.")
        else ->
            Pair("LIBERADA", "Solo firme para colheita mecânica contínua.")
    }

    val caneFireRiskScore = when {
        rh < 25 && tempMean > 32.0 && windKmH > 18.0 -> 95
        rh < 35 && tempMean > 29.0 && windKmH > 14.0 -> 75
        rh < 50 && tempMean > 26.0 -> 45
        else -> 20
    }
    val caneFireStatus = when {
        caneFireRiskScore >= 80 -> Pair("CRÍTICO", Color(0xFFFF1744))
        caneFireRiskScore >= 50 -> Pair("MODERADO", Color(0xFFF59E0B))
        else -> Pair("BAIXO", Color(0xFF10B981))
    }

    val caneSucroseStatus = when {
        estimatedSoilMoisture in 28..48 && tempMean in 20.0..32.0 ->
            Triple("ACÚMULO ALTO", "Estresse hídrico moderado concentra sacarose (+ATR).", Color(0xFF10B981))
        estimatedSoilMoisture > 75 ->
            Triple("DILUIÇÃO", "Excesso de chuva estimula vegetação e dilui ATR.", Color(0xFF0284C7))
        else ->
            Triple("ESTÁVEL", "Teor de brix e sacarose em níveis normais.", Color(0xFFF59E0B))
    }

    // Citrus
    val citrusDormancyStatus = when {
        estimatedSoilMoisture in 25..45 ->
            Triple("DORMÊNCIA BENÉFICA", "Estresse induz gemas florais para florada uniforme.", Color(0xFF10B981))
        estimatedSoilMoisture < 25 ->
            Triple("SECA EXCESSIVA", "Risco de aborto de flores e queda de chumbinhos.", Color(0xFFFF1744))
        else ->
            Triple("BROTAÇÃO VEGETATIVA", "Umidade direcionada para crescimento vegetativo.", Color(0xFF38BDF8))
    }

    val citrusFungalRisk = when {
        rh >= 78 && tempMean >= 22.0 && station.rainVolumeMm > 0.0 ->
            Pair("ALERTA ALTO", Color(0xFFFF1744))
        rh >= 72 && tempMean >= 21.0 ->
            Pair("ATENÇÃO", Color(0xFFF59E0B))
        else ->
            Pair("BAIXO RISCO", Color(0xFF10B981))
    }

    val psyllidRisk = when {
        tempMean in 24.0..33.0 && windKmH < 12.0 && station.rainVolumeMm < 1.0 ->
            Pair("ALTO VOO", Color(0xFFFF1744))
        windKmH > 16.0 || station.rainVolumeMm >= 4.0 ->
            Pair("BAIXO", Color(0xFF10B981))
        else ->
            Pair("MODERADO", Color(0xFFF59E0B))
    }

    val citrusIrrigationMm = max(0.0, Math.round((et0 * 0.75 - (station.rainVolumeMm * 0.7)) * 10.0) / 10.0)

    // Pulverização
    val isWindGood = windKmH in 3.0..12.0
    val isTempGood = tempMean <= 30.0
    val isRhGood = rh >= 50
    val isRainFree = station.rainProbability < 40 && station.rainVolumeMm < 1.0
    val isSprayGood = isWindGood && isTempGood && isRhGood && isRainFree

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("agro_smart_intelligence_section")
    ) {
        // 1. KPI Ribbon Rápido (4 Métricas Críticas sem cards pesados)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CleanMetricChip(
                icon = Icons.Default.WaterDrop,
                label = "Chuva",
                value = "${station.rainVolumeMm} mm",
                tint = Color(0xFF0284C7),
                modifier = Modifier.weight(1f)
            )
            CleanMetricChip(
                icon = Icons.Default.Grass,
                label = "Solo",
                value = "$estimatedSoilMoisture%",
                tint = Color(0xFF10B981),
                modifier = Modifier.weight(1f)
            )
            CleanMetricChip(
                icon = Icons.Default.Opacity,
                label = "ETo",
                value = "$et0Formatted mm/d",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.weight(1f)
            )
            CleanMetricChip(
                icon = Icons.Default.Air,
                label = "Vento",
                value = "${station.windSpeed.toInt()} km/h",
                tint = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Segmented Tabs Limpas e Modernas
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            contentColor = Color(0xFF10B981),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Color(0xFF10B981),
                    height = 2.5.dp
                )
            },
            divider = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { idx, title ->
                val isSelected = selectedTab == idx
                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("tab_agro_$idx")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Conteúdo Dinâmico por Aba - Clean, Streamlined e Sem Balões Grandes
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "AgroTabTransition"
        ) { tabIndex ->
            when (tabIndex) {
                0 -> CleanCaneSugarView(
                    station = station,
                    caneTraffic = caneTraffic,
                    caneFireStatus = caneFireStatus,
                    caneFireRiskScore = caneFireRiskScore,
                    caneSucroseStatus = caneSucroseStatus,
                    soilMoisture = estimatedSoilMoisture
                )
                1 -> CleanCitrusView(
                    station = station,
                    citrusDormancyStatus = citrusDormancyStatus,
                    citrusFungalRisk = citrusFungalRisk,
                    psyllidRisk = psyllidRisk,
                    citrusIrrigationMm = citrusIrrigationMm,
                    soilMoisture = estimatedSoilMoisture
                )
                2 -> CleanWaterBalanceView(
                    station = station,
                    et0Formatted = et0Formatted,
                    estimatedSoilMoisture = estimatedSoilMoisture,
                    soilStatus = soilStatus
                )
                3 -> CleanSprayWindowView(
                    station = station,
                    isSprayGood = isSprayGood,
                    isWindGood = isWindGood,
                    isTempGood = isTempGood,
                    isRhGood = isRhGood,
                    isRainFree = isRainFree
                )
                4 -> CleanCiiagroView(
                    station = station,
                    ciiagro = ciiagroRecord
                )
            }
        }
    }
}

// ==========================================
// COMPONENTES CLEAN E MINIMALISTAS
// ==========================================

@Composable
private fun CleanMetricChip(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun CleanRowItem(
    icon: ImageVector,
    title: String,
    statusText: String,
    statusColor: Color,
    detailText: String,
    extraMetric: String? = null
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.16f),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detailText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )

            if (extraMetric != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = extraMetric,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }
        }
    }
}

// 1. CANA-DE-AÇÚCAR (CLEAN)
@Composable
private fun CleanCaneSugarView(
    station: WeatherStationEntity,
    caneTraffic: Pair<String, String>,
    caneFireStatus: Pair<String, Color>,
    caneFireRiskScore: Int,
    caneSucroseStatus: Triple<String, String, Color>,
    soilMoisture: Int
) {
    Column {
        CleanRowItem(
            icon = Icons.Default.WaterDamage,
            title = "Trafegabilidade de Colhedoras",
            statusText = caneTraffic.first,
            statusColor = if (caneTraffic.first.startsWith("LIBERADA")) Color(0xFF10B981) else Color(0xFFFF1744),
            detailText = caneTraffic.second,
            extraMetric = "Umidade Solo: $soilMoisture% • Chuva: ${station.rainVolumeMm} mm"
        )

        CleanRowItem(
            icon = Icons.Default.WbSunny,
            title = "Síntese de Sacarose (ATR)",
            statusText = caneSucroseStatus.first,
            statusColor = caneSucroseStatus.third,
            detailText = caneSucroseStatus.second
        )

        // Risco de Fogo em Palhada com barra elegante
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = caneFireStatus.second, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Risco de Fogo em Palhada", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = "${caneFireStatus.first} ($caneFireRiskScore/100)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = caneFireStatus.second
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { caneFireRiskScore / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = caneFireStatus.second,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

// 2. CITRUS (CLEAN)
@Composable
private fun CleanCitrusView(
    station: WeatherStationEntity,
    citrusDormancyStatus: Triple<String, String, Color>,
    citrusFungalRisk: Pair<String, Color>,
    psyllidRisk: Pair<String, Color>,
    citrusIrrigationMm: Double,
    soilMoisture: Int
) {
    Column {
        CleanRowItem(
            icon = Icons.Default.Park,
            title = "Florada & Estresse Hídrico",
            statusText = citrusDormancyStatus.first,
            statusColor = citrusDormancyStatus.third,
            detailText = citrusDormancyStatus.second,
            extraMetric = if (citrusIrrigationMm > 0) "Demanda de Irrigação: $citrusIrrigationMm mm/dia (Gotejo)" else "Sem necessidade de irrigação"
        )

        CleanRowItem(
            icon = Icons.Default.Warning,
            title = "Alerta Cancro / Pinta Preta",
            statusText = citrusFungalRisk.first,
            statusColor = citrusFungalRisk.second,
            detailText = "Molhamento foliar favorece disseminação bacteriana e fúngica."
        )

        CleanRowItem(
            icon = Icons.Default.BugReport,
            title = "Vetor Greening (Psilídeo HLB)",
            statusText = psyllidRisk.first,
            statusColor = psyllidRisk.second,
            detailText = "Monitorar bordaduras do pomar e realizar aplicações rotacionais."
        )
    }
}

// 3. BALANÇO HÍDRICO (CLEAN)
@Composable
private fun CleanWaterBalanceView(
    station: WeatherStationEntity,
    et0Formatted: String,
    estimatedSoilMoisture: Int,
    soilStatus: String
) {
    val netWater = station.rainVolumeMm - (et0Formatted.toDoubleOrNull() ?: 4.2)
    val netSign = if (netWater >= 0) "+${String.format(java.util.Locale.US, "%.1f", netWater)}" else String.format(java.util.Locale.US, "%.1f", netWater)
    val netColor = if (netWater >= 0) Color(0xFF10B981) else Color(0xFFF59E0B)

    Column {
        CleanRowItem(
            icon = Icons.Default.Opacity,
            title = "Saldo Hídrico 24h",
            statusText = "$netSign mm",
            statusColor = netColor,
            detailText = "Entrada (Chuva: ${station.rainVolumeMm} mm) vs Saída (ETo: $et0Formatted mm/dia)",
            extraMetric = "Status do Solo: $soilStatus ($estimatedSoilMoisture%)"
        )

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Capacidade de Água no Solo (CAD)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(soilStatus, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                }
                Text("$estimatedSoilMoisture%", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF10B981))
            }
        }
    }
}

// 4. PULVERIZAÇÃO (CLEAN)
@Composable
private fun CleanSprayWindowView(
    station: WeatherStationEntity,
    isSprayGood: Boolean,
    isWindGood: Boolean,
    isTempGood: Boolean,
    isRhGood: Boolean,
    isRainFree: Boolean
) {
    val bannerColor = if (isSprayGood) Color(0xFF10B981) else Color(0xFFEF4444)

    Column {
        CleanRowItem(
            icon = if (isSprayGood) Icons.Default.CheckCircle else Icons.Default.Warning,
            title = "Janela Operacional de Pulverização",
            statusText = if (isSprayGood) "FAVORÁVEL" else "DESFAVORÁVEL",
            statusColor = bannerColor,
            detailText = if (isSprayGood) "Gotas protegidas contra deriva de vento e evaporação rápida."
                         else "Fora da faixa agronômica ideal para aplicação."
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CleanSprayTile("Vento", "${station.windSpeed} km/h", isWindGood, Modifier.weight(1f))
            CleanSprayTile("Temp", "${station.currentTemp}°C", isTempGood, Modifier.weight(1f))
            CleanSprayTile("UR", "${station.humidity}%", isRhGood, Modifier.weight(1f))
            CleanSprayTile("Sem Chuva", "${station.rainProbability}%", isRainFree, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CleanSprayTile(
    label: String,
    value: String,
    isOk: Boolean,
    modifier: Modifier = Modifier
) {
    val tint = if (isOk) Color(0xFF10B981) else Color(0xFFFF1744)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, tint.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

// 5. REDE CIIAGRO / IAC (CLEAN)
@Composable
private fun CleanCiiagroView(
    station: WeatherStationEntity,
    ciiagro: CiiagroRecordEntity?
) {
    Column {
        CleanRowItem(
            icon = Icons.Default.Agriculture,
            title = "Rede Agrometeorológica CIIAGRO / IAC",
            statusText = "ATIVO SP",
            statusColor = Color(0xFF10B981),
            detailText = ciiagro?.cropManagementRecommendation
                ?: "Recomendação agrometeorológica oficial do IAC para ${station.name}.",
            extraMetric = "Radiação Solar: ${ciiagro?.solarRadiationMj ?: 19.5} MJ/m² • ETo: ${ciiagro?.et0MmDay ?: 4.2} mm"
        )
    }
}
