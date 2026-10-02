package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.WeatherStationEntity
import com.example.util.safeDrawText

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
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            liveTimeMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }

    // View layer: 0 = Chuva & Acumulados (mm), 1 = Temperatura Atual (°C), 2 = Previsão de Risco
    var activeLayer by remember { mutableIntStateOf(0) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    // Bounding box of São Paulo state in lat/lon
    val minLon = -53.5
    val maxLon = -44.0
    val minLat = -25.5
    val maxLat = -19.5

    // Helper: Exact centering on any target coordinates
    val centerOnCoordinates: (Double, Double, Float) -> Unit = { targetLat, targetLon, targetZoom ->
        val w = if (canvasWidth > 50f) canvasWidth else 800f
        val h = if (canvasHeight > 50f) canvasHeight else 600f
        val normX = ((targetLon - minLon) / (maxLon - minLon)).toFloat().coerceIn(0f, 1f)
        val normY = ((maxLat - targetLat) / (maxLat - minLat)).toFloat().coerceIn(0f, 1f)
        val baseX = normX * w
        val baseY = normY * h
        zoomScale = targetZoom
        panOffsetX = (w / 2f - baseX) * targetZoom
        panOffsetY = (h / 2f - baseY) * targetZoom
    }

    // Function to zoom while strictly anchoring to the EXACT user location (or selected station)
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

    // Automatic focus and zoom when a station is selected or searched
    androidx.compose.runtime.LaunchedEffect(selectedStation?.id, canvasWidth) {
        selectedStation?.let { st ->
            centerOnCoordinates(st.lat, st.lon, 2.4f)
        }
    }

    // Automatic focus and zoom when user GPS coordinates are detected
    androidx.compose.runtime.LaunchedEffect(userCoordinates, canvasWidth) {
        userCoordinates?.let { (uLat, uLon) ->
            centerOnCoordinates(uLat, uLon, 2.5f)
        }
    }

    val textMeasurer = rememberTextMeasurer()

    fun mapLonToX(lon: Double, width: Float, zoom: Float, panX: Float): Float {
        val normX = ((lon - minLon) / (maxLon - minLon)).toFloat().coerceIn(0f, 1f)
        val base = normX * width
        return width / 2f + (base - width / 2f) * zoom + panX
    }

    fun mapLatToY(lat: Double, height: Float, zoom: Float, panY: Float): Float {
        val normY = ((maxLat - lat) / (maxLat - minLat)).toFloat().coerceIn(0f, 1f)
        val base = normY * height
        return height / 2f + (base - height / 2f) * zoom + panY
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("state_precipitation_forecast_map"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with Layer Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF0284C7).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Mapa SP",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Mapa de Chuva e Previsões SP",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Visualização cartográfica regional em alta fidelidade",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp
                        )
                    }
                }

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

            Spacer(modifier = Modifier.height(10.dp))

            // Layer Selector Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = activeLayer == 0,
                        onClick = { activeLayer = 0 },
                        label = { Text("Chuva Acumulada (mm)", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = activeLayer == 1,
                        onClick = { activeLayer = 1 },
                        label = { Text("Campo Térmico (°C)", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Thermostat, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF97316),
                            selectedLabelColor = Color.White
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = activeLayer == 2,
                        onClick = { activeLayer = 2 },
                        label = { Text("Probabilidade / Risco (%)", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF8B5CF6),
                            selectedLabelColor = Color.White
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = activeLayer == 3,
                        onClick = { activeLayer = 3 },
                        label = { Text("Rede CIIAGRO / IAC", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Park, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF10B981),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Format Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Formato:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                FilterChip(
                    selected = effectiveMapFormat == 0,
                    onClick = { setFormat(0) },
                    label = { Text("Windy Vento & Chuva", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("chip_state_map_format_0")
                )
                FilterChip(
                    selected = effectiveMapFormat == 1,
                    onClick = { setFormat(1) },
                    label = { Text("Topográfico SP", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF10B981),
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("chip_state_map_format_1")
                )
                FilterChip(
                    selected = effectiveMapFormat == 2,
                    onClick = { setFormat(2) },
                    label = { Text("Radar Doppler", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF00E5FF),
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.testTag("chip_state_map_format_2")
                )
            }

            // Map Background Theme Selector Row (Black & Blue / Terrestre / White)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fundo:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                FilterChip(
                    selected = effectiveBgTheme == 0,
                    onClick = { setBgTheme(0) },
                    label = { Text("Black & Blue", fontSize = 10.sp, fontWeight = if (effectiveBgTheme == 0) FontWeight.Bold else FontWeight.Normal) },
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
                    modifier = Modifier.testTag("chip_state_theme_black_blue")
                )
                FilterChip(
                    selected = effectiveBgTheme == 1,
                    onClick = { setBgTheme(1) },
                    label = { Text("Terrestre 🌍", fontSize = 10.sp, fontWeight = if (effectiveBgTheme == 1) FontWeight.Bold else FontWeight.Normal) },
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
                    modifier = Modifier.testTag("chip_state_theme_terrestre")
                )
                FilterChip(
                    selected = effectiveBgTheme == 2,
                    onClick = { setBgTheme(2) },
                    label = { Text("White ⚪", fontSize = 10.sp, fontWeight = if (effectiveBgTheme == 2) FontWeight.Bold else FontWeight.Normal) },
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
                    modifier = Modifier.testTag("chip_state_theme_white")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val mapHeight = when (effectiveMapFormat) {
                0 -> 340.dp
                1 -> 380.dp
                else -> 420.dp
            }

            // Canvas Map Box with Safe Touch Zoom and Pan
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mapHeight)
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(14.dp)
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .clipToBounds()
                    .background(
                        when (effectiveBgTheme) {
                            1 -> Color(0xFF0C2417) // Terrestre
                            2 -> Color(0xFFF1F5F9) // White
                            else -> if (effectiveMapFormat == 1) Color(0xFF0B1726) else if (effectiveMapFormat == 2) Color(0xFF030B14) else Color(0xFF070F1E)
                        },
                        RoundedCornerShape(14.dp)
                    )
                    .border(
                        1.dp,
                        when (effectiveBgTheme) {
                            1 -> Color(0xFF166534).copy(alpha = 0.85f)
                            2 -> Color(0xFF94A3B8).copy(alpha = 0.85f)
                            else -> if (effectiveMapFormat == 1) Color(0xFF10B981).copy(alpha = 0.5f) else if (effectiveMapFormat == 2) Color(0xFF00E5FF).copy(alpha = 0.5f) else Color(0xFF1E293B)
                        },
                        RoundedCornerShape(14.dp)
                    )
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            clip = true
                            shape = RoundedCornerShape(14.dp)
                        }
                        .clipToBounds()
                        .onSizeChanged { size ->
                            canvasWidth = size.width.toFloat()
                            canvasHeight = size.height.toFloat()
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newZoom = (zoomScale * zoom).coerceIn(0.9f, 5.0f)
                                zoomScale = newZoom
                                panOffsetX += pan.x
                                panOffsetY += pan.y
                            }
                        }
                        .testTag("canvas_state_precipitation_map")
                ) {
                    clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
                        val w = size.width
                        val h = size.height

                        // Base Canvas / Ocean / Outer Atmosphere according to background theme
                        when (effectiveBgTheme) {
                            0 -> { // Black & Blue
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFF081C33), Color(0xFF030A14)),
                                        center = Offset(w * 0.45f, h * 0.45f),
                                        radius = (w * 0.9f).coerceAtLeast(300f)
                                    ),
                                    size = Size(w, h)
                                )
                            }
                            1 -> { // Terrestre (Oceano Atlântico em gradiente marinho real)
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xFF0D253A), Color(0xFF143B5C), Color(0xFF0E2233)),
                                        startY = 0f,
                                        endY = h
                                    ),
                                    size = Size(w, h)
                                )
                            }
                            2 -> { // White (Céu e oceano em azul cartográfico suave)
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xFFE2F0FD), Color(0xFFD6E9FA)),
                                        startY = 0f,
                                        endY = h
                                    ),
                                    size = Size(w, h)
                                )
                            }
                        }

                    // 1. Draw São Paulo state border approximation
                    val borderPoints = listOf(
                        Pair(-20.0, -50.9), Pair(-19.8, -49.5), Pair(-20.2, -47.4),
                        Pair(-20.8, -47.1), Pair(-21.8, -46.5), Pair(-22.4, -46.3),
                        Pair(-22.5, -45.0), Pair(-23.4, -44.7), Pair(-23.8, -45.4),
                        Pair(-24.0, -46.3), Pair(-24.7, -47.5), Pair(-25.2, -48.1),
                        Pair(-24.8, -48.9), Pair(-24.3, -49.8), Pair(-23.1, -50.1),
                        Pair(-22.6, -51.5), Pair(-22.4, -52.8), Pair(-21.6, -52.2),
                        Pair(-20.8, -51.5), Pair(-20.0, -50.9)
                    )

                    val borderPath = Path()
                    borderPoints.forEachIndexed { i, (lat, lon) ->
                        val x = mapLonToX(lon, w, zoomScale, panOffsetX)
                        val y = mapLatToY(lat, h, zoomScale, panOffsetY)
                        if (i == 0) borderPath.moveTo(x, y) else borderPath.lineTo(x, y)
                    }
                    borderPath.close()

                    // Fill state background according to selected theme
                    when (effectiveBgTheme) {
                        0 -> { // Black & Blue
                            drawPath(
                                path = borderPath,
                                brush = Brush.radialGradient(
                                    colors = if (effectiveMapFormat == 1) listOf(Color(0xFF132A3E), Color(0xFF091624)) else listOf(Color(0xFF0F223D), Color(0xFF081426)),
                                    center = Offset(w / 2f, h / 2f),
                                    radius = w * 0.7f * zoomScale
                                )
                            )
                        }
                        1 -> { // Terrestre (Relevo / Vegetação natural)
                            drawPath(
                                path = borderPath,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF4D6328), // Noroeste
                                        Color(0xFF5A6630), // Centro / Planalto
                                        Color(0xFF386641), // Leste / Vale do Paraíba
                                        Color(0xFF265330)  // Litoral / Serra do Mar verde exuberante
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset(w, h)
                                )
                            )
                        }
                        2 -> { // White (Papel cartográfico puro)
                            drawPath(
                                path = borderPath,
                                color = Color.White
                            )
                        }
                    }

                    // Draw state outline
                    val outlineColor = when (effectiveBgTheme) {
                        1 -> Color(0xFFFEF08A) // Terrestre (ivory/dourado nítido)
                        2 -> Color(0xFF0F172A) // White (charcoal escuro nítido)
                        else -> if (effectiveMapFormat == 1) Color(0xFF10B981) else Color(0xFF38BDF8)
                    }
                    drawPath(
                        path = borderPath,
                        color = outlineColor.copy(alpha = if (effectiveBgTheme == 2) 0.95f else 0.85f),
                        style = Stroke(width = if (effectiveBgTheme == 2) 2.2f else 1.8f)
                    )

                    // Se formato Topográfico: desenha rios e rodovias principais
                    if (effectiveMapFormat == 1 || effectiveBgTheme == 1) {
                        val riverColor = when (effectiveBgTheme) {
                            1 -> Color(0xFF38BDF8)
                            2 -> Color(0xFF0284C7)
                            else -> Color(0xFF00E5FF)
                        }
                        val roadColor = when (effectiveBgTheme) {
                            1 -> Color(0xFFFBBF24)
                            2 -> Color(0xFFD97706)
                            else -> Color(0xFFF59E0B)
                        }
                        // Rio Tietê
                        val tietePath = Path().apply {
                            val tpts = listOf(Pair(-23.53, -46.0), Pair(-23.1, -47.4), Pair(-22.6, -48.5), Pair(-21.7, -49.8), Pair(-20.7, -51.3))
                            tpts.forEachIndexed { i, (lat, lon) ->
                                val x = mapLonToX(lon, w, zoomScale, panOffsetX)
                                val y = mapLatToY(lat, h, zoomScale, panOffsetY)
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                        }
                        drawPath(tietePath, color = riverColor.copy(alpha = 0.75f), style = Stroke(width = 2.0f * zoomScale.coerceIn(0.8f, 2.0f)))

                        // Rodovia Castelo Branco (SP-280)
                        val sp280Path = Path().apply {
                            val rpts = listOf(Pair(-23.53, -46.7), Pair(-23.45, -47.4), Pair(-22.95, -48.4), Pair(-22.88, -49.3), Pair(-22.95, -49.8))
                            rpts.forEachIndexed { i, (lat, lon) ->
                                val x = mapLonToX(lon, w, zoomScale, panOffsetX)
                                val y = mapLatToY(lat, h, zoomScale, panOffsetY)
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                        }
                        drawPath(sp280Path, color = roadColor.copy(alpha = 0.65f), style = Stroke(width = 1.5f * zoomScale.coerceIn(0.8f, 2.0f)))
                    }

                    // Se formato Radar Doppler: desenha anéis Doppler Bauru e Prudente
                    if (effectiveMapFormat == 2) {
                        val bauruX = mapLonToX(-49.0587, w, zoomScale, panOffsetX)
                        val bauruY = mapLatToY(-22.3145, h, zoomScale, panOffsetY)
                        listOf(w * 0.15f * zoomScale, w * 0.30f * zoomScale).forEach { r ->
                            drawCircle(
                                color = Color(0xFF00E5FF).copy(alpha = 0.4f),
                                radius = r,
                                center = Offset(bauruX, bauruY),
                                style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f))
                            )
                        }
                    }

                    // 2. Draw Heatmaps / Precipitation plumes around stations
                    stations.forEach { st ->
                        val cx = mapLonToX(st.lon, w, zoomScale, panOffsetX)
                        val cy = mapLatToY(st.lat, h, zoomScale, panOffsetY)

                        when (activeLayer) {
                            0 -> { // Rain Volume mm
                                val rain = st.rainVolumeMm
                                if (rain > 0.5) {
                                    val radius = (18f + rain.toFloat() * 1.5f) * zoomScale
                                    val color = when {
                                        rain >= 30.0 -> Color(0xFFFF1744) // Vermelho torrencial
                                        rain >= 15.0 -> Color(0xFFFF9100) // Laranja forte
                                        rain >= 5.0 -> Color(0xFF00E5FF)  // Ciano moderado
                                        else -> Color(0xFF0284C7)         // Azul leve
                                    }
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.0f)),
                                            center = Offset(cx, cy),
                                            radius = radius
                                        ),
                                        radius = radius,
                                        center = Offset(cx, cy)
                                    )
                                }
                            }
                            1 -> { // Temperature field
                                val temp = st.currentTemp
                                val radius = 32f * zoomScale
                                val tempColor = when {
                                    temp >= 32.0 -> Color(0xFFEF4444)
                                    temp >= 26.0 -> Color(0xFFF59E0B)
                                    temp >= 20.0 -> Color(0xFF10B981)
                                    else -> Color(0xFF38BDF8)
                                }
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(tempColor.copy(alpha = 0.35f), tempColor.copy(alpha = 0.0f)),
                                        center = Offset(cx, cy),
                                        radius = radius
                                    ),
                                    radius = radius,
                                    center = Offset(cx, cy)
                                )
                            }
                            2 -> { // Risk & Rain Probability
                                val prob = st.rainProbability
                                if (prob >= 40) {
                                    val radius = (15f + prob * 0.3f) * zoomScale
                                    val color = if (prob >= 70) Color(0xFFEC4899) else Color(0xFF8B5CF6)
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(color.copy(alpha = 0.4f), color.copy(alpha = 0.0f)),
                                            center = Offset(cx, cy),
                                            radius = radius
                                        ),
                                        radius = radius,
                                        center = Offset(cx, cy)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Draw Station Pinpoints and Badges
                    stations.forEach { st ->
                        val cx = mapLonToX(st.lon, w, zoomScale, panOffsetX)
                        val cy = mapLatToY(st.lat, h, zoomScale, panOffsetY)
                        val isSelected = selectedStation?.id == st.id

                        val pinColor = when (activeLayer) {
                            0 -> if (st.rainVolumeMm >= 15.0) Color(0xFFFF5252) else Color(0xFF38BDF8)
                            1 -> if (st.currentTemp >= 30.0) Color(0xFFF97316) else Color(0xFF34D399)
                            2 -> if (st.rainProbability >= 65) Color(0xFFA855F7) else Color(0xFF38BDF8)
                            else -> Color(0xFF10B981) // CIIAGRO Agrometeorological Red
                        }

                        // Outer ring if selected
                        if (isSelected) {
                            drawCircle(
                                color = Color.White,
                                radius = 10f * zoomScale,
                                center = Offset(cx, cy),
                                style = Stroke(width = 2.5f)
                            )
                        }

                        // Station Dot
                        drawCircle(
                            color = if (isSelected) Color.White else pinColor,
                            radius = if (isSelected) 6f * zoomScale else 4.5f * zoomScale,
                            center = Offset(cx, cy)
                        )

                        // Data Value Pill & City Name
                        val metricText = when (activeLayer) {
                            0 -> "${st.rainVolumeMm}mm"
                            1 -> "${st.currentTemp.toInt()}°C"
                            2 -> "${st.rainProbability}%"
                            else -> "CIIAGRO ${st.humidity}% UR"
                        }

                        // High-contrast background pill for city labels and data
                        val pillBgColor = when (effectiveBgTheme) {
                            2 -> Color(0xFFFFFFFF).copy(alpha = 0.94f)
                            1 -> Color(0xFF0C2417).copy(alpha = 0.92f)
                            else -> Color(0xFF0A192F).copy(alpha = 0.88f)
                        }
                        val pillBorderColor = when (effectiveBgTheme) {
                            2 -> if (isSelected) Color(0xFF0F172A) else Color(0xFF94A3B8)
                            1 -> if (isSelected) Color(0xFF86EFAC) else Color(0xFF4ADE80).copy(alpha = 0.5f)
                            else -> if (isSelected) Color.White else pinColor
                        }
                        val cityTextColor = when (effectiveBgTheme) {
                            2 -> if (isSelected) Color(0xFF0F172A) else Color(0xFF1E293B)
                            1 -> if (isSelected) Color.White else Color(0xFFF0FDF4)
                            else -> if (isSelected) Color.White else Color(0xFFE2E8F0)
                        }

                        val cityName = st.name.take(12)
                        val textMeasure = textMeasurer.measure(
                            text = "$cityName  $metricText",
                            style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        )
                        val pLeft = cx + 6f * zoomScale
                        val pTop = cy - 15f * zoomScale
                        val pWidth = textMeasure.size.width.toFloat() + 8f
                        val pHeight = 26f * zoomScale.coerceIn(0.9f, 1.3f)

                        drawRoundRect(
                            color = pillBgColor,
                            topLeft = Offset(pLeft, pTop),
                            size = Size(pWidth, pHeight),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                        drawRoundRect(
                            color = pillBorderColor.copy(alpha = 0.7f),
                            topLeft = Offset(pLeft, pTop),
                            size = Size(pWidth, pHeight),
                            cornerRadius = CornerRadius(4f, 4f),
                            style = Stroke(width = 0.8f)
                        )

                        // Draw City Label
                        safeDrawText(
                            textMeasurer = textMeasurer,
                            text = cityName,
                            topLeft = Offset(pLeft + 3f, pTop + 2f),
                            style = TextStyle(
                                color = cityTextColor,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )

                        // Draw Metric Label
                        safeDrawText(
                            textMeasurer = textMeasurer,
                            text = metricText,
                            topLeft = Offset(pLeft + 3f, pTop + 13f),
                            style = TextStyle(
                                color = if (effectiveBgTheme == 2 && pinColor == Color(0xFF38BDF8)) Color(0xFF0284C7) else pinColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    } // close clipRect
                }

                // Zoom and Reset Controls Floating Overlay (Top-Right)
                val zoomControlsBg = when (effectiveBgTheme) {
                    2 -> Color(0xFFFFFFFF).copy(alpha = 0.92f)
                    1 -> Color(0xFF0C2417).copy(alpha = 0.92f)
                    else -> Color(0xFF0F172A).copy(alpha = 0.85f)
                }
                val zoomControlsBorder = when (effectiveBgTheme) {
                    2 -> Color(0xFFCBD5E1)
                    1 -> Color(0xFF166534)
                    else -> Color(0xFF334155)
                }
                val zoomIconColor = when (effectiveBgTheme) {
                    2 -> Color(0xFF0F172A)
                    1 -> Color(0xFF86EFAC)
                    else -> Color.White
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(zoomControlsBg, RoundedCornerShape(10.dp))
                        .border(1.dp, zoomControlsBorder, RoundedCornerShape(10.dp))
                ) {
                    IconButton(
                        onClick = { applyZoomToExactLocation(zoomScale * 1.3f) },
                        modifier = Modifier.size(32.dp).testTag("btn_zoom_in_state_map")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In (Foco Exato)", tint = zoomIconColor, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { applyZoomToExactLocation(zoomScale / 1.3f) },
                        modifier = Modifier.size(32.dp).testTag("btn_zoom_out_state_map")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out (Foco Exato)", tint = zoomIconColor, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            zoomScale = 1.0f
                            panOffsetX = 0f
                            panOffsetY = 0f
                        },
                        modifier = Modifier.size(32.dp).testTag("btn_reset_state_map")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset", tint = if (effectiveBgTheme == 2) Color(0xFF0284C7) else Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    }
                    userCoordinates?.let {
                        IconButton(
                            onClick = { applyZoomToExactLocation(3.0f) },
                            modifier = Modifier.size(32.dp).testTag("btn_center_user_state_map")
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = "Focar Meu Local", tint = if (effectiveBgTheme == 2) Color(0xFF0284C7) else Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Legend Badge Overlay (Bottom-Left)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0B132B).copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    when (activeLayer) {
                                        0 -> Color(0xFF00E5FF)
                                        1 -> Color(0xFFF97316)
                                        else -> Color(0xFFA855F7)
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (activeLayer) {
                                0 -> "Precipitação Acumulada em Tempo Real"
                                1 -> "Isotermas de Superfície"
                                else -> "Probabilidade de Chuva e Descargas"
                            },
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Station Focus Carousel Below Map
            Text(
                text = "Toque em um polo regional para centrar o mapa:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(stations) { st ->
                    val isSel = selectedStation?.id == st.id
                    FilterChip(
                        selected = isSel,
                        onClick = { onStationSelected(st) },
                        label = { Text("${st.name} (${st.rainVolumeMm} mm)") },
                        leadingIcon = {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(13.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
}
