package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OpenWeatherResponse(
    @Json(name = "name") val name: String?,
    @Json(name = "main") val main: OpenWeatherMain?,
    @Json(name = "weather") val weather: List<OpenWeatherCondition>?,
    @Json(name = "wind") val wind: OpenWeatherWind?,
    @Json(name = "rain") val rain: OpenWeatherRain?,
    @Json(name = "coord") val coord: OpenWeatherCoord?
)

@JsonClass(generateAdapter = true)
data class OpenWeatherMain(
    @Json(name = "temp") val temp: Double?,
    @Json(name = "feels_like") val feelsLike: Double?,
    @Json(name = "temp_min") val tempMin: Double?,
    @Json(name = "temp_max") val tempMax: Double?,
    @Json(name = "humidity") val humidity: Int?,
    @Json(name = "pressure") val pressure: Int?
)

@JsonClass(generateAdapter = true)
data class OpenWeatherCondition(
    @Json(name = "id") val id: Int?,
    @Json(name = "main") val main: String?,
    @Json(name = "description") val description: String?,
    @Json(name = "icon") val icon: String?
)

@JsonClass(generateAdapter = true)
data class OpenWeatherWind(
    @Json(name = "speed") val speed: Double?,
    @Json(name = "deg") val deg: Int?
)

@JsonClass(generateAdapter = true)
data class OpenWeatherRain(
    @Json(name = "1h") val oneHour: Double?
)

@JsonClass(generateAdapter = true)
data class OpenWeatherCoord(
    @Json(name = "lat") val lat: Double?,
    @Json(name = "lon") val lon: Double?
)

// Weatherbit API Models
@JsonClass(generateAdapter = true)
data class WeatherbitResponse(
    @Json(name = "data") val data: List<WeatherbitDataItem>?
)

@JsonClass(generateAdapter = true)
data class WeatherbitDataItem(
    @Json(name = "city_name") val cityName: String?,
    @Json(name = "temp") val temp: Double?,
    @Json(name = "app_temp") val appTemp: Double?,
    @Json(name = "rh") val rh: Int?,
    @Json(name = "pres") val pres: Double?,
    @Json(name = "wind_spd") val windSpd: Double?,
    @Json(name = "wind_cdir") val windCdir: String?,
    @Json(name = "precip") val precip: Double?,
    @Json(name = "uv") val uv: Double?,
    @Json(name = "aqi") val aqi: Int?,
    @Json(name = "weather") val weather: WeatherbitCondition?
)

@JsonClass(generateAdapter = true)
data class WeatherbitCondition(
    @Json(name = "icon") val icon: String?,
    @Json(name = "code") val code: Int?,
    @Json(name = "description") val description: String?
)
