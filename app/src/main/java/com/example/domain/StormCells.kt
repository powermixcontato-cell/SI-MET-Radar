package com.example.domain

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Células de tempestade REAIS detectadas no mosaico de radar da RainViewer (sem chave).
 * Nada é inventado: sem dado de radar → erro; sem eco forte → lista vazia ("Nenhuma célula detectada agora").
 *
 * Método:
 *  1. Pixels do tile (esquema de cor 2 "Universal Blue") → dBZ pela tabela oficial da RainViewer
 *     (https://www.rainviewer.com/files/rainviewer_api_colors_table.csv, coluna Universal Blue, linhas de chuva).
 *  2. Componentes conexos (8 vizinhos) com dBZ >= [DEFAULT_THRESHOLD_DBZ] e >= [DEFAULT_MIN_PIXELS] pixels = célula.
 *  3. Deslocamento: casa cada célula com a mais próxima do quadro anterior (até [MAX_MATCH_KM]) → velocidade e rumo.
 *  4. Taxa de chuva estimada pelo pico de dBZ (Marshall-Palmer Z = 200 R^1.6).
 */
object StormCells {
    const val DEFAULT_THRESHOLD_DBZ = 40
    const val DEFAULT_MIN_PIXELS = 4
    const val MAX_MATCH_KM = 40.0
    const val TILE_SIZE = 256
    const val NO_ECHO = Int.MIN_VALUE

    /** ARGB (Android) → dBZ, esquema Universal Blue (chuva). */
    private val COLOR_TO_DBZ: Map<Int, Int> = mapOf(
        0x14636159.toInt() to -10,
        0x1966635A.toInt() to -9,
        0x1E69665C.toInt() to -8,
        0x246C685D.toInt() to -7,
        0x296F6B5F.toInt() to -6,
        0x2E726E61.toInt() to -5,
        0x34757062.toInt() to -4,
        0x39787364.toInt() to -3,
        0x3E7C7565.toInt() to -2,
        0x447F7867.toInt() to -1,
        0x49827B69.toInt() to 0,
        0x4E857D6A.toInt() to 1,
        0x5488806C.toInt() to 2,
        0x598B826D.toInt() to 3,
        0x5E8E856F.toInt() to 4,
        0x64928871.toInt() to 5,
        0x6E9E9375.toInt() to 6,
        0x78AA9E79.toInt() to 7,
        0x82B6A97E.toInt() to 8,
        0x8CC2B482.toInt() to 9,
        0x96CEC087.toInt() to 10,
        0xA0D2C48B.toInt() to 11,
        0xAAD6C88F.toInt() to 12,
        0xB4DACC93.toInt() to 13,
        0xBEDED097.toInt() to 14,
        0xFF88DDEE.toInt() to 15,
        0xFF6CD1EB.toInt() to 16,
        0xFF51C5E8.toInt() to 17,
        0xFF36BAE5.toInt() to 18,
        0xFF1BAEE2.toInt() to 19,
        0xFF00A3E0.toInt() to 20,
        0xFF009AD5.toInt() to 21,
        0xFF0091CA.toInt() to 22,
        0xFF0088BF.toInt() to 23,
        0xFF007FB4.toInt() to 24,
        0xFF0077AA.toInt() to 25,
        0xFF0070A3.toInt() to 26,
        0xFF00699C.toInt() to 27,
        0xFF006295.toInt() to 28,
        0xFF005B8E.toInt() to 29,
        0xFF005588.toInt() to 30,
        0xFF005180.toInt() to 31,
        0xFF004E78.toInt() to 32,
        0xFF004A70.toInt() to 33,
        0xFF004768.toInt() to 34,
        0xFFFFEE00.toInt() to 35,
        0xFFFFE000.toInt() to 36,
        0xFFFFD200.toInt() to 37,
        0xFFFFC500.toInt() to 38,
        0xFFFFB700.toInt() to 39,
        0xFFFFAA00.toInt() to 40,
        0xFFFF9F00.toInt() to 41,
        0xFFFF9500.toInt() to 42,
        0xFFFF8B00.toInt() to 43,
        0xFFFF8100.toInt() to 44,
        0xFFFF4400.toInt() to 45,
        0xFFF23600.toInt() to 46,
        0xFFE62800.toInt() to 47,
        0xFFD91B00.toInt() to 48,
        0xFFCD0D00.toInt() to 49,
        0xFFC10000.toInt() to 50,
        0xFFA80000.toInt() to 51,
        0xFF8F0000.toInt() to 52,
        0xFF760000.toInt() to 53,
        0xFF5D0000.toInt() to 54,
        0xFFFFAAFF.toInt() to 55,
        0xFFFF9FFF.toInt() to 56,
        0xFFFF95FF.toInt() to 57,
        0xFFFF8BFF.toInt() to 58,
        0xFFFF81FF.toInt() to 59,
        0xFFFF77FF.toInt() to 60,
        0xFFFF6CFF.toInt() to 61,
        0xFFFF62FF.toInt() to 62,
        0xFFFF58FF.toInt() to 63,
        0xFFFF4EFF.toInt() to 64,
        0xFFFFFFFF.toInt() to 74
    )

    fun dbzOf(argb: Int): Int {
        if ((argb ushr 24) == 0) return NO_ECHO
        return COLOR_TO_DBZ[argb] ?: NO_ECHO
    }

    // ---- Projeção Web Mercator (tiles XYZ) ----
    fun tileX(lon: Double, z: Int): Int = ((lon + 180.0) / 360.0 * (1 shl z)).toInt()
    fun tileY(lat: Double, z: Int): Int =
        ((1.0 - asinh(kotlin.math.tan(Math.toRadians(lat))) / PI) / 2.0 * (1 shl z)).toInt()
    fun pixelToLon(globalPx: Double, z: Int): Double = globalPx / (TILE_SIZE * (1 shl z)) * 360.0 - 180.0
    fun pixelToLat(globalPy: Double, z: Int): Double {
        val n = PI - 2.0 * PI * globalPy / (TILE_SIZE * (1 shl z))
        return Math.toDegrees(atan(sinh(n)))
    }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * kotlin.math.sin(dLon / 2).pow(2)
        return 2 * r * kotlin.math.asin(sqrt(a))
    }

    /** Grade de dBZ de um mosaico: [originPx]/[originPy] = pixel global (zoom [z]) do canto superior esquerdo. */
    class DbzGrid(val width: Int, val height: Int, val z: Int, val originPx: Int, val originPy: Int, val dbz: IntArray) {
        fun lat(py: Double) = pixelToLat(originPy + py, z)
        fun lon(px: Double) = pixelToLon(originPx + px, z)
        /** km² aproximados de um pixel na latitude [lat]. */
        fun pixelAreaKm2(lat: Double): Double {
            val side = 40075.016 * cos(Math.toRadians(lat)) / (TILE_SIZE * (1 shl z))
            return side * side
        }
    }

    data class Detected(
        val lat: Double, val lon: Double, val peakDbz: Int, val pixels: Int, val areaKm2: Double
    )

    fun detect(grid: DbzGrid, thresholdDbz: Int = DEFAULT_THRESHOLD_DBZ, minPixels: Int = DEFAULT_MIN_PIXELS): List<Detected> {
        val w = grid.width; val h = grid.height
        val seen = BooleanArray(w * h)
        val out = ArrayList<Detected>()
        val stack = IntArray(w * h)
        for (start in 0 until w * h) {
            if (seen[start] || grid.dbz[start] == NO_ECHO || grid.dbz[start] < thresholdDbz) continue
            var sp = 0; stack[sp++] = start; seen[start] = true
            var n = 0; var sx = 0.0; var sy = 0.0; var sw = 0.0; var peak = Int.MIN_VALUE
            while (sp > 0) {
                val i = stack[--sp]
                val x = i % w; val y = i / w; val v = grid.dbz[i]
                n++; val wt = (v - thresholdDbz + 1).toDouble(); sx += x * wt; sy += y * wt; sw += wt
                if (v > peak) peak = v
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (!seen[j] && grid.dbz[j] != NO_ECHO && grid.dbz[j] >= thresholdDbz) { seen[j] = true; stack[sp++] = j }
                }
            }
            if (n >= minPixels) {
                val lat = grid.lat(sy / sw + 0.5); val lon = grid.lon(sx / sw + 0.5)
                out += Detected(lat, lon, peak, n, n * grid.pixelAreaKm2(lat))
            }
        }
        return out.sortedByDescending { it.peakDbz * 10_000 + it.pixels }
    }

    data class Motion(val speedKmH: Double, val bearingDeg: Double)

    /** Rumo (graus a partir do Norte, horário) de 1 → 2. */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2); val dl = Math.toRadians(lon2 - lon1)
        val y = kotlin.math.sin(dl) * cos(p2)
        val x = cos(p1) * kotlin.math.sin(p2) - kotlin.math.sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Casa cada célula atual com a mais próxima do quadro anterior. null = sem par (célula nova). */
    fun track(now: List<Detected>, before: List<Detected>, minutesBetween: Double): List<Motion?> =
        now.map { c ->
            if (minutesBetween <= 0) return@map null
            val prev = before.minByOrNull { distanceKm(c.lat, c.lon, it.lat, it.lon) } ?: return@map null
            val d = distanceKm(prev.lat, prev.lon, c.lat, c.lon)
            if (d > MAX_MATCH_KM) null else Motion(d / (minutesBetween / 60.0), bearingDeg(prev.lat, prev.lon, c.lat, c.lon))
        }

    /** Marshall-Palmer: R (mm/h) = (Z/200)^(1/1.6), Z = 10^(dBZ/10). */
    fun rainRateMmH(dbz: Int): Double = (10.0.pow(dbz / 10.0) / 200.0).pow(1.0 / 1.6)

    fun compass(bearing: Double): String {
        val names = listOf("Norte", "Nordeste", "Leste", "Sudeste", "Sul", "Sudoeste", "Oeste", "Noroeste")
        val abbr = listOf("N", "NE", "L", "SE", "S", "SO", "O", "NO")
        val i = ((bearing / 45.0).roundToInt()) % 8
        return "${names[i]} (${bearing.roundToInt()}° ${abbr[i]})"
    }

    /** Ponto após andar [km] no rumo [bearing]. */
    fun destination(lat: Double, lon: Double, bearing: Double, km: Double): Pair<Double, Double> {
        val r = 6371.0; val d = km / r; val b = Math.toRadians(bearing)
        val p1 = Math.toRadians(lat); val l1 = Math.toRadians(lon)
        val p2 = kotlin.math.asin(kotlin.math.sin(p1) * cos(d) + cos(p1) * kotlin.math.sin(d) * cos(b))
        val l2 = l1 + atan2(kotlin.math.sin(b) * kotlin.math.sin(d) * cos(p1), cos(d) - kotlin.math.sin(p1) * kotlin.math.sin(p2))
        return Math.toDegrees(p2) to Math.toDegrees(l2)
    }
}
