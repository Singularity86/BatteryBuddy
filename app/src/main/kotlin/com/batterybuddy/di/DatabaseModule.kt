package com.batterybuddy.di

import android.content.Context
import androidx.room.Room
import com.batterybuddy.data.db.BatteryDatabase
import com.batterybuddy.data.db.dao.AppUsageDao
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BatteryDatabase =
        Room.databaseBuilder(context, BatteryDatabase::class.java, "battery_truth.db")
            .build()

    @Provides fun provideBatteryReadingDao(db: BatteryDatabase): BatteryReadingDao = db.batteryReadingDao()
    @Provides fun provideChargeSessionDao(db: BatteryDatabase): ChargeSessionDao   = db.chargeSessionDao()
    @Provides fun provideDischargeEventDao(db: BatteryDatabase): DischargeEventDao = db.dischargeEventDao()
    @Provides fun provideOvernightHoldDao(db: BatteryDatabase): OvernightHoldDao   = db.overnightHoldDao()
    @Provides fun provideAppUsageDao(db: BatteryDatabase): AppUsageDao             = db.appUsageDao()
}
