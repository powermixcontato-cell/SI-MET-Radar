package com.example.ui.components

import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.WeatherStationEntity
import com.example.util.safeDrawText
import com.example.data.local.entity.hasRealData
import com.example.ui.map.*
import androidx.compose.foundation.gestures.detectTapGestures

/**
 * Novo Mapa Geográfico Interativo de Alta Definição:
 * - Camada de Chuva Acumulada (mm) e Previsões Climáticas do Estado de SP
 * - Isolinhas e manchas térmicas/pluviométricas suaves renderizadas com alta legibilidade
 * - Marcadores interativos de municípios com tooltip instantâneo
 * - Alternância entre Camada de Chuva (mm), Temperatura (°C) e Risco Climático
 * - Controles de Zoom (+/-) e Reset de visualização seguros contra travamentos
 */
@Composable
fun StatePrecipitationForecastMap(
    stations: List<WeatherStationEntity>,
    selectedStation: WeatherStationEntity?,
    userCoordinates: Pair<Double, Double>? = null,
    onStationSelected: (WeatherStationEntity) -> Unit,
    mapFormat: Int = 0,
    onMapFormatChanged: (Int) -> Unit = {},
    mapBackgroundTheme: Int = 0,
    onMapBackgroundThemeChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (stations.isEmpty()) return

    // Dynamic measured canvas dimensions for accurate geographic projection and centering
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    // Map format state: 0 = Panorâmico 16:9, 1 = Topográfico Cartográfico, 2 = Radar Doppler Integrado
    var currentFormat by remember(mapFormat) { mutableIntStateOf(mapFormat) }
    val effectiveMapFormat = currentFormat
    val setFormat: (Int) -> Unit = {
        currentFormat = it
        onMapFormatChanged(it)
    }

    // Map background theme: 0 = Black & Blue, 1 = Terrestre, 2 = White
    var currentBgTheme by remember(mapBackgroundTheme) { mutableIntStateOf(mapBackgroundTheme) }
    val effectiveBgTheme = currentBgTheme
    val setBgTheme: (Int) -> Unit = {
        currentBgTheme = it
        onMapBackgroundThemeChanged(it)
    }

    // Live clock ticker
    var liveTimeMillis by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    // Relógio de 30 s que só roda com a tela visível (RESUMED); antes atualizava a cada 1 s sempre
    val clockLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(clockLifecycleOwner) {
        clockLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                liveTimeMillis = System.currentTimeMillis()
                kotlinx.coroutines.delay(30_000L)
            }
        }
    }

    // View layer: 0 = Chuva & Acumulados (mm), 1 = Temperatura Atual (°C), 2 = Previsão de Risco
    var activeLayer by remember { mutableIntStateOf(0) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val mapPadPx = with(density) { 10.dp.toPx() }
    val currentOnStationSelected by androidx.compose.runtime.rememberUpdatedState(onStationSelected)

    // Centraliza exatamente (lat, lon) usando a MESMA projeção do desenho
    val centerOnCoordinates: (Double, Double, Float) -> Unit = { targetLat, targetLon, targetZoom ->
        val w = if (canvasWidth > 50f) canvasWidth else 800f
        val h = if (canvasHeight > 50f) canvasHeight else 600f
        val (px, py) = SpProjection(w, h, paddingPx = mapPadPx).panToCenter(targetLat, targetLon, targetZoom)
        zoomScale = targetZoom
        panOffsetX = px
        panOffsetY = py
    }

    // Zoom ancorado na localização do usuário (ou estação selecionada)
    val applyZoomToExactLocation: (Float) -> Unit = { newZoom ->
        val clampedZoom = newZoom.coerceIn(0.9f, 5.0f)
        val target = userCoordinates ?: selectedStation?.let { Pair(it.lat, it.lon) }
        if (target != null) {
            val (tLat, tLon) = target
            centerOnCoordinates(tLat, tLon, clampedZoom)
        } else {
            val ratio = clampedZoom / (if (zoomScale > 0) zoomScale else 1f)
            zoomScale = clampedZoom
            panOffsetX *= ratio
            panOffsetY *= ratio
        }
    }

    // Foco automático na estação selecionada (zoom moderado para ainda ver a região)
    androidx.compose.runtime.LaunchedEffect(selectedStation?.id, canvasWidth) {
        selectedStation?.let { st -> if (canvasWidth > 50f) centerOnCoordinates(st.lat, st.lon, 1.6f) }
    }

    // Foco automático na localização GPS do usuário
    androidx.compose.runtime.LaunchedEffect(userCoordinates, canvasWidth) {
        userCoordinates?.let { (uLat, uLon) -> if (canvasWidth > 50f) centerOnCoordinates(uLat, uLon, 1.8f) }
    }

    val textMeasurer = rememberTextMeasurer()


    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("state_precipitation_forecast_map"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header with Layer Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFF0284C7).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Mapa SP",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mdInline("**Mapa de Chuva e Previsões SP**"),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = mdInline("• Visualização cartográfica regional em **alta fidelidade**"),
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${stations.size} Cidades",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Layer Selector Filter Chips
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = activeLayer == 0,
                        onClick = { activeLayer = 0 },
                        label = { Text("Chuva Acumulada (mm)", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                        leadingIcon = {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.height(32.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = activeLayer == 1,
                        onClick = { activeLayer = 1 },
                        label = { Text("Campo Térmico (°C)", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                        leadingIcon = {
                            Icon(Icons.Default.Thermostat, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF97316),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.height(32.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = activeLayer == 2,
                        onClick = { activeLayer = 2 },
                        label = { Text("Probabilidade / Risco (%)", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                        leadingIcon = {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF8B5CF6),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.height(32.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = activeLayer == 3,
                        onClick = { activeLayer = 3 },
                        label = { Text("Umidade (Open-Meteo)", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                        leadingIcon = {
                            Icon(Icons.Default.Park, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF10B981),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Format Selector Chips
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(mdInline("**Formato** ·"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, softWrap = false)
                FilterChip(
                    selected = effectiveMapFormat == 0,
                    onClick = { setFormat(0) },
                    label = { Text("Windy Vento & Chuva", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.height(32.dp).testTag("chip_state_map_format_0")
                )
                FilterChip(
                    selected = effectiveMapFormat == 1,
                    onClick = { setFormat(1) },
                    label = { Text("Topográfico SP", fontSize = 10.sp, maxLines = 1, softWrap = false) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF10B981),
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.height(32.dp).testTag("chip_state_map_format_1")
                )
                FilterChip(
                    selected = effectiveMapFormat == 2,
                    onClick = { setFormat(2) },
                    label = { Text("Anéis de radar", fontSize = 10.sp, maxLines = 1, softWrap = false) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF00E5FF),
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.height(32.dp).testTag("chip_state_map_format_2")
                )
            }
            }

            // Map Background Theme Selector Row (Black & Blue / Terrestre / White)
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(mdInline("**Fundo** ·"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, softWrap = false)
                FilterChip(
                    selected = effectiveBgTheme == 0,
                    onClick = { setBgTheme(0) },
                    label = { Text("Black & Blue", fontSize = 10.sp, maxLines = 1, softWrap = false, fontWeight = if (effectiveBgTheme == 0) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(Color(0xFF0284C7), CircleShape)
                                .border(1.dp, Color(0xFF38BDF8), CircleShape)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0C223A),
                        selectedLabelColor = Color(0xFF38BDF8)
                    ),
                    modifier = Modifier.height(32.dp).testTag("chip_state_theme_black_blue")
                )
                FilterChip(
                    selected = effectiveBgTheme == 1,
                    onClick = { setBgTheme(1) },
                    label = { Text("Terrestre 🌍", fontSize = 10.sp, maxLines = 1, softWrap = false, fontWeight = if (effectiveBgTheme == 1) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(Color(0xFF22C55E), CircleShape)
                                .border(1.dp, Color(0xFF86EFAC), CircleShape)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF14532D),
                        selectedLabelColor = Color(0xFF86EFAC)
                    ),
                    modifier = Modifier.height(32.dp).testTag("chip_state_theme_terrestre")
                )
                FilterChip(
                    selected = effectiveBgTheme == 2,
                    onClick = { setBgTheme(2) },
                    label = { Text("White ⚪", fontSize = 10.sp, maxLines = 1, softWrap = false, fontWeight = if (effectiveBgTheme == 2) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(Color(0xFFFFFFFF), CircleShape)
                                .border(1.dp, Color(0xFF64748B), CircleShape)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFE2E8F0),
                        selectedLabelColor = Color(0xFF0F172A)
                    ),
                    modifier = Modifier.height(32.dp).testTag("chip_state_theme_white")
                )
            }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Altura proporcional ao formato de SP (≈1,5:1) — antes sobrava faixa vazia embaixo
            val mapHeight = when (effectiveMapFormat) {
                0 -> 260.dp
                1 -> 270.dp
                else -> 280.dp
            }

            // ---- Dados da camada (SOMENTE estações com dado real) e campo interpolado em cache ----
            val theme = remember(effectiveBgTheme) { MapTheme.of(effectiveBgTheme) }
            val ramp = layerRamp(activeLayer)
            val realStations = remember(stations) { stations.filter { it.hasRealData() } }
            val field = remember(realStations, activeLayer) {
                ScalarField.idw(realStations.map { FieldPoint(it.lat, it.lon, layerValue(it, activeLayer)) })
            }
            val fieldImage = remember(field, activeLayer) { field?.toImageBitmap(ramp, 0.85f) }
            val isolines = remember(field, activeLayer) { field?.isolines(layerIsoLevels(activeLayer)) ?: emptyMap() }
            val noRainToday = activeLayer == 0 && realStations.isNotEmpty() && realStations.all { it.rainVolumeMm < 0.2 }
            val windVectors = remember(realStations) {
                realStations.mapNotNull { st ->
                    val fromDeg = com.example.data.remote.compassToDegrees(st.windDirection) ?: return@mapNotNull null
                    Triple(st, fromDeg, st.windSpeed)
                }
            }
            val lastUpdate = realStations.maxOfOrNull { it.lastUpdated }
            val sdf = remember { java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("pt", "BR")) }

            // Canvas Map Box with Safe Touch Zoom and Pan
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mapHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.outside, RoundedCornerShape(14.dp))
                    .border(1.dp, theme.labelBorder, RoundedCornerShape(14.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .onSizeChanged { size ->
                            canvasWidth = size.width.toFloat()
                            canvasHeight = size.height.toFloat()
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val newZoom = (zoomScale * zoom).coerceIn(0.9f, 5.0f)
                                val (px, py) = SpProjection.gesturePan(
                                    canvasWidth, canvasHeight, zoomScale, newZoom,
                                    panOffsetX, panOffsetY, centroid.x, centroid.y, pan.x, pan.y
                                )
                                zoomScale = newZoom
                                panOffsetX = px
                                panOffsetY = py
                            }
                        }
                        .pointerInput(stations) {
                            detectTapGestures { tap ->
                                val proj = SpProjection(canvasWidth, canvasHeight, zoomScale, panOffsetX, panOffsetY, mapPadPx)
                                val hitPx = 28.dp.toPx()
                                stations
                                    .map { it to kotlin.math.hypot(proj.x(it.lon) - tap.x, proj.y(it.lat) - tap.y) }
                                    .filter { it.second <= hitPx }
                                    .minByOrNull { it.second }
                                    ?.let { currentOnStationSelected(it.first) }
                            }
                        }
                        .testTag("canvas_state_precipitation_map")
                ) {
                    val w = size.width
                    val h = size.height
                    val proj = SpProjection(w, h, zoomScale, panOffsetX, panOffsetY, mapPadPx)

                    // 1. Mapa-base (contorno IBGE, oceano, vizinhos, grade, rios/rodovias no Topográfico)
                    val statePath = drawSpBasemap(
                        proj, theme, textMeasurer,
                        showRivers = effectiveMapFormat == 1 || effectiveBgTheme == 1,
                        showRoads = effectiveMapFormat == 1,
                        showGrid = true
                    )

                    // 2. Campo contínuo interpolado (IDW) + isolinhas, recortados pelo estado
                    fieldImage?.let { drawScalarField(it, proj, statePath) }
                    drawIsolines(
                        isolines, proj, statePath,
                        if (theme.isLight) Color(0xFF0F172A).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.30f)
                    )
                    drawSpOutline(statePath, theme)

                    // 3a. Formato "Anéis de radar": alcance REAL em km dos radares IPMet (Bauru e P. Prudente)
                    if (effectiveMapFormat == 2) {
                        val ringColor = if (theme.isLight) Color(0xFF0369A1) else Color(0xFF67E8F9)
                        listOf(-22.3145 to -49.0587, -22.1256 to -51.3889).forEach { (lat, lon) ->
                            val c = Offset(proj.x(lon), proj.y(lat))
                            listOf(120f, 240f).forEach { km ->
                                drawCircle(
                                    color = ringColor.copy(alpha = if (km == 240f) 0.55f else 0.35f),
                                    radius = km * proj.pxPerKm,
                                    center = c,
                                    style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f))
                                )
                            }
                            drawCircle(ringColor, radius = 3.dp.toPx(), center = c)
                        }
                    }

                    // 3b. Formato "Windy": setas de vento REAL (Open-Meteo) em cada cidade
                    if (effectiveMapFormat == 0) {
                        val arrowColor = if (theme.isLight) Color(0xFF0F172A).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.8f)
                        windVectors.forEach { (st, fromDeg, speed) ->
                            val towards = Math.toRadians(fromDeg + 180.0)
                            val len = (10f + speed.toFloat().coerceIn(0f, 40f) * 0.6f).dp.toPx()
                            val sx = proj.x(st.lon); val sy = proj.y(st.lat)
                            val ex = sx + (kotlin.math.sin(towards) * len).toFloat()
                            val ey = sy - (kotlin.math.cos(towards) * len).toFloat()
                            drawLine(arrowColor, Offset(sx, sy), Offset(ex, ey), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                            val ah = 4.dp.toPx()
                            val back = towards + Math.PI
                            listOf(-0.5, 0.5).forEach { d ->
                                drawLine(
                                    arrowColor, Offset(ex, ey),
                                    Offset(ex + (kotlin.math.sin(back + d) * ah).toFloat(), ey - (kotlin.math.cos(back + d) * ah).toFloat()),
                                    strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round
                                )
                            }
                        }
                    }

                    // 4. Pinos + rótulos (nome completo; valor em pílula própria; sem sobreposição)
                    val labels = stations.map { st ->
                        val real = st.hasRealData()
                        val v = layerValue(st, activeLayer)
                        MapLabel(
                            key = st.id,
                            x = proj.x(st.lon),
                            y = proj.y(st.lat),
                            name = st.name,
                            value = if (real) layerValueText(st, activeLayer) else "sem dado",
                            valueColor = if (!real) Color(0xFF64748B) else if (activeLayer == 0 && v < 0.2f) Color(0xFF94A3B8) else ramp.color(v),
                            priority = if (real) (v * 10).toInt() else -1,
                            selected = selectedStation?.id == st.id
                        )
                    }
                    labels.forEach { lb ->
                        drawCityPin(lb.x, lb.y, lb.valueColor, lb.selected, theme)
                    }
                    val controlsCount = if (userCoordinates != null) 4 else 3
                    val avoidZoom = Rect(0f, h - 54.dp.toPx(), (12 + 40 * controlsCount).dp.toPx(), h)
                    drawMapLabels(textMeasurer, labels, theme, avoid = listOf(avoidZoom))

                    // 5. Localização do usuário
                    userCoordinates?.let { (uLat, uLon) ->
                        val ux = proj.x(uLon); val uy = proj.y(uLat)
                        drawCircle(Color(0xFF38BDF8).copy(alpha = 0.3f), radius = 12.dp.toPx(), center = Offset(ux, uy))
                        drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(ux, uy))
                        drawCircle(Color(0xFF0284C7), radius = 4.dp.toPx(), center = Offset(ux, uy))
                    }
                }

                // Aviso honesto quando não há o que mostrar
                val emptyMsg = when {
                    realStations.isEmpty() -> "Aguardando a 1ª atualização real da Open-Meteo"
                    noRainToday -> "Sem chuva prevista hoje nas ${realStations.size} cidades"
                    else -> null
                }
                emptyMsg?.let {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = theme.labelBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.labelBorder),
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                    ) {
                        Text(it, color = theme.labelFg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }

                // Zoom and Reset Controls Floating Overlay (Top-Right)
                val zoomIconColor = if (theme.isLight) Color(0xFF0F172A) else Color.White
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(theme.labelBg, RoundedCornerShape(10.dp))
                        .border(1.dp, theme.labelBorder, RoundedCornerShape(10.dp))
                ) {
                    IconButton(
                        onClick = { applyZoomToExactLocation(zoomScale * 1.3f) },
                        modifier = Modifier.size(40.dp).testTag("btn_zoom_in_state_map")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In (Foco Exato)", tint = zoomIconColor, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { applyZoomToExactLocation(zoomScale / 1.3f) },
                        modifier = Modifier.size(40.dp).testTag("btn_zoom_out_state_map")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out (Foco Exato)", tint = zoomIconColor, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            zoomScale = 1.0f
                            panOffsetX = 0f
                            panOffsetY = 0f
                        },
                        modifier = Modifier.size(40.dp).testTag("btn_reset_state_map")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset", tint = if (theme.isLight) Color(0xFF0284C7) else Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    }
                    userCoordinates?.let {
                        IconButton(
                            onClick = { applyZoomToExactLocation(3.0f) },
                            modifier = Modifier.size(40.dp).testTag("btn_center_user_state_map")
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = "Focar Meu Local", tint = if (theme.isLight) Color(0xFF0284C7) else Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Legenda com unidade + fonte e horário do dado
            MapLegend(
                ramp = ramp,
                title = layerTitle(activeLayer),
                sourceLine = buildString {
                    append("Fonte: Open-Meteo (previsão numérica, não é observação) • ")
                    append("${realStations.size}/${stations.size} cidades • campo interpolado (IDW) entre cidades")
                    if (lastUpdate != null && lastUpdate > 0L) append(" • atualizado ${sdf.format(java.util.Date(lastUpdate))}")
                },
                modifier = Modifier.testTag("state_map_legend")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Station Focus Carousel Below Map
            Text(
                text = mdInline("• **Toque em uma cidade** para centrar o mapa:"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(stations, key = { it.id }) { st ->
                    val real = st.hasRealData()
                    val v = layerValue(st, activeLayer)
                    CityValueChip(
                        name = st.name,
                        value = if (real) layerValueText(st, activeLayer) else null,
                        valueColor = if (activeLayer == 0 && v < 0.2f) Color(0xFF94A3B8) else ramp.color(v),
                        selected = selectedStation?.id == st.id,
                        onClick = { onStationSelected(st) },
                        modifier = Modifier.testTag("chip_state_city_${st.id}")
                    )
                }
            }
            }
        }
    }
}

/** Valor numérico da camada para uma estação (0 chuva mm do dia, 1 temp °C, 2 prob. %, 3 UR %). */
internal fun layerValue(st: WeatherStationEntity, layer: Int): Float = when (layer) {
    0 -> st.rainVolumeMm.toFloat()
    1 -> st.currentTemp.toFloat()
    2 -> st.rainProbability.toFloat()
    else -> st.humidity.toFloat()
}

internal fun layerValueText(st: WeatherStationEntity, layer: Int): String = when (layer) {
    0 -> "${fmt1(st.rainVolumeMm)} mm"
    1 -> "${kotlin.math.round(st.currentTemp).toInt()}°C"
    2 -> "${st.rainProbability}%"
    else -> "${st.humidity}% UR"
}

internal fun layerRamp(layer: Int): MapRamp = when (layer) {
    0 -> MapRamp.RainMm
    1 -> MapRamp.TempC
    2 -> MapRamp.ProbPct
    else -> MapRamp.HumidityPct
}

internal fun layerIsoLevels(layer: Int): List<Float> = when (layer) {
    0 -> listOf(1f, 5f, 10f, 20f, 50f)
    1 -> listOf(15f, 20f, 25f, 30f, 35f)
    2 -> listOf(30f, 50f, 70f, 90f)
    else -> listOf(40f, 60f, 80f)
}

internal fun layerTitle(layer: Int): String = when (layer) {
    0 -> "Chuva prevista no dia"
    1 -> "Temperatura atual"
    2 -> "Probabilidade de chuva na hora"
    else -> "Umidade relativa atual"
}
