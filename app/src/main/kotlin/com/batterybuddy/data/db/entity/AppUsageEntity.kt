package com.batterybuddy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_usage_samples",
    indices = [
        Index("window_start"),
        Index("package_name")
    ]
)
data class AppUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    @ColumnInfo(name = "package_name")      val packageName: String,
    @ColumnInfo(name = "foreground_time_ms") val foregroundTimeMillis: Long,
    @ColumnInfo(name = "window_start")      val windowStartTimestamp: Long,
    @ColumnInfo(name = "window_end")        val windowEndTimestamp: Long
)
