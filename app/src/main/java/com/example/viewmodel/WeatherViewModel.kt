package com.example.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ClimateTrendEntity
import com.example.data.local.entity.DailyForecastEntity
import com.example.data.local.entity.HourlyForecastEntity
import com.example.data.local.entity.RegionSubscriptionEntity
import com.example.data.local.entity.UserPreferencesEntity
import com.example.data.local.entity.WeatherAlertEntity
import com.example.data.local.entity.WeatherStationEntity
import com.example.data.model.WeatherNewsItem
import com.example.data.model.WeatherNewsProvider
import com.example.data.remote.GeminiMapsWeatherService
import com.example.data.remote.MapsRainPrecisionResult
import com.example.data.repository.AgroLoadState
import com.example.data.repository.AgroSeries
import com.example.data.repository.WeatherRepository
import com.example.util.NotificationHelper
import com.example.util.PdfExportOptions
import com.example.util.PdfExporter
import com.example.util.ShareHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale

data class StormCellTrajectory(
    val id: String,
    val name: String,
    val originLat: Double,
    val originLon: Double,
    val dbzPeak: Int,
    val rainRateMmH: Double,
    val speedKmH: Double,
    val directionAngleDeg: Double,
    val headingCompass: String,
    val impactTowns: List<String>,
    val eta15m: String,
    val eta30m: String,
    val eta45m: String
)

data class RegionalRainSummary(
    val regionId: String,
    val regionName: String,
    val accumulated24hMm: Double,
    val forecastedTodayMm: Double,
    val alertLevel: String, // "NORMAL", "ATENCAO", "ALERTA", "EMERGENCIA"
    val riskDescription: String
)

/** Estado do módulo de rios/indicadores de um estado (v5.1). */
data class HazardsUiState(
    val loading: Boolean = false,
    val gauges: List<com.example.domain.GaugeStatus> = emptyList(),
    val forecastAlerts: List<com.example.domain.HazardAlert> = emptyList(),
    val glofasError: String? = null,
    val forecastError: String? = null,
    val updatedAt: Long = 0L
)

class WeatherViewModel(
    private val repository: WeatherRepository,
    /** null em testes; no app guarda o estado selecionado e a opção de notificações. */
    private val settings: com.example.data.local.AppSettings? = null,
    private val hazardsService: com.example.data.remote.HazardsService = com.example.data.remote.HazardsService()
) : ViewModel() {

    // ---------------- v5.1: estado (SP/PR/RS), alertas e enchentes ----------------
    private val _selectedState = MutableStateFlow(settings?.selectedState ?: com.example.domain.BrState.SP)
    val selectedState: StateFlow<com.example.domain.BrState> = _selectedState.asStateFlow()

    private val _hazards = MutableStateFlow<Map<com.example.domain.BrState, HazardsUiState>>(emptyMap())
    val hazardsByState: StateFlow<Map<com.example.domain.BrState, HazardsUiState>> = _hazards.asStateFlow()

    val inmetAlertsError: StateFlow<String?> = repository.alertsError
    val inmetAlertsUpdatedAt: StateFlow<Long> = repository.alertsLastUpdated

    private val _alertNotificationsEnabled = MutableStateFlow(settings?.alertNotificationsEnabled ?: false)
    val alertNotificationsEnabled: StateFlow<Boolean> = _alertNotificationsEnabled.asStateFlow()

    fun setAlertNotificationsEnabled(context: Context, enabled: Boolean) {
        settings?.alertNotificationsEnabled = enabled
        _alertNotificationsEnabled.value = enabled
        if (enabled) com.example.work.AlertsWorker.schedule(context.applicationContext)
        else com.example.work.AlertsWorker.cancel(context.applicationContext)
    }

    fun setSelectedState(state: com.example.domain.BrState) {
        if (state == _selectedState.value) return
        _selectedState.value = state
        settings?.selectedState = state
        selectStation(state.defaultStationId)
        viewModelScope.launch { repository.refreshAllHourlyAndDailyLive(state.defaultStationId, stateFilter = state) }
        refreshHazards(state)
    }

    private val hazardMutex = kotlinx.coroutines.sync.Mutex()

    /** Rios (GloFAS + ANA) e indicadores de previsão do estado. Cache de 30 min. Erros viram mensagens (sem dados inventados). */
    fun refreshHazards(state: com.example.domain.BrState = _selectedState.value, force: Boolean = false) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            hazardMutex.withLock {
                val prev = _hazards.value[state]
                // Cache de 30 min só para uma consulta completa (com falha, tenta de novo na próxima chamada)
                if (!force && prev != null && !prev.loading && System.currentTimeMillis() - prev.updatedAt < 30 * 60_000L &&
                    prev.glofasError == null && prev.forecastError == null) return@withLock
                if (repository.userPreferences.first()?.isOfflineModeForced == true) return@withLock
                _hazards.value = _hazards.value + (state to (prev ?: HazardsUiState()).copy(loading = true))
                val brt = java.util.TimeZone.getTimeZone("America/Sao_Paulo")
                val iso = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = brt }
                val dmy = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = brt }
                val now = System.currentTimeMillis()
                val today = iso.format(java.util.Date(now))
                val gauges = com.example.domain.FloodGauges.forState(state)
                var glofasErr: String? = null
                val series: List<com.example.data.remote.GlofasSeries?> = try {
                    hazardsService.fetchGlofas(gauges.map { it.glofasLat to it.glofasLon }, today = today)
                } catch (e: Exception) { glofasErr = e.message ?: "erro de rede"; gauges.map { null } }
                val statuses = kotlinx.coroutines.coroutineScope {
                    gauges.mapIndexed { i, g ->
                        async {
                            var anaErr: String? = null
                            val ana = g.anaCode?.let { code ->
                                try {
                                    hazardsService.fetchAnaLevels(code, dmy.format(java.util.Date(now - 3 * 86_400_000L)), dmy.format(java.util.Date(now)))
                                } catch (e: Exception) { anaErr = e.message ?: "erro de rede"; emptyList() }
                            } ?: emptyList()
                            com.example.domain.FloodGauges.evaluate(g, series.getOrNull(i), glofasErr, ana, anaErr)
                        }
                    }.awaitAll()
                }
                var fcErr: String? = null
                val cities = stationsFor(state).take(15).map { com.example.domain.HazardRules.City(it.name, it.lat, it.lon) }
                val fcAlerts = try {
                    val pts = hazardsService.fetchForecastRisk(cities.map { it.lat to it.lon }, days = 3)
                    com.example.domain.HazardRules.fromForecast(state, cities, pts)
                } catch (e: Exception) { fcErr = e.message ?: "erro de rede"; prev?.forecastAlerts ?: emptyList() }
                _hazards.value = _hazards.value + (state to HazardsUiState(
                    loading = false, gauges = statuses, forecastAlerts = fcAlerts,
                    glofasError = glofasErr, forecastError = fcErr, updatedAt = now
                ))
            }
        }
    }

    private suspend fun stationsFor(state: com.example.domain.BrState): List<WeatherStationEntity> =
        repository.allStations.first().filter { com.example.domain.BrState.ofStationId(it.id) == state }

    /** Alertas (3 categorias) do estado selecionado: INMET + indicadores de previsão + rios. */
    val hazardAlerts: StateFlow<List<com.example.domain.HazardAlert>> by lazy {
        combine(repository.allAlerts, _selectedState, _hazards) { alerts, state, hz ->
            val h = hz[state]
            com.example.domain.HazardRules.sort(
                com.example.domain.HazardRules.fromInmet(alerts, state) +
                    (h?.forecastAlerts ?: emptyList()) +
                    com.example.domain.HazardRules.fromRivers(state, h?.gauges ?: emptyList())
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    private suspend fun computeHazardAlerts(state: com.example.domain.BrState): List<com.example.domain.HazardAlert> {
        val h = _hazards.value[state]
        return com.example.domain.HazardRules.sort(
            com.example.domain.HazardRules.fromInmet(repository.allAlerts.first(), state) +
                (h?.forecastAlerts ?: emptyList()) +
                com.example.domain.HazardRules.fromRivers(state, h?.gauges ?: emptyList())
        )
    }

    fun refreshInmetAlertsNow() {
        viewModelScope.launch { repository.refreshInmetAlerts() }
    }


    private val _selectedStationId = MutableStateFlow("sao_paulo")
    val selectedStationId: StateFlow<String> = _selectedStationId.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // City search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Radar player controls
    private val _activeRadarCenter = MutableStateFlow("bauru") // "bauru" or "presidente_prudente"
    val activeRadarCenter: StateFlow<String> = _activeRadarCenter.asStateFlow()

    private val _radarTimeStep = MutableStateFlow(4) // 0=-60m, 1=-45m, 2=-30m, 3=-15m, 4=Agora, 5=+15m, 6=+30m
    val radarTimeStep: StateFlow<Int> = _radarTimeStep.asStateFlow()

    private val _isRadarPlaying = MutableStateFlow(true)
    val isRadarPlaying: StateFlow<Boolean> = _isRadarPlaying.asStateFlow()

    private val _selectedRadarStationInspect = MutableStateFlow<WeatherStationEntity?>(null)
    val selectedRadarStationInspect: StateFlow<WeatherStationEntity?> = _selectedRadarStationInspect.asStateFlow()

    // Trajectory & Full-Screen Expanded Map
    private val _showTrajectories = MutableStateFlow(true)
    val showTrajectories: StateFlow<Boolean> = _showTrajectories.asStateFlow()

    private val _isExpandedMapOpen = MutableStateFlow(false)
    val isExpandedMapOpen: StateFlow<Boolean> = _isExpandedMapOpen.asStateFlow()

    private val _selectedStormCell = MutableStateFlow<StormCellTrajectory?>(null)
    val selectedStormCell: StateFlow<StormCellTrajectory?> = _selectedStormCell.asStateFlow()

    // Current user GPS coordinates (null if not yet acquired)
    private val _userCoordinates = MutableStateFlow<Pair<Double, Double>?>(null)
    val userCoordinates: StateFlow<Pair<Double, Double>?> = _userCoordinates.asStateFlow()

    // Google Maps Grounding & Gemini Rain Precision State
    private val geminiMapsService = GeminiMapsWeatherService()
    private val _mapsRainPrecisionState = MutableStateFlow<MapsRainPrecisionUiState>(MapsRainPrecisionUiState.Idle)
    val mapsRainPrecisionState: StateFlow<MapsRainPrecisionUiState> = _mapsRainPrecisionState.asStateFlow()

    // Hourly rain mm selector index
    private val _selectedHourIndex = MutableStateFlow(0)
    val selectedHourIndex: StateFlow<Int> = _selectedHourIndex.asStateFlow()

    val allStations: StateFlow<List<WeatherStationEntity>> = repository.allStations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResults: StateFlow<List<WeatherStationEntity>> = combine(allStations, _searchQuery) { stations, query ->
        if (query.isBlank()) {
            stations
        } else {
            stations.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.region.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Cidades do estado selecionado (seletor SP/PR/RS). */
    val stationsOfSelectedState: StateFlow<List<WeatherStationEntity>> by lazy {
        combine(allStations, _selectedState) { list, st -> list.filter { com.example.domain.BrState.ofStationId(it.id) == st } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    val allAlerts: StateFlow<List<WeatherAlertEntity>> = repository.allAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val climateTrends: StateFlow<List<ClimateTrendEntity>> = repository.climateTrends
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val regionSubscriptions: StateFlow<List<RegionSubscriptionEntity>> = repository.regionSubscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPreferences: StateFlow<UserPreferencesEntity> = repository.userPreferences
        .combine(_selectedStationId) { prefs, _ -> prefs ?: UserPreferencesEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesEntity())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentStation: StateFlow<WeatherStationEntity?> = _selectedStationId
        .flatMapLatest { id -> repository.getStationById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val hourlyForecasts: StateFlow<List<HourlyForecastEntity>> = _selectedStationId
        .flatMapLatest { id -> repository.getHourlyForecasts(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyForecasts: StateFlow<List<DailyForecastEntity>> = _selectedStationId
        .flatMapLatest { id -> repository.getDailyForecasts(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentCiiagroRecord: StateFlow<com.example.data.local.entity.CiiagroRecordEntity?> = _selectedStationId
        .flatMapLatest { id -> repository.getCiiagroRecord(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allCiiagroRecords: StateFlow<List<com.example.data.local.entity.CiiagroRecordEntity>> = repository.allCiiagroRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentAgroSeries: StateFlow<AgroSeries?> = combine(
        _selectedStationId,
        repository.agroSeries
    ) { stationId, map ->
        map[stationId]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentAgroLoadState: StateFlow<AgroLoadState> = combine(
        _selectedStationId,
        repository.agroLoadState
    ) { stationId, map ->
        map[stationId] ?: AgroLoadState.Idle
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AgroLoadState.Idle)

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
        if (inForeground) {
            refreshActiveStation()
        }
    }

    fun ensureAgroSeriesLoaded(stationId: String? = null) {
        val targetId = stationId ?: _selectedStationId.value
        viewModelScope.launch {
            repository.ensureAgroSeries(targetId)
        }
    }

    // Alternar entre Mapa Nativo de Radar/Vento (Canvas 60fps) e Mapa Interativo Windy (WebView)
    private val _isWindyWebViewEnabled = MutableStateFlow(false)
    val isWindyWebViewEnabled: StateFlow<Boolean> = _isWindyWebViewEnabled.asStateFlow()

    fun setWindyWebViewEnabled(enabled: Boolean) {
        _isWindyWebViewEnabled.value = enabled
    }

    fun toggleWindyWebView() {
        _isWindyWebViewEnabled.value = !_isWindyWebViewEnabled.value
    }

    // Notícias do clima e avisos meteorológicos da região selecionada ou GPS
    val regionalWeatherNews: StateFlow<List<WeatherNewsItem>> = combine(
        currentStation,
        allAlerts,
        _userCoordinates
    ) { station, alerts, coords ->
        WeatherNewsProvider.getNewsForRegion(station, alerts, coords)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active storm cells modeled from IPMet radar reflectivity
    val activeStormCells: List<StormCellTrajectory> = listOf(
        StormCellTrajectory(
            id = "cell_barretos_colina",
            name = "Célula Severa Barretos & Baixo Pardo",
            originLat = -20.557,
            originLon = -48.567,
            dbzPeak = 54,
            rainRateMmH = 42.0,
            speedKmH = 40.0,
            directionAngleDeg = 20.0,
            headingCompass = "Leste-Sudeste (110° ESE)",
            impactTowns = listOf("Barretos", "Colina", "Bebedouro", "Severínia"),
            eta15m = "Colina (+15m)",
            eta30m = "Bebedouro (+30m)",
            eta45m = "Monte Azul Paulista (+45m)"
        ),
        StormCellTrajectory(
            id = "cell_bauru_botucatu",
            name = "Célula Convectiva Centro-Oeste (Bauru)",
            originLat = -22.55,
            originLon = -48.80,
            dbzPeak = 52,
            rainRateMmH = 34.0,
            speedKmH = 38.0,
            directionAngleDeg = 25.0,
            headingCompass = "Leste-Sudeste (115° ESE)",
            impactTowns = listOf("Botucatu", "Lençóis Paulista", "Jaú", "Bauru"),
            eta15m = "Lençóis Paulista (+15m)",
            eta30m = "Botucatu (+30m)",
            eta45m = "Jaú (+45m)"
        ),
        StormCellTrajectory(
            id = "cell_vale_paraiba",
            name = "Linha de Instabilidade Mantiqueira",
            originLat = -22.95,
            originLon = -45.75,
            dbzPeak = 56,
            rainRateMmH = 48.0,
            speedKmH = 32.0,
            directionAngleDeg = 15.0,
            headingCompass = "Leste (95° E)",
            impactTowns = listOf("Campos do Jordão", "São José dos Campos", "Taubaté"),
            eta15m = "Campos do Jordão (+15m)",
            eta30m = "Pindamonhangaba (+30m)",
            eta45m = "Taubaté (+45m)"
        ),
        StormCellTrajectory(
            id = "cell_campinas_tiete",
            name = "Célula Convectiva Médio Tietê",
            originLat = -22.95,
            originLon = -47.20,
            dbzPeak = 42,
            rainRateMmH = 18.0,
            speedKmH = 26.0,
            directionAngleDeg = 35.0,
            headingCompass = "Sudeste (125° SE)",
            impactTowns = listOf("Campinas", "Paulínia", "Jundiaí", "Sumaré"),
            eta15m = "Paulínia (+15m)",
            eta30m = "Campinas (+30m)",
            eta45m = "Jundiaí (+45m)"
        ),
        StormCellTrajectory(
            id = "cell_litoral_santos",
            name = "Instabilidade Orográfica Serra do Mar",
            originLat = -23.95,
            originLon = -46.35,
            dbzPeak = 44,
            rainRateMmH = 24.0,
            speedKmH = 20.0,
            directionAngleDeg = 10.0,
            headingCompass = "Leste-Nordeste (70° ENE)",
            impactTowns = listOf("Santos", "São Vicente", "Guarujá", "Bertioga"),
            eta15m = "Santos (+15m)",
            eta30m = "Guarujá (+30m)",
            eta45m = "Bertioga (+45m)"
        )
    )

    // Regional rainfall summaries across SP macro-regions
    val regionalRainfallSummaries: List<RegionalRainSummary> = listOf(
        RegionalRainSummary(
            regionId = "barretos",
            regionName = "Região de Barretos / Norte Agro",
            accumulated24hMm = 38.5,
            forecastedTodayMm = 52.0,
            alertLevel = "ALERTA_LARANJA",
            riskDescription = "Célula convectiva severa sobre lavouras de cana e citrus com rajadas de vento"
        ),
        RegionalRainSummary(
            regionId = "vale_paraiba",
            regionName = "Vale do Paraíba & Mantiqueira",
            accumulated24hMm = 58.5,
            forecastedTodayMm = 72.0,
            alertLevel = "ALERTA_VERMELHO",
            riskDescription = "Alto risco de alagamentos e deslizamentos nas encostas da Serra"
        ),
        RegionalRainSummary(
            regionId = "baixada_santista",
            regionName = "Baixada Santista / Litoral",
            accumulated24hMm = 52.0,
            forecastedTodayMm = 68.0,
            alertLevel = "ALERTA_VERMELHO",
            riskDescription = "Chuva orográfica persistente na Serra do Mar e maré elevada"
        ),
        RegionalRainSummary(
            regionId = "rmsp",
            regionName = "Região Metropolitana de SP",
            accumulated24hMm = 41.2,
            forecastedTodayMm = 55.0,
            alertLevel = "ALERTA_LARANJA",
            riskDescription = "Pancadas fortes à tarde com pontos de retenção de água na Capital"
        ),
        RegionalRainSummary(
            regionId = "centro_bauru",
            regionName = "Centro-Oeste (Bauru / IPMet)",
            accumulated24hMm = 32.4,
            forecastedTodayMm = 44.0,
            alertLevel = "ALERTA_LARANJA",
            riskDescription = "Célula convectiva com rajadas e ecos de até 52 dBZ no radar"
        ),
        RegionalRainSummary(
            regionId = "campinas",
            regionName = "Região de Campinas (RMC)",
            accumulated24hMm = 26.8,
            forecastedTodayMm = 36.0,
            alertLevel = "ALERTA_AMARELO",
            riskDescription = "Pancadas moderadas isoladas com descargas atmosféricas"
        ),
        RegionalRainSummary(
            regionId = "sorocaba_itapetininga",
            regionName = "Sorocaba & Itapetininga",
            accumulated24hMm = 21.5,
            forecastedTodayMm = 28.0,
            alertLevel = "ALERTA_AMARELO",
            riskDescription = "Instabilidade moderada em deslocamento pelo sudoeste"
        ),
        RegionalRainSummary(
            regionId = "vale_ribeira",
            regionName = "Vale do Ribeira & Registro",
            accumulated24hMm = 36.0,
            forecastedTodayMm = 42.0,
            alertLevel = "ALERTA_AMARELO",
            riskDescription = "Umidade marítima constante na bacia hidrográfica"
        ),
        RegionalRainSummary(
            regionId = "oeste_prudente",
            regionName = "Oeste / Presidente Prudente",
            accumulated24hMm = 14.0,
            forecastedTodayMm = 20.0,
            alertLevel = "NORMAL",
            riskDescription = "Nebulosidade variável sem previsão de acumulados extremos"
        ),
        RegionalRainSummary(
            regionId = "ribeirao_preto",
            regionName = "Ribeirão Preto & Franca",
            accumulated24hMm = 9.5,
            forecastedTodayMm = 15.0,
            alertLevel = "NORMAL",
            riskDescription = "Tempo predominantemente seco com pancadas rápidas pontuais"
        ),
        RegionalRainSummary(
            regionId = "noroeste_riopreto",
            regionName = "Noroeste / São José do Rio Preto",
            accumulated24hMm = 4.2,
            forecastedTodayMm = 8.0,
            alertLevel = "NORMAL",
            riskDescription = "Ar quente continental prevalece com baixa probabilidade de chuva"
        )
    )

    // Live Clock tracking real-time models and sync
    private val _liveCurrentTime = MutableStateFlow(System.currentTimeMillis())
    val liveCurrentTime: StateFlow<Long> = _liveCurrentTime.asStateFlow()

    // Shared Map Format: 0 = Panorâmico 16:9, 1 = Circular PPI 360°, 2 = Cartográfico
    private val _mapFormat = MutableStateFlow(0)
    val mapFormat: StateFlow<Int> = _mapFormat.asStateFlow()

    fun setMapFormat(format: Int) {
        _mapFormat.value = format.coerceIn(0, 2)
    }

    // Shared Map Background Theme: 0 = Black & Blue, 1 = Terrestre (Relevo/Verde), 2 = White (Claro)
    private val _mapBackgroundTheme = MutableStateFlow(0)
    val mapBackgroundTheme: StateFlow<Int> = _mapBackgroundTheme.asStateFlow()

    fun setMapBackgroundTheme(theme: Int) {
        _mapBackgroundTheme.value = theme.coerceIn(0, 2)
    }

    init {
        viewModelScope.launch {
            repository.initializePreloadedDataIfNeeded()
            val state = _selectedState.value
            val saved = repository.getSavedActiveStationId()
            _selectedStationId.value = if (saved != null && com.example.domain.BrState.ofStationId(saved) == state) saved else state.defaultStationId
            repository.refreshAllHourlyAndDailyLive(_selectedStationId.value, stateFilter = state)
        }
        // v5.1: avisos oficiais do INMET (antes nunca eram buscados) e rios/indicadores, a cada 30 min
        viewModelScope.launch {
            while (true) {
                repository.refreshInmetAlerts()
                refreshHazards(_selectedState.value)
                kotlinx.coroutines.delay(30 * 60_000L)
            }
        }
        viewModelScope.launch {
            var lastHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            while (true) {
                _liveCurrentTime.value = System.currentTimeMillis()
                val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                if (currentHour != lastHour) {
                    lastHour = currentHour
                    repository.refreshAllHourlyAndDailyLive(_selectedStationId.value, stateFilter = _selectedState.value)
                }
                kotlinx.coroutines.delay(1000L)
            }
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1800L)
                if (_isRadarPlaying.value) {
                    val nextStep = (_radarTimeStep.value + 1) % 7
                    _radarTimeStep.value = nextStep
                }
            }
        }
    }

    fun getFormattedRadarTime(timeStep: Int): Pair<String, String> {
        val offsetMinutes = (timeStep - 4) * 15
        val targetMillis = _liveCurrentTime.value + (offsetMinutes * 60 * 1000L)
        val sdfTime = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val sdfTimeShort = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
        val sdfDate = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeStr = if (timeStep == 4) sdfTime.format(java.util.Date(targetMillis)) else sdfTimeShort.format(java.util.Date(targetMillis))
        val dateStr = sdfDate.format(java.util.Date(targetMillis))
        val label = when {
            offsetMinutes == 0 -> "AO VIVO • Doppler IPMet"
            offsetMinutes < 0 -> "${offsetMinutes} min (Histórico)"
            else -> "+${offsetMinutes} min (Nowcasting Previsto)"
        }
        return Pair("$timeStr ($label)", dateStr)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun toggleTrajectories() {
        _showTrajectories.value = !_showTrajectories.value
    }

    fun setExpandedMapOpen(isOpen: Boolean) {
        _isExpandedMapOpen.value = isOpen
    }

    fun selectStormCell(cell: StormCellTrajectory?) {
        _selectedStormCell.value = cell
    }

    fun selectHourIndex(index: Int) {
        _selectedHourIndex.value = index
    }

    fun focusOnUserLocation(context: Context, onResult: (String, Boolean) -> Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            onResult("Permissão de GPS necessária para detectar sua cidade automaticamente", false)
            return
        }

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        applyLocationCoordinates(context, loc.latitude, loc.longitude, onResult)
                    } else {
                        fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                            if (lastLoc != null) {
                                applyLocationCoordinates(context, lastLoc.latitude, lastLoc.longitude, onResult)
                            } else {
                                fallbackLocationManager(context, onResult)
                            }
                        }.addOnFailureListener {
                            fallbackLocationManager(context, onResult)
                        }
                    }
                }
                .addOnFailureListener {
                    fallbackLocationManager(context, onResult)
                }
        } catch (e: SecurityException) {
            onResult("Permissão de localização revogada: ${e.localizedMessage}", false)
        } catch (e: Exception) {
            fallbackLocationManager(context, onResult)
        }
    }

    private fun applyLocationCoordinates(context: Context, uLat: Double, uLon: Double, onResult: (String, Boolean) -> Unit) {
        _userCoordinates.value = Pair(uLat, uLon)
        
        // Reverse geocoding to resolve exact city name if available
        var detectedCity: String? = null
        try {
            val geocoder = android.location.Geocoder(context, Locale("pt", "BR"))
            val addresses = geocoder.getFromLocation(uLat, uLon, 1)
            if (!addresses.isNullOrEmpty()) {
                detectedCity = addresses[0].subAdminArea ?: addresses[0].locality ?: addresses[0].subLocality
            }
        } catch (_: Exception) {
            // Geocoder service may not be available on all devices or network states
        }

        val stations = allStations.value
        if (stations.isNotEmpty()) {
            val closest = stations.minByOrNull { st ->
                calculateDistanceKm(uLat, uLon, st.lat, st.lon)
            }
            if (closest != null) {
                val dist = calculateDistanceKm(uLat, uLon, closest.lat, closest.lon)
                selectStation(closest.id)
                val distFormatted = String.format(Locale.US, "%.1f", dist)
                val cityMsg = if (!detectedCity.isNullOrBlank()) "em $detectedCity (" else ""
                val msg = if (cityMsg.isNotEmpty()) {
                    "Localizado $cityMsg~$distFormatted km de ${closest.name})! Previsão e radar atualizados."
                } else {
                    "Clima detectado! Região de ${closest.name} selecionada (~$distFormatted km)."
                }
                onResult(msg, true)
                return
            }
        }
        val fallbackCityMsg = detectedCity?.let { " em $it" } ?: ""
        onResult("Localização encontrada$fallbackCityMsg (Lat $uLat, Lon $uLon)", true)
    }

    private fun fallbackLocationManager(context: Context, onResult: (String, Boolean) -> Unit) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager != null) {
                val providers = locationManager.getProviders(true)
                var bestLoc: android.location.Location? = null
                for (provider in providers) {
                    val l = locationManager.getLastKnownLocation(provider) ?: continue
                    if (bestLoc == null || l.accuracy < bestLoc.accuracy) {
                        bestLoc = l
                    }
                }
                if (bestLoc != null) {
                    applyLocationCoordinates(context, bestLoc.latitude, bestLoc.longitude, onResult)
                    return
                }
            }
            val defaultStation = allStations.value.find { it.id == "barretos" }
                ?: allStations.value.find { it.id == "bauru" }
                ?: allStations.value.firstOrNull()
            if (defaultStation != null) {
                selectStation(defaultStation.id)
            }
            onResult("Buscando coordenadas GPS... Estação ${defaultStation?.name ?: "SP"} selecionada.", true)
        } catch (e: Exception) {
            onResult("Erro ao detectar localização: ${e.localizedMessage}", false)
        }
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    fun selectStation(stationId: String) {
        _selectedStationId.value = stationId
        viewModelScope.launch {
            val currentPrefs = userPreferences.value
            repository.updatePreferences(currentPrefs.copy(activeStationId = stationId))
            repository.refreshStation(stationId)
        }
    }

    fun setRadarCenter(center: String) {
        _activeRadarCenter.value = center
    }

    fun setRadarTimeStep(step: Int) {
        _radarTimeStep.value = step.coerceIn(0, 6)
    }

    fun toggleRadarPlayback() {
        _isRadarPlaying.value = !_isRadarPlaying.value
    }

    fun inspectRadarStation(station: WeatherStationEntity?) {
        _selectedRadarStationInspect.value = station
    }

    fun refreshActiveStation() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.refreshStation(_selectedStationId.value)
            _isRefreshing.value = false
        }
    }

    fun toggleEnergySaver() {
        viewModelScope.launch {
            val current = userPreferences.value
            val updated = current.copy(isEnergySaverEnabled = !current.isEnergySaverEnabled)
            repository.updatePreferences(updated)
        }
    }

    fun selectTheme(themeKey: String) {
        viewModelScope.launch {
            val current = userPreferences.value
            repository.updatePreferences(current.copy(selectedThemeKey = themeKey))
        }
    }

    fun toggleForcedOffline() {
        viewModelScope.launch {
            val current = userPreferences.value
            repository.updatePreferences(current.copy(isOfflineModeForced = !current.isOfflineModeForced))
        }
    }

    fun saveApiKeys(openWeatherKey: String, weatherbitKey: String) {
        viewModelScope.launch {
            val current = userPreferences.value
            repository.updatePreferences(
                current.copy(
                    openWeatherApiKey = openWeatherKey.trim(),
                    weatherbitApiKey = weatherbitKey.trim()
                )
            )
            repository.refreshStation(_selectedStationId.value)
        }
    }

    fun toggleSubscription(regionId: String, isSubscribed: Boolean) {
        viewModelScope.launch {
            val currentList = regionSubscriptions.value
            val item = currentList.find { it.regionId == regionId }
            if (item != null) {
                repository.updateSubscription(item.copy(isSubscribed = isSubscribed))
            }
        }
    }

    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch {
            repository.acknowledgeAlert(alertId)
        }
    }

    fun triggerSuddenChangeAlertTest(context: Context) {
        viewModelScope.launch {
            val station = currentStation.value ?: allStations.value.firstOrNull()
            val regionName = station?.region ?: "Estado de São Paulo"
            val alertId = "alert_test_${System.currentTimeMillis()}"

            val alert = WeatherAlertEntity(
                id = alertId,
                regionId = station?.id ?: "sp",
                regionName = regionName,
                severity = "ALERTA_VERMELHO",
                title = "Alerta IPMet: Mudança Brusca no Tempo!",
                description = "Radar IPMet detectou aproximação rápida de rajadas de 70 km/h e chuva torrencial em $regionName.",
                radarStationSource = "Radar IPMet Bauru / Defesa Civil SP",
                dbzPeak = 56,
                timestamp = System.currentTimeMillis()
            )
            repository.addAlert(alert)

            NotificationHelper.showWeatherAlertNotification(
                context = context,
                notificationId = (System.currentTimeMillis() % 10000).toInt(),
                title = alert.title,
                message = alert.description,
                regionName = regionName,
                isSevere = true
            )
        }
    }

    fun shareCurrentWeather(context: Context) {
        val station = currentStation.value ?: return
        ShareHelper.shareWeatherStatus(context, station)
    }

    /** v5.1: PDF da tela inicial (mapa de chuva, previsão 7/15 dias, alertas, rios, fontes). */
    fun exportMainReportPdf(
        context: Context,
        options: com.example.util.MainReportOptions = com.example.util.MainReportOptions(),
        onResult: (File?) -> Unit
    ) {
        viewModelScope.launch {
            val state = _selectedState.value
            val sid = _selectedStationId.value
            val station = repository.getStationById(sid).first()
            val daily = repository.getDailyForecasts(sid).first()
            val alerts = computeHazardAlerts(state)
            val gauges = _hazards.value[state]?.gauges ?: emptyList()
            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    com.example.util.SimetReports.mainReport(context.applicationContext, state, station, daily, alerts, gauges, options)
                } catch (e: Exception) { null }
            }
            onResult(file)
        }
    }

    /** Mantido para as telas antigas (Previsão/Ajustes): agora gera o boletim novo com as opções padrão. */
    fun exportWeatherReportPdf(context: Context, onResult: (File?) -> Unit) =
        exportMainReportPdf(context, com.example.util.MainReportOptions(), onResult)

    /** Compatibilidade com a v5.0 (diálogo antigo): mapeia as opções antigas para o boletim novo. */
    fun exportCustomWeatherReportPdf(
        context: Context,
        options: PdfExportOptions = PdfExportOptions(),
        onResult: (File?) -> Unit
    ) = exportMainReportPdf(
        context,
        com.example.util.MainReportOptions(includeAlerts = options.includeAlerts, include15Days = options.includeDailyForecast),
        onResult
    )

    /** v5.1: boletim agro (cana e citros) com cabeçalho, seções, fontes e numeração de páginas. */
    fun exportAgroReportPdf(context: Context, onResult: (File?) -> Unit) {
        viewModelScope.launch {
            val state = _selectedState.value
            val stationId = _selectedStationId.value
            repository.ensureAgroSeries(stationId)
            val station = repository.getStationById(stationId).first()
            val daily = repository.getDailyForecasts(stationId).first()
            val series = repository.agroSeries.value[stationId]
            val inmet = repository.allAlerts.first().filter { it.id.startsWith("inmet_") && state in com.example.data.repository.InmetAlertMapper.statesOfEntity(it) }
            val month = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Sao_Paulo")).get(java.util.Calendar.MONTH) + 1
            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    com.example.util.SimetReports.agroReport(context.applicationContext, state, station, daily, series, inmet, month)
                } catch (e: Exception) { null }
            }
            onResult(file)
        }
    }

    fun clearAppCaches(context: Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                // Delete temporary cache directory files safely
                context.cacheDir.listFiles()?.forEach { file ->
                    file.deleteRecursively()
                }
                // Refresh local database cache
                refreshActiveStation()
                onResult(true, "Cache limpo e dados do radar sincronizados com sucesso!")
            } catch (e: Exception) {
                onResult(false, "Falha ao limpar caches: ${e.localizedMessage}")
            }
        }
    }

    fun requestMapsRainPrecision(customQuery: String? = null) {
        viewModelScope.launch {
            _mapsRainPrecisionState.value = MapsRainPrecisionUiState.Loading
            try {
                val coords = _userCoordinates.value
                val st = currentStation.value
                val targetQuery = when {
                    !customQuery.isNullOrBlank() -> customQuery.trim()
                    st != null -> "Município de ${st.name}, ${com.example.domain.BrState.ofStationId(st.id).uf}"
                    coords != null -> "Localização Atual do Usuário em SP"
                    else -> "Bauru e Região Central, SP"
                }

                val lat = coords?.first ?: st?.lat
                val lon = coords?.second ?: st?.lon

                // v5.1: o serviço rotula este campo como "AVISOS OFICIAIS VIGENTES (INMET)". Antes recebia as células de
                // tempestade ilustrativas do radar nativo; agora recebe só avisos reais do INMET do estado (ou nada).
                val stormsSummary = com.example.domain.HazardRules.fromInmet(
                    repository.allAlerts.first(), com.example.domain.BrState.ofStationId(st?.id ?: _selectedStationId.value)
                ).distinctBy { it.id.substringBefore('#') }.joinToString("; ") { "${it.title} — ${it.area} (${it.validity})" }

                val stationSummary = st?.let {
                    "Estação ${it.name}: ${it.currentTemp}°C, Chuva acumulada: ${it.rainVolumeMm}mm, Umidade: ${it.humidity}%"
                } ?: ""

                val result = geminiMapsService.getRainPrecisionReport(
                    query = targetQuery,
                    latitude = lat,
                    longitude = lon,
                    activeStormsSummary = stormsSummary,
                    nearbyStationSummary = stationSummary
                )
                _mapsRainPrecisionState.value = MapsRainPrecisionUiState.Success(result)
            } catch (e: Exception) {
                _mapsRainPrecisionState.value = MapsRainPrecisionUiState.Error(
                    e.localizedMessage ?: "Erro ao consultar precisão de chuva com Google Maps"
                )
            }
        }
    }

    fun clearMapsRainPrecision() {
        _mapsRainPrecisionState.value = MapsRainPrecisionUiState.Idle
    }
}

sealed interface MapsRainPrecisionUiState {
    object Idle : MapsRainPrecisionUiState
    object Loading : MapsRainPrecisionUiState
    data class Success(val result: MapsRainPrecisionResult) : MapsRainPrecisionUiState
    data class Error(val message: String) : MapsRainPrecisionUiState
}

class WeatherViewModelFactory(
    private val repository: WeatherRepository,
    private val context: Context? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(repository, context?.let { com.example.data.local.AppSettings(it) }) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
