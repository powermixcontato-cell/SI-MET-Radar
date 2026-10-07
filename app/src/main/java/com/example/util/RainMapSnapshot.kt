package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.example.domain.BrState
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Imagem estática do mapa de chuva para o PDF: mapa base escuro Esri + último quadro de radar do RainViewer,
 * recortada nos limites do estado (zoom 6). Rede síncrona: chamar fora da thread principal.
 */
object RainMapSnapshot {
    data class Result(val bitmap: Bitmap, val frameTimeSec: Long)

    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private const val Z = 6

    private fun get(url: String): ByteArray? = try {
        client.newCall(Request.Builder().url(url).header("User-Agent", "SI-MET-Radar/5.1 (Android)").build()).execute().use { r ->
            if (r.isSuccessful) r.body?.bytes() else null
        }
    } catch (_: Exception) { null }

    private fun px(lon: Double): Double = (lon + 180.0) / 360.0 * 256 * (1 shl Z)
    private fun py(lat: Double): Double {
        val r = Math.toRadians(lat)
        return (1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2 * 256 * (1 shl Z)
    }

    fun fetch(state: BrState): Result? {
        val meta = get("https://api.rainviewer.com/public/weather-maps.json")?.toString(Charsets.UTF_8) ?: return null
        val json = JSONObject(meta)
        val host = json.optString("host")
        val past = json.optJSONObject("radar")?.optJSONArray("past") ?: return null
        if (past.length() == 0) return null
        val last = past.getJSONObject(past.length() - 1)
        val path = last.optString("path"); val time = last.optLong("time")

        val x0 = px(state.minLon); val x1 = px(state.maxLon)
        val y0 = py(state.maxLat); val y1 = py(state.minLat)
        val tx0 = floor(x0 / 256).toInt(); val tx1 = floor(x1 / 256).toInt()
        val ty0 = floor(y0 / 256).toInt(); val ty1 = floor(y1 / 256).toInt()
        val full = Bitmap.createBitmap((tx1 - tx0 + 1) * 256, (ty1 - ty0 + 1) * 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(full)
        c.drawColor(Color.rgb(30, 30, 30))
        var radarTiles = 0
        for (tx in tx0..tx1) for (ty in ty0..ty1) {
            val dx = (tx - tx0) * 256f; val dy = (ty - ty0) * 256f
            get("https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/$Z/$ty/$tx")
                ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.let { c.drawBitmap(it, dx, dy, null) }
            get("$host$path/256/$Z/$tx/$ty/2/1_0.png")
                ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.let { c.drawBitmap(it, dx, dy, null); radarTiles++ }
            get("https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/$Z/$ty/$tx")
                ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.let { c.drawBitmap(it, dx, dy, null) }
        }
        if (radarTiles == 0) return null
        val crop = Rect((x0 - tx0 * 256).toInt(), (y0 - ty0 * 256).toInt(), (x1 - tx0 * 256).toInt(), (y1 - ty0 * 256).toInt())
        val out = Bitmap.createBitmap(crop.width(), crop.height(), Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        oc.drawBitmap(full, crop, Rect(0, 0, crop.width(), crop.height()), Paint(Paint.FILTER_BITMAP_FLAG))
        val label = Paint().apply { isAntiAlias = true; color = Color.WHITE; textSize = 13f; typeface = Typeface.DEFAULT_BOLD }
        oc.drawRect(0f, out.height - 20f, out.width.toFloat(), out.height.toFloat(), Paint().apply { color = Color.argb(160, 0, 0, 0) })
        oc.drawText("Radar ${PdfReportBuilder.brt(time * 1000, "dd/MM HH:mm")} • RainViewer • Esri", 6f, out.height - 6f, label)
        full.recycle()
        return Result(out, time)
    }
}
