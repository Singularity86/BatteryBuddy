package com.batterybuddy.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.batterybuddy.data.db.BatteryDatabase
import com.batterybuddy.data.db.dao.AppUsageDao
import com.batterybuddy.data.db.dao.BatteryProfileDao
import com.batterybuddy.data.db.dao.BatteryReadingDao
import com.batterybuddy.data.db.dao.ChargeSessionDao
import com.batterybuddy.data.db.dao.DischargeEventDao
import com.batterybuddy.data.db.dao.OvernightHoldDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // Adds multi-battery support: a battery_profiles table plus a battery_id
    // column on charge_sessions / discharge_events. Existing rows are attributed
    // to a default "Battery 1" so prior history is preserved.
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `battery_profiles` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`label` TEXT NOT NULL, " +
                    "`rated_mah` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `battery_profiles` (`id`, `label`, `rated_mah`, `created_at`) " +
                    "VALUES (1, 'Battery 1', 4500, ${System.currentTimeMillis()})"
            )
            db.execSQL("ALTER TABLE `charge_sessions` ADD COLUMN `battery_id` INTEGER")
            db.execSQL("ALTER TABLE `discharge_events` ADD COLUMN `battery_id` INTEGER")
            db.execSQL("UPDATE `charge_sessions` SET `battery_id` = 1")
            db.execSQL("UPDATE `discharge_events` SET `battery_id` = 1")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_charge_sessions_battery_id` ON `charge_sessions` (`battery_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_discharge_events_battery_id` ON `discharge_events` (`battery_id`)")
        }
    }

    // Corrects energy values written before the µAh×mV → mWh conversion was fixed.
    // The old code divided by 1_000 where the units require 1_000_000, so every
    // stored figure was 1000× too large. No schema change — data repair only.
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE `charge_sessions` SET `energy_added_mwh` = `energy_added_mwh` / 1000 " +
                    "WHERE `energy_added_mwh` IS NOT NULL"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BatteryDatabase =
        Room.databaseBuilder(context, BatteryDatabase::class.java, "battery_truth.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides fun provideBatteryReadingDao(db: BatteryDatabase): BatteryReadingDao = db.batteryReadingDao()
    @Provides fun provideChargeSessionDao(db: BatteryDatabase): ChargeSessionDao   = db.chargeSessionDao()
    @Provides fun provideDischargeEventDao(db: BatteryDatabase): DischargeEventDao = db.dischargeEventDao()
    @Provides fun provideOvernightHoldDao(db: BatteryDatabase): OvernightHoldDao   = db.overnightHoldDao()
    @Provides fun provideAppUsageDao(db: BatteryDatabase): AppUsageDao             = db.appUsageDao()
    @Provides fun provideBatteryProfileDao(db: BatteryDatabase): BatteryProfileDao = db.batteryProfileDao()
}
