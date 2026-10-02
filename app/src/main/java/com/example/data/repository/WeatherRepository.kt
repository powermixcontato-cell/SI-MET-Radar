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
import com.example.data.remote.CiiagroClient
import com.example.data.remote.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

class WeatherRepository(private val dao: WeatherDao) {

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

    suspend fun refreshAllHourlyAndDailyLive() = withContext(Dispatchers.IO) {
        val stations = dao.getAllStationsSync().ifEmpty { getInitialSpStations() }
        for (station in stations) {
            val hourly = generateHourlyForStation(station)
            val daily = generateDailyForStation(station)
            dao.deleteDailyByStation(station.id)
            dao.insertDailyForecasts(daily)
            dao.deleteHourlyByStation(station.id)
            dao.insertHourlyForecasts(hourly)
        }
    }

    suspend fun initializePreloadedDataIfNeeded() = withContext(Dispatchers.IO) {
        val existingPrefs = dao.getUserPreferencesSync()
        if (existingPrefs == null) {
            dao.insertUserPreferences(UserPreferencesEntity(selectedThemeKey = "oled_dark"))
        } else if (existingPrefs.selectedThemeKey == "ipmet_cyan") {
            dao.insertUserPreferences(existingPrefs.copy(selectedThemeKey = "oled_dark"))
        }

        val initialStations = getInitialSpStations()
        dao.insertStations(initialStations)

        // Seed initial hourly & 15-day daily forecast for all stations
        for (station in initialStations) {
            val hourly = generateHourlyForStation(station)
            val daily = generateDailyForStation(station)
            dao.deleteDailyByStation(station.id)
            dao.insertDailyForecasts(daily)
            dao.deleteHourlyByStation(station.id)
            dao.insertHourlyForecasts(hourly)
        }

        // Seed initial alerts
        val initialAlerts = listOf(
            WeatherAlertEntity(
                id = "alert_sp_01",
                regionId = "centro_bauru",
                regionName = "Centro-Oeste / Bauru",
                severity = "ALERTA_LARANJA",
                title = "Alerta de Chuva Forte e Rajadas",
                description = "IPMet Bauru detectou linha de instabilidade com ecos de 48 a 54 dBZ avançando de Botucatu para Bauru. Rajadas estimadas em até 65 km/h.",
                radarStationSource = "Radar IPMet Bauru (UNESP)",
                dbzPeak = 52,
                timestamp = System.currentTimeMillis() - 15 * 60 * 1000
            ),
            WeatherAlertEntity(
                id = "alert_sp_02",
                regionId = "vale_paraiba",
                regionName = "Vale do Paraíba & Serra da Mantiqueira",
                severity = "ALERTA_VERMELHO",
                title = "Tempestade Severa com Risco de Granizo",
                description = "Refletividade extrema atingindo 58 dBZ em Campos do Jordão e São José dos Campos. Alto risco de alagamentos e queda localizada de granizo.",
                radarStationSource = "Radar Pico do Couto / IPMet",
                dbzPeak = 58,
                timestamp = System.currentTimeMillis() - 40 * 60 * 1000
            ),
            WeatherAlertEntity(
                id = "alert_sp_03",
                regionId = "baixada_santista",
                regionName = "Baixada Santista e Litoral",
                severity = "ALERTA_AMARELO",
                title = "Chuva Contínua e Maré Alta",
                description = "Acumulado moderado persistente nas encostas da Serra do Mar. IPMet monitora células isoladas em Santos e Guarujá.",
                radarStationSource = "Radar São Roque / IPMet",
                dbzPeak = 38,
                timestamp = System.currentTimeMillis() - 90 * 60 * 1000
            ),
            WeatherAlertEntity(
                id = "alert_sp_04",
                regionId = "rmsp",
                regionName = "Região Metropolitana de SP",
                severity = "AVISO_METEOROLOGICO",
                title = "Queda Brusca de Temperatura",
                description = "Entrada de frente fria polar pelo sul do estado provocará declínio térmico de mais de 8°C nas próximas 12 horas.",
                radarStationSource = "Rede IPMet & Defesa Civil SP",
                dbzPeak = 25,
                timestamp = System.currentTimeMillis() - 120 * 60 * 1000
            ),
            WeatherAlertEntity(
                id = "alert_sp_05",
                regionId = "barretos",
                regionName = "Região de Barretos / Bacia Baixo Pardo e Grande",
                severity = "ALERTA_LARANJA",
                title = "Alerta Agro: Tempestade Severa & Rajadas em Barretos",
                description = "IPMet e CIIAGRO monitoram núcleos convectivos de 48 dBZ avançando sobre os canaviais e pomares de Barretos e Colina. Risco de granizo localizado e rajadas de vento > 60 km/h.",
                radarStationSource = "Radar IPMet Bauru / Rede CIIAGRO",
                dbzPeak = 48,
                timestamp = System.currentTimeMillis() - 25 * 60 * 1000
            )
        )
        dao.insertAlerts(initialAlerts)

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
        dao.insertClimateTrends(trends)

        // Seed regional subscriptions com suporte expresso a BARRETOS e demais polos
        val subscriptions = listOf(
            RegionSubscriptionEntity("barretos", "Região de Barretos (Norte Paulista / Agro)", true, 8, true, true),
            RegionSubscriptionEntity("rmsp", "Região Metropolitana de SP", true, 10, true, true),
            RegionSubscriptionEntity("campinas", "Região de Campinas & Circuito das Águas", true, 10, true, true),
            RegionSubscriptionEntity("centro_bauru", "Centro-Oeste / Bauru (Radar IPMet)", true, 8, true, true),
            RegionSubscriptionEntity("oeste_prudente", "Oeste / Presidente Prudente (Radar IPMet)", true, 10, true, true),
            RegionSubscriptionEntity("ribeirao_preto", "Ribeirão Preto & Franca (Cana & Café)", true, 10, true, true),
            RegionSubscriptionEntity("noroeste_riopreto", "Noroeste / São José do Rio Preto", true, 10, true, true),
            RegionSubscriptionEntity("araraquara", "Araraquara & São Carlos (Citrus & Cana)", true, 8, true, true),
            RegionSubscriptionEntity("piracicaba", "Piracicaba & Limeira (Pólo Sucroenergético)", true, 10, true, true),
            RegionSubscriptionEntity("vale_paraiba", "Vale do Paraíba & Mantiqueira", true, 8, true, true),
            RegionSubscriptionEntity("baixada_santista", "Baixada Santista & Litoral Sul", true, 12, true, true),
            RegionSubscriptionEntity("sorocaba_itapetininga", "Sorocaba & Itapetininga", true, 10, true, true),
            RegionSubscriptionEntity("vale_ribeira", "Vale do Ribeira & Registro", true, 10, true, true)
        )
        dao.insertSubscriptions(subscriptions)

        // Seed initial CIIAGRO agrometeorological live records & forecasts
        val ciiagroRecords = listOf(
            CiiagroRecordEntity(
                stationId = "barretos",
                municipality = "Barretos",
                airTemp = 28.5,
                tempMin = 19.2,
                tempMax = 33.4,
                relativeHumidity = 58,
                rainAccumulatedMm = 12.6,
                et0MmDay = 4.8,
                windSpeedKmH = 14.5,
                solarRadiationMj = 21.2,
                soilWaterDeficitRisk = "Atenção Leve",
                cropManagementRecommendation = "Janela de pulverização aberta das 06h às 10h. Condição ideal para colheita mecânica de cana até a aproximação das trovoadas vespertinas.",
                forecastRain7DaysMm = 38.5
            ),
            CiiagroRecordEntity(
                stationId = "bauru",
                municipality = "Bauru",
                airTemp = 24.2,
                tempMin = 18.0,
                tempMax = 29.5,
                relativeHumidity = 72,
                rainAccumulatedMm = 8.4,
                et0MmDay = 3.6,
                windSpeedKmH = 18.5,
                solarRadiationMj = 18.5,
                soilWaterDeficitRisk = "Sem Déficit",
                cropManagementRecommendation = "Solo com boa capacidade de campo. Evitar pulverização foliar nas próximas 3 horas devido ao vento acima de 15 km/h e radar IPMet com ecos de chuva.",
                forecastRain7DaysMm = 44.0
            ),
            CiiagroRecordEntity(
                stationId = "ribeirao_preto",
                municipality = "Ribeirão Preto",
                airTemp = 27.6,
                tempMin = 18.5,
                tempMax = 31.8,
                relativeHumidity = 62,
                rainAccumulatedMm = 6.2,
                et0MmDay = 4.4,
                windSpeedKmH = 12.0,
                solarRadiationMj = 20.0,
                soilWaterDeficitRisk = "Sem Déficit",
                cropManagementRecommendation = "Excelente acúmulo de ATR na cana-de-açúcar. Monitorar umidade relativa à tarde contra focos de queimada em palhada.",
                forecastRain7DaysMm = 32.0
            ),
            CiiagroRecordEntity(
                stationId = "araraquara",
                municipality = "Araraquara",
                airTemp = 25.8,
                tempMin = 17.5,
                tempMax = 30.2,
                relativeHumidity = 66,
                rainAccumulatedMm = 9.8,
                et0MmDay = 4.0,
                windSpeedKmH = 15.0,
                solarRadiationMj = 19.1,
                soilWaterDeficitRisk = "Sem Déficit",
                cropManagementRecommendation = "Condições ideais para pomares cítricos. Atenção a períodos úmidos prolongados para controle do cancro cítrico.",
                forecastRain7DaysMm = 41.5
            ),
            CiiagroRecordEntity(
                stationId = "presidente_prudente",
                municipality = "Presidente Prudente",
                airTemp = 26.5,
                tempMin = 19.0,
                tempMax = 31.0,
                relativeHumidity = 68,
                rainAccumulatedMm = 7.0,
                et0MmDay = 4.2,
                windSpeedKmH = 16.0,
                solarRadiationMj = 20.5,
                soilWaterDeficitRisk = "Sem Déficit",
                cropManagementRecommendation = "Manejo de pastagens e cana favorável. Vento e radar indicam tempo instável no fim do dia.",
                forecastRain7DaysMm = 28.0
            ),
            CiiagroRecordEntity(
                stationId = "sao_jose_rio_preto",
                municipality = "São José do Rio Preto",
                airTemp = 29.2,
                tempMin = 20.1,
                tempMax = 34.0,
                relativeHumidity = 54,
                rainAccumulatedMm = 4.5,
                et0MmDay = 5.2,
                windSpeedKmH = 11.5,
                solarRadiationMj = 22.0,
                soilWaterDeficitRisk = "Atenção Leve",
                cropManagementRecommendation = "Irrigação por gotejamento recomendada em citrus. Alto ETo diário exige reposição hídrica nos pomares jovens.",
                forecastRain7DaysMm = 22.0
            ),
            CiiagroRecordEntity(
                stationId = "piracicaba",
                municipality = "Piracicaba",
                airTemp = 25.0,
                tempMin = 17.0,
                tempMax = 29.0,
                relativeHumidity = 70,
                rainAccumulatedMm = 11.2,
                et0MmDay = 3.8,
                windSpeedKmH = 13.0,
                solarRadiationMj = 18.0,
                soilWaterDeficitRisk = "Sem Déficit",
                cropManagementRecommendation = "Umidade do solo ideal para plantio e soqueira de cana. Monitorar previsão de chuvas para liberação de colhedoras.",
                forecastRain7DaysMm = 36.0
            ),
            CiiagroRecordEntity(
                stationId = "campinas",
                municipality = "Campinas",
                airTemp = 24.5,
                tempMin = 16.5,
                tempMax = 28.5,
                relativeHumidity = 74,
                rainAccumulatedMm = 10.5,
                et0MmDay = 3.5,
                windSpeedKmH = 14.0,
                solarRadiationMj = 17.5,
                soilWaterDeficitRisk = "Sem Déficit",
                cropManagementRecommendation = "Hortifrutigranjeiros e grãos com bom balanço hídrico. Realizar pulverização no início da manhã.",
                forecastRain7DaysMm = 39.0
            )
        )
        dao.insertCiiagroRecords(ciiagroRecords)
    }

    suspend fun refreshStation(stationId: String, forceOffline: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val currentStation = dao.getStationByIdSync(stationId) ?: return@withContext false
        val prefs = dao.getUserPreferencesSync() ?: UserPreferencesEntity()

        if (forceOffline || prefs.isOfflineModeForced) {
            // In offline mode, keep stored data with updated check timestamp
            dao.insertStation(currentStation.copy(lastUpdated = System.currentTimeMillis()))
            return@withContext true
        }

        // Try OpenWeatherMap API if user provided a key
        if (prefs.openWeatherApiKey.isNotBlank()) {
            try {
                val api = NetworkClient.openWeatherRetrofit
                val res = api.getCurrentWeather(currentStation.lat, currentStation.lon, prefs.openWeatherApiKey)
                val temp = res.main?.temp ?: currentStation.currentTemp
                val updatedStation = currentStation.copy(
                    currentTemp = temp,
                    feelsLike = res.main?.feelsLike ?: currentStation.feelsLike,
                    minTemp = res.main?.tempMin ?: currentStation.minTemp,
                    maxTemp = res.main?.tempMax ?: currentStation.maxTemp,
                    humidity = res.main?.humidity ?: currentStation.humidity,
                    pressure = res.main?.pressure ?: currentStation.pressure,
                    windSpeed = (res.wind?.speed ?: 3.0) * 3.6, // m/s to km/h
                    rainVolumeMm = res.rain?.oneHour ?: currentStation.rainVolumeMm,
                    weatherCondition = res.weather?.firstOrNull()?.description?.replaceFirstChar { it.uppercase() } ?: currentStation.weatherCondition,
                    lastUpdated = System.currentTimeMillis()
                )
                dao.insertStation(updatedStation)
                return@withContext true
            } catch (_: Exception) {
                // Fallback to offline / IPMet simulation
            }
        }

        // Try Weatherbit API if user provided key
        if (prefs.weatherbitApiKey.isNotBlank()) {
            try {
                val api = NetworkClient.weatherbitRetrofit
                val res = api.getCurrentWeather(currentStation.lat, currentStation.lon, prefs.weatherbitApiKey)
                val item = res.data?.firstOrNull()
                if (item != null) {
                    val updatedStation = currentStation.copy(
                        currentTemp = item.temp ?: currentStation.currentTemp,
                        feelsLike = item.appTemp ?: currentStation.feelsLike,
                        humidity = item.rh ?: currentStation.humidity,
                        pressure = item.pres?.toInt() ?: currentStation.pressure,
                        windSpeed = (item.windSpd ?: 4.0) * 3.6,
                        rainVolumeMm = item.precip ?: currentStation.rainVolumeMm,
                        uvIndex = item.uv?.toInt() ?: currentStation.uvIndex,
                        aqi = item.aqi ?: currentStation.aqi,
                        weatherCondition = item.weather?.description?.replaceFirstChar { it.uppercase() } ?: currentStation.weatherCondition,
                        lastUpdated = System.currentTimeMillis()
                    )
                    dao.insertStation(updatedStation)
                    return@withContext true
                }
            } catch (_: Exception) {
                // Fallback
            }
        }

        // Standard IPMet sync simulation: updates micro-variations and Doppler reflectivity
        val variation = (Math.random() * 0.6 - 0.3)
        val newTemp = Math.round((currentStation.currentTemp + variation) * 10.0) / 10.0
        val updated = currentStation.copy(
            currentTemp = newTemp,
            lastUpdated = System.currentTimeMillis()
        )
        dao.insertStation(updated)

        // Regenerate live 24h hourly and 15-day daily forecasts matching current clock
        val hourly = generateHourlyForStation(updated)
        val daily = generateDailyForStation(updated)
        dao.deleteDailyByStation(stationId)
        dao.insertDailyForecasts(daily)
        dao.deleteHourlyByStation(stationId)
        dao.insertHourlyForecasts(hourly)

        // Também sincroniza dados do CIIAGRO se disponível
        refreshCiiagroData(stationId)

        return@withContext true
    }

    suspend fun refreshCiiagroData(stationId: String): Boolean = withContext(Dispatchers.IO) {
        val existing = dao.getCiiagroRecordSync(stationId)
        val station = dao.getStationByIdSync(stationId) ?: return@withContext false

        try {
            val res = CiiagroClient.apiService.getStationAgroData(station.name)
            val live = res.liveData
            val forecast = res.forecasts?.firstOrNull()

            if (live != null) {
                val record = CiiagroRecordEntity(
                    stationId = stationId,
                    municipality = live.municipality ?: station.name,
                    airTemp = live.airTemp ?: station.currentTemp,
                    tempMin = live.tempMin ?: station.minTemp,
                    tempMax = live.tempMax ?: station.maxTemp,
                    relativeHumidity = live.relativeHumidity ?: station.humidity,
                    rainAccumulatedMm = live.rainAccumulatedMm ?: station.rainVolumeMm,
                    et0MmDay = live.et0MmDay ?: (existing?.et0MmDay ?: 4.2),
                    windSpeedKmH = live.windSpeedKmH ?: station.windSpeed,
                    solarRadiationMj = live.solarRadiation ?: (existing?.solarRadiationMj ?: 19.5),
                    soilWaterDeficitRisk = live.waterBalanceStatus ?: (existing?.soilWaterDeficitRisk ?: "Sem Déficit"),
                    cropManagementRecommendation = forecast?.managementRecommendation ?: (existing?.cropManagementRecommendation ?: "Manejo agrometeorológico monitorado pelo CIIAGRO/IAC."),
                    forecastRain7DaysMm = forecast?.rainForecastMm ?: (existing?.forecastRain7DaysMm ?: 35.0),
                    liveDataSource = res.source ?: "CIIAGRO / IAC - SP",
                    lastUpdated = System.currentTimeMillis()
                )
                dao.insertCiiagroRecord(record)
                return@withContext true
            }
        } catch (_: Exception) {
            // Em caso de offline ou falha na API CIIAGRO, simula ou atualiza timestamp dos dados agronômicos locais
        }

        if (existing != null) {
            dao.insertCiiagroRecord(existing.copy(lastUpdated = System.currentTimeMillis()))
        } else {
            // Cria registro inicial se não existir
            val defaultRecord = CiiagroRecordEntity(
                stationId = stationId,
                municipality = station.name,
                airTemp = station.currentTemp,
                tempMin = station.minTemp,
                tempMax = station.maxTemp,
                relativeHumidity = station.humidity,
                rainAccumulatedMm = station.rainVolumeMm,
                et0MmDay = 4.2,
                windSpeedKmH = station.windSpeed,
                solarRadiationMj = 20.0,
                soilWaterDeficitRisk = if (station.humidity < 40) "Atenção Leve" else "Sem Déficit",
                cropManagementRecommendation = "Dados agrometeorológicos de ${station.name} sincronizados com a rede do CIIAGRO.",
                forecastRain7DaysMm = station.rainVolumeMm * 3.5,
                liveDataSource = "CIIAGRO / IAC - SP",
                lastUpdated = System.currentTimeMillis()
            )
            dao.insertCiiagroRecord(defaultRecord)
        }
        return@withContext true
    }

    suspend fun updatePreferences(prefs: UserPreferencesEntity) = withContext(Dispatchers.IO) {
        dao.insertUserPreferences(prefs)
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
                region = "Centro-Oeste / IPMet UNESP",
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
                region = "Oeste Paulista / IPMet UNESP",
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

    private fun generateHourlyForStation(station: WeatherStationEntity): List<HourlyForecastEntity> {
        val calendar = java.util.Calendar.getInstance()
        val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        
        // Gerar as próximas 24 horas consecutivas a partir da hora atual
        return (0..23).map { offset ->
            val hour = (currentHour + offset) % 24
            val hourStr = String.format(java.util.Locale.getDefault(), "%02d:00", hour)
            
            // Ciclo circadiano diurno/noturno realista (mínima entre 05h-06h, máxima às 14h-15h)
            val hourAngle = ((hour - 6) * Math.PI) / 12.0
            val tempRange = (station.maxTemp - station.minTemp).coerceAtLeast(3.0)
            val normalizedCycle = (Math.sin(hourAngle - Math.PI / 2.0) + 1.0) / 2.0
            val calculatedTemp = station.minTemp + (normalizedCycle * tempRange)
            val finalTemp = Math.round(calculatedTemp * 10.0) / 10.0

            // Probabilidade e precipitação dinâmica (pico convectivo vespertino entre 14h e 19h)
            val isAfternoonConvection = hour in 14..19
            val baseProb = if (isAfternoonConvection) {
                (station.rainProbability * 1.25).toInt().coerceIn(15, 95)
            } else if (hour in 20..23 || hour in 0..3) {
                (station.rainProbability * 0.65).toInt().coerceIn(10, 80)
            } else {
                (station.rainProbability * 0.35).toInt().coerceIn(5, 50)
            }

            val rainMm = if (baseProb >= 50) {
                val factor = if (isAfternoonConvection) 1.4 else 0.7
                Math.round(((station.rainVolumeMm / 3.5) * factor) * 10.0) / 10.0
            } else 0.0

            val condition = when {
                baseProb >= 75 -> "Chuva Forte / Trovoada"
                baseProb >= 50 -> "Pancada de Chuva"
                baseProb >= 25 -> "Parcialmente Nublado"
                else -> if (hour in 6..18) "Ensolarado" else "Céu Estrelado"
            }
            val iconType = when {
                baseProb >= 75 -> "storm"
                baseProb >= 50 -> "rain"
                baseProb >= 25 -> "cloudy"
                else -> "sunny"
            }

            HourlyForecastEntity(
                stationId = station.id,
                hourText = hourStr,
                temp = finalTemp,
                rainProbability = baseProb,
                rainVolumeMm = rainMm,
                condition = condition,
                iconType = iconType
            )
        }
    }

    private fun generateDailyForStation(station: WeatherStationEntity): List<DailyForecastEntity> {
        // 15 Dias completos com datas e dias da semana reais a partir do calendário do sistema
        val weekDayNames = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
        val baseCal = java.util.Calendar.getInstance()

        return (0..14).map { dayOffset ->
            val dayCal = java.util.Calendar.getInstance()
            dayCal.timeInMillis = baseCal.timeInMillis
            dayCal.add(java.util.Calendar.DAY_OF_YEAR, dayOffset)

            val dayOfWeekInt = dayCal.get(java.util.Calendar.DAY_OF_WEEK) // 1=Dom, 2=Seg, ... 7=Sáb
            val dayName = when (dayOffset) {
                0 -> "Hoje"
                1 -> "Amanhã"
                else -> weekDayNames[(dayOfWeekInt - 1).coerceIn(0, 6)]
            }

            val dateText = String.format(
                java.util.Locale.getDefault(),
                "%02d/%02d",
                dayCal.get(java.util.Calendar.DAY_OF_MONTH),
                dayCal.get(java.util.Calendar.MONTH) + 1
            )

            val deltaMin = (sin(dayOffset * 0.7) * 2.2) - 0.5
            val deltaMax = (cos(dayOffset * 0.6) * 3.0) - 0.8
            val rawProb = ((station.rainProbability + (dayOffset * 7) + (if (dayOffset % 3 == 0) 25 else -10)) % 90).coerceIn(10, 88)
            val isRainy = rawProb >= 50
            val rainMm = if (isRainy) {
                Math.round(((station.rainVolumeMm * 0.8) + (dayOffset * 1.5) + (if (rawProb > 70) 8.0 else 2.0)) * 10.0) / 10.0
            } else 0.0

            val condition = when {
                rawProb >= 75 -> "Tempestade com Trovoada"
                rawProb >= 55 -> "Pancadas de Chuva"
                rawProb >= 35 -> "Parcialmente Nublado"
                else -> "Sol e Poucas Nuvens"
            }
            val iconType = when {
                rawProb >= 75 -> "storm"
                rawProb >= 50 -> "rain"
                rawProb >= 30 -> "cloudy"
                else -> "sunny"
            }

            DailyForecastEntity(
                stationId = station.id,
                dayOfWeek = dayName,
                dateText = dateText,
                minTemp = Math.round((station.minTemp + deltaMin) * 10.0) / 10.0,
                maxTemp = Math.round((station.maxTemp + deltaMax) * 10.0) / 10.0,
                rainProbability = rawProb,
                rainVolumeMm = rainMm,
                condition = condition,
                iconType = iconType
            )
        }
    }
}
