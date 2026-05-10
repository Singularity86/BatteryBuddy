package com.batterybuddy.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.batterybuddy.data.db.dao.AppUsageDao
import com.batterybuddy.data.db.dao.BatteryReadingDao
import com.batterybuddy.data.db.dao.ChargeSessionDao
import com.batterybuddy.data.db.dao.DischargeEventDao
import com.batterybuddy.data.db.dao.OvernightHoldDao
import com.batterybuddy.data.db.entity.AppUsageEntity
import com.batterybuddy.data.db.entity.BatteryReadingEntity
import com.batterybuddy.data.db.entity.ChargeSessionEntity
import com.batterybuddy.data.db.entity.DischargeEventEntity
import com.batterybuddy.data.db.entity.OvernightHoldEntity

@Database(
    entities = [
        BatteryReadingEntity::class,
        ChargeSessionEntity::class,
        DischargeEventEntity::class,
        OvernightHoldEntity::class,
        AppUsageEntity::class
    ],
    version = 1,
    exportSchema = true   // schema JSON written to app/schemas/ for migration auditing
)
abstract class BatteryDatabase : RoomDatabase() {
    abstract fun batteryReadingDao(): BatteryReadingDao
    abstract fun chargeSessionDao(): ChargeSessionDao
    abstract fun dischargeEventDao(): DischargeEventDao
    abstract fun overnightHoldDao(): OvernightHoldDao
    abstract fun appUsageDao(): AppUsageDao
}
