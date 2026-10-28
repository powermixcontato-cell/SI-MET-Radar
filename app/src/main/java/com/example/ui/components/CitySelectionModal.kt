package com.example.ui.components

import android.content.Context
import android.location.Geocoder
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.LocalRainService
import com.example.data.remote.RainPlace
import com.example.viewmodel.WeatherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val OledBlack = Color(0xFF000000)
private val CardBg = Color(0xFF0A0F1D)
private val SurfaceBorder = Color(0xFF1E293B)
private val CyanAccent = Color(0xFF00E5FF)
private val EmeraldAccent = Color(0xFF10B981)
private val AmberGold = Color(0xFFF59E0B)

/** Geocodificação reversa rápida para obter o nome da cidade via GPS. */
private fun reverseGeocodeGps(context: Context, lat: Double, lon: Double): RainPlace {
    try {
        if (Geocoder.isPresent()) {
            @Suppress("DEPRECATION")
            val addrs = Geocoder(context, Locale("pt", "BR")).getFromLocation(lat, lon, 1)
            val addr = addrs?.firstOrNull()
            if (addr != null) {
                val city = addr.subAdminArea ?: addr.locality ?: addr.adminArea
                if (!city.isNullOrBlank()) {
                    return RainPlace(city, addr.adminArea, lat, lon)
                }
            }
        }
    } catch (_: Exception) {
    }
    return RainPlace("Minha Localização", "GPS", lat, lon)
}

private val POPULAR_CAPITALS = listOf(
    RainPlace("São Paulo", "São Paulo", -23.5489, -46.6388),
    RainPlace("Campinas", "São Paulo", -22.9056, -47.0608),
    RainPlace("Bauru", "São Paulo", -22.3145, -49.0587),
    RainPlace("Ribeirão Preto", "São Paulo", -21.1767, -47.8208),
    RainPlace("Santos", "São Paulo", -23.9608, -46.3331),
    RainPlace("Curitiba", "Paraná", -25.4284, -49.2733),
    RainPlace("Londrina", "Paraná", -23.3103, -51.1628),
    RainPlace("Maringá", "Paraná", -23.4209, -51.9331),
    RainPlace("Porto Alegre", "Rio Grande do Sul", -30.0346, -51.2177),
    RainPlace("Caxias do Sul", "Rio Grande do Sul", -29.1678, -51.1794),
    RainPlace("Pelotas", "Rio Grande do Sul", -31.7654, -52.3376),
    RainPlace("Rio de Janeiro", "Rio de Janeiro", -22.9068, -43.1729),
    RainPlace("Belo Horizonte", "Minas Gerais", -19.9208, -43.9378),
    RainPlace("Brasília", "Distrito Federal", -15.7975, -47.8919),
    RainPlace("Goiânia", "Goiás", -16.6869, -49.2648),
    RainPlace("Salvador", "Bahia", -12.9777, -38.5016),
    RainPlace("Florianópolis", "Santa Catarina", -27.5954, -48.5480),
    RainPlace("Cuiabá", "Mato Grosso", -15.6010, -56.0974)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CitySelectionModal(
    viewModel: WeatherViewModel,
    onDismiss: () -> Unit,
    onCitySelected: (RainPlace) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<RainPlace>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Favoritas, 1: Recentes, 2: Principais

    var favoritesList by remember { mutableStateOf(viewModel.getFavoriteCities()) }
    var recentsList by remember { mutableStateOf(viewModel.getRecentCities()) }

    // GPS location requester
    val locator = rememberMyLocationRequester { fix ->
        scope.launch {
            val place = withContext(Dispatchers.IO) {
                reverseGeocodeGps(context.applicationContext, fix.lat, fix.lon)
            }
            Toast.makeText(context, "Localização identificada: ${place.label}", Toast.LENGTH_SHORT).show()
            onCitySelected(place)
            onDismiss()
        }
    }

    // Debounced search on Open-Meteo Geocoding API
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length < 2) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(320L)
        val results = withContext(Dispatchers.IO) {
            try {
                LocalRainService.searchCities(q)
            } catch (_: Exception) {
                emptyList()
            }
        }
        searchResults = results
        isSearching = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OledBlack,
        dragHandle = null,
        modifier = Modifier.testTag("modal_city_selection")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .background(OledBlack)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationCity,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Selecionar Cidade",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp
                        )
                    }
                    Text(
                        "Qualquer município do Brasil via Open-Meteo",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp).testTag("btn_close_city_modal")
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Botão GPS: Usar minha localização
            Button(
                onClick = { locator.request() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(12.dp),
                enabled = !locator.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_gps_city_selection")
            ) {
                if (locator.busy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Obtendo sinal GPS...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                } else {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Usar minha localização (GPS)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Campo de Busca
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("Digite o nome da cidade (ex: Campinas, Londrina...)", fontSize = 13.sp, color = Color(0xFF64748B))
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = CyanAccent)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar", tint = Color(0xFF94A3B8))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedContainerColor = CardBg,
                    unfocusedContainerColor = CardBg,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = CyanAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_search_city")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Modo Busca Ativa
            if (searchQuery.isNotBlank()) {
                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = CyanAccent, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Buscando municípios no Brasil...", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }
                } else if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Nenhum município encontrado para \"$searchQuery\".\nTente sem acento ou verifique a digitação.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        "Resultados encontrados (${searchResults.size})",
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults) { place ->
                            val isFav = viewModel.isFavoriteCity(place)
                            CityResultItem(
                                place = place,
                                isFavorite = isFav,
                                onSelect = {
                                    onCitySelected(place)
                                    onDismiss()
                                },
                                onToggleFavorite = {
                                    viewModel.toggleFavoriteCity(place)
                                    favoritesList = viewModel.getFavoriteCities()
                                }
                            )
                        }
                    }
                }
            } else {
                // Navegação por abas quando a busca está vazia
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = CardBg,
                    contentColor = CyanAccent,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = CyanAccent
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "⭐ Favoritas (${favoritesList.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "🕒 Recentes (${recentsList.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                "🇧🇷 Principais",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 2) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (selectedTab) {
                    0 -> {
                        // Lista de Favoritas
                        if (favoritesList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Nenhuma cidade favoritada ainda.\nBusque pelo nome e toque na estrela para salvar.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(favoritesList) { place ->
                                    CityResultItem(
                                        place = place,
                                        isFavorite = true,
                                        onSelect = {
                                            onCitySelected(place)
                                            onDismiss()
                                        },
                                        onToggleFavorite = {
                                            viewModel.toggleFavoriteCity(place)
                                            favoritesList = viewModel.getFavoriteCities()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // Lista de Recentes
                        if (recentsList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Nenhuma cidade recente.\nAs cidades consultadas aparecerão aqui automaticamente.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(recentsList) { place ->
                                    val isFav = viewModel.isFavoriteCity(place)
                                    CityResultItem(
                                        place = place,
                                        isFavorite = isFav,
                                        onSelect = {
                                            onCitySelected(place)
                                            onDismiss()
                                        },
                                        onToggleFavorite = {
                                            viewModel.toggleFavoriteCity(place)
                                            favoritesList = viewModel.getFavoriteCities()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        // Principais Cidades / Capitais
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Text(
                                    "Acesso Rápido (Polos Regionais e Capitais)",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(POPULAR_CAPITALS) { place ->
                                val isFav = viewModel.isFavoriteCity(place)
                                CityResultItem(
                                    place = place,
                                    isFavorite = isFav,
                                    onSelect = {
                                        onCitySelected(place)
                                        onDismiss()
                                    },
                                    onToggleFavorite = {
                                        viewModel.toggleFavoriteCity(place)
                                        favoritesList = viewModel.getFavoriteCities()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CityResultItem(
    place: RainPlace,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val uf = RainPlace.ufOf(place.admin1.orEmpty()) ?: place.admin1.orEmpty().ifBlank { "BR" }
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        modifier = Modifier.fillMaxWidth().testTag("city_item_${place.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = CyanAccent.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "${place.name}, $uf",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        if (place.admin1.isNullOrBlank() || place.admin1 == uf) "Brasil • ${String.format(Locale.US, "%.2f, %.2f", place.lat, place.lon)}"
                        else "${place.admin1} • ${String.format(Locale.US, "%.2f, %.2f", place.lat, place.lon)}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp).testTag("btn_fav_${place.name}")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                    tint = if (isFavorite) AmberGold else Color(0xFF64748B),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
