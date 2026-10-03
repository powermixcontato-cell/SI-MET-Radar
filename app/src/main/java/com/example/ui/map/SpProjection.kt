package com.example.ui.map

import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.min

/**
 * Projeção equiretangular com escala ÚNICA nos dois eixos (correção cos(lat) em -22,5°),
 * ajustada ao canvas com margem, e zoom/pan em torno do centro do canvas.
 * Antes cada mapa esticava lon/lat de forma independente para largura/altura (o estado
 * ficava deformado e mudava de forma com a altura do card) e "grampeava" (coerceIn) as
 * coordenadas fora do retângulo, colando pontos na borda.
 */
class SpProjection(
    val width: Float,
    val height: Float,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    paddingPx: Float = 12f
) {
    private val cosLat = cos(Math.toRadians(-22.5))
    private val spanX = (SpGeo.MAX_LON - SpGeo.MIN_LON) * cosLat
    private val spanY = SpGeo.MAX_LAT - SpGeo.MIN_LAT
    /** px por grau de latitude com zoom 1. */
    val baseScale: Double = min(
        ((width - 2 * paddingPx).coerceAtLeast(1f)) / spanX,
        ((height - 2 * paddingPx).coerceAtLeast(1f)) / spanY
    )
    private val offX = (width - spanX * baseScale) / 2.0
    private val offY = (height - spanY * baseScale) / 2.0

    fun baseX(lon: Double): Float = (offX + (lon - SpGeo.MIN_LON) * cosLat * baseScale).toFloat()
    fun baseY(lat: Double): Float = (offY + (SpGeo.MAX_LAT - lat) * baseScale).toFloat()

    fun x(lon: Double): Float = width / 2f + (baseX(lon) - width / 2f) * zoom + panX
    fun y(lat: Double): Float = height / 2f + (baseY(lat) - height / 2f) * zoom + panY

    fun lon(screenX: Float): Double {
        val bx = (screenX - panX - width / 2f) / zoom + width / 2f
        return SpGeo.MIN_LON + (bx - offX) / (cosLat * baseScale)
    }

    fun lat(screenY: Float): Double {
        val by = (screenY - panY - height / 2f) / zoom + height / 2f
        return SpGeo.MAX_LAT - (by - offY) / baseScale
    }

    /** px de tela por km (com zoom). */
    val pxPerKm: Float get() = (baseScale * zoom / 111.32).toFloat()

    /** Pan necessário para centralizar (lat, lon) com o zoom informado. */
    fun panToCenter(lat: Double, lon: Double, targetZoom: Float): Pair<Float, Float> =
        Pair((width / 2f - baseX(lon)) * targetZoom, (height / 2f - baseY(lat)) * targetZoom)

    fun ringPath(ring: FloatArray): Path = Path().apply {
        val n = ring.size / 2
        for (i in 0 until n) {
            val px = x(ring[2 * i].toDouble()); val py = y(ring[2 * i + 1].toDouble())
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }

    /** Contorno de SP (continente + Ilhabela). */
    fun statePath(): Path = ringPath(SpGeo.OUTLINE).also { it.addPath(ringPath(SpGeo.ILHABELA)) }

    fun polyline(points: List<Pair<Double, Double>>): Path = Path().apply {
        points.forEachIndexed { i, (lat, lon) -> if (i == 0) moveTo(x(lon), y(lat)) else lineTo(x(lon), y(lat)) }
    }

    companion object {
        /**
         * Novo pan após pinça/arrasto mantendo fixo o ponto sob o centróide do gesto
         * (antes o zoom de pinça sempre ampliava em torno do centro do canvas).
         * O pan é limitado para o mapa não "sumir" da tela.
         */
        fun gesturePan(
            width: Float, height: Float, oldZoom: Float, newZoom: Float,
            panX: Float, panY: Float, centroidX: Float, centroidY: Float, dragX: Float, dragY: Float
        ): Pair<Float, Float> {
            val r = if (oldZoom > 0f) newZoom / oldZoom else 1f
            var px = centroidX - width / 2f - (centroidX - width / 2f - panX) * r + dragX
            var py = centroidY - height / 2f - (centroidY - height / 2f - panY) * r + dragY
            val limX = width * newZoom / 2f
            val limY = height * newZoom / 2f
            px = px.coerceIn(-limX, limX)
            py = py.coerceIn(-limY, limY)
            return Pair(px, py)
        }
    }
}
