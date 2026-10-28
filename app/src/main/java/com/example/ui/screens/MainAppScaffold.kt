package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppSettings
import com.example.data.remote.RainPlace
import com.example.domain.BrState
import com.example.ui.components.CitySelectionModal
import com.example.ui.components.readLocation
import com.example.viewmodel.WeatherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainAppScaffold(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appSettings = remember { AppSettings(context) }

    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    val tabHistory = rememberSaveable { mutableStateListOf(0) }
    var othersRoute by rememberSaveable { mutableStateOf<OthersRoute?>(null) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    var showInitialCityModal by rememberSaveable { mutableStateOf(false) }

    val hazardAlerts by viewModel.hazardAlerts.collectAsStateWithLifecycle()

    // 1) BOTÃO VOLTAR DO ANDROID:
    // Nunca fechar de dentro de telas internas, modais ou abas diferentes da inicial.
    // Só na aba inicial pede segundo toque ("Toque de novo para sair").
    BackHandler(enabled = true) {
        if (othersRoute != null) {
            othersRoute = null
        } else if (tabHistory.size > 1) {
            tabHistory.removeAt(tabHistory.size - 1)
            currentTab = tabHistory.lastOrNull() ?: 0
        } else if (currentTab != 0) {
            currentTab = 0
            tabHistory.clear()
            tabHistory.add(0)
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000L) {
                (context as? android.app.Activity)?.finish()
            } else {
                lastBackPressTime = now
                Toast.makeText(context, "Toque de novo para sair", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 3) LOCALIZAÇÃO NO PRIMEIRO USO:
    // Pede permissão e usa GPS para definir cidade/estado em todo o Brasil. Se negar, abre o seletor.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        appSettings.hasRequestedInitialLocation = true
        if (granted) {
            coroutineScope.launch(Dispatchers.IO) {
                val loc = readLocation(context)
                if (loc != null) {
                    val place = resolvePlace(context, loc.latitude, loc.longitude)
                    val st = resolveBrState(place.admin1)
                    withContext(Dispatchers.Main) {
                        viewModel.selectCity(place)
                        viewModel.setSelectedState(st)
                        Toast.makeText(context, "Localização: ${place.name}, ${place.admin1 ?: st.uf}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) { showInitialCityModal = true }
                }
            }
        } else {
            showInitialCityModal = true
        }
    }

    LaunchedEffect(Unit) {
        if (!appSettings.hasRequestedInitialLocation) {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                appSettings.hasRequestedInitialLocation = true
                coroutineScope.launch(Dispatchers.IO) {
                    val loc = readLocation(context)
                    if (loc != null) {
                        val place = resolvePlace(context, loc.latitude, loc.longitude)
                        val st = resolveBrState(place.admin1)
                        withContext(Dispatchers.Main) {
                            viewModel.selectCity(place)
                            viewModel.setSelectedState(st)
                        }
                    }
                }
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        }
    }

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
                            if (currentTab != index) {
                                if (tabHistory.lastOrNull() != index) tabHistory.add(index)
                                currentTab = index
                            }
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
                        onOpenAlerts = {
                            if (tabHistory.lastOrNull() != 3) tabHistory.add(3)
                            currentTab = 3
                        },
                        onOpenForecast = {
                            if (tabHistory.lastOrNull() != 1) tabHistory.add(1)
                            currentTab = 1
                        },
                        onOpenFloods = {
                            othersRoute = OthersRoute.FLOODS
                            if (tabHistory.lastOrNull() != 4) tabHistory.add(4)
                            currentTab = 4
                        },
                        onOpenOthers = {
                            othersRoute = null
                            if (tabHistory.lastOrNull() != 4) tabHistory.add(4)
                            currentTab = 4
                        }
                    )
                    1 -> DailyForecastScreen(
                        viewModel = viewModel,
                        onNavigateToRadar = {
                            if (tabHistory.lastOrNull() != 0) tabHistory.add(0)
                            currentTab = 0
                        }
                    )
                    2 -> AgroClimaScreen(viewModel = viewModel)
                    3 -> AlertsCenterScreen(
                        viewModel = viewModel,
                        onOpenFloods = {
                            othersRoute = OthersRoute.FLOODS
                            if (tabHistory.lastOrNull() != 4) tabHistory.add(4)
                            currentTab = 4
                        }
                    )
                    4 -> OthersScreen(viewModel = viewModel, route = othersRoute, onRouteChange = { othersRoute = it })
                }
            }

            if (showInitialCityModal) {
                CitySelectionModal(
                    viewModel = viewModel,
                    onDismiss = { showInitialCityModal = false },
                    onCitySelected = { place ->
                        viewModel.selectCity(place)
                        val st = resolveBrState(place.admin1)
                        viewModel.setSelectedState(st)
                        showInitialCityModal = false
                    }
                )
            }
        }
    }
}

private fun resolveBrState(admin1: String?): BrState {
    if (admin1.isNullOrBlank()) return BrState.SP
    val n = BrState.normalize(admin1)
    return when {
        n.contains("rio grande do sul") || n.contains("rs") -> BrState.RS
        n.contains("parana") || n.contains("pr") -> BrState.PR
        else -> BrState.SP
    }
}

private fun resolvePlace(context: Context, lat: Double, lon: Double): RainPlace {
    try {
        val geocoder = android.location.Geocoder(context, java.util.Locale("pt", "BR"))
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(lat, lon, 1)
        if (!addresses.isNullOrEmpty()) {
            val addr = addresses[0]
            val city = addr.locality ?: addr.subAdminArea ?: addr.subLocality ?: "Local Atual"
            val state = addr.adminArea ?: "Brasil"
            return RainPlace(name = city, admin1 = state, lat = lat, lon = lon)
        }
    } catch (_: Exception) { }

    return RainPlace(name = "Sua Localização", admin1 = "Brasil", lat = lat, lon = lon)
}
