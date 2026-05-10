package com.batterybuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.batterybuddy.data.db.entity.BatteryReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryReadingDao {

    @Insert
    suspend fun insert(reading: BatteryReadingEntity): Long

    @Query("SELECT * FROM battery_readings WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getReadingsForSession(sessionId: Long): Flow<List<BatteryReadingEntity>>

    @Query("SELECT * FROM battery_readings WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun getReadingsSince(since: Long): Flow<List<BatteryReadingEntity>>

    @Query("SELECT * FROM battery_readings ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestReading(): BatteryReadingEntity?

    @Query("SELECT * FROM battery_readings ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestReading(): Flow<BatteryReadingEntity?>

    @Query("SELECT * FROM battery_readings ORDER BY timestamp ASC")
    suspend fun getAllReadings(): List<BatteryReadingEntity>

    @Query("DELETE FROM battery_readings")
    suspend fun deleteAll()
}
