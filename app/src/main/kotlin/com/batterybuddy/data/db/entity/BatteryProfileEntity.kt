package com.batterybuddy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_profiles")
data class BatteryProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "label")      val label: String,
    @ColumnInfo(name = "rated_mah")  val ratedMah: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
