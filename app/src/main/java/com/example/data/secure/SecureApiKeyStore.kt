package com.example.data.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Fonte das chaves opcionais (OpenWeatherMap / Weatherbit). */
interface ApiKeyProvider {
    suspend fun openWeatherKey(): String
    suspend fun weatherbitKey(): String
    suspend fun save(openWeatherKey: String, weatherbitKey: String)
}

/** Arquivo: files/datastore/secure_api_keys.preferences_pb (excluído do backup). */
private val Context.secureKeysDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_api_keys")

/**
 * Chaves de API cifradas com AES/GCM usando uma chave do Android Keystore e gravadas no DataStore.
 * (Não usa androidx.security:security-crypto / EncryptedSharedPreferences, que está descontinuado.)
 */
class SecureApiKeyStore(context: Context) : ApiKeyProvider {
    private val appContext = context.applicationContext
    private val store get() = appContext.secureKeysDataStore

    companion object {
        private const val TAG = "SecureApiKeyStore"
        private const val KEY_ALIAS = "simet_api_keys_aes"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val OPEN_WEATHER = stringPreferencesKey("openweather_key_enc")
        private val WEATHERBIT = stringPreferencesKey("weatherbit_key_enc")
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(enc: String?): String {
        if (enc.isNullOrEmpty()) return ""
        return try {
            val bytes = Base64.decode(enc, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, 12)
            val ct = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao decifrar chave salva", e)
            ""
        }
    }

    override suspend fun openWeatherKey(): String = decrypt(store.data.first()[OPEN_WEATHER])
    override suspend fun weatherbitKey(): String = decrypt(store.data.first()[WEATHERBIT])

    override suspend fun save(openWeatherKey: String, weatherbitKey: String) {
        val ow = encrypt(openWeatherKey.trim())
        val wb = encrypt(weatherbitKey.trim())
        store.edit { p ->
            if (ow.isEmpty()) p.remove(OPEN_WEATHER) else p[OPEN_WEATHER] = ow
            if (wb.isEmpty()) p.remove(WEATHERBIT) else p[WEATHERBIT] = wb
        }
    }
}
