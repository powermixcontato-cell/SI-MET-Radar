package com.example.data.local

import android.content.Context
import com.example.domain.BrState

/** Preferências simples da v5.1 (estado selecionado e notificações de alertas). */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("simet_v51", Context.MODE_PRIVATE)

    var selectedState: BrState
        get() = BrState.fromUf(prefs.getString(KEY_STATE, BrState.SP.uf))
        set(v) { prefs.edit().putString(KEY_STATE, v.uf).apply() }

    var alertNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY, false)
        set(v) { prefs.edit().putBoolean(KEY_NOTIFY, v).apply() }

    /** IDs de avisos já notificados (evita notificação repetida). */
    var notifiedAlertIds: Set<String>
        get() = prefs.getStringSet(KEY_NOTIFIED, emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet(KEY_NOTIFIED, v.toSet()).apply() }

    /** Último lugar escolhido no card "Chuva no seu local" (codificado por RainPlace.encode). */
    var localRainPlace: String?
        get() = prefs.getString(KEY_LOCAL_RAIN, null)
        set(v) { prefs.edit().putString(KEY_LOCAL_RAIN, v).apply() }

    /** Favoritos do card "Chuva no seu local" (até 8, ordem preservada; um por linha). */
    var localRainFavorites: List<String>
        get() = prefs.getString(KEY_LOCAL_RAIN_FAV, null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        set(v) { prefs.edit().putString(KEY_LOCAL_RAIN_FAV, v.take(8).joinToString("\n")).apply() }

    /** Cidade ativa na aba Previsão salva como RainPlace codificado. */
    var activeCityPlace: String?
        get() = prefs.getString(KEY_ACTIVE_CITY_PLACE, null)
        set(v) { prefs.edit().putString(KEY_ACTIVE_CITY_PLACE, v).apply() }

    /** Cidades favoritas da aba Previsão (codificadas por RainPlace.encode). */
    var favoriteCities: List<String>
        get() = prefs.getString(KEY_FAV_CITIES, null)?.split("\n")?.filter { it.isNotBlank() } ?: DEFAULT_FAVORITES
        set(v) { prefs.edit().putString(KEY_FAV_CITIES, v.take(20).joinToString("\n")).apply() }

    /** Cidades recentes selecionadas (codificadas por RainPlace.encode). */
    var recentCities: List<String>
        get() = prefs.getString(KEY_RECENT_CITIES, null)?.split("\n")?.filter { it.isNotBlank() } ?: DEFAULT_RECENTS
        set(v) { prefs.edit().putString(KEY_RECENT_CITIES, v.take(15).joinToString("\n")).apply() }

    fun addRecentCity(place: com.example.data.remote.RainPlace) {
        val encoded = place.encode()
        val current = recentCities.filterNot {
            val dec = com.example.data.remote.RainPlace.decode(it) ?: return@filterNot false
            dec.name.equals(place.name, ignoreCase = true) &&
                (dec.admin1 == null || place.admin1 == null || dec.admin1.equals(place.admin1, ignoreCase = true))
        }
        recentCities = listOf(encoded) + current
    }

    fun toggleFavoriteCity(place: com.example.data.remote.RainPlace): Boolean {
        val encoded = place.encode()
        val current = favoriteCities.toMutableList()
        val existingIndex = current.indexOfFirst {
            val dec = com.example.data.remote.RainPlace.decode(it) ?: return@indexOfFirst false
            dec.name.equals(place.name, ignoreCase = true) &&
                (dec.admin1 == null || place.admin1 == null || dec.admin1.equals(place.admin1, ignoreCase = true))
        }
        val isNowFavorite = if (existingIndex >= 0) {
            current.removeAt(existingIndex)
            false
        } else {
            current.add(0, encoded)
            true
        }
        favoriteCities = current
        return isNowFavorite
    }

    fun isFavoriteCity(place: com.example.data.remote.RainPlace): Boolean {
        return favoriteCities.any {
            val dec = com.example.data.remote.RainPlace.decode(it) ?: return@any false
            dec.name.equals(place.name, ignoreCase = true) &&
                (dec.admin1 == null || place.admin1 == null || dec.admin1.equals(place.admin1, ignoreCase = true))
        }
    }

    companion object {
        private const val KEY_LOCAL_RAIN = "local_rain_place"
        private const val KEY_LOCAL_RAIN_FAV = "local_rain_favorites"
        private const val KEY_STATE = "selected_state"
        private const val KEY_NOTIFY = "alert_notifications"
        private const val KEY_NOTIFIED = "notified_alert_ids"
        private const val KEY_ACTIVE_CITY_PLACE = "active_city_place"
        private const val KEY_FAV_CITIES = "forecast_favorite_cities"
        private const val KEY_RECENT_CITIES = "forecast_recent_cities"

        val DEFAULT_FAVORITES = listOf(
            "São Paulo|São Paulo|-23.5489|-46.6388",
            "Curitiba|Paraná|-25.4284|-49.2733",
            "Porto Alegre|Rio Grande do Sul|-30.0346|-51.2177",
            "Campinas|São Paulo|-22.9056|-47.0608",
            "Bauru|São Paulo|-22.3145|-49.0587",
            "Ribeirão Preto|São Paulo|-21.1767|-47.8208"
        )

        val DEFAULT_RECENTS = listOf(
            "São Paulo|São Paulo|-23.5489|-46.6388",
            "Campinas|São Paulo|-22.9056|-47.0608",
            "Curitiba|Paraná|-25.4284|-49.2733"
        )
    }
}
