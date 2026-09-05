package jibaro.etherdrive.reserve.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import jibaro.etherdrive.reserve.data.db.dao.AppUsageDao
import jibaro.etherdrive.reserve.data.db.dao.BatteryProfileDao
import jibaro.etherdrive.reserve.data.db.dao.BatteryReadingDao
import jibaro.etherdrive.reserve.data.db.dao.ChargeSessionDao
import jibaro.etherdrive.reserve.data.db.dao.DischargeEventDao
import jibaro.etherdrive.reserve.data.db.dao.OvernightHoldDao
import jibaro.etherdrive.reserve.data.db.entity.AppUsageEntity
import jibaro.etherdrive.reserve.data.db.entity.BatteryProfileEntity
import jibaro.etherdrive.reserve.data.db.entity.BatteryReadingEntity
import jibaro.etherdrive.reserve.data.db.entity.ChargeSessionEntity
import jibaro.etherdrive.reserve.data.db.entity.DischargeEventEntity
import jibaro.etherdrive.reserve.data.db.entity.OvernightHoldEntity

@Database(
    entities = [
        BatteryReadingEntity::class,
        ChargeSessionEntity::class,
        DischargeEventEntity::class,
        OvernightHoldEntity::class,
        AppUsageEntity::class,
        BatteryProfileEntity::class
    ],
    version = 3,
    exportSchema = true   // schema JSON written to app/schemas/ for migration auditing
)
abstract class BatteryDatabase : RoomDatabase() {
    abstract fun batteryReadingDao(): BatteryReadingDao
    abstract fun chargeSessionDao(): ChargeSessionDao
    abstract fun dischargeEventDao(): DischargeEventDao
    abstract fun overnightHoldDao(): OvernightHoldDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun batteryProfileDao(): BatteryProfileDao
}
