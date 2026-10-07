package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.BrState

/** Diálogo em tela cheia para mapas interativos (o card da tela inicial fica estático). */
@Composable
fun SimetFullscreenDialog(
    title: String,
    subtitle: String?,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0A0F1D)).statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (!subtitle.isNullOrBlank()) Text(subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White) }
            }
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

/**
 * "Ver trajetória da chuva" (estilo animação IPMet): quadros passados do radar (RainViewer, 10 min),
 * nuvens no infravermelho (GOES-East / NASA GIBS), rastro dos quadros anteriores e +3 h de previsão do modelo
 * (Open-Meteo, rotulada como previsão — não é radar). Play/pausa, controle deslizante e hora em BRT.
 */
@Composable
fun RainTrajectoryDialog(state: BrState, lat: Double, lon: Double, onDismiss: () -> Unit) {
    SimetFullscreenDialog(
        title = "Trajetória da chuva • ${state.displayName}",
        subtitle = "Radar RainViewer (últimas ~2 h) • nuvens GOES-East IR • horários em BRT",
        onDismiss = onDismiss
    ) {
        SimetLeafletMap(
            mode = SimetMapMode.TRAJECTORY, lat = lat, lon = lon, zoom = state.zoom + 1, state = state,
            interactive = true, modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun ExpandedLeafletMapDialog(
    title: String,
    subtitle: String?,
    mode: SimetMapMode,
    state: BrState,
    lat: Double,
    lon: Double,
    zoom: Int,
    onDismiss: () -> Unit
) {
    SimetFullscreenDialog(title, subtitle, onDismiss) {
        SimetLeafletMap(mode = mode, lat = lat, lon = lon, zoom = zoom, state = state, interactive = true, modifier = Modifier.fillMaxSize())
    }
}

