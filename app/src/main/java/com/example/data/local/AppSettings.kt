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

    companion object {
        private const val KEY_LOCAL_RAIN = "local_rain_place"
        private const val KEY_LOCAL_RAIN_FAV = "local_rain_favorites"
        private const val KEY_STATE = "selected_state"
        private const val KEY_NOTIFY = "alert_notifications"
        private const val KEY_NOTIFIED = "notified_alert_ids"
    }
}
