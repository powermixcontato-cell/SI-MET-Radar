package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import kotlinx.coroutines.isActive
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WindPower
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.WeatherStationEntity
import com.example.util.safeDrawText
import com.example.viewmodel.StormCellTrajectory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// SP Coordinate Bounds:
// Lat: ~ -19.7 (North) to -25.3 (South) -> Span ~ 5.6
// Lon: ~ -53.1 (West) to -44.2 (East) -> Span ~ 8.9
private const val MIN_LON = -53.2
private const val MAX_LON = -44.0
private const val MIN_LAT = -25.2
private const val MAX_LAT = -19.8

data class WindyStreamParticle(
    var x: Float,
    var y: Float,
    var speed: Float,
    var age: Int,
    var maxLife: Int,
    var prevX: Float = x,
    var prevY: Float = y
)

@Composable
fun IpmetRadarCanvas(
    stations: List<WeatherStationEntity>,
    activeCenter: String, // "bauru" or "presidente_prudente"
    timeStep: Int,
    isPlaying: Boolean,
    isEnergySaver: Boolean,
    selectedStation: WeatherStationEntity?,
    stormCells: List<StormCellTrajectory> = emptyList(),
    showTrajectories: Boolean = true,
    selectedStormCell: StormCellTrajectory? = null,
    userCoordinates: Pair<Double, Double>? = null,
    onStationSelected: (WeatherStationEntity) -> Unit,
    onCenterChanged: (String) -> Unit,
    onTimeStepChanged: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onSelectStormCell: (StormCellTrajectory?) -> Unit = {},
    onToggleTrajectories: () -> Unit = {},
    onOpenExpandedMap: () -> Unit = {},
    isExpandedMode: Boolean = false,
    mapFormat: Int = 0,
    onMapFormatChanged: (Int) -> Unit = {},
    mapBackgroundTheme: Int = 0,
    onMapBackgroundThemeChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Dynamic measured canvas dimensions for accurate geographic projection and centering
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    // Map format state: 0 = Windy Vento & Chuva, 1 = Circular PPI 360°, 2 = Cartográfico Topo/Bacias
    var currentFormat by remember(mapFormat) { mutableIntStateOf(mapFormat) }
    val effectiveMapFormat = currentFormat
    val setFormat: (Int) -> Unit = {
        currentFormat = it
        onMapFormatChanged(it)
    }

    // Map Background Theme state: 0 = Black & Blue, 1 = Terrestre, 2 = White
    var currentBgTheme by remember(mapBackgroundTheme) { mutableIntStateOf(mapBackgroundTheme) }
    val effectiveBgTheme = currentBgTheme
    val setBgTheme: (Int) -> Unit = {
        currentBgTheme = it
        onMapBackgroundThemeChanged(it)
    }

    // Windy layer mode: 0 = Vento Fluído (Streamlines), 1 = Radar Doppler (Chuva), 2 = Temperatura (°C)
    var windyLayer by remember { mutableIntStateOf(0) }

    // Windy Sonde on tap
    var sondeOffset by remember { mutableStateOf<Offset?>(null) }
    var sondeStation by remember { mutableStateOf<WeatherStationEntity?>(null) }
    var sondeCoordinates by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Zoom & Pan state for interactive radar map (controlled via GPS, search & buttons)
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    // Windy Wind Particle System (140 dynamic particles)
    val windParticles = remember {
        val rng = java.util.Random(1337)
        List(140) {
            val px = rng.nextFloat() * 1200f
            val py = rng.nextFloat() * 900f
            val spd = 2.0f + rng.nextFloat() * 3.5f
            val maxL = 35 + rng.nextInt(55)
            WindyStreamParticle(px, py, spd, rng.nextInt(maxL), maxL, px, py)
        }
    }

    var particleTick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(isPlaying, isEnergySaver) {
        if (!isEnergySaver) {
            val rng = java.util.Random()
            while (isActive) {
                androidx.compose.runtime.withFrameNanos {
                    val w = if (canvasWidth > 50f) canvasWidth else 800f
                    val h = if (canvasHeight > 50f) canvasHeight else 600f
                    windParticles.forEach { p ->
                        p.prevX = p.x
                        p.prevY = p.y
                        val angleRad = 2.85f + (p.y / h - 0.5f) * 0.35f
                        val effectiveSpd = p.speed * (if (isPlaying) 1.25f else 0.45f) * zoomScale.coerceIn(0.8f, 2.5f)
                        p.x += kotlin.math.cos(angleRad) * effectiveSpd
                        p.y += kotlin.math.sin(angleRad) * effectiveSpd
                        p.age++
                        if (p.x < -30f || p.y < -30f || p.x > w + 30f || p.y > h + 30f || p.age > p.maxLife) {
                            p.x = w + rng.nextFloat() * 40f
                            p.y = rng.nextFloat() * h
                            p.prevX = p.x
                            p.prevY = p.y
                            p.age = 0
                            p.speed = 2.0f + rng.nextFloat() * 3.5f
                            p.maxLife = 35 + rng.nextInt(55)
                        }
                    }
                    particleTick++
                }
            }
        }
    }

    // Live clock ticking every second to track real-time models and radar steps
    var liveTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            liveTimeMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }

    // Helper: Exact pan coordinates to place any lat/lon exactly in the center of the canvas
    val centerOnCoordinates: (Double, Double, Float) -> Unit = { targetLat, targetLon, targetZoom ->
        val w = if (canvasWidth > 50f) canvasWidth else 800f
        val h = if (canvasHeight > 50f) canvasHeight else 600f
        val normX = ((targetLon - MIN_LON) / (MAX_LON - MIN_LON)).coerceIn(0.0, 1.0)
        val normY = ((MAX_LAT - targetLat) / (MAX_LAT - MIN_LAT)).coerceIn(0.0, 1.0)
        val baseX = (normX * (w - 60f) + 30f).toFloat()
        val baseY = (normY * (h - 60f) + 30f).toFloat()
        zoomScale = targetZoom
        panOffsetX = (w / 2f - baseX) * targetZoom
        panOffsetY = (h / 2f - baseY) * targetZoom
    }

    // Automatic focus & zoom when a station is selected or searched
    androidx.compose.runtime.LaunchedEffect(selectedStation?.id, canvasWidth) {
        selectedStation?.let { st ->
            centerOnCoordinates(st.lat, st.lon, 2.4f)
        }
    }

    // Automatic focus & zoom when user coordinates (GPS) are updated
    androidx.compose.runtime.LaunchedEffect(userCoordinates, canvasWidth) {
        userCoordinates?.let { (uLat, uLon) ->
            centerOnCoordinates(uLat, uLon, 2.6f)
        }
    }

    // Function to zoom while strictly anchoring to the EXACT user location (or selected station) instead of a fixed point
    val applyZoomToExactLocation: (Float) -> Unit = { newZoom ->
        val clampedZoom = newZoom.coerceIn(0.8f, 6.0f)
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

    // Infinite transition for sweep rotation
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isEnergySaver) 12000 else 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    val textMeasurer = rememberTextMeasurer()
    val scrollState = androidx.compose.foundation.rememberScrollState()

    Column(
        modifier = if (isExpandedMode) {
            modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .background(MaterialTheme.colorScheme.background)
        } else {
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        }
    ) {
        // Top Toolbar: Center Switch & Status & Trajectory Toggle & Fullscreen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = activeCenter == "bauru",
                    onClick = { onCenterChanged("bauru") },
                    label = { Text("Bauru", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("chip_radar_bauru")
                )
                FilterChip(
                    selected = activeCenter == "presidente_prudente",
                    onClick = { onCenterChanged("presidente_prudente") },
                    label = { Text("P. Prudente", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("chip_radar_prudente")
                )

                FilterChip(
                    selected = showTrajectories,
                    onClick = onToggleTrajectories,
                    label = { Text("Trajetórias", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = null,
                            tint = if (showTrajectories) MaterialTheme.colorScheme.onPrimary else Color(0xFF00E5FF),
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("chip_toggle_trajectories")
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF00E676).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF00E676), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "AO VIVO",
                            color = Color(0xFF00E676),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!isExpandedMode) {
                    IconButton(
                        onClick = onOpenExpandedMap,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(32.dp)
                            .testTag("button_open_expanded_radar")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Abrir Mapa Expandido",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Map Format Selector Row (Panorâmico 16:9 / Circular PPI 360° / Cartográfico)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Formato:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            FilterChip(
                selected = effectiveMapFormat == 0,
                onClick = { setFormat(0) },
                label = { Text("Windy Vento & Chuva", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(Icons.Default.WindPower, contentDescription = null, modifier = Modifier.size(13.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF0284C7),
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.testTag("chip_format_windy")
            )
            FilterChip(
                selected = effectiveMapFormat == 1,
                onClick = { setFormat(1) },
                label = { Text("Circular PPI 360°", fontSize = 11.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(13.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF00E5FF),
                    selectedLabelColor = Color.Black
                ),
                modifier = Modifier.testTag("chip_format_circular")
            )
            FilterChip(
                selected = effectiveMapFormat == 2,
                onClick = { setFormat(2) },
                label = { Text("Topográfico SP", fontSize = 11.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(13.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF10B981),
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.testTag("chip_format_cartographic")
            )
        }

        // Map Background Theme Selector Row: Black & Blue / Terrestre / White
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Fundo:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
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
                modifier = Modifier.testTag("chip_theme_black_blue")
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
                modifier = Modifier.testTag("chip_theme_terrestre")
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
                modifier = Modifier.testTag("chip_theme_white")
            )
        }

        val boxHeight = when (effectiveMapFormat) {
            0 -> if (isExpandedMode) 440.dp else 350.dp
            1 -> if (isExpandedMode) 370.dp else 330.dp
            else -> if (isExpandedMode) 400.dp else 330.dp
        }
        val isCircularScope = effectiveMapFormat == 1
        val boxShape = if (isCircularScope) CircleShape else RoundedCornerShape(16.dp)

        // Radar Interactive Canvas Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(boxHeight)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = (if (isCircularScope) Modifier.size(boxHeight) else Modifier.fillMaxSize())
                    .clip(boxShape)
                    .clipToBounds()
                    .background(
                        color = if (isEnergySaver) Color.Black else when (effectiveBgTheme) {
                            1 -> Color(0xFF0C2417) // Terrestre
                            2 -> Color(0xFFF1F5F9) // White
                            else -> if (effectiveMapFormat == 1) Color(0xFF030A14) else if (effectiveMapFormat == 2) Color(0xFF0B1726) else Color(0xFF070F1E)
                        },
                        shape = boxShape
                    )
                    .border(
                        width = if (effectiveMapFormat == 1) 2.5.dp else 1.dp,
                        color = when (effectiveBgTheme) {
                            1 -> Color(0xFF166534).copy(alpha = 0.85f)
                            2 -> Color(0xFF94A3B8).copy(alpha = 0.85f)
                            else -> if (effectiveMapFormat == 1) Color(0xFF00E5FF).copy(alpha = 0.85f) else if (effectiveMapFormat == 2) Color(0xFF10B981).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        },
                        shape = boxShape
                    )
            ) {
            val radarCenterLat = if (activeCenter == "bauru") -22.3145 else -22.1256
            val radarCenterLon = if (activeCenter == "bauru") -49.0587 else -51.3889
            val effectiveSweep = if (isPlaying) sweepAngle else 45f

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(boxShape)
                    .clipToBounds()
                    .onSizeChanged { size ->
                        canvasWidth = size.width.toFloat()
                        canvasHeight = size.height.toFloat()
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { tapOffset ->
                            val w = canvasWidth.takeIf { it > 50f } ?: 800f
                            val h = canvasHeight.takeIf { it > 50f } ?: 600f
                            val normX = ((tapOffset.x - panOffsetX) / (w * zoomScale)).coerceIn(0f, 1f)
                            val normY = ((tapOffset.y - panOffsetY) / (h * zoomScale)).coerceIn(0f, 1f)
                            val tapLon = MIN_LON + normX * (MAX_LON - MIN_LON)
                            val tapLat = MAX_LAT - normY * (MAX_LAT - MIN_LAT)
                            val closest = stations.minByOrNull { st ->
                                val dLat = st.lat - tapLat
                                val dLon = st.lon - tapLon
                                dLat * dLat + dLon * dLon
                            }
                            sondeOffset = tapOffset
                            sondeStation = closest
                            sondeCoordinates = Pair(tapLat, tapLon)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newZoom = (zoomScale * zoom).coerceIn(0.8f, 6.0f)
                            zoomScale = newZoom
                            panOffsetX += pan.x
                            panOffsetY += pan.y
                        }
                    }
                    .testTag("radar_interactive_canvas")
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

                // Formato Cartográfico: Desenha malha viária, rios e coordenadas
                if (effectiveMapFormat == 2) {
                    drawCartographicBasemap(w, h, zoomScale, panOffsetX, panOffsetY, textMeasurer, effectiveBgTheme)
                }

                // Formato 0 (Windy): Camada Térmica se selecionada
                if (effectiveMapFormat == 0 && windyLayer == 2) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x33FF5722),
                                Color(0x33FF9800),
                                Color(0x224CAF50),
                                Color(0x2203A9F4)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        ),
                        size = Size(w, h)
                    )
                }

                // 1. Draw São Paulo state border and counties grid (IPMet Style)
                drawSpBoundary(w, h, zoomScale, panOffsetX, panOffsetY, effectiveBgTheme, textMeasurer)

                // 2. Windy Wind Streamlines Particle System
                if (effectiveMapFormat == 0 && windyLayer != 1) {
                    drawWindyWindParticles(windParticles, w, h, effectiveBgTheme)
                }

                // 3. Draw Doppler Radar Range Rings & Azimuth Crosshairs (Authentic IPMet 60/120/240/360/480 km)
                val centerX = mapLonToX(radarCenterLon, w, zoomScale, panOffsetX)
                val centerY = mapLatToY(radarCenterLat, h, zoomScale, panOffsetY)

                // Formato Circular PPI 360°: Mostrador com graduação azimutal
                if (effectiveMapFormat == 1) {
                    drawCircularPpiScope(w, h, textMeasurer, centerX, centerY, effectiveSweep, isPlaying)
                }

                val ringColors = when (effectiveBgTheme) {
                    1 -> Color(0xFFFEF08A).copy(alpha = 0.70f)
                    2 -> Color(0xFF475569).copy(alpha = 0.80f)
                    else -> Color(0xFF1E3A5F).copy(alpha = 0.85f)
                }
                val ringRadii = listOf(w * 0.12f * zoomScale, w * 0.24f * zoomScale, w * 0.38f * zoomScale, w * 0.52f * zoomScale)
                val ringLabels = listOf("60 km", "120 km", "240 km", "360 km")
                val ringTextColor = when (effectiveBgTheme) {
                    1 -> Color(0xFFFEF9C3)
                    2 -> Color(0xFF0F172A)
                    else -> Color(0xFF94A3B8)
                }

                ringRadii.forEachIndexed { idx, r ->
                    drawCircle(
                        color = ringColors,
                        radius = r,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1.3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f))
                    )
                    // Ring labels with high-contrast background
                    safeDrawText(
                        textMeasurer = textMeasurer,
                        text = ringLabels[idx],
                        topLeft = Offset(centerX + r + 4f, centerY - 14f),
                        style = TextStyle(color = ringTextColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    )
                }

                // Azimuth Radians / Crosshairs (IPMet standard N-S / W-E / NE-SW / NW-SE)
                drawLine(
                    color = ringColors,
                    start = Offset(centerX - w * 0.54f * zoomScale, centerY),
                    end = Offset(centerX + w * 0.54f * zoomScale, centerY),
                    strokeWidth = 1.2f
                )
                drawLine(
                    color = ringColors,
                    start = Offset(centerX, centerY - h * 0.54f * zoomScale),
                    end = Offset(centerX, centerY + h * 0.54f * zoomScale),
                    strokeWidth = 1.2f
                )

                // 3. Draw Simulated Reflectivity Rain Clusters & Storm Cell Trajectories
                drawRadarReflectivityClusters(
                    w = w,
                    h = h,
                    timeStep = timeStep,
                    isEnergySaver = isEnergySaver,
                    stormCells = stormCells,
                    showTrajectories = showTrajectories,
                    selectedStormCell = selectedStormCell,
                    textMeasurer = textMeasurer,
                    zoom = zoomScale,
                    panX = panOffsetX,
                    panY = panOffsetY
                )

                // 4. Draw Rotating Doppler Radar Sweep Beam (if not paused or energy saving disabled)
                if (isPlaying || !isEnergySaver) {
                    val sweepRad = Math.toRadians(effectiveSweep.toDouble())
                    val sweepLen = w * 0.54f * zoomScale
                    val sweepEndX = centerX + (cos(sweepRad) * sweepLen).toFloat()
                    val sweepEndY = centerY + (sin(sweepRad) * sweepLen).toFloat()

                    // Glowing sweep line
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF00E5FF), Color(0x0000E5FF)),
                            start = Offset(centerX, centerY),
                            end = Offset(sweepEndX, sweepEndY)
                        ),
                        start = Offset(centerX, centerY),
                        end = Offset(sweepEndX, sweepEndY),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )

                    // Sweep cone wedge
                    val sweepPath = Path().apply {
                        moveTo(centerX, centerY)
                        lineTo(sweepEndX, sweepEndY)
                        val prevRad = Math.toRadians((effectiveSweep - 35.0).coerceAtLeast(0.0))
                        val prevX = centerX + (cos(prevRad) * sweepLen).toFloat()
                        val prevY = centerY + (sin(prevRad) * sweepLen).toFloat()
                        lineTo(prevX, prevY)
                        close()
                    }
                    drawPath(
                        path = sweepPath,
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x3300E5FF), Color(0x0000E5FF)),
                            center = Offset(centerX, centerY),
                            radius = sweepLen
                        )
                    )
                }

                // 5. Draw Radar Dish Origin Pulse Point
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = 5f,
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.5f,
                    center = Offset(centerX, centerY)
                )

                // 6. Draw City Stations Pins (Melhoria de visualização e destaque)
                stations.forEach { st ->
                    val sx = mapLonToX(st.lon, w, zoomScale, panOffsetX)
                    val sy = mapLatToY(st.lat, h, zoomScale, panOffsetY)
                    val isCurrent = st.id == selectedStation?.id
                    val isBarretos = st.id == "barretos" || st.name.contains("Barretos", ignoreCase = true)

                    // Station Dot & Alert Colors
                    val pinColor = when {
                        st.dbzReflectivity >= 50 -> Color(0xFFFF1744) // Severe (Vermelho)
                        st.dbzReflectivity >= 40 -> Color(0xFFFF9100) // Moderate (Laranja)
                        st.dbzReflectivity >= 25 -> Color(0xFFFFD600) // Light (Amarelo)
                        isBarretos -> Color(0xFF38BDF8)
                        else -> Color(0xFF00E5FF)
                    }

                    // Outer halo if current or Barretos
                    if (isCurrent || isBarretos) {
                        drawCircle(
                            color = (if (isBarretos) Color(0xFF38BDF8) else pinColor).copy(alpha = 0.35f),
                            radius = (if (isBarretos) 14f else 11f) * zoomScale.coerceIn(0.9f, 1.8f),
                            center = Offset(sx, sy)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = if (isBarretos) 6.5f else 5.5f,
                            center = Offset(sx, sy)
                        )
                    }

                    // Station Center Pin
                    drawCircle(
                        color = pinColor,
                        radius = if (isCurrent || isBarretos) 5f else 3.8f,
                        center = Offset(sx, sy)
                    )

                    // High-contrast background pill for city labels (crucial for map readability)
                    val labelText = if (isBarretos) "★ Barretos ${st.currentTemp.toInt()}°" else "${st.name} ${st.currentTemp.toInt()}°"
                    val textLayout = textMeasurer.measure(
                        text = labelText,
                        style = TextStyle(
                            fontSize = if (isCurrent || isBarretos) 10.sp else 9.sp,
                            fontWeight = if (isCurrent || isBarretos) FontWeight.Bold else FontWeight.Medium
                        )
                    )

                    val pillLeft = sx + 7f
                    val pillTop = sy - 14f
                    val pillWidth = textLayout.size.width + 10f
                    val pillHeight = textLayout.size.height + 4f

                    // High-contrast background pill for city labels
                    val pillBgColor = when (effectiveBgTheme) {
                        2 -> Color(0xFFFFFFFF).copy(alpha = 0.94f)
                        1 -> Color(0xFF0C2417).copy(alpha = 0.92f)
                        else -> Color(0xFF0A192F).copy(alpha = 0.88f)
                    }
                    val pillBorderColor = when (effectiveBgTheme) {
                        2 -> if (isBarretos) Color(0xFF0284C7) else if (isCurrent) Color(0xFF0F172A) else Color(0xFF94A3B8)
                        1 -> if (isBarretos) Color(0xFF38BDF8) else if (isCurrent) Color(0xFF86EFAC) else Color(0xFF4ADE80).copy(alpha = 0.5f)
                        else -> if (isBarretos) Color(0xFF38BDF8) else pinColor
                    }
                    val cityTextColor = when (effectiveBgTheme) {
                        2 -> if (isBarretos) Color(0xFF0284C7) else if (isCurrent) Color(0xFF0F172A) else Color(0xFF1E293B)
                        1 -> if (isBarretos) Color(0xFF7DD3FC) else if (isCurrent) Color.White else Color(0xFFF0FDF4)
                        else -> if (isBarretos) Color(0xFF38BDF8) else if (isCurrent) Color.White else Color(0xFFE2E8F0)
                    }

                    // Draw contrast pill behind text
                    drawRoundRect(
                        color = pillBgColor,
                        topLeft = Offset(pillLeft - 4f, pillTop - 2f),
                        size = androidx.compose.ui.geometry.Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawRoundRect(
                        color = pillBorderColor,
                        topLeft = Offset(pillLeft - 4f, pillTop - 2f),
                        size = androidx.compose.ui.geometry.Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(4f, 4f),
                        style = Stroke(width = if (isBarretos || isCurrent) 1.2f else 0.8f)
                    )

                    // Draw City Label Text
                    safeDrawText(
                        textMeasurer = textMeasurer,
                        text = labelText,
                        topLeft = Offset(pillLeft, pillTop),
                        style = TextStyle(
                            color = cityTextColor,
                            fontSize = if (isCurrent || isBarretos) 10.sp else 9.sp,
                            fontWeight = if (isCurrent || isBarretos) FontWeight.Bold else FontWeight.Medium
                        )
                    )

                    // Rain Cloud on Map when Raining or High Rain Probability
                    val isRainingHere = st.rainProbability >= 40 || st.rainVolumeMm > 0.0 || st.dbzReflectivity >= 25 || st.iconType == "rain" || st.iconType == "storm"
                    if (isRainingHere) {
                        val isStorm = st.dbzReflectivity >= 45 || st.iconType == "storm"
                        drawRainCloud(
                            cx = sx - 16f,
                            cy = sy - 14f,
                            scale = (if (isCurrent) 1.25f else 0.95f) * zoomScale.coerceIn(0.8f, 1.5f),
                            isThunderstorm = isStorm,
                            rainMm = st.rainVolumeMm
                        )
                    }
                }

                // 7. Draw User GPS Location Pin Marker
                userCoordinates?.let { (uLat, uLon) ->
                    val ux = mapLonToX(uLon, w, zoomScale, panOffsetX)
                    val uy = mapLatToY(uLat, h, zoomScale, panOffsetY)

                    // Outer pulse ring
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = 0.30f),
                        radius = 16f * zoomScale.coerceIn(0.9f, 2f),
                        center = Offset(ux, uy)
                    )
                    // Inner accent ring
                    drawCircle(
                        color = Color(0xFF0284C7),
                        radius = 9f,
                        center = Offset(ux, uy)
                    )
                    // Solid white core
                    drawCircle(
                        color = Color.White,
                        radius = 5.5f,
                        center = Offset(ux, uy)
                    )
                    // Blue center dot
                    drawCircle(
                        color = Color(0xFF0284C7),
                        radius = 2.5f,
                        center = Offset(ux, uy)
                    )

                    // "Sua Localização" Label Tag
                    safeDrawText(
                        textMeasurer = textMeasurer,
                        text = "📍 Você está aqui",
                        topLeft = Offset(ux - 35f, uy + 14f),
                        style = TextStyle(
                            color = Color(0xFF38BDF8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Sonda Windy Target Crosshair on Tap
                sondeOffset?.let { pt ->
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.45f),
                        radius = 16f,
                        center = pt,
                        style = Stroke(width = 1.5f)
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = 3.5f,
                        center = pt
                    )
                    drawLine(
                        color = Color(0xFF00E5FF),
                        start = Offset(pt.x - 20f, pt.y),
                        end = Offset(pt.x + 20f, pt.y),
                        strokeWidth = 1.3f
                    )
                    drawLine(
                        color = Color(0xFF00E5FF),
                        start = Offset(pt.x, pt.y - 20f),
                        end = Offset(pt.x, pt.y + 20f),
                        strokeWidth = 1.3f
                    )
                }
                } // close clipRect
            }

            // Windy Layer Selector Pills (Top-End in Windy format)
            if (effectiveMapFormat == 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color(0xCC091426), RoundedCornerShape(20.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (windyLayer == 0) Color(0xFF0284C7) else Color.Transparent,
                        modifier = Modifier.clickable { windyLayer = 0 }
                    ) {
                        Text(
                            "💨 Vento",
                            color = if (windyLayer == 0) Color.White else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (windyLayer == 1) Color(0xFF10B981) else Color.Transparent,
                        modifier = Modifier.clickable { windyLayer = 1 }
                    ) {
                        Text(
                            "🌧️ Chuva",
                            color = if (windyLayer == 1) Color.White else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (windyLayer == 2) Color(0xFFFF9100) else Color.Transparent,
                        modifier = Modifier.clickable { windyLayer = 2 }
                    ) {
                        Text(
                            "🌡️ Temp",
                            color = if (windyLayer == 2) Color.White else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Windy Sonde Card on tap
            sondeOffset?.let {
                val st = sondeStation
                val coords = sondeCoordinates
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .testTag("windy_sonde_card"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE091426)),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF00E5FF), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "SONDA WINDY • ${st?.name ?: "Ponto SP"}",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "💨 Vento: ${st?.windSpeed?.toInt() ?: 18} km/h ${st?.windDirection ?: "ESE"} • 🌡️ ${st?.currentTemp ?: 25.0}°C • 🌧️ ${st?.rainVolumeMm ?: 0.0} mm",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { sondeOffset = null },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Fechar sonda",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Radar Corner Info Tag
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = "Banda S / C - Refletividade CAPPI 2.5km",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp
                )
                Text(
                    text = if (userCoordinates != null) "🎯 Foco: Localização Exata (GPS) | Zoom: ${"%.1f".format(zoomScale)}x"
                           else "Alcance: 450 km | Resolução: 1 km | Zoom: ${"%.1f".format(zoomScale)}x",
                    color = if (userCoordinates != null) Color(0xFF00E5FF) else Color(0xFF64748B),
                    fontSize = 9.sp,
                    fontWeight = if (userCoordinates != null) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            // Floating Zoom & Pan Controls (Bottom-End of Radar Box)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column {
                        IconButton(
                            onClick = {
                                applyZoomToExactLocation(zoomScale * 1.35f)
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("button_map_zoom_in")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Aproximar Zoom no Meu Local",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                applyZoomToExactLocation(zoomScale / 1.35f)
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("button_map_zoom_out")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Afastar Zoom no Meu Local",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (zoomScale != 1f || panOffsetX != 0f || panOffsetY != 0f) {
                            IconButton(
                                onClick = {
                                    zoomScale = 1f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("button_map_reset_zoom")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Visão Geral do Radar SP",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        userCoordinates?.let { (uLat, uLon) ->
                            IconButton(
                                onClick = {
                                    applyZoomToExactLocation(3.2f)
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("button_map_center_user")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "Focar Meu Local Exato",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        // Selected Storm Cell Info Card
        selectedStormCell?.let { cell ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("card_selected_storm_cell"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Thunderstorm,
                                contentDescription = null,
                                tint = if (cell.dbzPeak >= 50) Color(0xFFFF1744) else Color(0xFFFF9100),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = cell.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        IconButton(
                            onClick = { onSelectStormCell(null) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Vetor: ${cell.headingCompass} a ${cell.speedKmH.toInt()} km/h",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${cell.rainRateMmH} mm/h (${cell.dbzPeak} dBZ)",
                            color = if (cell.dbzPeak >= 50) Color(0xFFFF1744) else Color(0xFFFF9100),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Possível trajetória: ${cell.eta15m} ➔ ${cell.eta30m} ➔ ${cell.eta45m}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Selected Station Radar Details (Inspect Card)
        if (selectedStormCell == null) {
            selectedStation?.let { st ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "${st.name} - ${st.region}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                st.weatherCondition,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${st.dbzReflectivity} dBZ",
                                    color = getDbzColor(st.dbzReflectivity),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "${st.currentTemp}°C",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Text(
                                if (st.rainProbability > 50) "Chuva: ${st.rainVolumeMm} mm (${st.rainProbability}%)" else "Prob: ${st.rainProbability}%",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Radar Playback Controller & Time Scrubber
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onTogglePlay,
                            modifier = Modifier.testTag("button_radar_play_pause")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        val offsetMinutes = (timeStep - 4) * 15
                        val stepTimeMillis = liveTimeMillis + (offsetMinutes * 60 * 1000L)
                        val sdfLive = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale("pt", "BR")) }
                        val sdfStep = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "BR")) }
                        val sdfDate = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR")) }

                        val timeTitle = when {
                            timeStep == 4 -> "AO VIVO: ${sdfLive.format(java.util.Date(liveTimeMillis))} BRT"
                            timeStep < 4 -> "${sdfStep.format(java.util.Date(stepTimeMillis))} (${offsetMinutes} min)"
                            else -> "${sdfStep.format(java.util.Date(stepTimeMillis))} (+${offsetMinutes} min • Previsto)"
                        }

                        Column {
                            Text(
                                text = timeTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${sdfDate.format(java.util.Date(stepTimeMillis))} • Modelo IPMet Doppler",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (timeStep == 4) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF00E676).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676))
                        ) {
                            Text(
                                "● AO VIVO",
                                color = Color(0xFF00E676),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (timeStep > 4) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF38BDF8).copy(alpha = 0.2f)
                        ) {
                            Text(
                                "NOWCASTING",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF64748B).copy(alpha = 0.2f)
                        ) {
                            Text(
                                "DOPPLER",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Slider(
                    value = timeStep.toFloat(),
                    onValueChange = { onTimeStepChanged(it.toInt()) },
                    valueRange = 0f..6f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("slider_radar_time")
                )

                // Quick Time Step Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = timeStep == 0,
                        onClick = { onTimeStepChanged(0) },
                        label = { Text("-60m", fontSize = 10.sp) },
                        modifier = Modifier.testTag("chip_step_minus_60")
                    )
                    FilterChip(
                        selected = timeStep == 2,
                        onClick = { onTimeStepChanged(2) },
                        label = { Text("-30m", fontSize = 10.sp) },
                        modifier = Modifier.testTag("chip_step_minus_30")
                    )
                    FilterChip(
                        selected = timeStep == 4,
                        onClick = { onTimeStepChanged(4) },
                        label = { Text("● AGORA", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00E676),
                            selectedLabelColor = Color.Black
                        ),
                        modifier = Modifier.testTag("chip_step_live_now")
                    )
                    FilterChip(
                        selected = timeStep == 5,
                        onClick = { onTimeStepChanged(5) },
                        label = { Text("+15m", fontSize = 10.sp) },
                        modifier = Modifier.testTag("chip_step_plus_15")
                    )
                    FilterChip(
                        selected = timeStep == 6,
                        onClick = { onTimeStepChanged(6) },
                        label = { Text("+30m", fontSize = 10.sp) },
                        modifier = Modifier.testTag("chip_step_plus_30")
                    )
                }

                // dBZ Color Legend Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("dBZ:", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    DbzLegendItem(color = Color(0xFF00E676), label = "15 Fraca")
                    DbzLegendItem(color = Color(0xFFFFD600), label = "30 Mod")
                    DbzLegendItem(color = Color(0xFFFF9100), label = "45 Forte")
                    DbzLegendItem(color = Color(0xFFFF1744), label = "55 Granizo")
                    DbzLegendItem(color = Color(0xFFD500F9), label = "65+ Extrema")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "☁️ Nuvens no mapa indicam chuva ativa e tempestades",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "IPMet UNESP",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DbzLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(label, color = Color(0xFF94A3B8), fontSize = 9.sp)
    }
}

private fun getDbzColor(dbz: Int): Color {
    return when {
        dbz >= 55 -> Color(0xFFD500F9)
        dbz >= 45 -> Color(0xFFFF1744)
        dbz >= 35 -> Color(0xFFFF9100)
        dbz >= 25 -> Color(0xFFFFD600)
        dbz >= 15 -> Color(0xFF00E676)
        else -> Color(0xFF00E5FF)
    }
}

// SP Map coordinate mapping with zoom and pan
private fun mapLonToX(lon: Double, width: Float, zoom: Float = 1f, panX: Float = 0f): Float {
    val norm = ((lon - MIN_LON) / (MAX_LON - MIN_LON)).coerceIn(0.0, 1.0)
    val baseX = (norm * (width - 60f) + 30f).toFloat()
    val centerX = width / 2f
    return centerX + (baseX - centerX) * zoom + panX
}

private fun mapLatToY(lat: Double, height: Float, zoom: Float = 1f, panY: Float = 0f): Float {
    // Inverted because Lat decreases as we go South
    val norm = ((MAX_LAT - lat) / (MAX_LAT - MIN_LAT)).coerceIn(0.0, 1.0)
    val baseY = (norm * (height - 60f) + 30f).toFloat()
    val centerY = height / 2f
    return centerY + (baseY - centerY) * zoom + panY
}

// Draw SP state outline representation and landmass according to background theme
private fun DrawScope.drawSpBoundary(
    w: Float,
    h: Float,
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
    bgTheme: Int = 0,
    textMeasurer: TextMeasurer? = null
) {
    // 0. Neighboring States (Minas Gerais, Paraná)
    val mgPath = Path().apply {
        moveTo(mapLonToX(-51.5, w, zoom, panX), mapLatToY(-20.5, h, zoom, panY))
        lineTo(mapLonToX(-50.0, w, zoom, panX), mapLatToY(-19.8, h, zoom, panY))
        lineTo(mapLonToX(-47.5, w, zoom, panX), mapLatToY(-20.0, h, zoom, panY))
        lineTo(mapLonToX(-46.5, w, zoom, panX), mapLatToY(-21.8, h, zoom, panY))
        lineTo(mapLonToX(-44.6, w, zoom, panX), mapLatToY(-22.5, h, zoom, panY))
        lineTo(mapLonToX(-44.0, w, zoom, panX), mapLatToY(-21.0, h, zoom, panY))
        lineTo(mapLonToX(-48.0, w, zoom, panX), mapLatToY(-19.0, h, zoom, panY))
        lineTo(mapLonToX(-51.5, w, zoom, panX), mapLatToY(-19.5, h, zoom, panY))
        close()
    }
    val prPath = Path().apply {
        moveTo(mapLonToX(-53.0, w, zoom, panX), mapLatToY(-22.4, h, zoom, panY))
        lineTo(mapLonToX(-51.0, w, zoom, panX), mapLatToY(-23.2, h, zoom, panY))
        lineTo(mapLonToX(-49.5, w, zoom, panX), mapLatToY(-24.7, h, zoom, panY))
        lineTo(mapLonToX(-47.8, w, zoom, panX), mapLatToY(-25.0, h, zoom, panY))
        lineTo(mapLonToX(-48.5, w, zoom, panX), mapLatToY(-26.0, h, zoom, panY))
        lineTo(mapLonToX(-53.0, w, zoom, panX), mapLatToY(-25.5, h, zoom, panY))
        close()
    }

    val neighborFill = when (bgTheme) {
        1 -> Color(0xFF1E3524) // Muted Terrestre
        2 -> Color(0xFFF1F5F9) // Muted White/Slate
        else -> Color(0xFF061324) // Dark oceanic slate
    }
    drawPath(mgPath, neighborFill)
    drawPath(prPath, neighborFill)

    // 1. São Paulo State Detailed Contour
    val path = Path().apply {
        moveTo(mapLonToX(-53.0, w, zoom, panX), mapLatToY(-22.4, h, zoom, panY)) // Pontal do Paranapanema
        lineTo(mapLonToX(-52.2, w, zoom, panX), mapLatToY(-21.7, h, zoom, panY)) // Presidente Epitácio / Rio Paraná
        lineTo(mapLonToX(-51.5, w, zoom, panX), mapLatToY(-20.5, h, zoom, panY)) // Rio Paraná / Ilha Solteira
        lineTo(mapLonToX(-50.9, w, zoom, panX), mapLatToY(-20.2, h, zoom, panY)) // Santa Fé do Sul
        lineTo(mapLonToX(-50.0, w, zoom, panX), mapLatToY(-19.8, h, zoom, panY)) // Noroeste divisa MG
        lineTo(mapLonToX(-48.5, w, zoom, panX), mapLatToY(-20.0, h, zoom, panY)) // Barretos / Rio Grande
        lineTo(mapLonToX(-47.5, w, zoom, panX), mapLatToY(-20.0, h, zoom, panY)) // Franca / Rio Grande
        lineTo(mapLonToX(-46.5, w, zoom, panX), mapLatToY(-21.8, h, zoom, panY)) // Circuito das Águas
        lineTo(mapLonToX(-44.6, w, zoom, panX), mapLatToY(-22.5, h, zoom, panY)) // Vale do Paraíba / Cruzeiro
        lineTo(mapLonToX(-44.8, w, zoom, panX), mapLatToY(-23.4, h, zoom, panY)) // Ubatuba / Litoral Norte
        lineTo(mapLonToX(-45.4, w, zoom, panX), mapLatToY(-23.8, h, zoom, panY)) // Ilhabela / São Sebastião
        lineTo(mapLonToX(-46.4, w, zoom, panX), mapLatToY(-24.0, h, zoom, panY)) // Santos / Baixada Santista
        lineTo(mapLonToX(-47.5, w, zoom, panX), mapLatToY(-24.7, h, zoom, panY)) // Iguape / Cananéia
        lineTo(mapLonToX(-47.8, w, zoom, panX), mapLatToY(-25.0, h, zoom, panY)) // Cananéia / Ilha Comprida
        lineTo(mapLonToX(-48.9, w, zoom, panX), mapLatToY(-24.8, h, zoom, panY)) // Vale do Ribeira / Divisa PR
        lineTo(mapLonToX(-49.5, w, zoom, panX), mapLatToY(-24.7, h, zoom, panY)) // Divisa Paraná Sul
        lineTo(mapLonToX(-50.1, w, zoom, panX), mapLatToY(-23.1, h, zoom, panY)) // Ourinhos / Paranapanema
        lineTo(mapLonToX(-51.0, w, zoom, panX), mapLatToY(-23.2, h, zoom, panY)) // Paranapanema / Ourinhos
        lineTo(mapLonToX(-52.2, w, zoom, panX), mapLatToY(-22.6, h, zoom, panY)) // Paranapanema Oeste
        close()
    }

    when (bgTheme) {
        0 -> { // Black & Blue
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0F2648), Color(0xFF08172D)),
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = (w * 0.7f * zoom).coerceAtLeast(200f)
                )
            )
            drawPath(
                path = path,
                color = Color(0xFF00E5FF).copy(alpha = 0.85f),
                style = Stroke(width = 1.8f * zoom.coerceIn(0.8f, 2.2f))
            )
        }
        1 -> { // Terrestre (Relevo / Vegetação natural da Mata Atlântica e Cerrado)
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF4D6328), // Noroeste / cerrado e agro
                        Color(0xFF5A6630), // Centro / Planalto Paulista
                        Color(0xFF386641), // Leste / Vale do Paraíba
                        Color(0xFF265330)  // Litoral / Serra do Mar verde exuberante
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )
            // Shaded relief along Serra do Mar and Serra da Mantiqueira
            val reliefPath = Path().apply {
                moveTo(mapLonToX(-44.6, w, zoom, panX), mapLatToY(-22.5, h, zoom, panY))
                lineTo(mapLonToX(-44.8, w, zoom, panX), mapLatToY(-23.4, h, zoom, panY))
                lineTo(mapLonToX(-45.4, w, zoom, panX), mapLatToY(-23.8, h, zoom, panY))
                lineTo(mapLonToX(-46.4, w, zoom, panX), mapLatToY(-24.0, h, zoom, panY))
                lineTo(mapLonToX(-47.5, w, zoom, panX), mapLatToY(-24.7, h, zoom, panY))
                lineTo(mapLonToX(-47.8, w, zoom, panX), mapLatToY(-25.0, h, zoom, panY))
            }
            drawPath(
                path = reliefPath,
                color = Color(0xFF78623A).copy(alpha = 0.55f),
                style = Stroke(width = 9.0f * zoom.coerceIn(0.8f, 2.0f), cap = StrokeCap.Round)
            )
            // State border line (crisp ivory with subtle dark drop)
            drawPath(
                path = path,
                color = Color(0xFF0C2417).copy(alpha = 0.7f),
                style = Stroke(width = 3.0f * zoom.coerceIn(0.8f, 2.2f))
            )
            drawPath(
                path = path,
                color = Color(0xFFFEF9C3).copy(alpha = 0.95f),
                style = Stroke(width = 1.8f * zoom.coerceIn(0.8f, 2.2f))
            )
        }
        2 -> { // White (Papel cartográfico puro com linhas nítidas de alto contraste)
            drawPath(
                path = path,
                color = Color.White
            )
            drawPath(
                path = path,
                color = Color(0xFF0F172A).copy(alpha = 0.95f),
                style = Stroke(width = 2.2f * zoom.coerceIn(0.8f, 2.2f))
            )
        }
    }

    // 2. Main Rivers across SP (Tietê, Paranapanema)
    val riverColor = when (bgTheme) {
        1 -> Color(0xFF38BDF8)
        2 -> Color(0xFF0284C7)
        else -> Color(0xFF00E5FF)
    }

    // Rio Tietê
    val tietePath = Path().apply {
        val tpts = listOf(
            Pair(-23.53, -46.0), Pair(-23.45, -46.8), Pair(-23.1, -47.4),
            Pair(-22.7, -48.4), Pair(-22.2, -49.2), Pair(-21.6, -49.9), Pair(-20.7, -51.3)
        )
        tpts.forEachIndexed { i, (lat, lon) ->
            val x = mapLonToX(lon, w, zoom, panX)
            val y = mapLatToY(lat, h, zoom, panY)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(tietePath, color = riverColor.copy(alpha = 0.75f), style = Stroke(width = 2.0f * zoom.coerceIn(0.8f, 2.0f)))

    // Rio Paranapanema
    val paranaPath = Path().apply {
        val ppts = listOf(
            Pair(-23.5, -48.5), Pair(-23.2, -49.5), Pair(-23.0, -50.2),
            Pair(-22.6, -51.5), Pair(-22.4, -53.0)
        )
        ppts.forEachIndexed { i, (lat, lon) ->
            val x = mapLonToX(lon, w, zoom, panX)
            val y = mapLatToY(lat, h, zoom, panY)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(paranaPath, color = riverColor.copy(alpha = 0.65f), style = Stroke(width = 1.6f * zoom.coerceIn(0.8f, 2.0f)))

    // Ocean Label "OCEANO ATLÂNTICO"
    if (textMeasurer != null) {
        val oceanX = mapLonToX(-45.5, w, zoom, panX)
        val oceanY = mapLatToY(-24.7, h, zoom, panY)
        val oceanColor = when (bgTheme) {
            1 -> Color(0xFF7DD3FC).copy(alpha = 0.65f)
            2 -> Color(0xFF0284C7).copy(alpha = 0.75f)
            else -> Color(0xFF38BDF8).copy(alpha = 0.5f)
        }
        safeDrawText(
            textMeasurer = textMeasurer,
            text = "OCEANO ATLÂNTICO",
            topLeft = Offset(oceanX, oceanY),
            style = TextStyle(color = oceanColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        )
    }
}

// Draw rain clusters and storm cell trajectories
private fun DrawScope.drawRadarReflectivityClusters(
    w: Float,
    h: Float,
    timeStep: Int,
    isEnergySaver: Boolean,
    stormCells: List<StormCellTrajectory>,
    showTrajectories: Boolean,
    selectedStormCell: StormCellTrajectory?,
    textMeasurer: TextMeasurer,
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f
) {
    val offsetShift = (timeStep - 4) * 8f * zoom

    // Cluster 1: Bauru - Botucatu corridor
    val c1X = mapLonToX(-48.8, w, zoom, panX) + offsetShift
    val c1Y = mapLatToY(-22.6, h, zoom, panY) + offsetShift * 0.3f
    drawEchoBlob(c1X, c1Y, 36f * zoom.coerceIn(0.8f, 2.2f), Color(0xFFFF9100), Color(0xFFFF1744))
    drawRainCloud(c1X, c1Y - 18f * zoom, scale = 1.3f * zoom.coerceIn(0.8f, 1.6f), isThunderstorm = true, rainMm = 14.0)

    // Cluster 2: Vale do Paraíba (Campos do Jordão / SJC)
    val c2X = mapLonToX(-45.7, w, zoom, panX) + offsetShift
    val c2Y = mapLatToY(-23.0, h, zoom, panY)
    drawEchoBlob(c2X, c2Y, 28f * zoom.coerceIn(0.8f, 2.2f), Color(0xFFFF1744), Color(0xFFD500F9))
    drawRainCloud(c2X, c2Y - 16f * zoom, scale = 1.25f * zoom.coerceIn(0.8f, 1.6f), isThunderstorm = true, rainMm = 18.0)

    // Cluster 3: Baixada Santista / Serra do Mar
    val c3X = mapLonToX(-46.3, w, zoom, panX)
    val c3Y = mapLatToY(-23.9, h, zoom, panY)
    drawEchoBlob(c3X, c3Y, 32f * zoom.coerceIn(0.8f, 2.2f), Color(0xFFFFD600), Color(0xFFFF9100))
    drawRainCloud(c3X, c3Y - 16f * zoom, scale = 1.2f * zoom.coerceIn(0.8f, 1.6f), isThunderstorm = false, rainMm = 12.0)

    // Cluster 4: Campinas / Sorocaba
    val c4X = mapLonToX(-47.2, w, zoom, panX) + offsetShift * 0.8f
    val c4Y = mapLatToY(-23.1, h, zoom, panY)
    drawEchoBlob(c4X, c4Y, 24f * zoom.coerceIn(0.8f, 2.2f), Color(0xFF00E676), Color(0xFFFFD600))
    drawRainCloud(c4X, c4Y - 14f * zoom, scale = 1.1f * zoom.coerceIn(0.8f, 1.6f), isThunderstorm = false, rainMm = 7.5)

    // Draw active storm cell echoes and trajectories
    stormCells.forEach { cell ->
        val dtMin = (timeStep - 4) * 15f
        val kmTraveled = cell.speedKmH * (dtMin / 60f)
        val degRad = Math.toRadians(cell.directionAngleDeg)
        val scale = (w / 500f) * zoom
        val dx = (kmTraveled * cos(degRad) * 1.8f * scale).toFloat()
        val dy = (kmTraveled * sin(degRad) * 1.8f * scale).toFloat()

        val curX = mapLonToX(cell.originLon, w, zoom, panX) + dx
        val curY = mapLatToY(cell.originLat, h, zoom, panY) + dy

        val cellColor = if (cell.dbzPeak >= 55) Color(0xFFD500F9) else if (cell.dbzPeak >= 45) Color(0xFFFF1744) else Color(0xFFFF9100)
        val isSelected = selectedStormCell?.id == cell.id

        // Storm core echo
        drawEchoBlob(curX, curY, (if (isSelected) 30f else 22f) * zoom.coerceIn(0.8f, 2.0f), cellColor.copy(alpha = 0.6f), cellColor)
        drawRainCloud(curX, curY - 18f * zoom, scale = (if (isSelected) 1.4f else 1.15f) * zoom.coerceIn(0.8f, 1.6f), isThunderstorm = true, rainMm = cell.rainRateMmH)

        if (isSelected) {
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = 34f * zoom.coerceIn(0.8f, 2.0f),
                center = Offset(curX, curY),
                style = Stroke(width = 2.5f)
            )
        }

        // Draw Trajectory Vectors (Direction, ETA cones, Projected Path)
        if (showTrajectories) {
            val futureDist30m = (cell.speedKmH * 0.5f * 2.2f * scale).toFloat()
            val futureDist45m = (cell.speedKmH * 0.75f * 2.2f * scale).toFloat()
            val fX15 = curX + (futureDist30m * 0.5f * cos(degRad)).toFloat()
            val fY15 = curY + (futureDist30m * 0.5f * sin(degRad)).toFloat()
            val fX30 = curX + (futureDist30m * cos(degRad)).toFloat()
            val fY30 = curY + (futureDist30m * sin(degRad)).toFloat()
            val fX45 = curX + (futureDist45m * cos(degRad)).toFloat()
            val fY45 = curY + (futureDist45m * sin(degRad)).toFloat()

            // 1. Trajectory cone of uncertainty (cone de dispersão previsto)
            val conePath = Path().apply {
                val spread = 0.28
                val cX1 = curX + (futureDist45m * cos(degRad - spread)).toFloat()
                val cY1 = curY + (futureDist45m * sin(degRad - spread)).toFloat()
                val cX2 = curX + (futureDist45m * cos(degRad + spread)).toFloat()
                val cY2 = curY + (futureDist45m * sin(degRad + spread)).toFloat()
                moveTo(curX, curY)
                lineTo(cX1, cY1)
                lineTo(cX2, cY2)
                close()
            }
            drawPath(
                path = conePath,
                color = cellColor.copy(alpha = 0.18f)
            )

            // 2. High-visibility animated trajectory vector line
            // Glowing outer line
            drawLine(
                color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                start = Offset(curX, curY),
                end = Offset(fX45, fY45),
                strokeWidth = 6.0f * zoom.coerceIn(0.8f, 1.8f)
            )
            // Sharp central track line
            drawLine(
                color = Color(0xFF00E5FF),
                start = Offset(curX, curY),
                end = Offset(fX45, fY45),
                strokeWidth = 3.0f * zoom.coerceIn(0.8f, 1.8f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
            )

            // 3. Bold Trajectory Arrow Head at destination fX45, fY45
            val arrowAngle = degRad + Math.PI
            val arrowSize = 18f * zoom.coerceIn(0.8f, 1.8f)
            val aX1 = fX45 + (arrowSize * cos(arrowAngle - 0.45)).toFloat()
            val aY1 = fY45 + (arrowSize * sin(arrowAngle - 0.45)).toFloat()
            val aX2 = fX45 + (arrowSize * cos(arrowAngle + 0.45)).toFloat()
            val aY2 = fY45 + (arrowSize * sin(arrowAngle + 0.45)).toFloat()

            // Arrow triangle filled
            val arrowHeadPath = Path().apply {
                moveTo(fX45, fY45)
                lineTo(aX1, aY1)
                lineTo(aX2, aY2)
                close()
            }
            drawPath(arrowHeadPath, color = Color(0xFFFF1744))
            drawPath(arrowHeadPath, color = Color.White, style = Stroke(width = 1.5f))

            // 4. Projected arrival points (+15m, +30m, +45m) with rings
            drawCircle(Color(0xFF00E5FF), radius = 5f * zoom.coerceIn(0.8f, 1.8f), center = Offset(fX15, fY15))
            drawCircle(Color.White, radius = 2.5f * zoom.coerceIn(0.8f, 1.8f), center = Offset(fX15, fY15))

            drawCircle(Color(0xFFFF9100), radius = 6f * zoom.coerceIn(0.8f, 1.8f), center = Offset(fX30, fY30))
            drawCircle(Color.White, radius = 3f * zoom.coerceIn(0.8f, 1.8f), center = Offset(fX30, fY30))

            drawCircle(Color(0xFFFF1744), radius = 7f * zoom.coerceIn(0.8f, 1.8f), center = Offset(fX45, fY45))

            // 5. Projected arrival text with contrast background badge
            val etaLabel = "➔ ${cell.speedKmH.toInt()} km/h | Rumo ${cell.impactTowns.firstOrNull() ?: "Leste"}"
            val etaLayout = textMeasurer.measure(
                text = etaLabel,
                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)
            )
            drawRoundRect(
                color = Color(0xFF0F172A).copy(alpha = 0.9f),
                topLeft = Offset(fX15 + 4f, fY15 - 16f),
                size = androidx.compose.ui.geometry.Size(etaLayout.size.width + 8f, etaLayout.size.height + 4f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            safeDrawText(
                textMeasurer = textMeasurer,
                text = etaLabel,
                topLeft = Offset(fX15 + 8f, fY15 - 14f),
                style = TextStyle(color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            )

            // ETA tags (+15m, +30m, +45m)
            safeDrawText(
                textMeasurer = textMeasurer,
                text = "+15m",
                topLeft = Offset(fX15 + 6f, fY15 + 4f),
                style = TextStyle(color = Color(0xFF38BDF8), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            )
            safeDrawText(
                textMeasurer = textMeasurer,
                text = "+30m: ${cell.eta30m.substringBefore(" (")}",
                topLeft = Offset(fX30 + 6f, fY30 - 12f),
                style = TextStyle(color = Color(0xFFFFAB00), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            )
            safeDrawText(
                textMeasurer = textMeasurer,
                text = "+45m (Impacto)",
                topLeft = Offset(fX45 + 8f, fY45 - 14f),
                style = TextStyle(color = Color(0xFFFF5252), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

private fun DrawScope.drawEchoBlob(cx: Float, cy: Float, radius: Float, outerColor: Color, centerColor: Color) {
    drawCircle(
        color = outerColor.copy(alpha = 0.4f),
        radius = radius,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = outerColor.copy(alpha = 0.7f),
        radius = radius * 0.65f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = centerColor.copy(alpha = 0.9f),
        radius = radius * 0.35f,
        center = Offset(cx, cy)
    )
}

/**
 * Renders an intuitive rain cloud symbol with raindrops and optional lightning bolt.
 * Directly fulfills user requirement: "QUANDO ESTIVER CHOVENDO COLOQUE TIPO UMA NUVEM NO MAPA"
 */
private fun DrawScope.drawRainCloud(
    cx: Float,
    cy: Float,
    scale: Float = 1.0f,
    isThunderstorm: Boolean = false,
    rainMm: Double = 0.0
) {
    val cloudColor = if (isThunderstorm) Color(0xFF334155) else Color(0xFF475569)
    val highlightColor = if (isThunderstorm) Color(0xFF64748B) else Color(0xFF94A3B8)

    // Overlapping cloud puffs
    drawCircle(
        color = cloudColor.copy(alpha = 0.9f),
        radius = 7.5f * scale,
        center = Offset(cx, cy - 3f * scale)
    )
    drawCircle(
        color = cloudColor.copy(alpha = 0.9f),
        radius = 5.5f * scale,
        center = Offset(cx - 6f * scale, cy)
    )
    drawCircle(
        color = cloudColor.copy(alpha = 0.9f),
        radius = 6.0f * scale,
        center = Offset(cx + 6f * scale, cy)
    )
    // Cloud flat base
    drawLine(
        color = cloudColor.copy(alpha = 0.95f),
        start = Offset(cx - 6f * scale, cy + 2f * scale),
        end = Offset(cx + 6f * scale, cy + 2f * scale),
        strokeWidth = 6f * scale,
        cap = StrokeCap.Round
    )

    // Highlight top puff
    drawCircle(
        color = highlightColor.copy(alpha = 0.7f),
        radius = 4.5f * scale,
        center = Offset(cx - 1f * scale, cy - 4f * scale)
    )

    // Raindrops falling under cloud
    val dropColor = if (rainMm > 10.0 || isThunderstorm) Color(0xFF00E5FF) else Color(0xFF38BDF8)
    val dropLen = 5.5f * scale
    val slant = 2.0f * scale

    // Drop 1 (left)
    drawLine(
        color = dropColor,
        start = Offset(cx - 5f * scale, cy + 5f * scale),
        end = Offset(cx - 5f * scale - slant, cy + 5f * scale + dropLen),
        strokeWidth = 1.8f * scale,
        cap = StrokeCap.Round
    )
    // Drop 2 (center)
    drawLine(
        color = dropColor,
        start = Offset(cx, cy + 6f * scale),
        end = Offset(cx - slant, cy + 6f * scale + dropLen),
        strokeWidth = 1.8f * scale,
        cap = StrokeCap.Round
    )
    // Drop 3 (right)
    drawLine(
        color = dropColor,
        start = Offset(cx + 5f * scale, cy + 5f * scale),
        end = Offset(cx + 5f * scale - slant, cy + 5f * scale + dropLen),
        strokeWidth = 1.8f * scale,
        cap = StrokeCap.Round
    )

    // Lightning bolt for thunderstorm
    if (isThunderstorm) {
        val boltPath = Path().apply {
            moveTo(cx + 1.5f * scale, cy + 2.5f * scale)
            lineTo(cx - 2f * scale, cy + 7.5f * scale)
            lineTo(cx + 1.5f * scale, cy + 7.5f * scale)
            lineTo(cx - 2f * scale, cy + 14f * scale)
        }
        drawPath(
            path = boltPath,
            color = Color(0xFFFFD600),
            style = Stroke(width = 1.8f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// -------------------------------------------------------------
// Formato Circular PPI 360° - Mostrador Azimutal Tático
// -------------------------------------------------------------
private fun DrawScope.drawCircularPpiScope(
    w: Float,
    h: Float,
    textMeasurer: TextMeasurer,
    centerX: Float,
    centerY: Float,
    sweepAngle: Float,
    isPlaying: Boolean
) {
    val radius = min(w, h) * 0.47f
    val center = Offset(w / 2f, h / 2f)

    // Outer instrument ring (phosphor cyan)
    drawCircle(
        color = Color(0xFF00E5FF).copy(alpha = 0.85f),
        radius = radius,
        center = center,
        style = Stroke(width = 2.5f)
    )

    // Azimuth ticks & labels every 45 degrees
    val azimuthLabels = listOf(
        Pair(0.0, "0° N"),
        Pair(45.0, "45° NE"),
        Pair(90.0, "90° E"),
        Pair(135.0, "135° SE"),
        Pair(180.0, "180° S"),
        Pair(225.0, "225° SW"),
        Pair(270.0, "270° W"),
        Pair(315.0, "315° NW")
    )

    // Draw degree tick marks every 15 degrees
    for (deg in 0 until 360 step 15) {
        val rad = Math.toRadians((deg - 90).toDouble())
        val isMajor = deg % 45 == 0
        val tickLen = if (isMajor) 10f else 5f
        val startX = (center.x + cos(rad) * (radius - tickLen)).toFloat()
        val startY = (center.y + sin(rad) * (radius - tickLen)).toFloat()
        val endX = (center.x + cos(rad) * radius).toFloat()
        val endY = (center.y + sin(rad) * radius).toFloat()

        drawLine(
            color = if (isMajor) Color(0xFF00E5FF) else Color(0xFF1E3A5F),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = if (isMajor) 1.5f else 1.0f
        )
    }

    azimuthLabels.forEach { (deg, label) ->
        val rad = Math.toRadians(deg - 90.0)
        val textDist = radius - 18f
        val lx = (center.x + cos(rad) * textDist).toFloat()
        val ly = (center.y + sin(rad) * textDist).toFloat()
        safeDrawText(
            textMeasurer = textMeasurer,
            text = label,
            topLeft = Offset(lx - 14f, ly - 6f),
            style = TextStyle(color = Color(0xFF00E5FF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        )
    }
}

// -------------------------------------------------------------
// Formato Cartográfico - Malha Viária, Rios e Coordenadas
// -------------------------------------------------------------
private fun DrawScope.drawCartographicBasemap(
    w: Float,
    h: Float,
    zoom: Float,
    panX: Float,
    panY: Float,
    textMeasurer: TextMeasurer,
    bgTheme: Int = 0
) {
    val gridColor = when (bgTheme) {
        1 -> Color(0xFFE2E8F0).copy(alpha = 0.35f)
        2 -> Color(0xFF94A3B8).copy(alpha = 0.60f)
        else -> Color(0xFF334155).copy(alpha = 0.50f)
    }
    val gridTextColor = when (bgTheme) {
        1 -> Color(0xFFFEF9C3)
        2 -> Color(0xFF475569)
        else -> Color(0xFF94A3B8)
    }
    val riverColor = when (bgTheme) {
        1 -> Color(0xFF38BDF8)
        2 -> Color(0xFF0284C7)
        else -> Color(0xFF00E5FF)
    }
    val roadColor1 = when (bgTheme) {
        1 -> Color(0xFFFBBF24)
        2 -> Color(0xFFD97706)
        else -> Color(0xFFF59E0B)
    }
    val roadColor2 = when (bgTheme) {
        1 -> Color(0xFFFB923C)
        2 -> Color(0xFFEA580C)
        else -> Color(0xFFF97316)
    }

    // Paralelos (-20°S, -22°S, -24°S)
    val parallels = listOf(-20.0, -22.0, -24.0)
    parallels.forEach { lat ->
        val py = mapLatToY(lat, h, zoom, panY)
        drawLine(
            color = gridColor,
            start = Offset(0f, py),
            end = Offset(w, py),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
        )
        safeDrawText(
            textMeasurer = textMeasurer,
            text = "${lat.toInt()}°S",
            topLeft = Offset(6f, py - 12f),
            style = TextStyle(color = gridTextColor, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        )
    }

    // Meridianos (-52°W, -50°W, -48°W, -46°W)
    val meridians = listOf(-52.0, -50.0, -48.0, -46.0)
    meridians.forEach { lon ->
        val px = mapLonToX(lon, w, zoom, panX)
        drawLine(
            color = gridColor,
            start = Offset(px, 0f),
            end = Offset(px, h),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
        )
        safeDrawText(
            textMeasurer = textMeasurer,
            text = "${lon.toInt()}°W",
            topLeft = Offset(px + 4f, 6f),
            style = TextStyle(color = gridTextColor, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        )
    }

    // Rio Tietê
    val tietePts = listOf(
        Pair(-23.53, -46.0), Pair(-23.45, -46.8), Pair(-23.1, -47.4),
        Pair(-22.7, -48.4), Pair(-22.2, -49.2), Pair(-21.6, -49.9), Pair(-20.7, -51.3)
    )
    val tietePath = Path().apply {
        tietePts.forEachIndexed { i, (lat, lon) ->
            val x = mapLonToX(lon, w, zoom, panX)
            val y = mapLatToY(lat, h, zoom, panY)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(tietePath, color = riverColor.copy(alpha = 0.75f), style = Stroke(width = 2.0f * zoom.coerceIn(0.8f, 2.0f)))

    // Rodovia Castelo Branco (SP-280)
    val sp280Pts = listOf(
        Pair(-23.53, -46.7), Pair(-23.45, -47.4), Pair(-22.95, -48.4), Pair(-22.88, -49.3), Pair(-22.95, -49.8)
    )
    val sp280Path = Path().apply {
        sp280Pts.forEachIndexed { i, (lat, lon) ->
            val x = mapLonToX(lon, w, zoom, panX)
            val y = mapLatToY(lat, h, zoom, panY)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(sp280Path, color = roadColor1.copy(alpha = 0.70f), style = Stroke(width = 1.6f * zoom.coerceIn(0.8f, 2.0f)))

    // Rodovia Washington Luís (SP-310)
    val sp310Pts = listOf(
        Pair(-22.56, -47.4), Pair(-22.01, -47.89), Pair(-21.78, -48.17), Pair(-20.81, -49.37)
    )
    val sp310Path = Path().apply {
        sp310Pts.forEachIndexed { i, (lat, lon) ->
            val x = mapLonToX(lon, w, zoom, panX)
            val y = mapLatToY(lat, h, zoom, panY)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(sp310Path, color = roadColor2.copy(alpha = 0.70f), style = Stroke(width = 1.6f * zoom.coerceIn(0.8f, 2.0f)))

    // Legenda Cartográfica discreta
    val legendColor = when (bgTheme) {
        1 -> Color(0xFF86EFAC)
        2 -> Color(0xFF0F172A)
        else -> Color(0xFF10B981)
    }
    safeDrawText(
        textMeasurer = textMeasurer,
        text = "Base Cartográfica SP • Bacias & Rodovias",
        topLeft = Offset(10f, h - 18f),
        style = TextStyle(color = legendColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    )
}

// -------------------------------------------------------------
// Partículas de Vento Dinâmicas Estilo Windy (Streamlines)
// -------------------------------------------------------------
private fun DrawScope.drawWindyWindParticles(
    particles: List<WindyStreamParticle>,
    w: Float,
    h: Float,
    bgTheme: Int = 0
) {
    particles.forEach { p ->
        val pAlpha = (kotlin.math.sin((p.age.toFloat() / p.maxLife.toFloat()) * Math.PI).toFloat()).coerceIn(0f, 1f)
        val pColor = if (bgTheme == 2) {
            when {
                p.speed > 4.2f -> Color(0xFFDC2626) // Vermelho intenso (>35 km/h)
                p.speed > 3.2f -> Color(0xFFD97706) // Âmbar escuro (25-35 km/h)
                p.speed > 2.2f -> Color(0xFF0284C7) // Azul real (15-25 km/h)
                else -> Color(0xFF0369A1)           // Azul escuro (<15 km/h)
            }
        } else {
            when {
                p.speed > 4.2f -> Color(0xFFFF9100) // Laranja Windy (>35 km/h)
                p.speed > 3.2f -> Color(0xFFFFEA00) // Amarelo (25-35 km/h)
                p.speed > 2.2f -> Color(0xFF00E5FF) // Ciano Elétrico (15-25 km/h)
                else -> Color(0xFF38BDF8)           // Azul Claro (<15 km/h)
            }
        }
        val headColor = if (bgTheme == 2) Color(0xFF0F172A).copy(alpha = pAlpha * 0.95f) else Color.White.copy(alpha = pAlpha * 0.95f)
        val tailColor = pColor.copy(alpha = 0f)

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(tailColor, pColor.copy(alpha = pAlpha * 0.85f), headColor),
                start = Offset(p.prevX, p.prevY),
                end = Offset(p.x, p.y)
            ),
            start = Offset(p.prevX, p.prevY),
            end = Offset(p.x, p.y),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = headColor,
            radius = 1.4f,
            center = Offset(p.x, p.y)
        )
    }
}
