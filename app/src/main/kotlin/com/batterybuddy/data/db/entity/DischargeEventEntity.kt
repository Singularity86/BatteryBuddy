package com.batterybuddy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "discharge_events",
    indices = [Index("start_timestamp")]
)
data class DischargeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_timestamp")          val startTimestamp: Long,
    @ColumnInfo(name = "end_timestamp")            val endTimestamp: Long? = null,
    @ColumnInfo(name = "start_percent")            val startPercent: Int,
    @ColumnInfo(name = "end_percent")              val endPercent: Int? = null,
    @ColumnInfo(name = "start_charge_counter_uah") val startChargeCounterMicroAmpHours: Int? = null,
    @ColumnInfo(name = "end_charge_counter_uah")   val endChargeCounterMicroAmpHours: Int? = null,
    @ColumnInfo(name = "duration_minutes")         val durationMinutes: Int? = null,
    @ColumnInfo(name = "avg_current_ua")           val averageCurrentMicroAmps: Int? = null,
    @ColumnInfo(name = "has_anomalous_background") val hasAnomalousBackground: Boolean = false
)
