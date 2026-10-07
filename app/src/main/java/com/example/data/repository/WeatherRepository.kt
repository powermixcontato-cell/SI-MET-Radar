package com.example.data.repository

import com.example.data.local.dao.WeatherDao
import com.example.data.local.entity.CiiagroRecordEntity
import com.example.data.local.entity.ClimateTrendEntity
import com.example.data.local.entity.DailyForecastEntity
import com.example.data.local.entity.HourlyForecastEntity
import com.example.data.local.entity.RegionSubscriptionEntity
import com.example.data.local.entity.UserPreferencesEntity
import com.example.data.local.entity.WeatherAlertEntity
import com.example.data.local.entity.WeatherStationEntity
import com.example.data.remote.NetworkClient
import com.example.data.remote.OpenMeteoApi
import com.example.data.remote.OpenMeteoClient
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Estado do carregamento da série Open-Meteo por estação (aba Cana & Citros, acumulados). */
sealed interface AgroLoadState {
    data object Idle : AgroLoadState
    data object Loading : AgroLoadState
    data object Ready : AgroLoadState
    data class Error(val message: String) : AgroLoadState
}

class WeatherRepository(
    private val dao: WeatherDao,
    private val openMeteo: OpenMeteoApi = OpenMeteoClient.api,
    private val clock: () -> Long = { System.currentTimeMillis() },
    /** Chaves cifradas (DataStore + Keystore). null = usa os campos antigos do Room (testes). */
    private val keyStore: com.example.data.secure.ApiKeyProvider? = null
) {

    companion object {
        private const val TAG = "WeatherRepository"
        /** Evita chamar a Open-Meteo mais de uma vez a cada 15 min por local. */
        private const val MIN_FETCH_INTERVAL_MS = 15 * 60 * 1000L
        /** Requisições simultâneas à Open-Meteo na atualização de todas as cidades. */
        private const val MAX_PARALLEL_FETCHES = 4

        fun errorMessageFor(e: Throwable): String = when (e) {
            is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
                "Sem conexão com a Open-Meteo. Verifique a internet e tente novamente."
            is retrofit2.HttpException -> if (e.code() == 429) "Limite de consultas da Open-Meteo atingido. Tente em alguns minutos."
                else "Open-Meteo respondeu com erro ${e.code()}. Tente novamente."
            else -> "Falha ao obter dados da Open-Meteo. Tente novamente."
        }
    }

    /** Erro da última atualização (null = ok). A UI pode exibir "Desatualizado". */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val lastFetchByStation = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Último resumo agro real (Open-Meteo) por estação, usado pelo painel agro. */
    private val _agroSummaries = MutableStateFlow<Map<String, OpenMeteoAgroSummary>>(emptyMap())
    val agroSummaries: StateFlow<Map<String, OpenMeteoAgroSummary>> = _agroSummaries.asStateFlow()
    fun getAgroSummary(stationId: String): OpenMeteoAgroSummary? = _agroSummaries.value[stationId]

    /** Série horária/diária completa da última busca real por estação (aba Cana & Citros). Só em memória. */
    private val _agroSeries = MutableStateFlow<Map<String, AgroSeries>>(emptyMap())
    val agroSeries: StateFlow<Map<String, AgroSeries>> = _agroSeries.asStateFlow()

    /** Carregando / erro / pronto por estação (permite à UI mostrar progresso e erro em vez de tela vazia). */
    private val _agroLoadState = MutableStateFlow<Map<String, AgroLoadState>>(emptyMap())
    val agroLoadState: StateFlow<Map<String, AgroLoadState>> = _agroLoadState.asStateFlow()
    private fun setLoadState(stationId: String, state: AgroLoadState) = _agroLoadState.update { it + (stationId to state) }

    // ---------------- INMET (avisos oficiais) ----------------
    private val inmetService = com.example.data.remote.InmetAlertsService()

    /** null = ok; texto = "Avisos indisponíveis" (com link para avisos.inmet.gov.br na UI). */
    private val _alertsError = MutableStateFlow<String?>(null)
    val alertsError: StateFlow<String?> = _alertsError.asStateFlow()

    private val _alertsLastUpdated = MutableStateFlow(0L)
    val alertsLastUpdated: StateFlow<Long> = _alertsLastUpdated.asStateFlow()

    /**
     * Baixa o RSS do INMET, mantém só avisos de SP com Fim posterior a agora e grava como WeatherAlertEntity.
     * Devolve os avisos NOVOS (para notificação). Em falha, não apaga os avisos salvos.
     */
    suspend fun refreshInmetAlerts(): List<WeatherAlertEntity> = withContext(Dispatchers.IO) {
        val prefs = dao.getUserPreferencesSync()
        if (prefs?.isOfflineModeForced == true) return@withContext emptyList()
        val parsed = try {
            inmetService.fetch()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao obter avisos do INMET", e)
            _alertsError.value = "Avisos indisponíveis"
            return@withContext emptyList()
        }
        val now = clock()
        val entities = InmetAlertMapper.toEntities(parsed, now)
        val existing = dao.getAllAlertsSync().filter { it.id.startsWith("inmet_") }
        val ackIds = existing.filter { it.isAcknowledged }.map { it.id }.toSet()
        val existingIds = existing.map { it.id }.toSet()
        dao.deleteInmetAlerts()
        if (entities.isNotEmpty()) dao.insertAlerts(entities.map { if (it.id in ackIds) it.copy(isAcknowledged = true) else it })
        _alertsError.value = null
        _alertsLastUpdated.value = now
        entities.filter { it.id !in existingIds }
    }

    /**
     * Busca a Open-Meteo (atual + horária + diária) para uma estação e grava no banco.
     * Em falha: não altera dados nem lastUpdated e devolve false.
     */
    private val stationLocks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    /** Uma busca por estação por vez (a abertura da aba e a atualização geral não duplicam a chamada). */
    private suspend fun fetchAndStoreOpenMeteo(station: WeatherStationEntity, force: Boolean): Boolean =
        stationLocks.getOrPut(station.id) { kotlinx.coroutines.sync.Mutex() }.withLock { fetchAndStoreOpenMeteoLocked(station, force) }

    private suspend fun fetchAndStoreOpenMeteoLocked(station: WeatherStationEntity, force: Boolean): Boolean {
        val now = clock()
        val last = lastFetchByStation[station.id]
        if (!force && last != null && now - last < MIN_FETCH_INTERVAL_MS && _agroSeries.value.containsKey(station.id)) return true
        setLoadState(station.id, AgroLoadState.Loading)
        return try {
            val res = openMeteo.getForecast(station.lat, station.lon)
            val mapped = OpenMeteoMapper.map(res, station, now)
            if (mapped.hourly.isEmpty() && mapped.daily.isEmpty() && mapped.station == null) {
                Log.w(TAG, "Open-Meteo sem dados para ${station.id}")
                setLoadState(station.id, AgroLoadState.Error("A Open-Meteo não devolveu dados para ${station.name}."))
                return false
            }
            if (mapped.daily.isNotEmpty()) {
                dao.deleteDailyByStation(station.id)
                dao.insertDailyForecasts(mapped.daily)
            }
            if (mapped.hourly.isNotEmpty()) {
                dao.deleteHourlyByStation(station.id)
                dao.insertHourlyForecasts(mapped.hourly)
            }
            mapped.station?.let { dao.insertStation(it) }
            // update{} é atômico: várias cidades são buscadas em paralelo
            _agroSummaries.update { it + (station.id to mapped.agro) }
            mapped.series?.let { series -> _agroSeries.update { it + (station.id to series) } }
            lastFetchByStation[station.id] = now
            setLoadState(station.id, AgroLoadState.Ready)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            setLoadState(station.id, AgroLoadState.Idle)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Falha Open-Meteo para ${station.id}", e)
            setLoadState(station.id, AgroLoadState.Error(errorMessageFor(e)))
            false
        }
    }

    val allStations: Flow<List<WeatherStationEntity>> = dao.getAllStations()
    val allAlerts: Flow<List<WeatherAlertEntity>> = dao.getAllAlerts()
    val climateTrends: Flow<List<ClimateTrendEntity>> = dao.getClimateTrends()
    val regionSubscriptions: Flow<List<RegionSubscriptionEntity>> = dao.getAllSubscriptions()
    val userPreferences: Flow<UserPreferencesEntity?> = dao.getUserPreferences()
    val allCiiagroRecords: Flow<List<CiiagroRecordEntity>> = dao.getAllCiiagroRecords()

    fun getCiiagroRecord(stationId: String): Flow<CiiagroRecordEntity?> {
        return dao.getCiiagroRecord(stationId)
    }

    fun getStationById(stationId: String): Flow<WeatherStationEntity?> {
        return dao.getStationById(stationId)
    }

    fun getHourlyForecasts(stationId: String): Flow<List<HourlyForecastEntity>> {
        return dao.getHourlyForecasts(stationId)
    }

    fun getDailyForecasts(stationId: String): Flow<List<DailyForecastEntity>> {
        return dao.getDailyForecasts(stationId)
    }

    /**
     * Atualiza previsão real (Open-Meteo) de todas as estações, respeitando o intervalo de 15 min.
     * A estação [priorityStationId] (a selecionada) é buscada PRIMEIRO; as demais em paralelo
     * (até [MAX_PARALLEL_FETCHES] por vez). Antes eram 31 chamadas em sequência e a cidade aberta
     * podia ficar sem série (aba Cana & Citros vazia) por até ~1 min.
     */
    suspend fun refreshAllHourlyAndDailyLive(
        priorityStationId: String? = null,
        /** v5.1: limita às estações de um estado (SP/PR/RS). null = todas. */
        stateFilter: com.example.domain.BrState? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val prefs = dao.getUserPreferencesSync()
        if (prefs?.isOfflineModeForced == true) return@withContext false
        val stations = dao.getAllStationsSync().filter { stateFilter == null || com.example.domain.BrState.ofStationId(it.id) == stateFilter }
        var failures = 0
        val first = stations.firstOrNull { it.id == priorityStationId }
        if (first != null) {
            if (!fetchAndStoreOpenMeteo(first, force = false)) failures++ else refreshCiiagroData(first.id)
        }
        val gate = Semaphore(MAX_PARALLEL_FETCHES)
        failures += coroutineScope {
            stations.filter { it.id != first?.id }.map { station ->
                async {
                    gate.withPermit {
                        if (!fetchAndStoreOpenMeteo(station, force = false)) 1 else { refreshCiiagroData(station.id); 0 }
                    }
                }
            }.awaitAll().sum()
        }
        _lastError.value = if (stations.isNotEmpty() && failures == stations.size) {
            "Falha ao atualizar a previsão (Open-Meteo). Exibindo o último dado salvo."
        } else null
        failures == 0
    }

    /**
     * Garante a série Open-Meteo da estação (aba Cana & Citros ao abrir): busca se ainda não houver
     * série em memória. Devolve true se a série estiver disponível ao final.
     */
    suspend fun ensureAgroSeries(stationId: String): Boolean = withContext(Dispatchers.IO) {
        if (_agroSeries.value.containsKey(stationId)) return@withContext true
        val prefs = dao.getUserPreferencesSync()
        if (prefs?.isOfflineModeForced == true) {
            setLoadState(stationId, AgroLoadState.Error("Modo offline ativado nas Configurações: os índices precisam de uma atualização com internet."))
            return@withContext false
        }
        val station = dao.getStationByIdSync(stationId) ?: return@withContext false
        // force=false: se outra busca acabou de trazer a série (mesmo lock), não repete a chamada
        val ok = fetchAndStoreOpenMeteo(station, force = false)
        if (ok) refreshCiiagroData(stationId)
        ok && _agroSeries.value.containsKey(stationId)
    }

    suspend fun initializePreloadedDataIfNeeded() = withContext(Dispatchers.IO) {
        val existingPrefs = dao.getUserPreferencesSync()
        if (existingPrefs == null) {
            dao.insertUserPreferences(UserPreferencesEntity(selectedThemeKey = "oled_dark"))
        } else if (existingPrefs.selectedThemeKey == "ipmet_cyan") {
            dao.insertUserPreferences(existingPrefs.copy(selectedThemeKey = "oled_dark"))
        }

        // Grava só a lista de estações (id, nome, região, lat, lon) e apenas se a tabela estiver vazia.
        // Valores meteorológicos ficam "aguardando" (lastUpdated = 0) até a primeira atualização real.
        if (dao.countStations() == 0) {
            dao.insertStations(getInitialSpStations().map { it.asPlaceholder() })
        }
        // v5.1: cidades do PR e RS (só as que ainda não existem; nunca sobrescreve dados reais já salvos)
        val existingIds = dao.getAllStationsSync().map { it.id }.toSet()
        val missing = RegionalStations.paranaAndRs.filter { it.id !in existingIds }
        if (missing.isNotEmpty()) dao.insertStations(missing)

        // Migra chaves salvas em texto puro no Room para o armazenamento cifrado e apaga do Room
        val prefsNow = dao.getUserPreferencesSync()
        if (keyStore != null && prefsNow != null &&
            (prefsNow.openWeatherApiKey.isNotBlank() || prefsNow.weatherbitApiKey.isNotBlank())) {
            try {
                keyStore.save(prefsNow.openWeatherApiKey, prefsNow.weatherbitApiKey)
                dao.insertUserPreferences(prefsNow.copy(openWeatherApiKey = "", weatherbitApiKey = ""))
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao migrar chaves para armazenamento cifrado", e)
            }
        }

        // Nunca grava alertas nem previsões. Remove alertas fixos de instalações antigas.
        dao.deleteSeedAlerts()

        // Seed climate trends (São Paulo State historical comparison 1991-2020 normal vs 2026)
        val trends = listOf(
            ClimateTrendEntity(1, "Jan", 25.8, 24.5, 290.0, 275.0, 19, 18),
            ClimateTrendEntity(2, "Fev", 26.2, 24.8, 230.0, 240.0, 16, 17),
            ClimateTrendEntity(3, "Mar", 24.9, 23.9, 195.0, 180.0, 14, 15),
            ClimateTrendEntity(4, "Abr", 22.8, 21.6, 92.0, 85.0, 8, 8),
            ClimateTrendEntity(5, "Mai", 19.5, 18.2, 60.0, 65.0, 6, 6),
            ClimateTrendEntity(6, "Jun", 18.2, 17.0, 48.0, 52.0, 5, 5),
            ClimateTrendEntity(7, "Jul", 18.5, 16.8, 38.0, 45.0, 4, 4),
            ClimateTrendEntity(8, "Ago", 21.0, 18.3, 28.0, 40.0, 3, 4),
            ClimateTrendEntity(9, "Set", 23.4, 20.2, 95.0, 82.0, 9, 8), // Current month: warmer +1.4°C
            ClimateTrendEntity(10, "Out", 24.1, 21.9, 140.0, 130.0, 11, 11),
            ClimateTrendEntity(11, "Nov", 24.8, 23.0, 175.0, 160.0, 14, 13),
            ClimateTrendEntity(12, "Dez", 25.5, 24.1, 260.0, 235.0, 18, 17)
        )
        if (dao.countClimateTrends() == 0) dao.insertClimateTrends(trends)

        // Seed regional subscriptions com suporte expresso a BARRETOS e demais polos
        val subscriptions = listOf(
            RegionSubscriptionEntity("barretos", "Região de Barretos (Norte Paulista / Agro)", true, 8, true, true),
            RegionSubscriptionEntity("rmsp", "Região Metropolitana de SP", true, 10, true, true),
            RegionSubscriptionEntity("campinas", "Região de Campinas & Circuito das Águas", true, 10, true, true),
            RegionSubscriptionEntity("centro_bauru", "Centro-Oeste / Bauru", true, 8, true, true),
            RegionSubscriptionEntity("oeste_prudente", "Oeste / Presidente Prudente", true, 10, true, true),
            RegionSubscriptionEntity("ribeirao_preto", "Ribeirão Preto & Franca (Cana & Café)", true, 10, true, true),
            RegionSubscriptionEntity("noroeste_riopreto", "Noroeste / São José do Rio Preto", true, 10, true, true),
            RegionSubscriptionEntity("araraquara", "Araraquara & São Carlos (Citrus & Cana)", true, 8, true, true),
            RegionSubscriptionEntity("piracicaba", "Piracicaba & Limeira (Pólo Sucroenergético)", true, 10, true, true),
            RegionSubscriptionEntity("vale_paraiba", "Vale do Paraíba & Mantiqueira", true, 8, true, true),
            RegionSubscriptionEntity("baixada_santista", "Baixada Santista & Litoral Sul", true, 12, true, true),
            RegionSubscriptionEntity("sorocaba_itapetininga", "Sorocaba & Itapetininga", true, 10, true, true),
            RegionSubscriptionEntity("vale_ribeira", "Vale do Ribeira & Registro", true, 10, true, true)
        )
        // Só na primeira execução (antes regravava a cada abertura e apagava as escolhas do usuário)
        if (dao.countSubscriptions() == 0) dao.insertSubscriptions(subscriptions)

        // Registros agro fixos (CIIAGRO) não são mais gravados; apaga os de versões antigas.
        dao.deleteLegacyCiiagroRecords()
    }

    suspend fun refreshStation(stationId: String, forceOffline: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val currentStation = dao.getStationByIdSync(stationId) ?: return@withContext false
        val prefs = dao.getUserPreferencesSync() ?: UserPreferencesEntity()

        if (forceOffline || prefs.isOfflineModeForced) {
            // Modo offline: mantém os dados salvos e NÃO altera lastUpdated (reflete a última atualização real)
            return@withContext true
        }

        // 1) Previsão + tempo atual sempre pela Open-Meteo (F1)
        val openMeteoOk = fetchAndStoreOpenMeteo(currentStation, force = true)
        var anySuccess = openMeteoOk
        var base = dao.getStationByIdSync(stationId) ?: currentStation

        val owKey = readOpenWeatherKey(prefs)
        val wbKey = readWeatherbitKey(prefs)

        // 2) Tempo atual opcional por OpenWeatherMap (chave do usuário) – sobrescreve só os campos informados
        if (owKey.isNotBlank()) {
            try {
                val res = NetworkClient.openWeatherRetrofit.getCurrentWeather(base.lat, base.lon, owKey)
                val temp = res.main?.temp
                if (temp != null) {
                    val windKmh = res.wind?.speed?.let { it * 3.6 } // m/s → km/h
                    base = base.copy(
                        currentTemp = temp,
                        feelsLike = res.main?.feelsLike ?: base.feelsLike,
                        humidity = res.main?.humidity ?: base.humidity,
                        pressure = res.main?.pressure ?: base.pressure,
                        windSpeed = windKmh ?: base.windSpeed,
                        windDirection = if (windKmh != null) {
                            val compass = com.example.data.remote.degreesToCompass(res.wind?.deg?.toDouble())
                            if (compass != null) "$compass ${windKmh.toInt()} km/h" else "${windKmh.toInt()} km/h"
                        } else base.windDirection,
                        weatherCondition = res.weather?.firstOrNull()?.description?.replaceFirstChar { it.uppercase() } ?: base.weatherCondition,
                        synopticSummary = "Tempo atual: OpenWeatherMap • Previsão: Open-Meteo",
                        lastUpdated = clock()
                    )
                    dao.insertStation(base)
                    anySuccess = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha OpenWeatherMap para $stationId", e)
            }
        } else if (wbKey.isNotBlank()) {
            // 3) Tempo atual opcional por Weatherbit (chave do usuário)
            try {
                val item = NetworkClient.weatherbitRetrofit.getCurrentWeather(base.lat, base.lon, wbKey).data?.firstOrNull()
                if (item?.temp != null) {
                    val windKmh = item.windSpd?.let { it * 3.6 }
                    base = base.copy(
                        currentTemp = item.temp,
                        feelsLike = item.appTemp ?: base.feelsLike,
                        humidity = item.rh ?: base.humidity,
                        pressure = item.pres?.toInt() ?: base.pressure,
                        windSpeed = windKmh ?: base.windSpeed,
                        uvIndex = item.uv?.toInt() ?: base.uvIndex,
                        aqi = item.aqi ?: base.aqi,
                        weatherCondition = item.weather?.description?.replaceFirstChar { it.uppercase() } ?: base.weatherCondition,
                        synopticSummary = "Tempo atual: Weatherbit • Previsão: Open-Meteo",
                        lastUpdated = clock()
                    )
                    dao.insertStation(base)
                    anySuccess = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha Weatherbit para $stationId", e)
            }
        }

        if (!anySuccess) {
            // Todas as fontes falharam: não altera dados nem lastUpdated; expõe erro para a UI
            _lastError.value = "Falha ao atualizar ${currentStation.name}. Exibindo o último dado salvo."
            return@withContext false
        }
        _lastError.value = null

        // Também sincroniza dados do CIIAGRO se disponível
        refreshCiiagroData(stationId)

        return@withContext true
    }

    /**
     * Painel agro. O endpoint do CIIAGRO (CiiagroApiService) responde 404 e não há API pública confirmada:
     * a chamada foi desativada. Os dados agro vêm da Open-Meteo (ET0 FAO, radiação, chuva com past_days=30).
     * Sem dado real, nada é gravado e lastUpdated não muda.
     */
    suspend fun refreshCiiagroData(stationId: String): Boolean = withContext(Dispatchers.IO) {
        val station = dao.getStationByIdSync(stationId) ?: return@withContext false
        val agro = getAgroSummary(stationId) ?: return@withContext false
        if (station.lastUpdated <= 0L) return@withContext false
        val balance = agro.waterBalance7DaysMm
        val risk = when {
            balance == null -> "—"
            balance < -20.0 -> "Déficit (estimativa)"
            balance < 0.0 -> "Atenção (estimativa)"
            else -> "Sem déficit (estimativa)"
        }
        val n30 = agro.past30DaysAvailable
        val acc30 = if (agro.rainPast30DaysMm != null && n30 > 0) {
            " Chuva acumulada nos últimos $n30 dias: ${agro.rainPast30DaysMm} mm" +
                (agro.waterBalance30DaysMm?.let { " (balanço chuva − ET0: $it mm)" } ?: "") + "."
        } else ""
        val explanation = if (balance != null) {
            "Balanço hídrico estimado (7 dias) = chuva acumulada ${agro.rainPast7DaysMm} mm − ET0 acumulada ${agro.et0Past7DaysMm} mm = $balance mm.$acc30 Estimativa simples (Open-Meteo), não substitui medição de campo."
        } else "Balanço hídrico indisponível (dados insuficientes).$acc30"
        val record = CiiagroRecordEntity(
            stationId = stationId,
            municipality = station.name,
            airTemp = station.currentTemp,
            tempMin = agro.tempMinToday ?: station.minTemp,
            tempMax = agro.tempMaxToday ?: station.maxTemp,
            relativeHumidity = station.humidity,
            // acumulado de 30 dias (ou dos dias disponíveis – ver texto da recomendação)
            rainAccumulatedMm = agro.rainPast30DaysMm ?: agro.rainPast7DaysMm ?: 0.0,
            et0MmDay = agro.et0TodayMm ?: 0.0,
            windSpeedKmH = station.windSpeed,
            solarRadiationMj = agro.radiationTodayMj ?: 0.0,
            soilWaterDeficitRisk = risk,
            cropManagementRecommendation = explanation,
            forecastRain7DaysMm = agro.rainNext7DaysMm ?: 0.0,
            liveDataSource = "Open-Meteo (estimativa)",
            lastUpdated = station.lastUpdated
        )
        dao.insertCiiagroRecord(record)
        return@withContext true
    }

    private suspend fun readOpenWeatherKey(prefs: UserPreferencesEntity): String =
        try { keyStore?.openWeatherKey() } catch (e: Exception) { Log.w(TAG, "Chave OpenWeather ilegível", e); null }
            ?.takeIf { it.isNotBlank() } ?: prefs.openWeatherApiKey

    private suspend fun readWeatherbitKey(prefs: UserPreferencesEntity): String =
        try { keyStore?.weatherbitKey() } catch (e: Exception) { Log.w(TAG, "Chave Weatherbit ilegível", e); null }
            ?.takeIf { it.isNotBlank() } ?: prefs.weatherbitApiKey

    /** Chaves atuais para a tela de configurações. */
    suspend fun readApiKeys(): Pair<String, String> = withContext(Dispatchers.IO) {
        val prefs = dao.getUserPreferencesSync() ?: UserPreferencesEntity()
        Pair(readOpenWeatherKey(prefs), readWeatherbitKey(prefs))
    }

    /** Salva as chaves cifradas (ou no Room, se não houver keyStore) e limpa o texto puro do Room. */
    suspend fun saveApiKeys(openWeatherKey: String, weatherbitKey: String) = withContext(Dispatchers.IO) {
        val prefs = dao.getUserPreferencesSync() ?: UserPreferencesEntity()
        if (keyStore != null) {
            keyStore.save(openWeatherKey, weatherbitKey)
            dao.insertUserPreferences(prefs.copy(openWeatherApiKey = "", weatherbitApiKey = ""))
        } else {
            dao.insertUserPreferences(prefs.copy(openWeatherApiKey = openWeatherKey.trim(), weatherbitApiKey = weatherbitKey.trim()))
        }
    }

    /** activeStationId salvo nas preferências (também usado pelo widget). */
    suspend fun getSavedActiveStationId(): String? = withContext(Dispatchers.IO) {
        dao.getUserPreferencesSync()?.activeStationId
    }

    suspend fun updatePreferences(prefs: UserPreferencesEntity) = withContext(Dispatchers.IO) {
        dao.insertUserPreferences(prefs)
    }

    private val prefsMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Lê as preferências ATUAIS do banco e aplica [transform]. Evita sobrescrever as preferências com o
     * valor inicial padrão do StateFlow da UI (antes do primeiro carregamento) ou com uma cópia desatualizada.
     */
    suspend fun updatePreferences(transform: (UserPreferencesEntity) -> UserPreferencesEntity) = withContext(Dispatchers.IO) {
        prefsMutex.withLock {
            val current = dao.getUserPreferencesSync() ?: UserPreferencesEntity()
            dao.insertUserPreferences(transform(current))
        }
    }

    suspend fun updateSubscription(sub: RegionSubscriptionEntity) = withContext(Dispatchers.IO) {
        dao.updateSubscription(sub)
    }

    suspend fun addAlert(alert: WeatherAlertEntity) = withContext(Dispatchers.IO) {
        dao.insertAlert(alert)
    }

    suspend fun acknowledgeAlert(alertId: String) = withContext(Dispatchers.IO) {
        dao.acknowledgeAlert(alertId)
    }

    private fun getInitialSpStations(): List<WeatherStationEntity> {
        return listOf(
            WeatherStationEntity(
                id = "bauru",
                name = "Bauru",
                region = "Centro-Oeste",
                lat = -22.3145,
                lon = -49.0587,
                currentTemp = 24.2,
                minTemp = 18.0,
                maxTemp = 29.5,
                feelsLike = 25.1,
                humidity = 72,
                pressure = 1014,
                windSpeed = 18.5,
                windDirection = "SE 18 km/h",
                rainVolumeMm = 8.4,
                rainProbability = 75,
                dbzReflectivity = 48,
                weatherCondition = "Pancadas de Chuva (Radar Ativo)",
                iconType = "rain",
                synopticSummary = "Radar Meteorológico IPMet UNESP (Bauru - Banda S) operando em 2.8 GHz com alcance de 450 km. Célula de convecção isolada a sudoeste gerando ecos de moderada intensidade (48 dBZ).",
                sunrise = "06:12",
                sunset = "18:18",
                uvIndex = 7,
                aqi = 28
            ),
            WeatherStationEntity(
                id = "presidente_prudente",
                name = "Presidente Prudente",
                region = "Oeste Paulista",
                lat = -22.1256,
                lon = -51.3889,
                currentTemp = 27.6,
                minTemp = 20.1,
                maxTemp = 32.0,
                feelsLike = 29.0,
                humidity = 64,
                pressure = 1012,
                windSpeed = 22.0,
                windDirection = "NNO 22 km/h",
                rainVolumeMm = 2.0,
                rainProbability = 40,
                dbzReflectivity = 32,
                weatherCondition = "Muitas Nuvens e Calor",
                iconType = "cloudy",
                synopticSummary = "Radar Meteorológico IPMet UNESP (Presidente Prudente - Banda C) operando em varredura volumétrica. Instabilidades fracas a 60 km da divisa com MS.",
                sunrise = "06:21",
                sunset = "18:27",
                uvIndex = 9,
                aqi = 32
            ),
            WeatherStationEntity(
                id = "sao_paulo",
                name = "São Paulo",
                region = "Região Metropolitana (Capital)",
                lat = -23.5505,
                lon = -46.6333,
                currentTemp = 21.4,
                minTemp = 16.0,
                maxTemp = 25.0,
                feelsLike = 21.0,
                humidity = 82,
                pressure = 1017,
                windSpeed = 15.0,
                windDirection = "SSE 15 km/h",
                rainVolumeMm = 14.2,
                rainProbability = 85,
                dbzReflectivity = 44,
                weatherCondition = "Chuva Moderada Intermitente",
                iconType = "rain",
                synopticSummary = "Umidade transportada do oceano pela circulação marítima mantém o tempo instável com chuva contínua e neblina nos pontos altos.",
                sunrise = "06:08",
                sunset = "18:12",
                uvIndex = 5,
                aqi = 35
            ),
            WeatherStationEntity(
                id = "campinas",
                name = "Campinas",
                region = "Região de Campinas (RMC)",
                lat = -22.9099,
                lon = -47.0626,
                currentTemp = 23.8,
                minTemp = 17.5,
                maxTemp = 27.2,
                feelsLike = 24.3,
                humidity = 76,
                pressure = 1015,
                windSpeed = 16.0,
                windDirection = "SE 16 km/h",
                rainVolumeMm = 6.0,
                rainProbability = 60,
                dbzReflectivity = 40,
                weatherCondition = "Pancadas Isoladas à Tarde",
                iconType = "rain",
                synopticSummary = "Ecos de chuva monitorados pelo IPMet mostram desenvolvimento de cumulus congestus na região de Paulínia e Americana.",
                sunrise = "06:10",
                sunset = "18:15",
                uvIndex = 7,
                aqi = 38
            ),
            WeatherStationEntity(
                id = "santos",
                name = "Santos",
                region = "Baixada Santista / Litoral",
                lat = -23.9618,
                lon = -46.3322,
                currentTemp = 22.0,
                minTemp = 19.2,
                maxTemp = 24.0,
                feelsLike = 23.0,
                humidity = 88,
                pressure = 1018,
                windSpeed = 24.0,
                windDirection = "Leste 24 km/h",
                rainVolumeMm = 18.5,
                rainProbability = 90,
                dbzReflectivity = 42,
                weatherCondition = "Chuva e Vento no Litoral",
                iconType = "rain",
                synopticSummary = "Mar agitado e chuva orográfica reforçada pela Serra do Mar. IPMet alerta para acumulados expressivos nas encostas.",
                sunrise = "06:06",
                sunset = "18:11",
                uvIndex = 4,
                aqi = 20
            ),
            WeatherStationEntity(
                id = "ribeirao_preto",
                name = "Ribeirão Preto",
                region = "Norte Paulista",
                lat = -21.1704,
                lon = -47.8103,
                currentTemp = 28.5,
                minTemp = 19.0,
                maxTemp = 33.0,
                feelsLike = 29.8,
                humidity = 58,
                pressure = 1013,
                windSpeed = 12.0,
                windDirection = "N 12 km/h",
                rainVolumeMm = 0.0,
                rainProbability = 20,
                dbzReflectivity = 15,
                weatherCondition = "Sol com Aumento de Nuvens",
                iconType = "sunny",
                synopticSummary = "Massa de ar seco predomina sobre a bacia do Pardo, com rápida elevação térmica durante a tarde e baixa umidade relativa.",
                sunrise = "06:09",
                sunset = "18:16",
                uvIndex = 10,
                aqi = 45
            ),
            WeatherStationEntity(
                id = "sao_jose_dos_campos",
                name = "São José dos Campos",
                region = "Vale do Paraíba",
                lat = -23.2237,
                lon = -45.9009,
                currentTemp = 20.8,
                minTemp = 15.2,
                maxTemp = 24.5,
                feelsLike = 20.5,
                humidity = 84,
                pressure = 1017,
                windSpeed = 19.0,
                windDirection = "SSE 19 km/h",
                rainVolumeMm = 22.0,
                rainProbability = 80,
                dbzReflectivity = 54,
                weatherCondition = "Tempestade com Trovoada",
                iconType = "storm",
                synopticSummary = "Linha de instabilidade severa monitorada na Mantiqueira. Ecos de até 54 dBZ indicam granizo pontual e chuva forte.",
                sunrise = "06:05",
                sunset = "18:10",
                uvIndex = 5,
                aqi = 24
            ),
            WeatherStationEntity(
                id = "sorocaba",
                name = "Sorocaba",
                region = "Sudoeste Paulista",
                lat = -23.5015,
                lon = -47.4526,
                currentTemp = 22.5,
                minTemp = 16.8,
                maxTemp = 26.0,
                feelsLike = 22.7,
                humidity = 78,
                pressure = 1016,
                windSpeed = 14.0,
                windDirection = "SE 14 km/h",
                rainVolumeMm = 7.5,
                rainProbability = 65,
                dbzReflectivity = 38,
                weatherCondition = "Pancadas Isoladas",
                iconType = "rain",
                synopticSummary = "Banda de nebulosidade estratificada com núcleos convectivos isolados detectados pelos radares do IPMet.",
                sunrise = "06:10",
                sunset = "18:14",
                uvIndex = 6,
                aqi = 30
            ),
            WeatherStationEntity(
                id = "sao_jose_do_rio_preto",
                name = "São José do Rio Preto",
                region = "Noroeste Paulista",
                lat = -20.8113,
                lon = -49.3758,
                currentTemp = 29.0,
                minTemp = 20.5,
                maxTemp = 34.2,
                feelsLike = 31.0,
                humidity = 52,
                pressure = 1011,
                windSpeed = 14.0,
                windDirection = "NE 14 km/h",
                rainVolumeMm = 0.0,
                rainProbability = 15,
                dbzReflectivity = 12,
                weatherCondition = "Céu Claro e Quente",
                iconType = "sunny",
                synopticSummary = "Ar quente continental prevalece. Sem precipitação significativa detectada no quadrante norte do radar IPMet Bauru.",
                sunrise = "06:14",
                sunset = "18:22",
                uvIndex = 10,
                aqi = 48
            ),
            WeatherStationEntity(
                id = "sao_carlos",
                name = "São Carlos",
                region = "Central / Araraquara",
                lat = -22.0175,
                lon = -47.8908,
                currentTemp = 23.5,
                minTemp = 16.5,
                maxTemp = 27.0,
                feelsLike = 23.8,
                humidity = 74,
                pressure = 1015,
                windSpeed = 17.0,
                windDirection = "SE 17 km/h",
                rainVolumeMm = 5.2,
                rainProbability = 70,
                dbzReflectivity = 36,
                weatherCondition = "Chuva Leve a Moderada",
                iconType = "rain",
                synopticSummary = "Localizada no raio de 100 km do radar IPMet Bauru. Monitoramento contínuo aponta formação de chuvas rápidas.",
                sunrise = "06:10",
                sunset = "18:16",
                uvIndex = 7,
                aqi = 25
            ),
            WeatherStationEntity(
                id = "ubatuba",
                name = "Ubatuba",
                region = "Litoral Norte",
                lat = -23.4339,
                lon = -45.0838,
                currentTemp = 21.8,
                minTemp = 18.5,
                maxTemp = 23.8,
                feelsLike = 22.5,
                humidity = 92,
                pressure = 1019,
                windSpeed = 20.0,
                windDirection = "Leste 20 km/h",
                rainVolumeMm = 32.0,
                rainProbability = 95,
                dbzReflectivity = 46,
                weatherCondition = "Chuva Forte Intermitente",
                iconType = "rain",
                synopticSummary = "Chuva orográfica persistente na Serra do Mar com acumulados elevados nas últimas 24 horas.",
                sunrise = "06:03",
                sunset = "18:08",
                uvIndex = 4,
                aqi = 18
            ),
            WeatherStationEntity(
                id = "botucatu",
                name = "Botucatu",
                region = "Cuesta de Botucatu",
                lat = -22.8858,
                lon = -48.4450,
                currentTemp = 21.0,
                minTemp = 15.0,
                maxTemp = 24.8,
                feelsLike = 20.8,
                humidity = 79,
                pressure = 1016,
                windSpeed = 21.0,
                windDirection = "SE 21 km/h",
                rainVolumeMm = 11.0,
                rainProbability = 80,
                dbzReflectivity = 45,
                weatherCondition = "Neblina e Chuva",
                iconType = "rain",
                synopticSummary = "Ventos de sudeste canalizados pela cuesta trazem rápida condensação e instabilidade na área de cobertura do radar IPMet.",
                sunrise = "06:11",
                sunset = "18:16",
                uvIndex = 6,
                aqi = 22
            ),
            WeatherStationEntity(
                id = "franca",
                name = "Franca",
                region = "Alta Mogiana / Nordeste",
                lat = -20.5386,
                lon = -47.4008,
                currentTemp = 26.2,
                minTemp = 17.0,
                maxTemp = 30.5,
                feelsLike = 26.5,
                humidity = 60,
                pressure = 1013,
                windSpeed = 15.0,
                windDirection = "NE 15 km/h",
                rainVolumeMm = 1.5,
                rainProbability = 30,
                dbzReflectivity = 22,
                weatherCondition = "Parcialmente Nublado",
                iconType = "cloudy",
                synopticSummary = "Nebulosidade variável no planalto de Franca com instabilidade isolada no limite norte do estado.",
                sunrise = "06:08",
                sunset = "18:14",
                uvIndex = 9,
                aqi = 34
            ),
            WeatherStationEntity(
                id = "piracicaba",
                name = "Piracicaba",
                region = "Agrometeorologia / Médio Tietê",
                lat = -22.7253,
                lon = -47.6492,
                currentTemp = 24.0,
                minTemp = 17.2,
                maxTemp = 28.0,
                feelsLike = 24.5,
                humidity = 73,
                pressure = 1015,
                windSpeed = 16.0,
                windDirection = "SE 16 km/h",
                rainVolumeMm = 7.8,
                rainProbability = 68,
                dbzReflectivity = 39,
                weatherCondition = "Chuva Isolada",
                iconType = "rain",
                synopticSummary = "Formação de nuvens convectivas no vale do Piracicaba detectadas pelo radar IPMet de Bauru a 150 km.",
                sunrise = "06:11",
                sunset = "18:16",
                uvIndex = 7,
                aqi = 30
            ),
            WeatherStationEntity(
                id = "araraquara",
                name = "Araraquara",
                region = "Região Central",
                lat = -21.7946,
                lon = -48.1766,
                currentTemp = 25.1,
                minTemp = 18.0,
                maxTemp = 29.8,
                feelsLike = 25.8,
                humidity = 68,
                pressure = 1014,
                windSpeed = 18.0,
                windDirection = "E 18 km/h",
                rainVolumeMm = 9.2,
                rainProbability = 72,
                dbzReflectivity = 43,
                weatherCondition = "Pancadas de Chuva",
                iconType = "rain",
                synopticSummary = "Célula convectiva ativa deslocando-se para o nordeste paulista com ecos moderados no radar.",
                sunrise = "06:10",
                sunset = "18:16",
                uvIndex = 8,
                aqi = 28
            ),
            WeatherStationEntity(
                id = "marilia",
                name = "Marília",
                region = "Centro-Oeste / Alta Paulista",
                lat = -22.2139,
                lon = -49.9458,
                currentTemp = 25.8,
                minTemp = 18.6,
                maxTemp = 30.2,
                feelsLike = 26.4,
                humidity = 66,
                pressure = 1013,
                windSpeed = 20.0,
                windDirection = "ESE 20 km/h",
                rainVolumeMm = 4.5,
                rainProbability = 55,
                dbzReflectivity = 34,
                weatherCondition = "Nuvens Esparsas e Chuva Leve",
                iconType = "rain",
                synopticSummary = "Linha de instabilidade no planalto ocidental paulista com ecos detectados entre Bauru e Prudente.",
                sunrise = "06:15",
                sunset = "18:21",
                uvIndex = 8,
                aqi = 27
            ),
            WeatherStationEntity(
                id = "jundiai",
                name = "Jundiaí",
                region = "Aglomeração Urbana de Jundiaí",
                lat = -23.1857,
                lon = -46.8978,
                currentTemp = 22.8,
                minTemp = 16.5,
                maxTemp = 26.4,
                feelsLike = 23.0,
                humidity = 80,
                pressure = 1016,
                windSpeed = 15.0,
                windDirection = "SE 15 km/h",
                rainVolumeMm = 12.0,
                rainProbability = 78,
                dbzReflectivity = 41,
                weatherCondition = "Chuva Intermitente",
                iconType = "rain",
                synopticSummary = "Aproximação de instabilidade pela Serra do Japi com aumento da cobertura de nuvens e chuva contínua.",
                sunrise = "06:09",
                sunset = "18:14",
                uvIndex = 6,
                aqi = 31
            ),
            WeatherStationEntity(
                id = "campos_do_jordao",
                name = "Campos do Jordão",
                region = "Serra da Mantiqueira",
                lat = -22.7394,
                lon = -45.5913,
                currentTemp = 16.2,
                minTemp = 10.5,
                maxTemp = 19.8,
                feelsLike = 15.0,
                humidity = 90,
                pressure = 1022,
                windSpeed = 22.0,
                windDirection = "S 22 km/h",
                rainVolumeMm = 26.5,
                rainProbability = 92,
                dbzReflectivity = 53,
                weatherCondition = "Chuva Forte e Nevoeiro",
                iconType = "storm",
                synopticSummary = "Clima frio de altitude com núcleos convectivos orográficos intensos (53 dBZ) monitorados na Mantiqueira.",
                sunrise = "06:05",
                sunset = "18:09",
                uvIndex = 5,
                aqi = 15
            ),
            WeatherStationEntity(
                id = "registro",
                name = "Registro",
                region = "Vale do Ribeira",
                lat = -24.4881,
                lon = -47.8441,
                currentTemp = 22.4,
                minTemp = 18.0,
                maxTemp = 25.5,
                feelsLike = 23.2,
                humidity = 86,
                pressure = 1017,
                windSpeed = 12.0,
                windDirection = "SSE 12 km/h",
                rainVolumeMm = 16.0,
                rainProbability = 82,
                dbzReflectivity = 40,
                weatherCondition = "Chuva Moderada Contínua",
                iconType = "rain",
                synopticSummary = "Canalização de umidade marítima na bacia do Ribeira de Iguape com precipitação frequente.",
                sunrise = "06:08",
                sunset = "18:13",
                uvIndex = 5,
                aqi = 19
            ),
            WeatherStationEntity(
                id = "barretos",
                name = "Barretos",
                region = "Norte Paulista / Bacia do Rio Grande",
                lat = -20.5572,
                lon = -48.5678,
                currentTemp = 28.4,
                minTemp = 19.5,
                maxTemp = 33.8,
                feelsLike = 29.6,
                humidity = 62,
                pressure = 1012,
                windSpeed = 16.0,
                windDirection = "NNE 16 km/h",
                rainVolumeMm = 5.6,
                rainProbability = 55,
                dbzReflectivity = 38,
                weatherCondition = "Sol entre Nuvens e Pancadas de Chuva",
                iconType = "rain",
                synopticSummary = "Radar IPMet Bauru (alcance 450 km) e INMET monitoram núcleos convectivos isolados na bacia do Baixo Pardo e Rio Grande. Modelos ECMWF e CPTEC convergem para instabilidade no final da tarde.",
                sunrise = "06:12",
                sunset = "18:20",
                uvIndex = 9,
                aqi = 30
            ),
            WeatherStationEntity(
                id = "bebedouro",
                name = "Bebedouro",
                region = "Região de Barretos / Citricultura",
                lat = -20.9497,
                lon = -48.4792,
                currentTemp = 27.8,
                minTemp = 19.0,
                maxTemp = 32.5,
                feelsLike = 28.9,
                humidity = 65,
                pressure = 1013,
                windSpeed = 15.0,
                windDirection = "NE 15 km/h",
                rainVolumeMm = 4.0,
                rainProbability = 48,
                dbzReflectivity = 34,
                weatherCondition = "Pancadas Isoladas",
                iconType = "rain",
                synopticSummary = "Área de monitoramento direto dos radares IPMet UNESP e rede agrometeorológica paulista.",
                sunrise = "06:11",
                sunset = "18:19",
                uvIndex = 9,
                aqi = 32
            ),
            WeatherStationEntity(
                id = "olimpia",
                name = "Olímpia",
                region = "Noroeste / Estância Turística",
                lat = -20.7372,
                lon = -48.9144,
                currentTemp = 28.9,
                minTemp = 20.0,
                maxTemp = 34.0,
                feelsLike = 30.2,
                humidity = 58,
                pressure = 1012,
                windSpeed = 14.0,
                windDirection = "N 14 km/h",
                rainVolumeMm = 3.2,
                rainProbability = 42,
                dbzReflectivity = 30,
                weatherCondition = "Sol e Nuvens com Chuva Rápida",
                iconType = "rain",
                synopticSummary = "Termas dos Laranjais e região com convecção típica de verão/primavera detectada a 170 km de Bauru.",
                sunrise = "06:13",
                sunset = "18:21",
                uvIndex = 9,
                aqi = 28
            ),
            WeatherStationEntity(
                id = "aracatuba",
                name = "Araçatuba",
                region = "Oeste Paulista / Baixo Tietê",
                lat = -21.2089,
                lon = -50.4328,
                currentTemp = 29.5,
                minTemp = 21.0,
                maxTemp = 35.0,
                feelsLike = 31.8,
                humidity = 54,
                pressure = 1011,
                windSpeed = 18.0,
                windDirection = "NNO 18 km/h",
                rainVolumeMm = 1.0,
                rainProbability = 35,
                dbzReflectivity = 24,
                weatherCondition = "Calor e Pancadas no Fim da Tarde",
                iconType = "cloudy",
                synopticSummary = "Radar IPMet Presidente Prudente e Bauru cobrem o vale do Tietê com ecos dispersos.",
                sunrise = "06:18",
                sunset = "18:24",
                uvIndex = 10,
                aqi = 36
            ),
            WeatherStationEntity(
                id = "catanduva",
                name = "Catanduva",
                region = "Centro-Norte Paulista",
                lat = -21.1367,
                lon = -48.9744,
                currentTemp = 27.2,
                minTemp = 18.8,
                maxTemp = 32.0,
                feelsLike = 28.4,
                humidity = 64,
                pressure = 1013,
                windSpeed = 16.0,
                windDirection = "NE 16 km/h",
                rainVolumeMm = 6.2,
                rainProbability = 60,
                dbzReflectivity = 39,
                weatherCondition = "Pancadas de Chuva",
                iconType = "rain",
                synopticSummary = "Radar Bauru a 130 km detecta núcleos de chuva moderada em desenvolvimento na região.",
                sunrise = "06:12",
                sunset = "18:18",
                uvIndex = 8,
                aqi = 31
            ),
            WeatherStationEntity(
                id = "votuporanga",
                name = "Votuporanga",
                region = "Noroeste / Grandes Lagos",
                lat = -20.4228,
                lon = -49.9725,
                currentTemp = 29.1,
                minTemp = 20.4,
                maxTemp = 34.5,
                feelsLike = 31.2,
                humidity = 55,
                pressure = 1011,
                windSpeed = 15.0,
                windDirection = "N 15 km/h",
                rainVolumeMm = 2.4,
                rainProbability = 38,
                dbzReflectivity = 28,
                weatherCondition = "Sol Forte e Pancada Isolada",
                iconType = "sunny",
                synopticSummary = "Ar quente continental na bacia do São José dos Dourados com instabilidade local.",
                sunrise = "06:16",
                sunset = "18:23",
                uvIndex = 10,
                aqi = 34
            ),
            WeatherStationEntity(
                id = "assis",
                name = "Assis",
                region = "Médio Paranapanema",
                lat = -22.6617,
                lon = -50.4189,
                currentTemp = 25.4,
                minTemp = 17.8,
                maxTemp = 29.8,
                feelsLike = 26.0,
                humidity = 70,
                pressure = 1014,
                windSpeed = 19.0,
                windDirection = "S 19 km/h",
                rainVolumeMm = 8.8,
                rainProbability = 70,
                dbzReflectivity = 42,
                weatherCondition = "Pancadas com Trovoadas",
                iconType = "rain",
                synopticSummary = "Frente semi-estacionária no norte do Paraná induz instabilidade no Paranapanema paulista.",
                sunrise = "06:17",
                sunset = "18:23",
                uvIndex = 8,
                aqi = 26
            ),
            WeatherStationEntity(
                id = "ourinhos",
                name = "Ourinhos",
                region = "Sul Paulista / Divisa PR",
                lat = -22.9792,
                lon = -49.8706,
                currentTemp = 24.6,
                minTemp = 17.2,
                maxTemp = 28.5,
                feelsLike = 25.0,
                humidity = 74,
                pressure = 1015,
                windSpeed = 17.0,
                windDirection = "SE 17 km/h",
                rainVolumeMm = 10.2,
                rainProbability = 75,
                dbzReflectivity = 44,
                weatherCondition = "Chuva e Céu Encoberto",
                iconType = "rain",
                synopticSummary = "Ecos contínuos monitorados pelo radar IPMet de Bauru a 85 km de distância.",
                sunrise = "06:15",
                sunset = "18:20",
                uvIndex = 7,
                aqi = 24
            ),
            WeatherStationEntity(
                id = "taubate",
                name = "Taubaté",
                region = "Vale do Paraíba",
                lat = -23.0264,
                lon = -45.5558,
                currentTemp = 21.2,
                minTemp = 15.6,
                maxTemp = 25.0,
                feelsLike = 21.0,
                humidity = 82,
                pressure = 1017,
                windSpeed = 17.0,
                windDirection = "SE 17 km/h",
                rainVolumeMm = 18.0,
                rainProbability = 85,
                dbzReflectivity = 48,
                weatherCondition = "Chuva Forte e Rajadas",
                iconType = "storm",
                synopticSummary = "Convecção orográfica severa na calha do Rio Paraíba do Sul.",
                sunrise = "06:04",
                sunset = "18:09",
                uvIndex = 5,
                aqi = 22
            ),
            WeatherStationEntity(
                id = "limeira",
                name = "Limeira",
                region = "Região de Campinas / Centro-Leste",
                lat = -22.5647,
                lon = -47.4017,
                currentTemp = 24.1,
                minTemp = 17.0,
                maxTemp = 28.2,
                feelsLike = 24.8,
                humidity = 75,
                pressure = 1015,
                windSpeed = 16.0,
                windDirection = "SE 16 km/h",
                rainVolumeMm = 8.0,
                rainProbability = 68,
                dbzReflectivity = 40,
                weatherCondition = "Pancadas de Chuva",
                iconType = "rain",
                synopticSummary = "Ecos de precipitação moderada registrados na cobertura combinada Bauru/São Roque.",
                sunrise = "06:10",
                sunset = "18:15",
                uvIndex = 7,
                aqi = 29
            ),
            WeatherStationEntity(
                id = "rio_claro",
                name = "Rio Claro",
                region = "Região Central / Corumbataí",
                lat = -22.4114,
                lon = -47.5614,
                currentTemp = 23.9,
                minTemp = 16.8,
                maxTemp = 27.6,
                feelsLike = 24.2,
                humidity = 76,
                pressure = 1015,
                windSpeed = 18.0,
                windDirection = "SE 18 km/h",
                rainVolumeMm = 7.4,
                rainProbability = 65,
                dbzReflectivity = 38,
                weatherCondition = "Chuva Intermitente",
                iconType = "rain",
                synopticSummary = "Bacia do Rio Corumbataí com instabilidades típicas de circulação marítima e calor.",
                sunrise = "06:10",
                sunset = "18:16",
                uvIndex = 7,
                aqi = 27
            ),
            WeatherStationEntity(
                id = "itapeva",
                name = "Itapeva",
                region = "Sudoeste Paulista / Alto Ribeira",
                lat = -23.9822,
                lon = -48.8761,
                currentTemp = 20.5,
                minTemp = 14.5,
                maxTemp = 24.0,
                feelsLike = 20.0,
                humidity = 84,
                pressure = 1018,
                windSpeed = 16.0,
                windDirection = "SSE 16 km/h",
                rainVolumeMm = 14.0,
                rainProbability = 78,
                dbzReflectivity = 41,
                weatherCondition = "Chuva Contínua e Neblina",
                iconType = "rain",
                synopticSummary = "Planalto de Itapeva com temperaturas amenas e chuva constante vinda do sul.",
                sunrise = "06:13",
                sunset = "18:17",
                uvIndex = 6,
                aqi = 18
            )
        )
    }
}

/**
 * Estação "aguardando primeira atualização real": nenhum valor meteorológico fixo é exibido como real.
 * lastUpdated = 0 indica que nunca houve dado real.
 */
internal fun WeatherStationEntity.asPlaceholder(): WeatherStationEntity = copy(
    currentTemp = 0.0, minTemp = 0.0, maxTemp = 0.0, feelsLike = 0.0,
    humidity = 0, pressure = 0, windSpeed = 0.0, windDirection = "—",
    rainVolumeMm = 0.0, rainProbability = 0, dbzReflectivity = 0,
    weatherCondition = "Dados indisponíveis", iconType = "cloudy",
    synopticSummary = "Aguardando a primeira atualização (Open-Meteo).",
    sunrise = "—", sunset = "—", uvIndex = 0, aqi = 0,
    lastUpdated = 0L
)
