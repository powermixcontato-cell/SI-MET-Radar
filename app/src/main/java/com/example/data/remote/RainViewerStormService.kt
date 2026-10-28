package com.example.data.remote

import android.graphics.BitmapFactory
import com.example.domain.StormCells
import com.example.viewmodel.StormCellTrajectory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Cidade conhecida usada só para dar nome/ETA às células. */
data class NamedPlace(val name: String, val lat: Double, val lon: Double)

/** Resultado real: horário do quadro de radar (UTC, segundos) e células detectadas (pode ser vazia). */
data class StormCellsResult(
    val frameTimeSec: Long,
    val previousFrameTimeSec: Long?,
    val cells: List<StormCellTrajectory>,
    val source: String = StormCellsResult.SOURCE
) {
    companion object {
        const val SOURCE = "RainViewer (mosaico de radares, api.rainviewer.com)"
    }
}

/**
 * Detecta células de tempestade reais em SP/PR/RS a partir do mosaico público da RainViewer (sem chave).
 * Rede só fora da thread principal. Falha de rede/formato → exceção (o app mostra erro; NUNCA usa dado inventado).
 */
class RainViewerStormService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
) {
    companion object {
        const val META_URL = "https://api.rainviewer.com/public/weather-maps.json"
        const val ZOOM = 6
        const val COLOR_SCHEME = 2 // Universal Blue (único esquema disponível na API pública)
        // Caixa SP + PR + RS (com folga)
        const val LAT_N = -19.0; const val LAT_S = -34.5; const val LON_W = -58.5; const val LON_E = -43.5
        /** Quadro anterior usado para medir o deslocamento (~20 min antes). */
        const val TRACK_FRAMES_BACK = 2
        const val MAX_CELLS = 12
        const val CACHE_MS = 5 * 60_000L
    }

    @Volatile private var cache: Pair<Long, StormCellsResult>? = null

    fun fetch(places: List<NamedPlace>, now: Long = System.currentTimeMillis()): StormCellsResult {
        cache?.let { (at, r) -> if (now - at < CACHE_MS) return r }
        val meta = JSONObject(getString(META_URL))
        val host = meta.getString("host")
        val past = meta.getJSONObject("radar").getJSONArray("past")
        require(past.length() > 0) { "RainViewer sem quadros de radar" }
        val last = past.getJSONObject(past.length() - 1)
        val prevIdx = past.length() - 1 - TRACK_FRAMES_BACK
        val prev = if (prevIdx >= 0) past.getJSONObject(prevIdx) else null

        // Os tiles cobrem mais que SP/PR/RS: só ficam as células dentro da caixa da região
        fun inBox(c: StormCells.Detected) = c.lat in LAT_S..LAT_N && c.lon in LON_W..LON_E
        val nowCells = StormCells.detect(grid(host, last.getString("path"))).filter(::inBox)
        val beforeCells = prev?.let { StormCells.detect(grid(host, it.getString("path"))) } ?: emptyList()
        val t = last.getLong("time"); val tPrev = prev?.getLong("time")
        val motions = StormCells.track(nowCells, beforeCells, if (tPrev != null) (t - tPrev) / 60.0 else 0.0)

        val cells = nowCells.take(MAX_CELLS).mapIndexed { i, c -> toTrajectory(c, motions[i], places, t) }
        val result = StormCellsResult(t, tPrev, cells)
        cache = now to result
        return result
    }

    private fun grid(host: String, path: String): StormCells.DbzGrid {
        val x0 = StormCells.tileX(LON_W, ZOOM); val x1 = StormCells.tileX(LON_E, ZOOM)
        val y0 = StormCells.tileY(LAT_N, ZOOM); val y1 = StormCells.tileY(LAT_S, ZOOM)
        val ts = StormCells.TILE_SIZE
        val w = (x1 - x0 + 1) * ts; val h = (y1 - y0 + 1) * ts
        val dbz = IntArray(w * h) { StormCells.NO_ECHO }
        val px = IntArray(ts * ts)
        for (ty in y0..y1) for (tx in x0..x1) {
            val bytes = getBytes("$host$path/$ts/$ZOOM/$tx/$ty/$COLOR_SCHEME/0_0.png")
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Tile de radar ilegível")
            bmp.getPixels(px, 0, ts, 0, 0, ts, ts); bmp.recycle()
            val ox = (tx - x0) * ts; val oy = (ty - y0) * ts
            for (yy in 0 until ts) for (xx in 0 until ts) dbz[(oy + yy) * w + ox + xx] = StormCells.dbzOf(px[yy * ts + xx])
        }
        return StormCells.DbzGrid(w, h, ZOOM, x0 * ts, y0 * ts, dbz)
    }

    private fun toTrajectory(c: StormCells.Detected, m: StormCells.Motion?, places: List<NamedPlace>, t: Long): StormCellTrajectory {
        val near = places.minByOrNull { StormCells.distanceKm(c.lat, c.lon, it.lat, it.lon) }
        val nearKm = near?.let { StormCells.distanceKm(c.lat, c.lon, it.lat, it.lon) }
        val name = if (near != null && nearKm != null) "Célula a ${nearKm.roundToInt()} km de ${near.name}" else "Célula %.2f, %.2f".format(c.lat, c.lon)
        fun eta(min: Int): String {
            if (m == null || m.speedKmH < 3.0) return "—"
            val (la, lo) = StormCells.destination(c.lat, c.lon, m.bearingDeg, m.speedKmH * min / 60.0)
            val p = places.minByOrNull { StormCells.distanceKm(la, lo, it.lat, it.lon) } ?: return "—"
            return if (StormCells.distanceKm(la, lo, p.lat, p.lon) <= 25.0) "${p.name} (+${min}m)" else "—"
        }
        val etas = listOf(eta(15), eta(30), eta(45))
        return StormCellTrajectory(
            id = "rv_${t}_${"%.2f".format(c.lat)}_${"%.2f".format(c.lon)}",
            name = name,
            originLat = c.lat, originLon = c.lon,
            dbzPeak = c.peakDbz,
            rainRateMmH = (StormCells.rainRateMmH(c.peakDbz) * 10).roundToInt() / 10.0,
            speedKmH = m?.speedKmH?.let { (it * 10).roundToInt() / 10.0 } ?: 0.0,
            // Convenção do canvas: 0° = Leste, 90° = Sul (y para baixo) → converte do rumo geográfico
            directionAngleDeg = m?.let { (it.bearingDeg - 90.0 + 360.0) % 360.0 } ?: 0.0,
            headingCompass = m?.let { StormCells.compass(it.bearingDeg) } ?: "Sem deslocamento medido",
            impactTowns = etas.filter { it != "—" }.map { it.substringBefore(" (") }.distinct(),
            eta15m = etas[0], eta30m = etas[1], eta45m = etas[2]
        )
    }

    private fun getBytes(url: String): ByteArray =
        client.newCall(Request.Builder().url(url).header("User-Agent", "SIST-MET-Radar-Chuva").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code} em ${url.substringBefore("?")}")
            r.body?.bytes() ?: error("Resposta vazia")
        }

    private fun getString(url: String): String = String(getBytes(url), Charsets.UTF_8)
}
