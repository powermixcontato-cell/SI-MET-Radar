package com.example.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Série diária de vazão (m³/s) do GloFAS v4 via Open-Meteo Flood API para um ponto de rio. */
data class GlofasSeries(
    val gridLat: Double,
    val gridLon: Double,
    val dates: List<String>,          // yyyy-MM-dd
    val discharge: List<Double?>,     // reanálise/controle (river_discharge)
    val ensembleMean: List<Double?>,  // média do conjunto (river_discharge_mean)
    val ensembleMax: List<Double?>,   // máximo do conjunto (river_discharge_max)
    val todayIndex: Int
)

/** Leitura de nível (cm) de uma estação telemétrica da ANA. */
data class AnaReading(val dateTime: String, val levelCm: Double)

/** Previsão diária resumida de um ponto (Open-Meteo) para indicadores de risco. */
data class ForecastRiskPoint(
    val lat: Double,
    val lon: Double,
    val dates: List<String>,
    val gustMax: List<Double?>,
    val rainSum: List<Double?>,
    val dailyCode: List<Int?>,
    /** CAPE máxima (J/kg) por dia, calculada da série horária. */
    val capeMax: List<Double?>,
    /** Dias em que a série horária tem trovoada (95) ou trovoada com granizo (96/99). */
    val thunderDays: List<Int?> // maior código 95..99 do dia, ou null
)

/**
 * Fontes públicas sem chave usadas nos módulos Alertas e Alagamentos/Enchentes (v5.1):
 * - Open-Meteo Flood API (GloFAS v4): https://flood-api.open-meteo.com/v1/flood
 * - ANA – telemetria (HidroWeb/SNIRH): https://telemetriaws1.ana.gov.br/ServiceANA.asmx
 * - Open-Meteo Forecast: https://api.open-meteo.com/v1/forecast
 * Todas as chamadas lançam exceção em erro de rede/HTTP; a UI mostra "indisponível" (nunca valores inventados).
 */
class HazardsService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
) {
    companion object {
        const val FLOOD_URL = "https://flood-api.open-meteo.com/v1/flood"
        const val ANA_URL = "https://telemetriaws1.ana.gov.br/ServiceANA.asmx/DadosHidrometeorologicos"
        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "SI-MET-Radar/5.1 (Android)").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            return resp.body?.string() ?: throw java.io.IOException("resposta vazia")
        }
    }

    private fun coords(points: List<Pair<Double, Double>>): String =
        "latitude=" + points.joinToString(",") { String.format(Locale.US, "%.3f", it.first) } +
            "&longitude=" + points.joinToString(",") { String.format(Locale.US, "%.3f", it.second) }

    /** Uma única chamada para vários pontos (a API devolve uma lista quando há mais de um ponto). */
    private fun objects(body: String): List<JSONObject> {
        val t = body.trim()
        return if (t.startsWith("[")) JSONArray(t).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        else listOf(JSONObject(t))
    }

    private fun JSONArray?.doubles(): List<Double?> =
        if (this == null) emptyList() else (0 until length()).map { if (isNull(it)) null else optDouble(it) }

    private fun JSONArray?.ints(): List<Int?> =
        if (this == null) emptyList() else (0 until length()).map { if (isNull(it)) null else optInt(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }

    fun fetchGlofas(points: List<Pair<Double, Double>>, pastDays: Int = 14, forecastDays: Int = 30, today: String): List<GlofasSeries> {
        if (points.isEmpty()) return emptyList()
        val url = "$FLOOD_URL?${coords(points)}&daily=river_discharge,river_discharge_mean,river_discharge_max" +
            "&past_days=$pastDays&forecast_days=$forecastDays"
        return objects(get(url)).map { o ->
            val d = o.optJSONObject("daily")
            val dates = d?.optJSONArray("time").strings()
            GlofasSeries(
                gridLat = o.optDouble("latitude"), gridLon = o.optDouble("longitude"),
                dates = dates,
                discharge = d?.optJSONArray("river_discharge").doubles(),
                ensembleMean = d?.optJSONArray("river_discharge_mean").doubles(),
                ensembleMax = d?.optJSONArray("river_discharge_max").doubles(),
                todayIndex = dates.indexOf(today).let { if (it >= 0) it else dates.indexOfFirst { s -> s >= today } }
            )
        }
    }

    /** Níveis (cm) das últimas [days] datas; ordem cronológica crescente. Datas no horário informado pela ANA. */
    fun fetchAnaLevels(stationCode: String, startDdMmYyyy: String, endDdMmYyyy: String): List<AnaReading> {
        val body = get("$ANA_URL?codEstacao=$stationCode&dataInicio=$startDdMmYyyy&dataFim=$endDdMmYyyy")
        val rows = Regex("(?s)<DadosHidrometereologicos[^>]*>(.*?)</DadosHidrometereologicos>").findAll(body)
        return rows.mapNotNull { m ->
            val r = m.groupValues[1]
            val dt = Regex("<DataHora>([^<]*)</DataHora>").find(r)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
            val lv = Regex("<Nivel>([^<]*)</Nivel>").find(r)?.groupValues?.get(1)?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
            AnaReading(dt, lv)
        }.toList().sortedBy { it.dateTime }
    }

    fun fetchForecastRisk(points: List<Pair<Double, Double>>, days: Int = 3): List<ForecastRiskPoint> {
        if (points.isEmpty()) return emptyList()
        val url = "$FORECAST_URL?${coords(points)}&daily=wind_gusts_10m_max,precipitation_sum,weather_code" +
            "&hourly=cape,weather_code&forecast_days=$days&timezone=America%2FSao_Paulo&wind_speed_unit=kmh"
        return objects(get(url)).map { o ->
            val d = o.optJSONObject("daily")
            val h = o.optJSONObject("hourly")
            val dates = d?.optJSONArray("time").strings()
            val hTimes = h?.optJSONArray("time").strings()
            val cape = h?.optJSONArray("cape").doubles()
            val codes = h?.optJSONArray("weather_code").ints()
            val capeMax = dates.map { day ->
                hTimes.indices.filter { hTimes[it].startsWith(day) }.mapNotNull { cape.getOrNull(it) }.maxOrNull()
            }
            val thunder = dates.map { day ->
                hTimes.indices.filter { hTimes[it].startsWith(day) }.mapNotNull { codes.getOrNull(it) }.filter { it in 95..99 }.maxOrNull()
            }
            ForecastRiskPoint(
                lat = o.optDouble("latitude"), lon = o.optDouble("longitude"),
                dates = dates,
                gustMax = d?.optJSONArray("wind_gusts_10m_max").doubles(),
                rainSum = d?.optJSONArray("precipitation_sum").doubles(),
                dailyCode = d?.optJSONArray("weather_code").ints(),
                capeMax = capeMax,
                thunderDays = thunder
            )
        }
    }
}
