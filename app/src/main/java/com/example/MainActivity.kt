package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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

        setContent {
            val userPrefs by viewModel.userPreferences.collectAsState()

            IpmetWeatherTheme(
                themeKey = userPrefs.selectedThemeKey,
                isEnergySaver = userPrefs.isEnergySaverEnabled
            ) {
                MainAppScaffold(viewModel = viewModel)
            }
        }
    }
}
