package com.example

import com.example.data.remote.LocalRainService
import com.example.data.remote.RainPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.data.remote.BrtTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalRainParsingTest {
    private val place = RainPlace("Curitiba", "Paraná", -25.43, -49.27)

    @Test fun geocodingKeepsOnlyBrazil() {
        val json = """{"results":[
          {"name":"Ribeirão Preto","latitude":-21.1775,"longitude":-47.81028,"country_code":"BR","admin1":"São Paulo"},
          {"name":"Curitiba","latitude":-25.42778,"longitude":-49.27306,"country_code":"BR","admin1":"Paraná"},
          {"name":"X","latitude":1.0,"longitude":2.0,"country_code":"AR","admin1":"Y"}]}"""
        val r = LocalRainService.parseGeocoding(json)
        assertEquals(2, r.size)
        assertEquals("Ribeirão Preto, SP", r[0].label)
        assertEquals(-25.42778, r[1].lat, 1e-6)
        assertTrue(LocalRainService.parseGeocoding("""{"generationtime_ms":0.5}""").isEmpty())
    }

    @Test fun forecastParsesNullsAndSummary() {
        val json = """{"hourly":{"time":["2026-10-07T13:00","2026-10-07T14:00","2026-10-07T15:00","2026-10-07T16:00","2026-10-07T17:00"],
          "precipitation":[0.0,0.1,1.2,0.6,0.0],"precipitation_probability":[5,20,70,null,30]},
          "daily":{"time":["2026-10-07","2026-10-08"],"precipitation_sum":[1.9,null],"precipitation_probability_max":[70,40]}}"""
        val fc = LocalRainService.parseForecast(json, place, 0L)
        assertEquals(5, fc.hourly.size)
        assertNull(fc.hourly[3].prob)
        assertNull(fc.daily[1].mm)
        val now = BrtTime(2026, 10, 7, 13, 25)
        val next = LocalRainService.nextHours(fc, now)
        assertEquals("Chuva prevista a partir das 15h até as 17h.", LocalRainService.rainSummary(next, now))
        val later = BrtTime(2026, 10, 7, 15, 10)
        assertEquals("Chuva prevista agora, até as 17h.", LocalRainService.rainSummary(LocalRainService.nextHours(fc, later), later))
    }

    @Test fun dryAndTomorrowAndMeta() {
        val json = """{"hourly":{"time":["2026-10-07T23:00","2026-10-08T00:00","2026-10-08T01:00"],
          "precipitation":[0.0,0.0,0.4],"precipitation_probability":[0,10,50]},
          "daily":{"time":["2026-10-07"],"precipitation_sum":[0.4],"precipitation_probability_max":[50]}}"""
        val fc = LocalRainService.parseForecast(json, place, 0L)
        val now = BrtTime(2026, 10, 7, 23, 5)
        assertEquals("Chuva prevista a partir das 1h de amanhã.", LocalRainService.rainSummary(LocalRainService.nextHours(fc, now), now))
        assertEquals("Sem chuva prevista nas próximas 2 h.",
            LocalRainService.rainSummary(LocalRainService.nextHours(fc, now).take(2), now))
        val run = LocalRainService.parseMeta("GFS", """{"last_run_initialisation_time":1791309600,"last_run_availability_time":1791335030}""")
        assertEquals(1791309600L, run!!.initUtcEpoch)
        assertEquals(place, RainPlace.decode(place.encode()))
        assertEquals(BrtTime(2026, 11, 1), BrtTime(2026, 10, 31).plusDays(1))
        assertEquals(4, BrtTime(2026, 10, 7).dayOfWeek) // quarta-feira
        assertEquals(BrtTime(2026, 10, 6, 15, 0), BrtTime.fromEpochMs(1791309600_000L)) // 18Z = 15h BRT
    }
}
