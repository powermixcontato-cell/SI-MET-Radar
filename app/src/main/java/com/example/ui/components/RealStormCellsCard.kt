package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.StormCellTrajectory
import com.example.viewmodel.WeatherViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Texto exibido quando o radar real não tem nenhuma célula acima do limiar. */
const val NO_STORM_CELLS_TEXT = "Nenhuma célula detectada agora"

fun formatRadarTimeBrt(epochSec: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).apply { timeZone = TimeZone.getTimeZone("America/Sao_Paulo") }
        .format(Date(epochSec * 1000)) + " (BRT)"

@Composable
fun RealStormCellsCard(
    state: WeatherViewModel.StormCellsUi,
    onRetry: () -> Unit,
    onSelect: (StormCellTrajectory) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("card_real_storm_cells"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Células de tempestade (radar real)", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            when (state) {
                WeatherViewModel.StormCellsUi.Loading ->
                    Text("Lendo o radar…", color = Color(0xFF94A3B8), fontSize = 12.sp)
                is WeatherViewModel.StormCellsUi.Error -> {
                    Text(state.message, color = Color(0xFFFCA5A5), fontSize = 12.sp)
                    TextButton(onClick = onRetry) { Text("Tentar de novo") }
                }
                is WeatherViewModel.StormCellsUi.Ready -> {
                    val r = state.result
                    Text(
                        "Fonte: ${r.source} • radar de ${formatRadarTimeBrt(r.frameTimeSec)} • eco ≥ 40 dBZ",
                        color = Color(0xFF94A3B8), fontSize = 11.sp, modifier = Modifier.testTag("storm_cells_source")
                    )
                    Spacer(Modifier.height(6.dp))
                    if (r.cells.isEmpty()) {
                        Text(NO_STORM_CELLS_TEXT, color = Color(0xFF86EFAC), fontSize = 13.sp, modifier = Modifier.testTag("storm_cells_empty"))
                    } else {
                        r.cells.forEach { c ->
                            Text(
                                "• ${c.name}: pico ${c.dbzPeak} dBZ (~${c.rainRateMmH} mm/h)" +
                                    if (c.speedKmH > 0) ", indo para ${c.headingCompass} a ${c.speedKmH} km/h" else "",
                                color = Color(0xFFE2E8F0), fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(c) }.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
