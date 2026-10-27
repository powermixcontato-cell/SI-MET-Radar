package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WindPower
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.entity.RegionSubscriptionEntity
import com.example.data.local.entity.WeatherAlertEntity
import com.example.viewmodel.WeatherViewModel

/**
 * Aba Aprimorada de Tendências & Alertas Meteorológicos:
 * - Filtro de Severidade dos Alertas (Todos, Vermelho / Risco Extremo, Laranja / Moderado)
 * - Indicador de Alerta Antecipado da Defesa Civil / IPMet
 * - Sumário de Risco Meteorológico e Hidrológico das Bacias de SP
 * - Configurações de Notificações com thresholds personalizados por bacia/região
 */
@Composable
fun AlertsAndRegionsScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val alerts by viewModel.allAlerts.collectAsStateWithLifecycle()
    val subscriptions by viewModel.regionSubscriptions.collectAsStateWithLifecycle()
    val allStations by viewModel.allStations.collectAsStateWithLifecycle()

    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0: Todos, 1: Severos (Vermelho), 2: Alertas Laranja

    val filteredAlerts = remember(alerts, selectedFilterIndex) {
        when (selectedFilterIndex) {
            1 -> alerts.filter { it.severity == "ALERTA_VERMELHO" }
            2 -> alerts.filter { it.severity == "ALERTA_LARANJA" }
            else -> alerts
        }
    }

    val redAlertsCount = remember(alerts) { alerts.count { it.severity == "ALERTA_VERMELHO" } }
    val orangeAlertsCount = remember(alerts) { alerts.count { it.severity == "ALERTA_LARANJA" } }

    // Permission launcher for Android 13+ push notifications
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.triggerSuddenChangeAlertTest(context)
            Toast.makeText(context, "Notificação enviada com sucesso!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissão de notificação negada pelo sistema.", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("alerts_and_regions_screen")
    ) {
        // Top Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Central de Alertas e Tendências",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Monitoramento contínuo IPMet UNESP e Defesa Civil SP",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (redAlertsCount > 0) Color(0xFFFF1744).copy(alpha = 0.18f) else Color(0xFF10B981).copy(alpha = 0.18f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (redAlertsCount > 0) Color(0xFFFF1744).copy(alpha = 0.5f) else Color(0xFF10B981).copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = if (redAlertsCount > 0) "$redAlertsCount Alerta(s) Crítico(s)" else "Sem Risco Extremo",
                            color = if (redAlertsCount > 0) Color(0xFFFF5252) else Color(0xFF10B981),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Real-Time Situation Summary Card (Defesa Civil & Bacias)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Panorama de Riscos Meteorológicos e Hídricos",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF450A0A).copy(alpha = 0.4f),
                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Nível Crítico", color = Color(0xFFFF8A80), fontSize = 10.sp)
                                Text("$redAlertsCount Regiões", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Tempestades / Inundações", color = Color(0xFF94A3B8), fontSize = 9.sp)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF422006).copy(alpha = 0.4f),
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Atenção Laranja", color = Color(0xFFFFD180), fontSize = 10.sp)
                                Text("$orangeAlertsCount Regiões", color = Color(0xFFFFAB00), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Chuva > 40mm e Vento", color = Color(0xFF94A3B8), fontSize = 9.sp)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF064E3B).copy(alpha = 0.4f),
                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Monitoramento", color = Color(0xFFA7F3D0), fontSize = 10.sp)
                                Text("${subscriptions.size} Bacias", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Radares Ativos 24h", color = Color(0xFF94A3B8), fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }

        // Test Push Notification Trigger Action
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Alerta de Mudança Brusca",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            "Disparar simulação imediata de notificação push no aparelho para validação em campo",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED

                                if (!hasPermission) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.triggerSuddenChangeAlertTest(context)
                                    Toast.makeText(context, "Notificação enviada!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                viewModel.triggerSuddenChangeAlertTest(context)
                                Toast.makeText(context, "Notificação enviada!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("btn_test_notification")
                    ) {
                        Text("Testar", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
                    }
                }
            }
        }

        // Filter Chips for Alerts
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Avisos Meteorológicos Vigentes (${filteredAlerts.size})",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = selectedFilterIndex == 0,
                            onClick = { selectedFilterIndex = 0 },
                            label = { Text("Todos (${alerts.size})", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = selectedFilterIndex == 1,
                            onClick = { selectedFilterIndex = 1 },
                            label = { Text("Críticos ($redAlertsCount)", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF1744),
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = selectedFilterIndex == 2,
                            onClick = { selectedFilterIndex = 2 },
                            label = { Text("Laranja ($orangeAlertsCount)", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF9100),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        if (filteredAlerts.isEmpty()) {
            item {
                Text(
                    text = "Nenhum alerta meteorológico severo registrado para o filtro selecionado.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        } else {
            items(filteredAlerts) { alert ->
                AlertItemCard(
                    alert = alert,
                    onAcknowledge = { viewModel.acknowledgeAlert(alert.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // Regional Subscriptions Configuration
        item {
            var regionSearchQuery by remember { androidx.compose.runtime.mutableStateOf("") }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Escolha de Alertas por Região e Bacia",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Exemplo: Ative ou desative o Alerta para a Região de Barretos, Bauru ou Ribeirão",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick selector buttons (Todas / Nenhuma / Barretos Foco)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
                        modifier = Modifier
                            .clickable {
                                // Toggle ou foca em Barretos
                                val barretosSub = subscriptions.find { it.regionId == "barretos" }
                                if (barretosSub != null) {
                                    viewModel.toggleSubscription("barretos", !barretosSub.isSubscribed)
                                    Toast.makeText(context, if (!barretosSub.isSubscribed) "Alertas de Barretos ativados!" else "Alertas de Barretos desativados", Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            val isBarretosOn = subscriptions.find { it.regionId == "barretos" }?.isSubscribed == true
                            Text(
                                text = if (isBarretosOn) "✓ Barretos Ativo" else "+ Ativar Barretos",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable {
                            subscriptions.forEach { sub ->
                                if (!sub.isSubscribed) viewModel.toggleSubscription(sub.regionId, true)
                            }
                            Toast.makeText(context, "Todas as regiões ativadas", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(
                            text = "Ativar Todas",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable {
                            subscriptions.forEach { sub ->
                                if (sub.isSubscribed) viewModel.toggleSubscription(sub.regionId, false)
                            }
                            Toast.makeText(context, "Todas as regiões silenciadas", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(
                            text = "Silenciar Todas",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        items(subscriptions) { sub ->
            RegionSubscriptionRow(
                sub = sub,
                onToggle = { isChecked -> viewModel.toggleSubscription(sub.regionId, isChecked) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AlertItemCard(
    alert: WeatherAlertEntity,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRed = alert.severity == "ALERTA_VERMELHO"
    val isOrange = alert.severity == "ALERTA_LARANJA"

    val containerColor = when {
        isRed -> Color(0xFF450A0A)
        isOrange -> Color(0xFF422006)
        else -> Color(0xFF1E293B)
    }
    val badgeColor = when {
        isRed -> Color(0xFFFF5252)
        isOrange -> Color(0xFFFFAB00)
        else -> Color(0xFF00E5FF)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = alert.severity.replace("_", " "),
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                if (!alert.isAcknowledged) {
                    IconButton(
                        onClick = onAcknowledge,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Confirmar Leitura",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${alert.title} - ${alert.regionName}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = alert.description,
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Origem: ${alert.radarStationSource}",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Refletividade Máx: ${alert.dbzPeak} dBZ",
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun RegionSubscriptionRow(
    sub: RegionSubscriptionEntity,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sub.regionName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = if (sub.isSubscribed) "Alertas ativos (Chuva > ${sub.alertThresholdMm} mm/h & Tempestades)" else "Alertas silenciados",
                    color = if (sub.isSubscribed) MaterialTheme.colorScheme.primary else Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }

            Switch(
                checked = sub.isSubscribed,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                modifier = Modifier.testTag("switch_region_${sub.regionId}")
            )
        }
    }
}
