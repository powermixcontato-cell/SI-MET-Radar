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
 * CIIAGRO - Centro Integrado de Informações Agrometeorológicas
 * Instituto Agronômico de Campinas (IAC) / Secretaria de Agricultura e Abastecimento de SP
 * DESATIVADO: o endpoint abaixo responde 404 e não há API pública confirmada do CIIAGRO.
 * A classe foi mantida, mas não é mais chamada nem exibida como fonte (painel agro usa Open-Meteo).
 */
@JsonClass(generateAdapter = true)
data class CiiagroStationData(
    @Json(name = "id_estacao") val stationCode: String? = null,
    @Json(name = "municipio") val municipality: String? = null,
    @Json(name = "data_hora") val timestamp: String? = null,
    @Json(name = "temp_ar") val airTemp: Double? = null,
    @Json(name = "temp_min") val tempMin: Double? = null,
    @Json(name = "temp_max") val tempMax: Double? = null,
    @Json(name = "umidade_rel") val relativeHumidity: Int? = null,
    @Json(name = "chuva_acumulada_mm") val rainAccumulatedMm: Double? = null,
    @Json(name = "eto_dia_mm") val et0MmDay: Double? = null,
    @Json(name = "vento_vel_kmh") val windSpeedKmH: Double? = null,
    @Json(name = "vento_dir") val windDirection: String? = null,
    @Json(name = "radiacao_solar") val solarRadiation: Double? = null,
    @Json(name = "balanco_hidrico_status") val waterBalanceStatus: String? = null
)

@JsonClass(generateAdapter = true)
data class CiiagroForecastData(
    @Json(name = "municipio") val municipality: String? = null,
    @Json(name = "data") val dateText: String? = null,
    @Json(name = "precipitacao_prevista_mm") val rainForecastMm: Double? = null,
    @Json(name = "eto_prevista_mm") val et0ForecastMm: Double? = null,
    @Json(name = "recomendacao_manejo") val managementRecommendation: String? = null,
    @Json(name = "risco_deficit") val deficitRisk: String? = null
)

@JsonClass(generateAdapter = true)
data class CiiagroResponse(
    @Json(name = "sucesso") val success: Boolean? = true,
    @Json(name = "fonte") val source: String? = "CIIAGRO / IAC / SAA-SP",
    @Json(name = "dados_ao_vivo") val liveData: CiiagroStationData? = null,
    @Json(name = "previsoes_agro") val forecasts: List<CiiagroForecastData>? = null
)

interface CiiagroApiService {
    @GET("api/agrometeo/estacao")
    suspend fun getStationAgroData(
        @Query("municipio") municipality: String
    ): CiiagroResponse
}

object CiiagroClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    val apiService: CiiagroApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.ciiagro.sp.gov.br/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(CiiagroApiService::class.java)
    }
}
