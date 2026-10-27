package com.example.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Lugar para a previsão "Chuva no seu local" (cidade da busca ou GPS). */
data class RainPlace(val name: String, val admin1: String?, val lat: Double, val lon: Double) {
    val label: String get() = if (admin1.isNullOrBlank()) name else "$name, ${ufOf(admin1) ?: admin1}"

    fun encode(): String = listOf(name.replace("|", " "), admin1.orEmpty().replace("|", " "), lat.toString(), lon.toString()).joinToString("|")

    companion object {
        fun decode(s: String?): RainPlace? {
            val p = s?.split("|") ?: return null
            if (p.size != 4) return null
            val lat = p[2].toDoubleOrNull() ?: return null
            val lon = p[3].toDoubleOrNull() ?: return null
            return RainPlace(p[0], p[1].ifBlank { null }, lat, lon)
        }

        private val UF = mapOf(
            "Acre" to "AC", "Alagoas" to "AL", "Amapá" to "AP", "Amazonas" to "AM", "Bahia" to "BA", "Ceará" to "CE",
            "Distrito Federal" to "DF", "Espírito Santo" to "ES", "Goiás" to "GO", "Maranhão" to "MA", "Mato Grosso" to "MT",
            "Mato Grosso do Sul" to "MS", "Minas Gerais" to "MG", "Pará" to "PA", "Paraíba" to "PB", "Paraná" to "PR",
            "Pernambuco" to "PE", "Piauí" to "PI", "Rio de Janeiro" to "RJ", "Rio Grande do Norte" to "RN",
            "Rio Grande do Sul" to "RS", "Rondônia" to "RO", "Roraima" to "RR", "Santa Catarina" to "SC",
            "São Paulo" to "SP", "Sergipe" to "SE", "Tocantins" to "TO"
        )
        fun ufOf(admin1: String): String? = UF[admin1] ?: UF.entries.firstOrNull { it.key.equals(admin1, true) }?.value
    }
}

/**
 * Data/hora local (Brasília) sem java.time (minSdk 24 sem desugaring).
 * Comparável; [epochDay] para diferença de dias.
 */
data class BrtTime(val year: Int, val month: Int, val day: Int, val hour: Int = 0, val minute: Int = 0) : Comparable<BrtTime> {
    private val key: Long get() = ((((year * 100L + month) * 100 + day) * 100 + hour) * 100 + minute)
    override fun compareTo(other: BrtTime): Int = key.compareTo(other.key)
    val epochDay: Long get() = GregorianCalendar(TimeZone.getTimeZone("UTC")).run {
        clear(); set(year, month - 1, day); timeInMillis / 86_400_000L
    }
    val date: BrtTime get() = BrtTime(year, month, day)
    /** 1 = domingo … 7 = sábado. */
    val dayOfWeek: Int get() = GregorianCalendar(TimeZone.getTimeZone("UTC")).run { clear(); set(year, month - 1, day); get(Calendar.DAY_OF_WEEK) }
    fun plusDays(n: Int): BrtTime = GregorianCalendar(TimeZone.getTimeZone("UTC")).run {
        clear(); set(year, month - 1, day); add(Calendar.DAY_OF_MONTH, n)
        BrtTime(get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH))
    }

    companion object {
        /** "2026-10-07T15:00" ou "2026-10-07". */
        fun parse(s: String): BrtTime {
            val y = s.substring(0, 4).toInt(); val m = s.substring(5, 7).toInt(); val d = s.substring(8, 10).toInt()
            return if (s.length >= 16) BrtTime(y, m, d, s.substring(11, 13).toInt(), s.substring(14, 16).toInt()) else BrtTime(y, m, d)
        }
        fun fromEpochMs(ms: Long): BrtTime = GregorianCalendar(TimeZone.getTimeZone("America/Sao_Paulo")).run {
            timeInMillis = ms
            BrtTime(get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH), get(Calendar.HOUR_OF_DAY), get(Calendar.MINUTE))
        }
    }
}

data class RainHour(val time: BrtTime, val mm: Double?, val prob: Int?)
data class RainDay(val date: BrtTime, val mm: Double?, val prob: Int?)
data class ModelRun(val model: String, val initUtcEpoch: Long, val availableUtcEpoch: Long)

data class LocalRainForecast(
    val place: RainPlace,
    val hourly: List<RainHour>,
    val daily: List<RainDay>,
    val fetchedAtMs: Long,
    val runs: List<ModelRun> = emptyList()
)

/**
 * Previsão de chuva por ponto (Open-Meteo Forecast, modelo "best_match") e busca de cidades
 * (Open-Meteo Geocoding, só Brasil). Rede só em IO (chamar de Dispatchers.IO); cache de 10 min.
 */
object LocalRainService {
    private const val CACHE_MS = 10 * 60 * 1000L
    /** Limiar para considerar "hora com chuva" (mm/h). */
    const val RAIN_MM = 0.2

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    }
    private val fcCache = ConcurrentHashMap<String, LocalRainForecast>()
    private val geoCache = ConcurrentHashMap<String, Pair<Long, List<RainPlace>>>()
    @Volatile private var runsCache: Pair<Long, List<ModelRun>>? = null

    private fun get(url: String): String {
        client.newCall(Request.Builder().url(url).header("User-Agent", "SI-MET-Radar/5.1").build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            return r.body?.string() ?: throw java.io.IOException("resposta vazia")
        }
    }

    fun searchCities(query: String): List<RainPlace> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val key = q.lowercase()
        geoCache[key]?.let { if (System.currentTimeMillis() - it.first < CACHE_MS) return it.second }
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", q).addQueryParameter("count", "8")
            .addQueryParameter("language", "pt").addQueryParameter("countryCode", "BR")
            .addQueryParameter("format", "json").build().toString()
        val list = parseGeocoding(get(url))
        geoCache[key] = System.currentTimeMillis() to list
        return list
    }

    fun forecast(place: RainPlace, force: Boolean = false): LocalRainForecast {
        val key = "%.3f,%.3f".format(java.util.Locale.US, place.lat, place.lon)
        if (!force) fcCache[key]?.let { if (System.currentTimeMillis() - it.fetchedAtMs < CACHE_MS) return it.copy(place = place) }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${"%.4f".format(java.util.Locale.US, place.lat)}" +
            "&longitude=${"%.4f".format(java.util.Locale.US, place.lon)}" +
            "&hourly=precipitation,precipitation_probability" +
            "&daily=precipitation_sum,precipitation_probability_max" +
            "&forecast_days=16&timezone=America%2FSao_Paulo"
        val parsed = parseForecast(get(url), place, System.currentTimeMillis())
        val withRuns = parsed.copy(runs = modelRuns())
        fcCache[key] = withRuns
        return withRuns
    }

    /** Rodadas mais recentes dos modelos que compõem o "best_match" na América do Sul (metadados oficiais da Open-Meteo). */
    private fun modelRuns(): List<ModelRun> {
        runsCache?.let { if (System.currentTimeMillis() - it.first < CACHE_MS) return it.second }
        val out = listOf("ecmwf_ifs025" to "ECMWF IFS", "ncep_gfs013" to "GFS").mapNotNull { (id, name) ->
            try { parseMeta(name, get("https://api.open-meteo.com/data/$id/static/meta.json")) } catch (_: Exception) { null }
        }
        runsCache = System.currentTimeMillis() to out
        return out
    }

    // ---------- parsing (puro, testado em unit test) ----------

    fun parseGeocoding(json: String): List<RainPlace> {
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            if (o.optString("country_code") != "BR") return@mapNotNull null
            if (!o.has("latitude") || !o.has("longitude")) return@mapNotNull null
            RainPlace(o.optString("name"), o.optString("admin1").ifBlank { null }, o.getDouble("latitude"), o.getDouble("longitude"))
        }
    }

    fun parseForecast(json: String, place: RainPlace, fetchedAtMs: Long): LocalRainForecast {
        val root = JSONObject(json)
        if (root.optBoolean("error", false)) throw java.io.IOException(root.optString("reason", "erro Open-Meteo"))
        val h = root.getJSONObject("hourly")
        val ht = h.getJSONArray("time")
        val hp = h.optJSONArray("precipitation")
        val hq = h.optJSONArray("precipitation_probability")
        val hourly = (0 until ht.length()).map { i ->
            RainHour(
                BrtTime.parse(ht.getString(i)),
                hp?.takeIf { !it.isNull(i) }?.getDouble(i),
                hq?.takeIf { !it.isNull(i) }?.getInt(i)
            )
        }
        val d = root.getJSONObject("daily")
        val dt = d.getJSONArray("time")
        val dp = d.optJSONArray("precipitation_sum")
        val dq = d.optJSONArray("precipitation_probability_max")
        val daily = (0 until dt.length()).map { i ->
            RainDay(
                BrtTime.parse(dt.getString(i)),
                dp?.takeIf { !it.isNull(i) }?.getDouble(i),
                dq?.takeIf { !it.isNull(i) }?.getInt(i)
            )
        }
        return LocalRainForecast(place, hourly, daily, fetchedAtMs)
    }

    fun parseMeta(model: String, json: String): ModelRun? {
        val o = JSONObject(json)
        val init = o.optLong("last_run_initialisation_time", 0L)
        val avail = o.optLong("last_run_availability_time", 0L)
        return if (init > 0) ModelRun(model, init, avail) else null
    }

    /** Próximas [hours] horas a partir da hora atual (horário de Brasília). */
    fun nextHours(fc: LocalRainForecast, now: BrtTime, hours: Int = 48): List<RainHour> {
        val start = now.copy(minute = 0)
        return fc.hourly.filter { it.time >= start }.take(hours)
    }

    private fun hh(t: BrtTime, now: BrtTime): String {
        val day = when (t.epochDay - now.epochDay) {
            0L -> ""
            1L -> " de amanhã"
            else -> " de %02d/%02d".format(t.day, t.month)
        }
        return "${t.hour}h$day"
    }

    /** Frase de início/fim da chuva nas próximas horas (sem inventar: só usa as horas recebidas). */
    fun rainSummary(next: List<RainHour>, now: BrtTime): String {
        if (next.isEmpty() || next.all { it.mm == null }) return "Sem dados horários de chuva para este ponto."
        fun wet(h: RainHour) = (h.mm ?: 0.0) >= RAIN_MM
        val horizon = next.size
        return if (wet(next.first())) {
            val stop = next.indexOfFirst { !wet(it) }
            if (stop < 0) "Chuva prevista agora e nas próximas $horizon h."
            else "Chuva prevista agora, até as ${hh(next[stop].time, now)}."
        } else {
            val start = next.indexOfFirst { wet(it) }
            if (start < 0) "Sem chuva prevista nas próximas $horizon h."
            else {
                val after = next.drop(start)
                val stop = after.indexOfFirst { !wet(it) }
                val tail = if (stop < 0) "" else " até as ${hh(after[stop].time, now)}"
                "Chuva prevista a partir das ${hh(next[start].time, now)}$tail."
            }
        }
    }

    fun nowBrt(): BrtTime = BrtTime.fromEpochMs(System.currentTimeMillis())
    fun epochToBrt(sec: Long): BrtTime = BrtTime.fromEpochMs(sec * 1000L)
}
