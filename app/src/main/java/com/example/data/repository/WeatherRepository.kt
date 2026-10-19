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
    suspend fun refreshAllHourlyAndDailyLive(priorityStationId: String? = null): Boolean = withContext(Dispatchers.IO) {
        val prefs = dao.getUserPreferencesSync()
        if (prefs?.isOfflineModeForced == true) return@withContext false
        val stations = dao.getAllStationsSync()
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

    /**
     * Lista fixa das estações (id, nome, região, lat, lon). Os valores meteorológicos começam
     * zerados ("aguardando", lastUpdated = 0) até a primeira atualização real da Open-Meteo.
     */
    private fun getInitialSpStations(): List<WeatherStationEntity> = listOf(
        station("bauru", "Bauru", "Centro-Oeste", -22.3145, -49.0587),
        station("presidente_prudente", "Presidente Prudente", "Oeste Paulista", -22.1256, -51.3889),
        station("sao_paulo", "São Paulo", "Região Metropolitana (Capital)", -23.5505, -46.6333),
        station("campinas", "Campinas", "Região de Campinas (RMC)", -22.9099, -47.0626),
        station("santos", "Santos", "Baixada Santista / Litoral", -23.9618, -46.3322),
        station("ribeirao_preto", "Ribeirão Preto", "Norte Paulista", -21.1704, -47.8103),
        station("sao_jose_dos_campos", "São José dos Campos", "Vale do Paraíba", -23.2237, -45.9009),
        station("sorocaba", "Sorocaba", "Sudoeste Paulista", -23.5015, -47.4526),
        station("sao_jose_do_rio_preto", "São José do Rio Preto", "Noroeste Paulista", -20.8113, -49.3758),
        station("sao_carlos", "São Carlos", "Central / Araraquara", -22.0175, -47.8908),
        station("ubatuba", "Ubatuba", "Litoral Norte", -23.4339, -45.0838),
        station("botucatu", "Botucatu", "Cuesta de Botucatu", -22.8858, -48.4450),
        station("franca", "Franca", "Alta Mogiana / Nordeste", -20.5386, -47.4008),
        station("piracicaba", "Piracicaba", "Agrometeorologia / Médio Tietê", -22.7253, -47.6492),
        station("araraquara", "Araraquara", "Região Central", -21.7946, -48.1766),
        station("marilia", "Marília", "Centro-Oeste / Alta Paulista", -22.2139, -49.9458),
        station("jundiai", "Jundiaí", "Aglomeração Urbana de Jundiaí", -23.1857, -46.8978),
        station("campos_do_jordao", "Campos do Jordão", "Serra da Mantiqueira", -22.7394, -45.5913),
        station("registro", "Registro", "Vale do Ribeira", -24.4881, -47.8441),
        station("barretos", "Barretos", "Norte Paulista / Bacia do Rio Grande", -20.5572, -48.5678),
        station("bebedouro", "Bebedouro", "Região de Barretos / Citricultura", -20.9497, -48.4792),
        station("olimpia", "Olímpia", "Noroeste / Estância Turística", -20.7372, -48.9144),
        station("aracatuba", "Araçatuba", "Oeste Paulista / Baixo Tietê", -21.2089, -50.4328),
        station("catanduva", "Catanduva", "Centro-Norte Paulista", -21.1367, -48.9744),
        station("votuporanga", "Votuporanga", "Noroeste / Grandes Lagos", -20.4228, -49.9725),
        station("assis", "Assis", "Médio Paranapanema", -22.6617, -50.4189),
        station("ourinhos", "Ourinhos", "Sul Paulista / Divisa PR", -22.9792, -49.8706),
        station("taubate", "Taubaté", "Vale do Paraíba", -23.0264, -45.5558),
        station("limeira", "Limeira", "Região de Campinas / Centro-Leste", -22.5647, -47.4017),
        station("rio_claro", "Rio Claro", "Região Central / Corumbataí", -22.4114, -47.5614),
        station("itapeva", "Itapeva", "Sudoeste Paulista / Alto Ribeira", -23.9822, -48.8761)
    )

    private fun station(id: String, name: String, region: String, lat: Double, lon: Double) =
        WeatherStationEntity(
            id = id, name = name, region = region, lat = lat, lon = lon,
            currentTemp = 0.0, minTemp = 0.0, maxTemp = 0.0, feelsLike = 0.0,
            humidity = 0, pressure = 0, windSpeed = 0.0, windDirection = "",
            rainVolumeMm = 0.0, rainProbability = 0, dbzReflectivity = 0,
            weatherCondition = "", iconType = "", synopticSummary = "",
            sunrise = "", sunset = "", uvIndex = 0, aqi = 0
        ).asPlaceholder()
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
