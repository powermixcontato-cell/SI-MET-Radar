package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.viewmodel.WeatherViewModel

@Composable
fun MainAppScaffold(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    var othersRoute by rememberSaveable { mutableStateOf<OthersRoute?>(null) }
    // v5.1: selo com os alertas (3 categorias) do estado selecionado
    val hazardAlerts by viewModel.hazardAlerts.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .zIndex(10f)
                    .testTag("main_bottom_bar")
            ) {
                val items = listOf(
                    Triple("Início", Icons.Default.Home, "nav_tab_radar"),
                    Triple("Previsão", Icons.Default.WbSunny, "nav_tab_forecast"),
                    Triple("Agro", Icons.Default.Agriculture, "nav_tab_agroclima"),
                    Triple("Alertas", Icons.Default.NotificationsActive, "nav_tab_alerts"),
                    Triple("Outros", Icons.Default.Apps, "nav_tab_others")
                )
                items.forEachIndexed { index, (label, icon, tag) ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = {
                            if (index == 4 && currentTab == 4) othersRoute = null
                            currentTab = index
                        },
                        icon = {
                            if (index == 3 && hazardAlerts.isNotEmpty()) {
                                BadgedBox(badge = {
                                    Badge(containerColor = Color(hazardAlerts.maxBy { it.severity.rank }.severity.argb)) {
                                        Text("${hazardAlerts.size}")
                                    }
                                }) { Icon(icon, contentDescription = label) }
                            } else {
                                Icon(icon, contentDescription = label)
                            }
                        },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8),
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag(tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { targetIndex ->
                when (targetIndex) {
                    0 -> RadarMapScreen(
                        viewModel = viewModel,
                        onOpenAlerts = { currentTab = 3 },
                        onOpenFloods = { othersRoute = OthersRoute.FLOODS; currentTab = 4 },
                        onOpenOthers = { othersRoute = null; currentTab = 4 }
                    )
                    1 -> DailyForecastScreen(
                        viewModel = viewModel,
                        onNavigateToRadar = { currentTab = 0 }
                    )
                    2 -> AgroClimaScreen(viewModel = viewModel)
                    3 -> AlertsCenterScreen(
                        viewModel = viewModel,
                        onOpenFloods = { othersRoute = OthersRoute.FLOODS; currentTab = 4 }
                    )
                    4 -> OthersScreen(viewModel = viewModel, route = othersRoute, onRouteChange = { othersRoute = it })
                }
            }
        }
    }
}
