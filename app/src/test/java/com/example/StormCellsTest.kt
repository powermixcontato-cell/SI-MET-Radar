package com.example

import com.example.domain.StormCells
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StormCellsTest {
    private fun grid(w: Int, h: Int, fill: (Int, Int) -> Int) =
        StormCells.DbzGrid(w, h, 6, 22 * 256, 36 * 256, IntArray(w * h) { fill(it % w, it / w) })

    @Test fun noEcho_meansNoCells_neverInvented() {
        val g = grid(64, 64) { _, _ -> StormCells.NO_ECHO }
        assertTrue(StormCells.detect(g).isEmpty())
    }

    @Test fun weakRain_belowThreshold_isNotACell() {
        val g = grid(64, 64) { _, _ -> 30 }
        assertTrue(StormCells.detect(g).isEmpty())
    }

    @Test fun twoSeparateCores_areTwoCells_withPeak() {
        val g = grid(64, 64) { x, y ->
            when {
                x in 5..8 && y in 5..8 -> if (x == 6 && y == 6) 55 else 45
                x in 40..42 && y in 40..42 -> 42
                else -> 20
            }
        }
        val cells = StormCells.detect(g)
        assertEquals(2, cells.size)
        assertEquals(55, cells[0].peakDbz) // mais forte primeiro
        assertEquals(16, cells[0].pixels)
        assertTrue(cells[0].lat < -21.9 && cells[0].lat > -27.1) // tile y=36 em z6
        assertTrue(cells[0].lon > -56.3 && cells[0].lon < -50.6) // tile x=22 em z6
    }

    @Test fun tinySpeck_isIgnored() {
        val g = grid(16, 16) { x, y -> if (x == 3 && y == 3) 60 else StormCells.NO_ECHO }
        assertTrue(StormCells.detect(g).isEmpty())
    }

    @Test fun colorTable_matchesRainViewerUniversalBlue() {
        assertEquals(35, StormCells.dbzOf(0xFFFFEE00.toInt()))  // #ffee00ff
        assertEquals(40, StormCells.dbzOf(0xFFFFAA00.toInt()))  // #ffaa00ff
        assertEquals(50, StormCells.dbzOf(0xFFC10000.toInt()))  // #c10000ff
        assertEquals(StormCells.NO_ECHO, StormCells.dbzOf(0x00000000))
    }

    @Test fun tracking_givesSpeedAndBearing_orNullWhenTooFar() {
        val before = listOf(StormCells.Detected(-22.0, -49.0, 50, 10, 60.0))
        val nowNear = listOf(StormCells.Detected(-22.0, -48.9, 50, 10, 60.0)) // ~10 km a leste
        val m = StormCells.track(nowNear, before, 20.0)[0]!!
        assertEquals(30.9, m.speedKmH, 1.5)
        assertEquals(90.0, m.bearingDeg, 2.0)
        val nowFar = listOf(StormCells.Detected(-25.0, -49.0, 50, 10, 60.0))
        assertNull(StormCells.track(nowFar, before, 20.0)[0])
    }

    @Test fun marshallPalmer_rainRate() {
        assertEquals(11.5, StormCells.rainRateMmH(40), 0.3)
        assertEquals(48.6, StormCells.rainRateMmH(50), 1.0)
    }
}
