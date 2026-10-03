package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Open-Meteo (https://open-meteo.com/en/docs) – previsão atual, horária e diária, sem chave.
 * Termos: a API gratuita é para uso NÃO comercial. Troque OPEN_METEO_BASE_URL pelo
 * endpoint do plano comercial antes de publicar com anúncios/assinatura.
 */
object OpenMeteoConfig {
    const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com/"
    const val SOURCE_LABEL = "Open-Meteo"
    const val TIMEZONE = "America/Sao_Paulo"
    const val CURRENT_PARAMS =
        "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,wind_speed_10m,wind_direction_10m,weather_code,pressure_msl"
    const val HOURLY_PARAMS =
        "temperature_2m,relative_humidity_2m,precipitation,precipitation_probability,wind_speed_10m,wind_direction_10m,wind_gusts_10m,weather_code"
    const val DAILY_PARAMS =
        "temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,wind_speed_10m_max,et0_fao_evapotranspiration,shortwave_radiation_sum,sunrise,sunset,uv_index_max"
    const val FORECAST_DAYS = 16 // máximo permitido (17 = HTTP 400)
    /**
     * Dias passados na série DIÁRIA (chuva/ET0 acumuladas). 30 dias para o acumulado mensal
     * (a API aceita até 92). A série HORÁRIA é limitada separadamente por PAST_HOURS/FORECAST_HOURS
     * para não baixar 30 dias de dados horários sem necessidade.
     */
    const val PAST_DAYS = 30
    /** Horas passadas na série horária (7 dias – usadas nos índices de doença e no acumulado de 24 h). */
    const val PAST_HOURS = 7 * 24
    /** Horas futuras na série horária (16 dias, o mesmo horizonte de FORECAST_DAYS). */
    const val FORECAST_HOURS = FORECAST_DAYS * 24
    /** Janelas de acumulado de chuva exibidas no app (dias). */
    val ACCUMULATION_WINDOWS = listOf(7, 15, 30)
}

@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    @Json(name = "utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    @Json(name = "current") val current: OpenMeteoCurrent? = null,
    @Json(name = "hourly") val hourly: OpenMeteoHourly? = null,
    @Json(name = "daily") val daily: OpenMeteoDaily? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoCurrent(
    @Json(name = "time") val time: String? = null,
    @Json(name = "temperature_2m") val temperature: Double? = null,
    @Json(name = "relative_humidity_2m") val humidity: Double? = null,
    @Json(name = "apparent_temperature") val apparentTemperature: Double? = null,
    @Json(name = "precipitation") val precipitation: Double? = null,
    @Json(name = "wind_speed_10m") val windSpeed: Double? = null,
    @Json(name = "wind_direction_10m") val windDirection: Double? = null,
    @Json(name = "weather_code") val weatherCode: Int? = null,
    @Json(name = "pressure_msl") val pressureMsl: Double? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoHourly(
    @Json(name = "time") val time: List<String>? = null,
    @Json(name = "temperature_2m") val temperature: List<Double?>? = null,
    @Json(name = "relative_humidity_2m") val humidity: List<Double?>? = null,
    @Json(name = "precipitation") val precipitation: List<Double?>? = null,
    @Json(name = "precipitation_probability") val precipitationProbability: List<Double?>? = null,
    @Json(name = "wind_speed_10m") val windSpeed: List<Double?>? = null,
    @Json(name = "wind_direction_10m") val windDirection: List<Double?>? = null,
    @Json(name = "wind_gusts_10m") val windGusts: List<Double?>? = null,
    @Json(name = "weather_code") val weatherCode: List<Int?>? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoDaily(
    @Json(name = "time") val time: List<String>? = null,
    @Json(name = "temperature_2m_max") val tempMax: List<Double?>? = null,
    @Json(name = "temperature_2m_min") val tempMin: List<Double?>? = null,
    @Json(name = "precipitation_sum") val precipitationSum: List<Double?>? = null,
    @Json(name = "precipitation_probability_max") val precipitationProbabilityMax: List<Double?>? = null,
    @Json(name = "wind_speed_10m_max") val windSpeedMax: List<Double?>? = null,
    @Json(name = "et0_fao_evapotranspiration") val et0: List<Double?>? = null,
    @Json(name = "shortwave_radiation_sum") val shortwaveRadiationSum: List<Double?>? = null,
    @Json(name = "sunrise") val sunrise: List<String?>? = null,
    @Json(name = "sunset") val sunset: List<String?>? = null,
    @Json(name = "uv_index_max") val uvIndexMax: List<Double?>? = null
)

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = OpenMeteoConfig.CURRENT_PARAMS,
        @Query("hourly") hourly: String = OpenMeteoConfig.HOURLY_PARAMS,
        @Query("daily") daily: String = OpenMeteoConfig.DAILY_PARAMS,
        @Query("forecast_days") forecastDays: Int = OpenMeteoConfig.FORECAST_DAYS,
        @Query("past_days") pastDays: Int = OpenMeteoConfig.PAST_DAYS,
        @Query("past_hours") pastHours: Int = OpenMeteoConfig.PAST_HOURS,
        @Query("forecast_hours") forecastHours: Int = OpenMeteoConfig.FORECAST_HOURS,
        @Query("timezone") timezone: String = OpenMeteoConfig.TIMEZONE,
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh"
    ): OpenMeteoResponse
}

object OpenMeteoClient {
    private val okHttpClient = SimetApiConfig.applyTo(
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
    ).build()

    fun create(baseUrl: String = OpenMeteoConfig.OPEN_METEO_BASE_URL, client: OkHttpClient = okHttpClient): OpenMeteoApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(OpenMeteoApi::class.java)

    /** Com SIMET_API_BASE_URL configurado, a mesma interface aponta para o servidor próprio (<base>/v1/forecast). */
    val api: OpenMeteoApi by lazy { create(SimetApiConfig.openMeteoBaseUrl()) }
}

/** Tabela local WMO weather_code → texto pt-BR e tipo de ícone do app. */
object WmoCodes {
    fun description(code: Int?): String = when (code) {
        null -> "—"
        0 -> "Céu limpo"
        1 -> "Predominantemente limpo"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Nevoeiro"
        51 -> "Garoa fraca"
        53 -> "Garoa moderada"
        55 -> "Garoa intensa"
        56, 57 -> "Garoa congelante"
        61 -> "Chuva fraca"
        63 -> "Chuva moderada"
        65 -> "Chuva forte"
        66, 67 -> "Chuva congelante"
        71, 73, 75, 77 -> "Neve"
        80 -> "Pancadas de chuva fracas"
        81 -> "Pancadas de chuva moderadas"
        82 -> "Pancadas de chuva violentas"
        85, 86 -> "Pancadas de neve"
        95 -> "Trovoada"
        96, 99 -> "Trovoada com granizo"
        else -> "Código WMO $code"
    }

    fun iconType(code: Int?): String = when (code) {
        null -> "cloudy"
        0, 1 -> "sunny"
        2, 3 -> "cloudy"
        45, 48 -> "fog"
        in 51..67, in 80..82 -> "rain"
        in 71..77, 85, 86 -> "cloudy"
        in 95..99 -> "storm"
        else -> "cloudy"
    }
}

/** Converte graus meteorológicos (de onde o vento vem) em rosa dos ventos pt-BR. */
fun degreesToCompass(deg: Double?): String? {
    if (deg == null) return null
    val dirs = listOf("N", "NNE", "NE", "ENE", "L", "ESE", "SE", "SSE", "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO")
    val idx = (((deg % 360) + 360) % 360 / 22.5 + 0.5).toInt() % 16
    return dirs[idx]
}

/** Rosa dos ventos pt-BR (N, NNE, …, L, …, O) ou inglesa → graus meteorológicos (de onde o vento vem). */
fun compassToDegrees(compass: String?): Double? {
    if (compass.isNullOrBlank() || compass.trim() == "—") return null
    val token = compass.trim().substringBefore(' ').uppercase()
    val pt = listOf("N", "NNE", "NE", "ENE", "L", "ESE", "SE", "SSE", "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO")
    val en = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    val idx = pt.indexOf(token).takeIf { it >= 0 } ?: en.indexOf(token).takeIf { it >= 0 } ?: return null
    return idx * 22.5
}
