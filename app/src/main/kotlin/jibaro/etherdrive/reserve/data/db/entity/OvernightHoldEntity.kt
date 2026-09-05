package jibaro.etherdrive.reserve.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "overnight_hold_events",
    foreignKeys = [
        ForeignKey(
            entity = ChargeSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("session_id")]
)
data class OvernightHoldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id")          val sessionId: Long,
    @ColumnInfo(name = "detected_timestamp")  val detectedTimestamp: Long,
    @ColumnInfo(name = "duration_minutes")    val durationMinutes: Int,
    @ColumnInfo(name = "cycle_cost_penalty")  val estimatedCycleCostPenalty: Float
)
