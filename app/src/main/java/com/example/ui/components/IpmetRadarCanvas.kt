package com.example.ui.components

import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
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
import com.example.ui.map.*
import androidx.compose.foundation.layout.PaddingValues
import java.util.Locale
import com.example.data.local.entity.hasRealData
import androidx.compose.material3.minimumInteractiveComponentSize
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// SP Coordinate Bounds:
// Lat: ~ -19.7 (North) to -25.3 (South) -> Span ~ 5.6
// Lon: ~ -53.1 (West) to -44.2 (East) -> Span ~ 8.9

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

    // Vento real (Open-Meteo) por estação: direção meteorológica (de onde vem) + velocidade km/h
    val windStations = remember(stations) {
        stations.filter { it.lastUpdated > 0L }.mapNotNull { st ->
            val fromDeg = com.example.data.remote.compassToDegrees(st.windDirection) ?: return@mapNotNull null
            val towardsRad = Math.toRadians(fromDeg + 180.0) // sentido do movimento = de onde vem + 180°
            floatArrayOf(st.lat.toFloat(), st.lon.toFloat(), sin(towardsRad).toFloat(), (-cos(towardsRad)).toFloat(), st.windSpeed.toFloat())
        }
    }
    val windStationsState = rememberUpdatedState(windStations)
    val hasWindData = windStations.isNotEmpty()

    var particleTick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(isPlaying, isEnergySaver, hasWindData) {
        if (!isEnergySaver && hasWindData) {
            val rng = java.util.Random()
            while (isActive) {
                androidx.compose.runtime.withFrameNanos {
                    val w = if (canvasWidth > 50f) canvasWidth else 800f
                    val h = if (canvasHeight > 50f) canvasHeight else 600f
                    val ws = windStationsState.value
                    val frameProj = SpProjection(w, h, zoomScale, panOffsetX, panOffsetY, RADAR_PAD)
                    val wsX = FloatArray(ws.size) { frameProj.x(ws[it][1].toDouble()) }
                    val wsY = FloatArray(ws.size) { frameProj.y(ws[it][0].toDouble()) }
                    windParticles.forEach { p ->
                        p.prevX = p.x
                        p.prevY = p.y
                        // Estação mais próxima da partícula (em coordenadas de tela)
                        var best: FloatArray? = null
                        var bestD = Float.MAX_VALUE
                        for (k in ws.indices) {
                            val d = (wsX[k] - p.x) * (wsX[k] - p.x) + (wsY[k] - p.y) * (wsY[k] - p.y)
                            if (d < bestD) { bestD = d; best = ws[k] }
                        }
                        val b = best
                        if (b != null) {
                            p.speed = (b[4] / 8.3f).coerceIn(0.6f, 7f) // km/h → px/quadro
                            val effectiveSpd = p.speed * (if (isPlaying) 1.25f else 0.45f) * zoomScale.coerceIn(0.8f, 2.5f)
                            p.x += b[2] * effectiveSpd
                            p.y += b[3] * effectiveSpd
                        }
                        p.age++
                        if (p.x < -30f || p.y < -30f || p.x > w + 30f || p.y > h + 30f || p.age > p.maxLife) {
                            p.x = rng.nextFloat() * w
                            p.y = rng.nextFloat() * h
                            p.prevX = p.x
                            p.prevY = p.y
                            p.age = 0
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

    // Helper: Exact pan coordinates to place any lat/lon exactly in the center of the canvas
    val centerOnCoordinates: (Double, Double, Float) -> Unit = { targetLat, targetLon, targetZoom ->
        val w = if (canvasWidth > 50f) canvasWidth else 800f
        val h = if (canvasHeight > 50f) canvasHeight else 600f
        val (px, py) = SpProjection(w, h, paddingPx = RADAR_PAD).panToCenter(targetLat, targetLon, targetZoom)
        zoomScale = targetZoom
        panOffsetX = px
        panOffsetY = py
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

    // Interpolação visual suave entre quadros (só desloca as manchas; trajetória usa o timeStep inteiro)
    val animatedStep by animateFloatAsState(
        targetValue = timeStep.toFloat(),
        animationSpec = tween(durationMillis = if (isEnergySaver) 0 else 600, easing = FastOutSlowInEasing),
        label = "RadarStepAnim"
    )

    val textMeasurer = rememberTextMeasurer()

    // Campos contínuos (IDW) calculados uma vez por atualização de dados — só estações com dado REAL
    val realStations = remember(stations) { stations.filter { it.hasRealData() } }
    val rainImage = remember(realStations) {
        val pts = realStations.map { FieldPoint(it.lat, it.lon, it.dbzReflectivity.toFloat()) }
        if (pts.none { it.value >= 10f }) null else ScalarField.idw(pts)?.toImageBitmap(MapRamp.Dbz, 0.85f)
    }
    val tempImage = remember(realStations) {
        ScalarField.idw(realStations.map { FieldPoint(it.lat, it.lon, it.currentTemp.toFloat()) })?.toImageBitmap(MapRamp.TempC, 0.6f)
    }
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
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = activeCenter == "bauru",
                    onClick = { onCenterChanged("bauru") },
                    label = { Text("Bauru", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.height(32.dp).testTag("chip_radar_bauru")
                )
                FilterChip(
                    selected = activeCenter == "presidente_prudente",
                    onClick = { onCenterChanged("presidente_prudente") },
                    label = { Text("P. Prudente", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.height(32.dp).testTag("chip_radar_prudente")
                )

                // Chip "Trajetórias" removido: não há células de tempestade reais para rastrear.
            }
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
                            "OPEN-METEO",
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
                            .size(40.dp)
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
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = mdInline("**Formato** ·"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false
            )
            FilterChip(
                selected = effectiveMapFormat == 0,
                onClick = { setFormat(0) },
                label = { Text("Windy Vento & Chuva", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                leadingIcon = {
                    Icon(Icons.Default.WindPower, contentDescription = null, modifier = Modifier.size(13.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF0284C7),
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.height(32.dp).testTag("chip_format_windy")
            )
            FilterChip(
                selected = effectiveMapFormat == 1,
                onClick = { setFormat(1) },
                label = { Text("Circular PPI 360°", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                leadingIcon = {
                    Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(13.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF00E5FF),
                    selectedLabelColor = Color.Black
                ),
                modifier = Modifier.height(32.dp).testTag("chip_format_circular")
            )
            FilterChip(
                selected = effectiveMapFormat == 2,
                onClick = { setFormat(2) },
                label = { Text("Topográfico SP", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                leadingIcon = {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(13.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF10B981),
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.height(32.dp).testTag("chip_format_cartographic")
            )
        }
        }

        // Map Background Theme Selector Row: Black & Blue / Terrestre / White
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = mdInline("**Fundo** ·"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false
            )
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
                modifier = Modifier.height(32.dp).testTag("chip_theme_black_blue")
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
                modifier = Modifier.height(32.dp).testTag("chip_theme_terrestre")
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
                modifier = Modifier.height(32.dp).testTag("chip_theme_white")
            )
        }
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
                            // Inversa EXATA da projeção do desenho (antes não batia com o zoom/pan e errava a cidade)
                            val tapProj = SpProjection(w, h, zoomScale, panOffsetX, panOffsetY, RADAR_PAD)
                            val tapLon = tapProj.lon(tapOffset.x)
                            val tapLat = tapProj.lat(tapOffset.y)
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
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val newZoom = (zoomScale * zoom).coerceIn(0.8f, 6.0f)
                            val (px, py) = SpProjection.gesturePan(
                                canvasWidth, canvasHeight, zoomScale, newZoom,
                                panOffsetX, panOffsetY, centroid.x, centroid.y, pan.x, pan.y
                            )
                            zoomScale = newZoom
                            panOffsetX = px
                            panOffsetY = py
                        }
                    }
                    .testTag("radar_interactive_canvas")
            ) {
                clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
                    val w = size.width
                    val h = size.height

                // 1. Mapa-base comum (contorno IBGE, oceano, vizinhos, grade, rios) — ui/map/MapDrawing.kt
                val proj = SpProjection(w, h, zoomScale, panOffsetX, panOffsetY, RADAR_PAD)
                val mapTheme = MapTheme.of(effectiveBgTheme)
                val statePath = drawSpBasemap(
                    proj, mapTheme, textMeasurer,
                    showRivers = effectiveMapFormat != 2,
                    showGrid = effectiveMapFormat != 2
                )
                // Formato Cartográfico: grade com coordenadas, rios e rodovias
                if (effectiveMapFormat == 2) {
                    drawCartographicBasemap(w, h, zoomScale, panOffsetX, panOffsetY, textMeasurer, effectiveBgTheme)
                }

                // 2. Campo contínuo interpolado (IDW) a partir da previsão real por cidade:
                //    camada Temp (Windy) = temperatura; demais = chuva (dBZ estimado pela taxa de chuva prevista)
                if (effectiveMapFormat == 0 && windyLayer == 2) {
                    tempImage?.let { drawScalarField(it, proj, statePath) }
                } else if (!(effectiveMapFormat == 0 && windyLayer == 0 && hasWindData && rainImage == null)) {
                    rainImage?.let { drawScalarField(it, proj, statePath) }
                }
                drawSpOutline(statePath, mapTheme)

                // Partículas de vento (Windy) por cima do campo
                if (effectiveMapFormat == 0 && windyLayer != 1 && hasWindData) {
                    drawWindyWindParticles(windParticles, w, h, effectiveBgTheme)
                }

                // 3. Anéis de alcance do radar IPMet em km REAIS (antes eram frações da largura da tela)
                val centerX = mapLonToX(radarCenterLon, w, h, zoomScale, panOffsetX)
                val centerY = mapLatToY(radarCenterLat, w, h, zoomScale, panOffsetY)
                val kmPx = proj.pxPerKm

                if (effectiveMapFormat == 1) {
                    drawCircularPpiScope(w, h, textMeasurer, centerX, centerY, effectiveSweep, isPlaying, 240f * kmPx)
                }

                val ringColors = when (effectiveBgTheme) {
                    1 -> Color(0xFFFEF08A).copy(alpha = 0.55f)
                    2 -> Color(0xFF475569).copy(alpha = 0.60f)
                    else -> Color(0xFF38BDF8).copy(alpha = 0.40f)
                }
                val ringKm = listOf(60f, 120f, 240f)
                val ringTextColor = when (effectiveBgTheme) {
                    1 -> Color(0xFFFEF9C3)
                    2 -> Color(0xFF0F172A)
                    else -> Color(0xFF94A3B8)
                }
                ringKm.forEach { km ->
                    val r = km * kmPx
                    drawCircle(
                        color = ringColors,
                        radius = r,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
                    )
                    // rótulos dos anéis são desenhados DEPOIS dos rótulos das cidades, só onde houver espaço (declutter)
                }
                val crossLen = 240f * kmPx
                drawLine(ringColors, Offset(centerX - crossLen, centerY), Offset(centerX + crossLen, centerY), strokeWidth = 1f)
                drawLine(ringColors, Offset(centerX, centerY - crossLen), Offset(centerX, centerY + crossLen), strokeWidth = 1f)

                // 3. Draw Simulated Reflectivity Rain Clusters & Storm Cell Trajectories
                drawRadarReflectivityClusters(
                    w = w,
                    h = h,
                    stations = stations,
                    timeStep = timeStep,
                    animatedStep = animatedStep,
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
                    val sweepLen = 240f * kmPx
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

                // 6. Pinos e rótulos das cidades: nome COMPLETO + valor em pílula separada, sem sobreposição
                stations.forEach { st ->
                    val isRainingHere = st.hasRealData() && (st.dbzReflectivity >= 10 || st.iconType == "rain" || st.iconType == "storm")
                    if (isRainingHere) {
                        drawRainCloud(
                            cx = proj.x(st.lon),
                            cy = proj.y(st.lat) - 11.dp.toPx(),
                            scale = 0.8f * zoomScale.coerceIn(1f, 1.5f),
                            isThunderstorm = st.dbzReflectivity >= 45 || st.iconType == "storm",
                            rainMm = st.rainVolumeMm
                        )
                    }
                }
                val showRainValue = effectiveMapFormat == 0 && windyLayer == 1
                val cityLabels = stations.map { st ->
                    val isBarretos = st.id == "barretos" || st.name.contains("Barretos", ignoreCase = true)
                    val real = st.hasRealData()
                    MapLabel(
                        key = st.id,
                        x = proj.x(st.lon),
                        y = proj.y(st.lat),
                        name = if (isBarretos) "★ ${st.name}" else st.name,
                        value = when {
                            !real -> "sem dado"
                            showRainValue -> "${fmt1(st.rainVolumeMm)} mm"
                            else -> "${kotlin.math.round(st.currentTemp).toInt()}°C"
                        },
                        valueColor = when {
                            !real -> Color(0xFF64748B)
                            showRainValue -> if (st.rainVolumeMm < 0.2) Color(0xFF94A3B8) else MapRamp.RainMm.color(st.rainVolumeMm.toFloat())
                            else -> MapRamp.TempC.color(st.currentTemp.toFloat())
                        },
                        priority = (if (isBarretos) 1000 else 0) + st.dbzReflectivity + (if (real) 100 else 0),
                        selected = st.id == selectedStation?.id
                    )
                }
                cityLabels.forEachIndexed { idx, lb ->
                    val st = stations[idx]
                    val pinColor = if (st.hasRealData() && st.dbzReflectivity >= 10) precipColor(st.dbzReflectivity.toFloat(), 1f)
                        else if (effectiveBgTheme == 2) Color(0xFF0284C7) else Color(0xFF22D3EE)
                    drawCityPin(lb.x, lb.y, pinColor, lb.selected, mapTheme)
                }
                val zoomButtons = 2 + (if (zoomScale != 1f || panOffsetX != 0f || panOffsetY != 0f) 1 else 0) + (if (userCoordinates != null) 1 else 0)
                val zoomPanelH = (16 + 40 * zoomButtons).dp.toPx()
                // Circular: máscara redonda; info centralizada embaixo e zoom no meio da borda direita (dentro do círculo)
                val clipCircle = if (effectiveMapFormat == 1) ClipCircle(Offset(w / 2f, h / 2f), min(w, h) / 2f) else null
                val uiAvoid = if (clipCircle != null) listOf(
                    Rect(w / 2f - 95.dp.toPx(), h - 44.dp.toPx(), w / 2f + 95.dp.toPx(), h), // linha de info (inferior-centro)
                    Rect(w - 74.dp.toPx(), h / 2f - zoomPanelH / 2f, w, h / 2f + zoomPanelH / 2f) // botões de zoom (direita-centro)
                ) else listOf(
                    Rect(if (effectiveMapFormat == 0) w - 200.dp.toPx() else w, 0f, w, 46.dp.toPx()), // pílulas Windy (topo-dir.)
                    Rect(0f, h - 30.dp.toPx(), w * 0.75f, h), // linha de info (inferior-esq.)
                    Rect(w - 58.dp.toPx(), h - zoomPanelH - 2.dp.toPx(), w, h) // botões de zoom
                )
                val placedLabels = drawMapLabels(
                    textMeasurer, cityLabels, mapTheme,
                    avoid = uiAvoid,
                    clipCircle = clipCircle,
                    // no círculo há menos área útil: só os 4 mais importantes podem cobrir pinos de outras cidades
                    importantCount = if (clipCircle != null) 4 else 6
                )

                // Declutter dos textos decorativos: rótulos de anel (km) e de azimute só onde não colidem
                // com rótulos/pinos de cidades nem com os botões. Antes eram desenhados por baixo e se sobrepunham.
                val occupied = ArrayList<Rect>(uiAvoid)
                placedLabels.forEach { pl -> occupied += pl.nameRect; pl.valueRect?.let { occupied += it } }
                val pinR = 5.dp.toPx()
                cityLabels.forEach { lb -> occupied += Rect(lb.x - pinR, lb.y - pinR, lb.x + pinR, lb.y + pinR) }
                val decoBg = if (effectiveBgTheme == 2) Color(0xCCF1F5F9) else Color(0x99030A14)
                ringKm.forEach { km ->
                    val r = km * kmPx
                    // tenta 8 posições sobre o anel (topo, base, laterais e diagonais); usa a primeira livre
                    val spots = listOf(0.0, 180.0, 270.0, 90.0, 315.0, 45.0, 225.0, 135.0).map { deg ->
                        val rad = Math.toRadians(deg - 90.0)
                        Offset((centerX + cos(rad) * r).toFloat(), (centerY + sin(rad) * r).toFloat())
                    }
                    for (spot in spots) {
                        if (drawTextIfFree(
                                textMeasurer, "${km.toInt()} km", spot,
                                TextStyle(color = ringTextColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                occupied, clipCircle, decoBg
                            ) != null) break
                    }
                }
                if (effectiveMapFormat == 1) {
                    val ppiR = 240f * kmPx
                    PPI_AZIMUTH_LABELS.forEach { (deg, label) ->
                        val rad = Math.toRadians(deg - 90.0)
                        // dentro do anel; se ocupado, logo fora dele
                        for (dist in listOf(ppiR - 20.dp.toPx(), ppiR + 12.dp.toPx())) {
                            if (drawTextIfFree(
                                    textMeasurer, label,
                                    Offset((centerX + cos(rad) * dist).toFloat(), (centerY + sin(rad) * dist).toFloat()),
                                    TextStyle(color = if (effectiveBgTheme == 2) Color(0xFF0369A1) else Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    occupied, clipCircle, decoBg
                                ) != null) break
                        }
                    }
                }

                // 7. Draw User GPS Location Pin Marker
                userCoordinates?.let { (uLat, uLon) ->
                    val ux = mapLonToX(uLon, w, h, zoomScale, panOffsetX)
                    val uy = mapLatToY(uLat, w, h, zoomScale, panOffsetY)

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
                            fontSize = 10.sp,
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
                        modifier = Modifier.clickable { windyLayer = 0 }.minimumInteractiveComponentSize()
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
                        modifier = Modifier.clickable { windyLayer = 1 }.minimumInteractiveComponentSize()
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
                        modifier = Modifier.clickable { windyLayer = 2 }.minimumInteractiveComponentSize()
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
                        .align(if (isCircularScope) Alignment.BottomCenter else Alignment.BottomStart)
                        .padding(if (isCircularScope) PaddingValues(bottom = 26.dp) else PaddingValues(10.dp))
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
                                text = if (st == null || st.lastUpdated == 0L) "Dados indisponíveis" else "💨 Vento: ${st.windDirection} • 🌡️ ${st.currentTemp}°C • 🌧️ ${st.rainVolumeMm} mm",
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

            // Info do mapa: 1 linha no canto inferior esquerdo (antes 2 linhas no topo, sob as pílulas Windy)
            if (sondeOffset == null) Column(
                modifier = Modifier
                    .align(if (isCircularScope) Alignment.BottomCenter else Alignment.BottomStart)
                    .padding(start = if (isCircularScope) 0.dp else 10.dp, bottom = if (isCircularScope) 18.dp else 8.dp)
                    .background(Color(0xCC091426), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = when {
                        isCircularScope && userCoordinates != null -> "🎯 GPS • ${"%.1f".format(Locale.US, zoomScale)}x"
                        isCircularScope -> "Open-Meteo • anéis 60/120/240 km"
                        userCoordinates != null -> "🎯 Foco: Localização Exata (GPS) | Zoom: ${"%.1f".format(Locale.US, zoomScale)}x"
                        else -> "Previsão Open-Meteo • anéis 60/120/240 km • ${"%.1f".format(Locale.US, zoomScale)}x"
                    },
                    color = if (userCoordinates != null) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    maxLines = 1,
                    fontWeight = if (userCoordinates != null) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            // Floating Zoom & Pan Controls (Bottom-End of Radar Box)
            Column(
                modifier = Modifier
                    // no círculo o canto inferior direito fica fora da máscara (botões ficavam cortados/inacessíveis)
                    .align(if (isCircularScope) Alignment.CenterEnd else Alignment.BottomEnd)
                    .padding(if (isCircularScope) PaddingValues(end = 14.dp) else PaddingValues(8.dp)),
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
                                .size(40.dp)
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
                                .size(40.dp)
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
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
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
                        val sdfLive = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "BR")) }
                        val sdfStep = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "BR")) }
                        val sdfDate = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR")) }

                        // Horário real do último dado recebido (nunca "AO VIVO")
                        val lastReal = stations.maxOfOrNull { it.lastUpdated } ?: 0L
                        val ageMin = ((liveTimeMillis - lastReal) / 60000L).coerceAtLeast(0)
                        val timeTitle = if (lastReal > 0L) {
                            "Último dado: ${sdfStep.format(java.util.Date(lastReal))} (há $ageMin min)"
                        } else "Dados indisponíveis"

                        Column {
                            Text(
                                text = timeTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = mdInline("• ${if (lastReal > 0L) sdfDate.format(java.util.Date(lastReal)) else "—"} • **Fonte: Open-Meteo** (previsão numérica)"),
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF64748B).copy(alpha = 0.2f)
                    ) {
                        Text(
                            "PREVISÃO",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Linha do tempo fictícia (-60 min … +30 min "nowcasting") removida: não há frames reais de radar.

                // dBZ Color Legend Bar (barra contínua em degradê)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("dBZ est.:", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Brush.horizontalGradient(colorStops = PrecipLegendStops))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("15 Fraca", "30 Mod", "45 Forte", "55 Granizo", "65+ Extrema").forEach { lbl ->
                                Text(lbl, color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        mdInline("• Campo = **chuva prevista** interpolada entre cidades (dBZ estimado, não é radar)"),
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        mdInline("*Open-Meteo*"),
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

private fun getDbzColor(dbz: Int): Color {
    return precipColor(dbz.toFloat(), 1f)
}

// Projeção comum (escala única lon/lat, sem esticar nem grampear coordenadas) — ver ui/map/SpProjection
private const val RADAR_PAD = 24f
private fun mapLonToX(lon: Double, width: Float, height: Float, zoom: Float = 1f, panX: Float = 0f): Float =
    SpProjection(width, height, zoom, panX, 0f, RADAR_PAD).x(lon)

private fun mapLatToY(lat: Double, width: Float, height: Float, zoom: Float = 1f, panY: Float = 0f): Float =
    SpProjection(width, height, zoom, 0f, panY, RADAR_PAD).y(lat)

// Draw rain clusters and storm cell trajectories
private fun DrawScope.drawRadarReflectivityClusters(
    w: Float,
    h: Float,
    stations: List<WeatherStationEntity>,
    timeStep: Int,
    animatedStep: Float = timeStep.toFloat(),
    isEnergySaver: Boolean,
    stormCells: List<StormCellTrajectory>,
    showTrajectories: Boolean,
    selectedStormCell: StormCellTrajectory?,
    textMeasurer: TextMeasurer,
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f
) {
    @Suppress("UNUSED_VARIABLE") val unusedStep = animatedStep

    // As 4 "manchas" fixas (inventadas) foram substituídas por manchas calculadas da chuva prevista
    // pela Open-Meteo em cada estação (dBZ ESTIMADO pela taxa de chuva da hora atual; não é radar).
    // Chuva por estação agora é um campo contínuo IDW (ver IpmetRadarCanvas → rainImage).

    // --- Passo 1: camada de chuva (manchas suaves) em camada offscreen com transparência uniforme ---
    val useLayer = !isEnergySaver
    if (useLayer) {
        drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = 0.72f })
    }
    stormCells.forEach { cell ->
        val dtMin = (timeStep - 4) * 15f
        val kmTraveled = cell.speedKmH * (dtMin / 60f)
        val degRad = Math.toRadians(cell.directionAngleDeg)
        val scale = (w / 500f) * zoom
        val dx = (kmTraveled * cos(degRad) * 1.8f * scale).toFloat()
        val dy = (kmTraveled * sin(degRad) * 1.8f * scale).toFloat()

        val curX = mapLonToX(cell.originLon, w, h, zoom, panX) + dx
        val curY = mapLatToY(cell.originLat, w, h, zoom, panY) + dy
        val isSelected = selectedStormCell?.id == cell.id
        val echoCore = precipColor(cell.dbzPeak.toFloat(), 1f)
        val echoOuter = precipColor((cell.dbzPeak - 12).toFloat(), 1f)
        drawEchoBlob(curX, curY, (if (isSelected) 30f else 22f) * zoom.coerceIn(0.8f, 2.0f), echoOuter, echoCore)
    }
    if (useLayer) {
        drawContext.canvas.restore()
    }

    // --- Passo 2: nuvens de chuva por cima das manchas ---
    stormCells.forEach { cell ->
        val dtMin = (timeStep - 4) * 15f
        val kmTraveled = cell.speedKmH * (dtMin / 60f)
        val degRad = Math.toRadians(cell.directionAngleDeg)
        val scale = (w / 500f) * zoom
        val dx = (kmTraveled * cos(degRad) * 1.8f * scale).toFloat()
        val dy = (kmTraveled * sin(degRad) * 1.8f * scale).toFloat()

        val curX = mapLonToX(cell.originLon, w, h, zoom, panX) + dx
        val curY = mapLatToY(cell.originLat, w, h, zoom, panY) + dy
        val isSelected = selectedStormCell?.id == cell.id
        drawRainCloud(curX, curY - 18f * zoom, scale = (if (isSelected) 1.4f else 1.15f) * zoom.coerceIn(0.8f, 1.6f), isThunderstorm = true, rainMm = cell.rainRateMmH)
    }

    // --- Passo 3: seleção e trajetórias (cálculos inalterados), sempre por cima ---
    // Draw active storm cell echoes and trajectories
    stormCells.forEach { cell ->
        val dtMin = (timeStep - 4) * 15f
        val kmTraveled = cell.speedKmH * (dtMin / 60f)
        val degRad = Math.toRadians(cell.directionAngleDeg)
        val scale = (w / 500f) * zoom
        val dx = (kmTraveled * cos(degRad) * 1.8f * scale).toFloat()
        val dy = (kmTraveled * sin(degRad) * 1.8f * scale).toFloat()

        val curX = mapLonToX(cell.originLon, w, h, zoom, panX) + dx
        val curY = mapLatToY(cell.originLat, w, h, zoom, panY) + dy

        val cellColor = if (cell.dbzPeak >= 55) Color(0xFFD500F9) else if (cell.dbzPeak >= 45) Color(0xFFFF1744) else Color(0xFFFF9100)
        val isSelected = selectedStormCell?.id == cell.id


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
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                style = TextStyle(color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )

            // ETA tags (+15m, +30m, +45m)
            safeDrawText(
                textMeasurer = textMeasurer,
                text = "+15m",
                topLeft = Offset(fX15 + 6f, fY15 + 4f),
                style = TextStyle(color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
            safeDrawText(
                textMeasurer = textMeasurer,
                text = "+30m: ${cell.eta30m.substringBefore(" (")}",
                topLeft = Offset(fX30 + 6f, fY30 - 12f),
                style = TextStyle(color = Color(0xFFFFAB00), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
            safeDrawText(
                textMeasurer = textMeasurer,
                text = "+45m (Impacto)",
                topLeft = Offset(fX45 + 8f, fY45 - 14f),
                style = TextStyle(color = Color(0xFFFF5252), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

private fun DrawScope.drawEchoBlob(cx: Float, cy: Float, radius: Float, outerColor: Color, centerColor: Color) {
    val r = radius * 1.35f   // borda difusa maior
    // Sub-manchas em posições fixas (determinísticas) para um formato menos "redondo"
    val subBlobs = arrayOf(
        floatArrayOf(0f, 0f, 1f),
        floatArrayOf(0.25f, -0.15f, 0.75f),
        floatArrayOf(-0.22f, 0.18f, 0.7f)
    )
    val mid = lerp(centerColor, outerColor, 0.5f)
    val fringe = Color(0xFF3DBA3D)
    for (sb in subBlobs) {
        val c = Offset(cx + sb[0] * r, cy + sb[1] * r)
        val rr = r * sb[2]
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to centerColor.copy(alpha = 1.0f),
                0.30f to mid.copy(alpha = 0.92f),
                0.60f to outerColor.copy(alpha = 0.75f),
                0.85f to fringe.copy(alpha = 0.40f),
                1.00f to Color.Transparent,
                center = c,
                radius = rr
            ),
            radius = rr,
            center = c
        )
    }
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
    isPlaying: Boolean,
    radiusPx: Float = min(w, h) * 0.47f
) {
    // Centrado no RADAR (antes ficava no centro do canvas, desalinhado dos anéis e das cidades)
    val radius = radiusPx
    val center = Offset(centerX, centerY)

    // Outer instrument ring (phosphor cyan)
    drawCircle(
        color = Color(0xFF00E5FF).copy(alpha = 0.85f),
        radius = radius,
        center = center,
        style = Stroke(width = 2.5f)
    )

    // Rótulos de azimute: desenhados depois das cidades com declutter (PPI_AZIMUTH_LABELS / drawTextIfFree)

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

}

/** Azimutes do PPI em pt-BR (L = leste, O = oeste), curtos para não poluir o mapa. */
private val PPI_AZIMUTH_LABELS = listOf(
    0.0 to "N", 45.0 to "NE", 90.0 to "L", 135.0 to "SE",
    180.0 to "S", 225.0 to "SO", 270.0 to "O", 315.0 to "NO"
)

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
        val py = mapLatToY(lat, w, h, zoom, panY)
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
            style = TextStyle(color = gridTextColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        )
    }

    // Meridianos (-52°W, -50°W, -48°W, -46°W)
    val meridians = listOf(-52.0, -50.0, -48.0, -46.0)
    meridians.forEach { lon ->
        val px = mapLonToX(lon, w, h, zoom, panX)
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
            style = TextStyle(color = gridTextColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        )
    }

    // Rio Tietê
    val tietePts = listOf(
        Pair(-23.53, -46.0), Pair(-23.45, -46.8), Pair(-23.1, -47.4),
        Pair(-22.7, -48.4), Pair(-22.2, -49.2), Pair(-21.6, -49.9), Pair(-20.7, -51.3)
    )
    val tietePath = Path().apply {
        tietePts.forEachIndexed { i, (lat, lon) ->
            val x = mapLonToX(lon, w, h, zoom, panX)
            val y = mapLatToY(lat, w, h, zoom, panY)
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
            val x = mapLonToX(lon, w, h, zoom, panX)
            val y = mapLatToY(lat, w, h, zoom, panY)
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
            val x = mapLonToX(lon, w, h, zoom, panX)
            val y = mapLatToY(lat, w, h, zoom, panY)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(sp310Path, color = roadColor2.copy(alpha = 0.70f), style = Stroke(width = 1.6f * zoom.coerceIn(0.8f, 2.0f)))

    // (legenda "Base Cartográfica" removida: ficava sob a linha de info do mapa)
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
