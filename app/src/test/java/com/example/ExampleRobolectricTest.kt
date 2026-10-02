package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.WeatherStationEntity
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read string from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("SI Met RADAR", appName)
    }

    @Test
    fun `test repository seed and read stations`() = runBlocking {
        val repository = WeatherRepository(database.weatherDao())
        repository.initializePreloadedDataIfNeeded()

        val stations = repository.allStations.first()
        assertTrue(stations.isNotEmpty())

        val saoPaulo = stations.find { it.id == "sao_paulo" }
        assertNotNull(saoPaulo)
        assertEquals("São Paulo", saoPaulo?.name)
        assertEquals("Região Metropolitana (Capital)", saoPaulo?.region)

        val bauru = stations.find { it.id == "bauru" }
        assertNotNull(bauru)
        assertEquals("Bauru", bauru?.name)
    }

    @Test
    fun `test regional subscriptions initial state`() = runBlocking {
        val repository = WeatherRepository(database.weatherDao())
        repository.initializePreloadedDataIfNeeded()

        val subs = repository.regionSubscriptions.first()
        assertTrue(subs.size >= 8)
    }
}
