package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.WeatherDao
import com.example.data.local.entity.CiiagroRecordEntity
import com.example.data.local.entity.ClimateTrendEntity
import com.example.data.local.entity.DailyForecastEntity
import com.example.data.local.entity.HourlyForecastEntity
import com.example.data.local.entity.RegionSubscriptionEntity
import com.example.data.local.entity.UserPreferencesEntity
import com.example.data.local.entity.WeatherAlertEntity
import com.example.data.local.entity.WeatherStationEntity

@Database(
    entities = [
        WeatherStationEntity::class,
        HourlyForecastEntity::class,
        DailyForecastEntity::class,
        WeatherAlertEntity::class,
        ClimateTrendEntity::class,
        RegionSubscriptionEntity::class,
        UserPreferencesEntity::class,
        CiiagroRecordEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun weatherDao(): WeatherDao

    companion object {
        /**
         * v2 → v3: o esquema das tabelas não mudou (mesmo identity hash). A versão sobe para
         * registrar a troca de fontes (Open-Meteo/INMET); a limpeza de dados semeados/fictícios
         * é feita pelo repositório. NÃO apaga os dados do usuário (antes: fallbackToDestructiveMigration).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remove alertas fixos/sintéticos e registros "CIIAGRO" semeados de versões antigas
                db.execSQL("DELETE FROM weather_alerts WHERE id LIKE 'alert_sp_%' OR id LIKE 'alert_test_%'")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ipmet_radar_weather.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    // Só a versão 1 (protótipo) ainda é recriada do zero
                    .fallbackToDestructiveMigrationFrom(true, 1)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
