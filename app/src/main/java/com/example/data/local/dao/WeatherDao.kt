package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ClimateTrendEntity
import com.example.data.local.entity.DailyForecastEntity
import com.example.data.local.entity.HourlyForecastEntity
import com.example.data.local.entity.RegionSubscriptionEntity
import com.example.data.local.entity.UserPreferencesEntity
import com.example.data.local.entity.WeatherAlertEntity
import com.example.data.local.entity.WeatherStationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {

    // Weather Stations (SP municipalities)
    @Query("SELECT * FROM weather_stations ORDER BY name ASC")
    fun getAllStations(): Flow<List<WeatherStationEntity>>

    @Query("SELECT * FROM weather_stations ORDER BY name ASC")
    suspend fun getAllStationsSync(): List<WeatherStationEntity>

    @Query("SELECT * FROM weather_stations WHERE id = :stationId LIMIT 1")
    fun getStationById(stationId: String): Flow<WeatherStationEntity?>

    @Query("SELECT * FROM weather_stations WHERE id = :stationId LIMIT 1")
    suspend fun getStationByIdSync(stationId: String): WeatherStationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<WeatherStationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: WeatherStationEntity)

    // Hourly Forecasts
    @Query("SELECT * FROM hourly_forecasts WHERE stationId = :stationId ORDER BY id ASC")
    fun getHourlyForecasts(stationId: String): Flow<List<HourlyForecastEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHourlyForecasts(forecasts: List<HourlyForecastEntity>)

    @Query("DELETE FROM hourly_forecasts WHERE stationId = :stationId")
    suspend fun deleteHourlyByStation(stationId: String)

    // Daily Forecasts
    @Query("SELECT * FROM daily_forecasts WHERE stationId = :stationId ORDER BY id ASC")
    fun getDailyForecasts(stationId: String): Flow<List<DailyForecastEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyForecasts(forecasts: List<DailyForecastEntity>)

    @Query("DELETE FROM daily_forecasts WHERE stationId = :stationId")
    suspend fun deleteDailyByStation(stationId: String)

    // Weather Alerts
    @Query("SELECT * FROM weather_alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<WeatherAlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<WeatherAlertEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: WeatherAlertEntity)

    @Query("UPDATE weather_alerts SET isAcknowledged = 1 WHERE id = :alertId")
    suspend fun acknowledgeAlert(alertId: String)

    @Query("DELETE FROM weather_alerts WHERE id = :alertId")
    suspend fun deleteAlert(alertId: String)

    // Climate Trends & Historical Comparison
    @Query("SELECT * FROM climate_trends ORDER BY monthIndex ASC")
    fun getClimateTrends(): Flow<List<ClimateTrendEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClimateTrends(trends: List<ClimateTrendEntity>)

    // Region Subscriptions
    @Query("SELECT * FROM region_subscriptions ORDER BY regionName ASC")
    fun getAllSubscriptions(): Flow<List<RegionSubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscriptions(subs: List<RegionSubscriptionEntity>)

    @Update
    suspend fun updateSubscription(sub: RegionSubscriptionEntity)

    // User Preferences
    @Query("SELECT * FROM user_preferences WHERE id = 1 LIMIT 1")
    fun getUserPreferences(): Flow<UserPreferencesEntity?>

    @Query("SELECT * FROM user_preferences WHERE id = 1 LIMIT 1")
    suspend fun getUserPreferencesSync(): UserPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserPreferences(prefs: UserPreferencesEntity)

    // CIIAGRO Live Agrometeorological Data
    @Query("SELECT * FROM ciiagro_records WHERE stationId = :stationId LIMIT 1")
    fun getCiiagroRecord(stationId: String): Flow<com.example.data.local.entity.CiiagroRecordEntity?>

    @Query("SELECT * FROM ciiagro_records WHERE stationId = :stationId LIMIT 1")
    suspend fun getCiiagroRecordSync(stationId: String): com.example.data.local.entity.CiiagroRecordEntity?

    @Query("SELECT * FROM ciiagro_records ORDER BY municipality ASC")
    fun getAllCiiagroRecords(): Flow<List<com.example.data.local.entity.CiiagroRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCiiagroRecord(record: com.example.data.local.entity.CiiagroRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCiiagroRecords(records: List<com.example.data.local.entity.CiiagroRecordEntity>)
}
