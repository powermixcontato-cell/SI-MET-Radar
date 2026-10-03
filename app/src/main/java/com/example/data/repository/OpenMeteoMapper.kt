package com.example.data.repository

import com.example.data.local.entity.DailyForecastEntity
import com.example.data.local.entity.HourlyForecastEntity
import com.example.data.local.entity.WeatherStationEntity
import com.example.data.remote.OpenMeteoConfig
import com.example.data.remote.OpenMeteoResponse
import com.example.data.remote.WmoCodes
import com.example.data.remote.degreesToCompass
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Resumo agro calculado a partir da Open-Meteo (past_days=30 na série diária + previsão). */
data class OpenMeteoAgroSummary(
    /** Soma da precipitação horária das últimas 24 h (past_days). */
    val rainLast24hMm: Double? = null,
    val rainPast7DaysMm: Double?,
    val et0Past7DaysMm: Double?,
    val rainNext7DaysMm: Double?,
    val et0TodayMm: Double?,
    val radiationTodayMj: Double?,
    val tempMinToday: Double?,
    val tempMaxToday: Double?,
    /** Balanço hídrico simples (estimativa) = chuva acumulada 7 d − ET0 acumulada 7 d. */
    val waterBalance7DaysMm: Double?,
    /** Chuva acumulada nos últimos 30 dias completos (ou nos [past30DaysAvailable] dias disponíveis). */
    val rainPast30DaysMm: Double? = null,
    val et0Past30DaysMm: Double? = null,
    val waterBalance30DaysMm: Double? = null,
    /** Quantos dias entraram no "acumulado 30 dias" (rótulo correto quando a API devolve menos). */
    val past30DaysAvailable: Int = 0
)

data class OpenMeteoMapped(
    val hourly: List<HourlyForecastEntity>,
    val daily: List<DailyForecastEntity>,
    val station: WeatherStationEntity?,
    val agro: OpenMeteoAgroSummary,
    /** Série completa (diária: 30 dias passados + previsão; horária: 7 dias passados + previsão). Só em memória. */
    val series: AgroSeries? = null
)

/** Uma hora da série Open-Meteo (valores podem faltar → null; nunca preenchidos com valores inventados). */
data class AgroHour(
    val time: String, // "yyyy-MM-ddTHH:mm" (America/Sao_Paulo)
    val temp: Double?,
    val rh: Double?,
    val rainMm: Double?,
    val prob: Int?,
    val windKmh: Double?,
    val gustKmh: Double?
)

data class AgroDay(
    val date: String, // "yyyy-MM-dd"
    val rainMm: Double?,
    val et0Mm: Double?,
    val tMin: Double?,
    val tMax: Double?,
    val prob: Int?
)

/** Série horária/diária com índice da hora/dia atual (dados reais da Open-Meteo; diária com past_days=30, horária com 7 dias passados). */
data class AgroSeries(
    val stationId: String,
    val hours: List<AgroHour>,
    val nowIndex: Int,
    val days: List<AgroDay>,
    val todayIndex: Int,
    val fetchedAt: Long
)

/** Funções puras de mapeamento Open-Meteo → entidades do app (testáveis sem rede). */
object OpenMeteoMapper {
    private val WEEK_DAYS = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")

    private fun round1(v: Double) = (v * 10.0).roundToInt() / 10.0

    /** dBZ equivalente estimado a partir da taxa de chuva (Marshall-Palmer). Não é medida de radar. */
    fun estimatedDbzFromRate(mmPerHour: Double?): Int {
        if (mmPerHour == null || mmPerHour <= 0.0) return 0
        return (10 * log10(200 * mmPerHour.pow(1.6))).roundToInt().coerceAtLeast(0)
    }

    fun map(res: OpenMeteoResponse, base: WeatherStationEntity, nowMillis: Long): OpenMeteoMapped {
        val cur = res.current
        val nowKey = cur?.time
        val todayStr = nowKey?.take(10) ?: java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone(OpenMeteoConfig.TIMEZONE)
        }.format(java.util.Date(nowMillis))
        val hourKey = nowKey?.let { if (it.length >= 13) it.take(13) + ":00" else null }

        // ---- Horário: 24 h a partir da hora atual ----
        val h = res.hourly
        val hourly = mutableListOf<HourlyForecastEntity>()
        var currentHourProb: Int? = null
        var currentHourRate: Double? = null
        var last24h: Double? = null
        val hTimes = h?.time.orEmpty()
        if (hTimes.isNotEmpty()) {
            var start = if (hourKey != null) hTimes.indexOf(hourKey) else -1
            if (start < 0) start = hTimes.indexOfFirst { it >= (hourKey ?: "") }.coerceAtLeast(0)
            currentHourProb = h?.precipitationProbability?.getOrNull(start)?.roundToInt()
            currentHourRate = h?.precipitation?.getOrNull(start)
            if (start >= 24) {
                val vals = (start - 24 until start).mapNotNull { h?.precipitation?.getOrNull(it) }
                if (vals.size >= 20) last24h = round1(vals.sum())
            }
            for (i in start until minOf(start + 24, hTimes.size)) {
                val temp = h?.temperature?.getOrNull(i) ?: continue
                val code = h.weatherCode?.getOrNull(i)
                hourly += HourlyForecastEntity(
                    stationId = base.id,
                    hourText = hTimes[i].substringAfter('T').take(5),
                    temp = round1(temp),
                    rainProbability = h.precipitationProbability?.getOrNull(i)?.roundToInt() ?: 0,
                    rainVolumeMm = round1(h.precipitation?.getOrNull(i) ?: 0.0),
                    condition = WmoCodes.description(code),
                    iconType = WmoCodes.iconType(code)
                )
            }
        }

        // ---- Diário: hoje + próximos dias (até 16) ----
        val d = res.daily
        val dTimes = d?.time.orEmpty()
        val daily = mutableListOf<DailyForecastEntity>()
        val todayIdx = dTimes.indexOf(todayStr)
        if (todayIdx >= 0) {
            for (i in todayIdx until minOf(todayIdx + OpenMeteoConfig.FORECAST_DAYS, dTimes.size)) {
                val tMax = d?.tempMax?.getOrNull(i) ?: continue
                val tMin = d.tempMin?.getOrNull(i) ?: continue
                val parts = dTimes[i].split("-").mapNotNull { it.toIntOrNull() }
                if (parts.size != 3) continue
                val (yy, mm, dd) = parts
                val cal = java.util.GregorianCalendar(yy, mm - 1, dd)
                val offset = i - todayIdx
                val dayName = when (offset) {
                    0 -> "Hoje"
                    1 -> "Amanhã"
                    else -> WEEK_DAYS[(cal.get(java.util.Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)]
                }
                val prob = d.precipitationProbabilityMax?.getOrNull(i)?.roundToInt() ?: 0
                val rain = d.precipitationSum?.getOrNull(i) ?: 0.0
                // Ícone diário pela chuva prevista (não há weather_code diário na lista de parâmetros)
                val icon = when {
                    rain >= 20.0 && prob >= 60 -> "storm"
                    rain >= 1.0 && prob >= 40 -> "rain"
                    prob >= 25 -> "cloudy"
                    else -> "sunny"
                }
                val cond = when (icon) {
                    "storm" -> "Chuva forte prevista"
                    "rain" -> "Chuva prevista"
                    "cloudy" -> "Possibilidade de chuva"
                    else -> "Sem chuva significativa"
                }
                daily += DailyForecastEntity(
                    stationId = base.id,
                    dayOfWeek = dayName,
                    dateText = String.format(Locale.US, "%02d/%02d", dd, mm),
                    minTemp = round1(tMin),
                    maxTemp = round1(tMax),
                    rainProbability = prob,
                    rainVolumeMm = round1(rain),
                    condition = cond,
                    iconType = icon
                )
            }
        }

        // ---- Agro (últimos 7 e 30 dias + próximos 7) ----
        fun sumRange(list: List<Double?>?, from: Int, to: Int): Double? {
            if (list == null || from < 0 || to > list.size || from >= to) return null
            val vals = (from until to).mapNotNull { list.getOrNull(it) }
            return if (vals.isEmpty()) null else round1(vals.sum())
        }
        val past7Rain = if (todayIdx >= 1) sumRange(d?.precipitationSum, maxOf(0, todayIdx - 7), todayIdx) else null
        val past7Et0 = if (todayIdx >= 1) sumRange(d?.et0, maxOf(0, todayIdx - 7), todayIdx) else null
        val next7Rain = if (todayIdx >= 0) sumRange(d?.precipitationSum, todayIdx, minOf(todayIdx + 7, dTimes.size)) else null
        val past30Start = if (todayIdx >= 1) maxOf(0, todayIdx - 30) else 0
        val past30Rain = if (todayIdx >= 1) sumRange(d?.precipitationSum, past30Start, todayIdx) else null
        val past30Et0 = if (todayIdx >= 1) sumRange(d?.et0, past30Start, todayIdx) else null
        val agro = OpenMeteoAgroSummary(
            rainLast24hMm = last24h,
            rainPast7DaysMm = past7Rain,
            et0Past7DaysMm = past7Et0,
            rainNext7DaysMm = next7Rain,
            et0TodayMm = d?.et0?.getOrNull(todayIdx)?.let(::round1),
            radiationTodayMj = d?.shortwaveRadiationSum?.getOrNull(todayIdx)?.let(::round1),
            tempMinToday = d?.tempMin?.getOrNull(todayIdx),
            tempMaxToday = d?.tempMax?.getOrNull(todayIdx),
            waterBalance7DaysMm = if (past7Rain != null && past7Et0 != null) round1(past7Rain - past7Et0) else null,
            rainPast30DaysMm = past30Rain,
            et0Past30DaysMm = past30Et0,
            waterBalance30DaysMm = if (past30Rain != null && past30Et0 != null) round1(past30Rain - past30Et0) else null,
            past30DaysAvailable = if (todayIdx >= 1) todayIdx - past30Start else 0
        )

        // ---- Estação (tempo atual) ----
        val station = if (cur?.temperature != null) {
            val windKmh = cur.windSpeed
            val compass = degreesToCompass(cur.windDirection)
            val todaySunrise = d?.sunrise?.getOrNull(todayIdx)?.substringAfter('T')?.take(5)
            val todaySunset = d?.sunset?.getOrNull(todayIdx)?.substringAfter('T')?.take(5)
            base.copy(
                currentTemp = round1(cur.temperature),
                feelsLike = cur.apparentTemperature?.let(::round1) ?: round1(cur.temperature),
                minTemp = agro.tempMinToday?.let(::round1) ?: base.minTemp,
                maxTemp = agro.tempMaxToday?.let(::round1) ?: base.maxTemp,
                humidity = cur.humidity?.roundToInt() ?: base.humidity,
                pressure = cur.pressureMsl?.roundToInt() ?: base.pressure,
                windSpeed = windKmh?.let(::round1) ?: 0.0,
                windDirection = if (compass != null && windKmh != null) "$compass ${windKmh.roundToInt()} km/h" else "—",
                rainVolumeMm = round1(d?.precipitationSum?.getOrNull(todayIdx) ?: 0.0),
                rainProbability = currentHourProb ?: d?.precipitationProbabilityMax?.getOrNull(todayIdx)?.roundToInt() ?: 0,
                dbzReflectivity = estimatedDbzFromRate(currentHourRate),
                weatherCondition = WmoCodes.description(cur.weatherCode),
                iconType = WmoCodes.iconType(cur.weatherCode),
                synopticSummary = "Previsão numérica ${OpenMeteoConfig.SOURCE_LABEL} para ${base.name} (modelo, não é observação de estação).",
                sunrise = todaySunrise ?: base.sunrise,
                sunset = todaySunset ?: base.sunset,
                uvIndex = d?.uvIndexMax?.getOrNull(todayIdx)?.roundToInt() ?: base.uvIndex,
                lastUpdated = nowMillis
            )
        } else null

        // ---- Série completa para os índices de Cana & Citros ----
        val series = run {
            val hours = hTimes.indices.map { i ->
                AgroHour(
                    time = hTimes[i],
                    temp = h?.temperature?.getOrNull(i),
                    rh = h?.humidity?.getOrNull(i),
                    rainMm = h?.precipitation?.getOrNull(i),
                    prob = h?.precipitationProbability?.getOrNull(i)?.roundToInt(),
                    windKmh = h?.windSpeed?.getOrNull(i),
                    gustKmh = h?.windGusts?.getOrNull(i)
                )
            }
            var nowIdx = if (hourKey != null) hTimes.indexOf(hourKey) else -1
            if (nowIdx < 0 && hTimes.isNotEmpty()) nowIdx = hTimes.indexOfFirst { it >= (hourKey ?: "") }
            val days = dTimes.indices.map { i ->
                AgroDay(
                    date = dTimes[i],
                    rainMm = d?.precipitationSum?.getOrNull(i),
                    et0Mm = d?.et0?.getOrNull(i),
                    tMin = d?.tempMin?.getOrNull(i),
                    tMax = d?.tempMax?.getOrNull(i),
                    prob = d?.precipitationProbabilityMax?.getOrNull(i)?.roundToInt()
                )
            }
            if (hours.isEmpty() && days.isEmpty()) null
            else AgroSeries(base.id, hours, nowIdx, days, todayIdx, nowMillis)
        }

        return OpenMeteoMapped(hourly, daily, station, agro, series)
    }
}
