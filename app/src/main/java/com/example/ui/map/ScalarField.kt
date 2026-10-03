package com.example.ui.map

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.cos
import kotlin.math.sqrt

/** Valor pontual (ex.: previsão Open-Meteo de uma cidade). */
data class FieldPoint(val lat: Double, val lon: Double, val value: Float)

/**
 * Campo contínuo em grade regular (lon/lat) sobre o retângulo de SP, interpolado por
 * IDW (Inverse Distance Weighting, potência 2) a partir dos pontos com dado REAL.
 * Células longe de qualquer ponto (> [fadeStartKm]) perdem opacidade até [maxKm],
 * para não "inventar" chuva onde não há informação.
 * O resultado é calculado uma vez (remember) e reaproveitado em todo frame de zoom/pan.
 */
class ScalarField(
    val cols: Int,
    val rows: Int,
    val values: FloatArray,
    val confidence: FloatArray,
    val pointCount: Int
) {
    val dLon = (SpGeo.MAX_LON - SpGeo.MIN_LON) / cols
    val dLat = (SpGeo.MAX_LAT - SpGeo.MIN_LAT) / rows

    fun cellLon(i: Int) = SpGeo.MIN_LON + (i + 0.5) * dLon
    fun cellLat(j: Int) = SpGeo.MAX_LAT - (j + 0.5) * dLat
    fun at(i: Int, j: Int) = values[j * cols + i]

    /** Imagem ARGB do campo (1 px por célula); desenhar com filtro bilinear e recorte pelo contorno. */
    fun toImageBitmap(ramp: MapRamp, alpha: Float): ImageBitmap {
        val px = IntArray(cols * rows)
        for (k in px.indices) px[k] = ramp.argb(values[k], alpha * confidence[k])
        val bmp = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, cols, 0, 0, cols, rows)
        return bmp.asImageBitmap()
    }

    /**
     * Isolinhas por "marching squares" para cada nível. Retorna, por nível, segmentos
     * (lon1, lat1, lon2, lat2) concatenados.
     */
    fun isolines(levels: List<Float>): Map<Float, FloatArray> = levels.associateWith { level ->
        val out = ArrayList<Float>()
        for (j in 0 until rows - 1) for (i in 0 until cols - 1) {
            val v0 = at(i, j); val v1 = at(i + 1, j); val v2 = at(i + 1, j + 1); val v3 = at(i, j + 1)
            if (v0.isNaN() || v1.isNaN() || v2.isNaN() || v3.isNaN()) continue
            val c0 = confidence[j * cols + i]
            if (c0 < 0.5f) continue
            var idx = 0
            if (v0 >= level) idx = idx or 1
            if (v1 >= level) idx = idx or 2
            if (v2 >= level) idx = idx or 4
            if (v3 >= level) idx = idx or 8
            if (idx == 0 || idx == 15) continue
            val x0 = cellLon(i); val x1 = cellLon(i + 1); val y0 = cellLat(j); val y1 = cellLat(j + 1)
            fun t(a: Float, b: Float) = if (b == a) 0.5 else ((level - a) / (b - a)).toDouble()
            // pontos nas arestas: topo(0-1), direita(1-2), base(3-2), esquerda(0-3)
            val top = doubleArrayOf(x0 + (x1 - x0) * t(v0, v1), y0)
            val right = doubleArrayOf(x1, y0 + (y1 - y0) * t(v1, v2))
            val bottom = doubleArrayOf(x0 + (x1 - x0) * t(v3, v2), y1)
            val left = doubleArrayOf(x0, y0 + (y1 - y0) * t(v0, v3))
            fun seg(a: DoubleArray, b: DoubleArray) { out.add(a[0].toFloat()); out.add(a[1].toFloat()); out.add(b[0].toFloat()); out.add(b[1].toFloat()) }
            when (idx) {
                1, 14 -> seg(left, top)
                2, 13 -> seg(top, right)
                3, 12 -> seg(left, right)
                4, 11 -> seg(right, bottom)
                5 -> { seg(left, top); seg(right, bottom) }
                6, 9 -> seg(top, bottom)
                7, 8 -> seg(left, bottom)
                10 -> { seg(top, right); seg(left, bottom) }
            }
        }
        out.toFloatArray()
    }

    companion object {
        fun idw(
            points: List<FieldPoint>,
            cols: Int = 120,
            rows: Int = 80,
            power: Double = 2.0,
            fadeStartKm: Double = 90.0,
            maxKm: Double = 220.0
        ): ScalarField? {
            val pts = points.filter { !it.value.isNaN() && !it.value.isInfinite() }
            if (pts.isEmpty()) return null
            val values = FloatArray(cols * rows)
            val conf = FloatArray(cols * rows)
            val cosLat = cos(Math.toRadians(-22.5))
            val dLon = (SpGeo.MAX_LON - SpGeo.MIN_LON) / cols
            val dLat = (SpGeo.MAX_LAT - SpGeo.MIN_LAT) / rows
            for (j in 0 until rows) {
                val lat = SpGeo.MAX_LAT - (j + 0.5) * dLat
                for (i in 0 until cols) {
                    val lon = SpGeo.MIN_LON + (i + 0.5) * dLon
                    var num = 0.0; var den = 0.0; var dMin = Double.MAX_VALUE; var exact = Float.NaN
                    for (p in pts) {
                        val dx = (p.lon - lon) * cosLat * 111.32
                        val dy = (p.lat - lat) * 111.32
                        val d = sqrt(dx * dx + dy * dy)
                        if (d < dMin) dMin = d
                        if (d < 0.5) { exact = p.value; break }
                        val wgt = 1.0 / Math.pow(d, power)
                        num += wgt * p.value; den += wgt
                    }
                    val k = j * cols + i
                    values[k] = if (!exact.isNaN()) exact else (num / den).toFloat()
                    conf[k] = when {
                        dMin <= fadeStartKm -> 1f
                        dMin >= maxKm -> 0f
                        else -> (1.0 - (dMin - fadeStartKm) / (maxKm - fadeStartKm)).toFloat()
                    }
                }
            }
            return ScalarField(cols, rows, values, conf, pts.size)
        }
    }
}
