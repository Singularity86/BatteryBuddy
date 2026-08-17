package com.batterybuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.batterybuddy.data.db.entity.BatteryProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryProfileDao {

    @Insert
    suspend fun insert(profile: BatteryProfileEntity): Long

    @Query("SELECT * FROM battery_profiles ORDER BY created_at ASC")
    fun getAll(): Flow<List<BatteryProfileEntity>>

    @Query("SELECT * FROM battery_profiles WHERE id = :id")
    suspend fun getById(id: Long): BatteryProfileEntity?

    @Query("UPDATE battery_profiles SET label = :label WHERE id = :id")
    suspend fun rename(id: Long, label: String)

    @Query("UPDATE battery_profiles SET rated_mah = :ratedMah WHERE id = :id")
    suspend fun setRatedMah(id: Long, ratedMah: Int)
}
