package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.AgroSeries
import com.example.domain.CropStageInfo
import com.example.domain.CropType
import com.example.domain.FaoCropEvapotranspiration
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

private val EmeraldAccent = Color(0xFF10B981)
private val DarkCardBorder = Color(0xFF27272A)

@Composable
fun FaoEtcCard(
    series: AgroSeries?,
    modifier: Modifier = Modifier
) {
    var selectedCrop by rememberSaveable { mutableStateOf(CropType.CANA) }
    var selectedStageId by rememberSaveable { mutableStateOf("mid") }

    val stages = FaoCropEvapotranspiration.stagesByCrop[selectedCrop].orEmpty()
    val activeStage = stages.find { it.id == selectedStageId } ?: stages.firstOrNull() ?: CropStageInfo("mid", "Fase Média", "", 1.0)

    val todayEt0 = series?.let { s ->
        if (s.todayIndex in s.days.indices) s.days[s.todayIndex].et0Mm else null
    }
    val todayEtc = FaoCropEvapotranspiration.calculateEtc(todayEt0, activeStage.kc)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("fao_etc_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(14.dp)) {
            // Cabeçalho
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(EmeraldAccent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Evapotranspiração da Cultura (ETc)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Fórmula FAO-56 Tabela 12: ETc = ET0 × Kc",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = EmeraldAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Seletor de Cultura
            Text(
                "SELECIONE A CULTURA",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CropType.entries.forEach { crop ->
                    val isSelected = crop == selectedCrop
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) EmeraldAccent.copy(alpha = 0.2f) else Color(0xFF18181B),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) EmeraldAccent else DarkCardBorder
                        ),
                        modifier = Modifier
                            .clickable {
                                selectedCrop = crop
                                // Mantém estágio se existir, ou pega o primeiro
                                val newStages = FaoCropEvapotranspiration.stagesByCrop[crop].orEmpty()
                                if (newStages.none { it.id == selectedStageId }) {
                                    selectedStageId = newStages.firstOrNull()?.id ?: "mid"
                                }
                            }
                            .testTag("crop_chip_${crop.name.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(crop.emoji, fontSize = 13.sp)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                crop.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFFE2E8F0) else Color(0xFFA1A1AA)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Seletor de Fase fenológica
            Text(
                "FASE DE DESENVOLVIMENTO (TABELA 12 FAO-56)",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                stages.forEach { stage ->
                    val isSelected = stage.id == activeStage.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedStageId = stage.id },
                        label = {
                            Text(
                                "${stage.name} (Kc ${String.format(Locale.US, "%.2f", stage.kc)})",
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldAccent.copy(alpha = 0.25f),
                            selectedLabelColor = Color(0xFFF1F5F9),
                            containerColor = Color(0xFF18181B),
                            labelColor = Color(0xFFA1A1AA)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = DarkCardBorder,
                            selectedBorderColor = EmeraldAccent
                        ),
                        modifier = Modifier.testTag("stage_chip_${stage.id}")
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Destaque do cálculo hoje
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "DEMANDA HÍDRICA HOJE (ETc)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldAccent
                            )
                            Text(
                                "${selectedCrop.label} • ${activeStage.name}",
                                fontSize = 11.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                        Text(
                            todayEtc?.let { "${String.format(Locale.US, "%.1f", it)} mm/dia" } ?: "— mm",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFF8FAFC)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Memória de cálculo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricSmallBox(
                            label = "ET0 Penman-Monteith",
                            value = todayEt0?.let { "${String.format(Locale.US, "%.1f", it)} mm" } ?: "—",
                            modifier = Modifier.weight(1f)
                        )
                        MetricSmallBox(
                            label = "Kc Tabela 12",
                            value = String.format(Locale.US, "%.2f", activeStage.kc),
                            modifier = Modifier.weight(1f)
                        )
                        MetricSmallBox(
                            label = "Consumo diário",
                            value = todayEtc?.let { "${(it * 10).roundToInt()} m³/ha" } ?: "—",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Projeção dos próximos 7 dias
            if (series != null && series.todayIndex in series.days.indices) {
                Text(
                    "PREVISÃO DE ETc (PRÓXIMOS 7 DIAS) VS CHUVA",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8)
                )
                Spacer(Modifier.height(6.dp))

                val next7 = series.days.subList(
                    series.todayIndex,
                    minOf(series.days.size, series.todayIndex + 7)
                )
                val dayFormat = SimpleDateFormat("EEE dd/MM", Locale("pt", "BR")).apply {
                    timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
                }
                val inFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    next7.forEach { day ->
                        val dateLabel = try {
                            dayFormat.format(inFormat.parse(day.date)!!)
                        } catch (_: Exception) { day.date }
                        val etc = FaoCropEvapotranspiration.calculateEtc(day.et0Mm, activeStage.kc)
                        val rain = day.rainMm ?: 0.0
                        val balance = if (etc != null) rain - etc else null

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF141416), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(dateLabel, fontSize = 11.sp, color = Color(0xFFCBD5E1), modifier = Modifier.width(75.dp))
                            Text(
                                "ETc: ${etc?.let { String.format(Locale.US, "%.1f", it) } ?: "—"} mm",
                                fontSize = 11.sp,
                                color = EmeraldAccent
                            )
                            Text(
                                "Chuva: ${String.format(Locale.US, "%.1f", rain)} mm",
                                fontSize = 11.sp,
                                color = Color(0xFF60A5FA)
                            )
                            Text(
                                balance?.let {
                                    if (it >= 0) "+${String.format(Locale.US, "%.1f", it)} mm"
                                    else "${String.format(Locale.US, "%.1f", it)} mm"
                                } ?: "—",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if ((balance ?: 0.0) >= 0) Color(0xFF34D399) else Color(0xFFF87171)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
            }

            // Nota de fonte visível
            Text(
                "Fonte de ET0: Open-Meteo (método FAO Penman-Monteith) • Coeficientes Kc: Tabela 12 da FAO-56 (Allen et al., 1998). " +
                "Utilizado no manejo de irrigação para repor a água consumida pela cultura.",
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricSmallBox(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
            .padding(vertical = 4.dp, horizontal = 6.dp)
    ) {
        Column {
            Text(label, fontSize = 9.sp, color = Color(0xFF94A3B8), maxLines = 1)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF1F5F9))
        }
    }
}
