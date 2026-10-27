package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.BrState
import com.example.domain.HazardAlert

/**
 * Seletor de estado SP / PR / RS (recentraliza mapas e previsão) + selo de alertas ativos do estado,
 * colorido pela maior severidade. Toque no selo → aba Alertas.
 */
@Composable
fun StateSelectorBar(
    selected: BrState,
    alerts: List<HazardAlert>,
    onSelect: (BrState) -> Unit,
    onOpenAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            BrState.entries.forEach { st ->
                val isSel = st == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) SimetCardDefaults.Accent else Color.Transparent)
                        .clickable { onSelect(st) }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                        .testTag("state_${st.uf}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        st.uf, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        val top = alerts.maxByOrNull { it.severity.rank }
        val color = top?.let { Color(it.severity.argb) } ?: Color(0xFF10B981)
        Surface(
            onClick = onOpenAlerts,
            shape = RoundedCornerShape(20.dp),
            color = color.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.7f)),
            modifier = Modifier.testTag("badge_alerts")
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (top != null) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                    contentDescription = "Alertas", tint = color, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(5.dp))
                if (top != null) {
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text("${alerts.size} alerta${if (alerts.size > 1) "s" else ""}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                } else {
                    Text("Sem alertas", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
