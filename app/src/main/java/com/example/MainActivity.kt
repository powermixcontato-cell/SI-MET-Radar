package com.example

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.data.local.AppDatabase
import com.example.data.remote.NetworkClient
import com.example.data.repository.WeatherRepository
import com.example.ui.screens.MainAppScaffold
import com.example.ui.theme.IpmetWeatherTheme
import com.example.util.NotificationHelper
import com.example.viewmodel.WeatherViewModel
import com.example.viewmodel.WeatherViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: WeatherViewModel by viewModels {
        val db = AppDatabase.getInstance(applicationContext)
        val repository = WeatherRepository(
            dao = db.weatherDao(),
            keyStore = com.example.data.secure.SecureApiKeyStore(applicationContext)
        )
        WeatherViewModelFactory(repository, applicationContext)
    }

    override fun onStart() {
        super.onStart()
        viewModel.setAppInForeground(true)
    }

    override fun onStop() {
        viewModel.setAppInForeground(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        // Initialize system notification channels for weather and radar alerts
        NotificationHelper.initNotificationChannels(this)

        // Pré-carrega o HTML do mapa (Leaflet ~160 KB dos assets) fora da thread principal
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try { com.example.ui.components.SimetMapHtml.get(applicationContext) } catch (_: Exception) { }
        }

        setContent {
            val userPrefs by viewModel.userPreferences.collectAsStateWithLifecycle()

            IpmetWeatherTheme(
                themeKey = userPrefs.selectedThemeKey,
                isEnergySaver = userPrefs.isEnergySaverEnabled
            ) {
                MainAppScaffold(viewModel = viewModel)
            }
        }
    }
}
