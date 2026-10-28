package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.UserPreferencesEntity
import com.example.data.remote.GeminiMapsWeatherService
import com.example.data.repository.WeatherRepository
import com.example.data.secure.ApiKeyProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Armazenamento "cifrado" falso, em memória. */
private class FakeKeyStore(var ow: String = "", var wb: String = "") : ApiKeyProvider {
    override suspend fun openWeatherKey() = ow
    override suspend fun weatherbitKey() = wb
    override suspend fun save(openWeatherKey: String, weatherbitKey: String) { ow = openWeatherKey; wb = weatherbitKey }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiKeySecurityTest {
    private lateinit var db: AppDatabase

    @Before fun setup() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun saveApiKeys_goesToEncryptedStore_neverToRoom_andKeepsOtherSettings() = runBlocking {
        val dao = db.weatherDao()
        dao.insertUserPreferences(UserPreferencesEntity(selectedThemeKey = "amber_warn", isOfflineModeForced = true))
        val ks = FakeKeyStore()
        val repo = WeatherRepository(dao = dao, keyStore = ks)

        assertTrue(repo.saveApiKeys("  ow-test  ", "wb-test"))

        assertEquals("ow-test", ks.ow)
        assertEquals("wb-test", ks.wb)
        val p = dao.getUserPreferencesSync()!!
        assertEquals("", p.openWeatherApiKey)
        assertEquals("", p.weatherbitApiKey)
        assertEquals("amber_warn", p.selectedThemeKey) // não sobrescreve ajustes
        assertTrue(p.isOfflineModeForced)
    }

    @Test fun saveApiKeys_withoutKeyStore_doesNotPersistPlaintext() = runBlocking {
        val dao = db.weatherDao()
        val repo = WeatherRepository(dao = dao, keyStore = null)
        assertFalse(repo.saveApiKeys("ow-test", "wb-test"))
        val p = dao.getUserPreferencesSync() ?: UserPreferencesEntity()
        assertEquals("", p.openWeatherApiKey)
        assertEquals("", p.weatherbitApiKey)
    }

    @Test fun migratePlaintextKeys_movesAndWipes_existingEncryptedWins() = runBlocking {
        val dao = db.weatherDao()
        dao.insertUserPreferences(UserPreferencesEntity(openWeatherApiKey = "old-ow", weatherbitApiKey = "old-wb", selectedThemeKey = "ipmet_cyan"))
        val ks = FakeKeyStore(ow = "new-ow")
        WeatherRepository(dao = dao, keyStore = ks).migratePlaintextKeys()

        assertEquals("new-ow", ks.ow) // chave cifrada mais nova não é trocada pela antiga
        assertEquals("old-wb", ks.wb)
        val p = dao.getUserPreferencesSync()!!
        assertEquals("", p.openWeatherApiKey)
        assertEquals("", p.weatherbitApiKey)
        assertEquals("ipmet_cyan", p.selectedThemeKey)
    }

    @Test fun readApiKeys_ignoresPlaintextFallback() = runBlocking {
        val dao = db.weatherDao()
        dao.insertUserPreferences(UserPreferencesEntity(openWeatherApiKey = "leaked"))
        val keys = WeatherRepository(dao = dao, keyStore = FakeKeyStore()).readApiKeys()
        assertEquals("" to "", keys)
    }

    @Test fun geminiKeyValidation() {
        assertFalse(GeminiMapsWeatherService.isUsableKey(null))
        assertFalse(GeminiMapsWeatherService.isUsableKey(" "))
        assertFalse(GeminiMapsWeatherService.isUsableKey("MY_GEMINI_API_KEY"))
        assertTrue(GeminiMapsWeatherService.isUsableKey("abc"))
    }

    @Test fun releaseBuild_hasNoEmbeddedSecrets() {
        if (!BuildConfig.DEBUG) {
            assertEquals("", BuildConfig.GEMINI_API_KEY)
            assertEquals("", BuildConfig.SIMET_API_KEY)
        }
    }
}
