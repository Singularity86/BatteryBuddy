package jibaro.etherdrive.reserve.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "charge_sessions",
    indices = [Index("start_timestamp"), Index("battery_id")]
)
data class ChargeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "battery_id")             val batteryId: Long? = null,
    @ColumnInfo(name = "start_timestamp")        val startTimestamp: Long,
    @ColumnInfo(name = "end_timestamp")          val endTimestamp: Long? = null,
    @ColumnInfo(name = "start_percent")          val startPercent: Int,
    @ColumnInfo(name = "end_percent")            val endPercent: Int? = null,
    @ColumnInfo(name = "peak_temp_tenths_c")     val peakTempTenthsCelsius: Int? = null,
    @ColumnInfo(name = "max_charge_counter_uah") val maxChargeCounterMicroAmpHours: Int? = null,
    @ColumnInfo(name = "min_charge_counter_uah") val minChargeCounterMicroAmpHours: Int? = null,
    @ColumnInfo(name = "charge_source")          val chargeSource: String,
    @ColumnInfo(name = "is_overnight_hold")      val isOvernightHold: Boolean = false,
    @ColumnInfo(name = "has_abusive_temp")       val hasAbusiveTemp: Boolean = false,
    @ColumnInfo(name = "duration_minutes")       val durationMinutes: Int? = null,
    @ColumnInfo(name = "energy_added_mwh")       val energyAddedMilliWattHours: Long? = null,
    // Column name is historical; the value is the fraction of the battery the
    // session refilled, not depth of discharge.
    @ColumnInfo(name = "depth_of_discharge")     val chargeFraction: Float? = null,
    @ColumnInfo(name = "weighted_cycle_cost")    val weightedCycleCost: Float? = null,
    @ColumnInfo(name = "charger_fingerprint")    val chargerFingerprint: String? = null,
    @ColumnInfo(name = "charger_label")          val chargerLabel: String? = null
)
