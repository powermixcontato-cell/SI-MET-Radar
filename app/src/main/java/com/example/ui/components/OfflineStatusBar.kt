package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OfflineStatusBar(
    isForcedOffline: Boolean,
    lastUpdated: Long,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onToggleOffline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastUpdated))

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("offline_status_bar"),
        shape = RoundedCornerShape(12.dp),
        color = if (isForcedOffline) Color(0xFF1E293B) else Color(0xFF0F1E36)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onToggleOffline() }
                    .testTag("toggle_offline_mode")
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isForcedOffline) Color(0xFFFFAB00) else Color(0xFF00E676),
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isForcedOffline) Icons.Default.CloudOff else Icons.Default.SignalCellularAlt,
                    contentDescription = null,
                    tint = if (isForcedOffline) Color(0xFFFFAB00) else Color(0xFF00E676),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = if (isForcedOffline) "Modo Offline Ativo (Banco Local Room)" else "Online • Open-Meteo / INMET",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Última atualização: $dateStr • Toque para alternar",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }

            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier.testTag("button_refresh_weather")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Atualizar Previsão",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
