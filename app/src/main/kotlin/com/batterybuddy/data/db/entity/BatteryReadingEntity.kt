package com.batterybuddy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "battery_readings",
    foreignKeys = [
        ForeignKey(
            entity = ChargeSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("session_id"),
        Index("timestamp")
    ]
)
data class BatteryReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // BatteryManager — always available
    val timestamp: Long,
    @ColumnInfo(name = "voltage_mv")          val voltageMillivolts: Int,
    @ColumnInfo(name = "temp_tenths_c")       val temperatureTenthsCelsius: Int,
    @ColumnInfo(name = "charge_counter_uah")  val chargeCounterMicroAmpHours: Int,
    @ColumnInfo(name = "current_ua")          val currentMicroAmps: Int,
    @ColumnInfo(name = "battery_percent")     val batteryPercent: Int,
    @ColumnInfo(name = "charge_source")       val chargeSource: String,
    @ColumnInfo(name = "charge_state")        val chargeState: String,
    @ColumnInfo(name = "is_screen_on")        val isScreenOn: Boolean,
    @ColumnInfo(name = "session_id")          val sessionId: Long? = null,

    // sysfs charger data — nullable; all null on devices with restricted sysfs
    @ColumnInfo(name = "charger_voltage_mv")       val chargerVoltageMillivolts: Int? = null,
    @ColumnInfo(name = "charger_current_max_ma")   val chargerCurrentMaxMilliamps: Int? = null,
    @ColumnInfo(name = "is_pd_active")             val isPdActive: Boolean? = null,
    @ColumnInfo(name = "charger_type")             val chargerType: String? = null,
    @ColumnInfo(name = "charge_protocol_label")    val chargeProtocolLabel: String? = null
)
