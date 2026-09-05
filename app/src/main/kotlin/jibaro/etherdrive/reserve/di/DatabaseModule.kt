package jibaro.etherdrive.reserve.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import jibaro.etherdrive.reserve.data.db.BatteryDatabase
import jibaro.etherdrive.reserve.data.db.dao.AppUsageDao
import jibaro.etherdrive.reserve.data.db.dao.BatteryProfileDao
import jibaro.etherdrive.reserve.data.db.dao.BatteryReadingDao
import jibaro.etherdrive.reserve.data.db.dao.ChargeSessionDao
import jibaro.etherdrive.reserve.data.db.dao.DischargeEventDao
import jibaro.etherdrive.reserve.data.db.dao.OvernightHoldDao
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
                    "VALUES (1, '$DEFAULT_BATTERY_LABEL', $DEFAULT_RATED_MAH, ${System.currentTimeMillis()})"
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

    /**
     * Seeds the default battery on a fresh install.
     *
     * MIGRATION_1_2 creates this row for devices upgrading from v1, but migrations
     * never run on a new install — Room builds the current schema directly. Without
     * this the profile table starts empty, and since rated capacity lives on the
     * profile, health estimates, the replacement outlook and the battery selector
     * all silently have nothing to work from.
     */
    private val SEED_DEFAULT_BATTERY = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO `battery_profiles` (`id`, `label`, `rated_mah`, `created_at`) " +
                    "VALUES (1, '$DEFAULT_BATTERY_LABEL', $DEFAULT_RATED_MAH, ${System.currentTimeMillis()})"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BatteryDatabase =
        Room.databaseBuilder(context, BatteryDatabase::class.java, "battery_truth.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .addCallback(SEED_DEFAULT_BATTERY)
            .build()

    @Provides fun provideBatteryReadingDao(db: BatteryDatabase): BatteryReadingDao = db.batteryReadingDao()
    @Provides fun provideChargeSessionDao(db: BatteryDatabase): ChargeSessionDao   = db.chargeSessionDao()
    @Provides fun provideDischargeEventDao(db: BatteryDatabase): DischargeEventDao = db.dischargeEventDao()
    @Provides fun provideOvernightHoldDao(db: BatteryDatabase): OvernightHoldDao   = db.overnightHoldDao()
    @Provides fun provideAppUsageDao(db: BatteryDatabase): AppUsageDao             = db.appUsageDao()
    @Provides fun provideBatteryProfileDao(db: BatteryDatabase): BatteryProfileDao = db.batteryProfileDao()

    // Shared by the seed callback and the v1 migration so a device that upgraded
    // and a device installed fresh end up with an identical default battery.
    private const val DEFAULT_BATTERY_LABEL = "Battery 1"
    private const val DEFAULT_RATED_MAH = 4500
}
