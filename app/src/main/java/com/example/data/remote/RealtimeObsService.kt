package com.example.data.remote

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Observações quase em tempo real para o mapa (v5.1). Chamado só fora da thread principal.
 * - METAR (aviationweather.gov, NOAA/AWC): aeroportos de SP, PR, RS e vizinhos, observação horária (~30–60 min).
 * - CEMADEN (pluviômetros, JSON público por UF): chuva 1 h / 24 h, leitura a cada 10 min (~30 min de atraso).
 *   O JSON não traz coordenadas: agrupamos por município (sede, código IBGE → assets/simet_map/municipios_sp_pr_rs.json).
 * Os dois servidores não liberam CORS, por isso a busca é nativa e o resultado vai para o Leaflet via evaluateJavascript.
 * Nada é inventado: leituras sem horário, com horário no futuro ou com mais de 3 h (CEMADEN) / 6 h (METAR) são descartadas.
 */
object RealtimeObsService {
    private const val CACHE_MS = 10 * 60_000L
    private const val METAR_URL =
        "https://aviationweather.gov/api/data/metar?bbox=-34.0,-58.0,-19.5,-44.0&format=json&hours=3"
    private const val CEMADEN_URL = "https://resources.cemaden.gov.br/graficos/interativo/getJson2.php?uf="
    val SUPPORTED_UF = setOf("SP", "PR", "RS")

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    }

    private data class Cached(val at: Long, val json: String)
    private var metarCache: Cached? = null
    private val cemadenCache = HashMap<String, Cached>()
    @Volatile private var municipios: Map<String, DoubleArray>? = null

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "SI-MET-Radar/5.1 (Android)").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return resp.body?.string() ?: throw IllegalStateException("resposta vazia")
        }
    }

    private fun numOrNull(o: JSONObject, key: String): Double? {
        val v = o.opt(key) ?: return null
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.trim().removeSuffix("+").replace(',', '.').toDoubleOrNull()
            else -> null
        }?.takeIf { !it.isNaN() }
    }

    /** {"fetched":seg,"items":[{id,name,lat,lon,t,temp,dewp,wdir,wspd,wgst,wx,visib,raw}]} (vento em nós, como no METAR). */
    @Synchronized
    fun metarsJson(nowMs: Long = System.currentTimeMillis()): String {
        metarCache?.let { if (nowMs - it.at < CACHE_MS) return it.json }
        val arr = JSONArray(get(METAR_URL))
        val latest = LinkedHashMap<String, JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("icaoId").takeIf { it.isNotBlank() } ?: continue
            val t = o.optLong("obsTime", 0L)
            if (t <= 0L || t * 1000 > nowMs + 15 * 60_000L || nowMs - t * 1000 > 6 * 3_600_000L) continue
            val prev = latest[id]
            if (prev == null || prev.optLong("obsTime") < t) latest[id] = o
        }
        val items = JSONArray()
        for ((id, o) in latest) {
            val lat = numOrNull(o, "lat") ?: continue
            val lon = numOrNull(o, "lon") ?: continue
            items.put(JSONObject().apply {
                put("id", id)
                put("name", o.optString("name"))
                put("lat", lat); put("lon", lon)
                put("t", o.optLong("obsTime"))
                numOrNull(o, "temp")?.let { put("temp", it) }
                numOrNull(o, "dewp")?.let { put("dewp", it) }
                val wd = o.opt("wdir")
                if (wd is Number) put("wdir", wd.toDouble()) else if (wd is String && wd.isNotBlank()) put("wdirTxt", wd)
                numOrNull(o, "wspd")?.let { put("wspd", it) }
                numOrNull(o, "wgst")?.let { put("wgst", it) }
                o.optString("wxString").takeIf { it.isNotBlank() && it != "null" }?.let { put("wx", it) }
                o.opt("visib")?.let { if (it != JSONObject.NULL) put("visib", it.toString()) }
                put("raw", o.optString("rawOb"))
            })
        }
        val json = JSONObject().put("fetched", nowMs / 1000).put("items", items).toString()
        metarCache = Cached(nowMs, json)
        return json
    }

    private fun loadMunicipios(context: Context): Map<String, DoubleArray> = municipios ?: synchronized(this) {
        municipios ?: run {
            val txt = context.assets.open("simet_map/municipios_sp_pr_rs.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val o = JSONObject(txt)
            val m = HashMap<String, DoubleArray>(o.length() * 2)
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val a = o.optJSONArray(k) ?: continue
                m[k] = doubleArrayOf(a.optDouble(0), a.optDouble(1))
            }
            m.also { municipios = it }
        }
    }

    /**
     * {"fetched":seg,"uf":"RS","stations":N,"fresh":M,"items":[{c,lat,lon,n,a1,a24,t}]}
     * a1/a24 = maior acumulado (mm) entre as estações do município com leitura nas últimas 3 h; t = leitura mais recente (seg UTC).
     */
    @Synchronized
    fun cemadenJson(context: Context, ufRaw: String, nowMs: Long = System.currentTimeMillis()): String {
        val uf = ufRaw.trim().uppercase(Locale.ROOT)
        require(uf in SUPPORTED_UF) { "UF não suportada" }
        cemadenCache[uf]?.let { if (nowMs - it.at < CACHE_MS) return it.json }
        val arr = JSONArray(get(CEMADEN_URL + uf))
        val coords = loadMunicipios(context)
        // Horário do CEMADEN vem em UTC ("dd/MM/yy HH:mm")
        val fmt = SimpleDateFormat("dd/MM/yy HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
        class Agg(val city: String, val lat: Double, val lon: Double) { var n = 0; var a1: Double? = null; var a24: Double? = null; var t = 0L }
        val byCity = LinkedHashMap<String, Agg>()
        var fresh = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val t = try { fmt.parse(o.optString("datahoraUltimovalor"))?.time } catch (_: Exception) { null } ?: continue
            if (t > nowMs + 20 * 60_000L || nowMs - t > 3 * 3_600_000L) continue
            val code = o.opt("codibge")?.toString() ?: continue
            val c = coords[code] ?: continue
            fresh++
            val agg = byCity.getOrPut(code) { Agg(o.optString("cidade"), c[0], c[1]) }
            agg.n++
            numOrNull(o, "acc1hr")?.let { v -> if (v >= 0) agg.a1 = maxOf(agg.a1 ?: 0.0, v) }
            numOrNull(o, "acc24hr")?.let { v -> if (v >= 0) agg.a24 = maxOf(agg.a24 ?: 0.0, v) }
            // alguns pluviômetros chegam com relógio adiantado (até ~20 min): limita ao horário atual
            agg.t = maxOf(agg.t, minOf(t, nowMs) / 1000)
        }
        val items = JSONArray()
        for (a in byCity.values) {
            items.put(JSONObject().apply {
                put("c", a.city); put("lat", a.lat); put("lon", a.lon); put("n", a.n); put("t", a.t)
                a.a1?.let { put("a1", it) }
                a.a24?.let { put("a24", it) }
            })
        }
        val json = JSONObject().put("fetched", nowMs / 1000).put("uf", uf).put("stations", arr.length())
            .put("fresh", fresh).put("items", items).toString()
        cemadenCache[uf] = Cached(nowMs, json)
        return json
    }
}
