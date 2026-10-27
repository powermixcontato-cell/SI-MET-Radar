package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.BrState

/**
 * Mapa de chuva da tela inicial (v5.1): último quadro do radar (RainViewer, composição de radares)
 * sobre mapa escuro Esri, altura fixa, sem gestos no card (toque = ampliar). "Ver trajetória da chuva" abre a animação.
 */
@Composable
fun RainMapCard(state: BrState, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var trajectory by rememberSaveable { mutableStateOf(false) }

    SimetCard(modifier = modifier.testTag("card_rain_map")) {
        SimetCardHeader(
            icon = Icons.Default.Radar,
            title = "Mapa de chuva • ${state.uf}",
            subtitle = "Radar (RainViewer) • último quadro • toque para ampliar"
        ) {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.OpenInFull, contentDescription = "Ampliar mapa de chuva", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        SimetLeafletMap(
            mode = SimetMapMode.RAIN, lat = state.centerLat, lon = state.centerLon, zoom = state.zoom, state = state,
            interactive = false, modifier = Modifier.fillMaxWidth().height(260.dp), onTap = { expanded = true }
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { trajectory = true },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SimetCardDefaults.Accent),
                modifier = Modifier.weight(1f).testTag("btn_rain_trajectory")
            ) {
                Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ver trajetória da chuva", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }

    if (expanded) {
        ExpandedLeafletMapDialog(
            title = "Mapa de chuva • ${state.displayName}",
            subtitle = "RainViewer (radar) • mapa Esri • horários em BRT",
            mode = SimetMapMode.RAIN, state = state, lat = state.centerLat, lon = state.centerLon, zoom = state.zoom,
            onDismiss = { expanded = false }
        )
    }
    if (trajectory) {
        RainTrajectoryDialog(state = state, lat = state.centerLat, lon = state.centerLon, onDismiss = { trajectory = false })
    }
}
