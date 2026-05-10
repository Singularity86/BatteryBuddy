package com.batterybuddy.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.batterybuddy.data.db.entity.AppUsageEntity

@Dao
interface AppUsageDao {

    @Insert
    suspend fun insertAll(samples: List<AppUsageEntity>)

    @Query("""
        SELECT * FROM app_usage_samples
        WHERE window_start >= :start AND window_end <= :end
        ORDER BY timestamp ASC
    """)
    suspend fun getSamplesInWindow(start: Long, end: Long): List<AppUsageEntity>

    @Query("""
        SELECT package_name, SUM(foreground_time_ms) AS total
        FROM app_usage_samples
        WHERE window_start >= :since
        GROUP BY package_name
        ORDER BY total DESC
        LIMIT :limit
    """)
    suspend fun getTopAppsSince(since: Long, limit: Int = 20): List<AppUsageTotals>

    @Query("SELECT * FROM app_usage_samples ORDER BY timestamp ASC")
    suspend fun getAllSamplesSnapshot(): List<AppUsageEntity>

    @Query("DELETE FROM app_usage_samples")
    suspend fun deleteAll()
}

data class AppUsageTotals(
    @ColumnInfo(name = "package_name") val packageName: String,
    val total: Long
)
