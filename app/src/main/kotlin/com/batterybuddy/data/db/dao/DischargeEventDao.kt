package com.batterybuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.batterybuddy.data.db.entity.DischargeEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DischargeEventDao {

    @Insert
    suspend fun insert(event: DischargeEventEntity): Long

    @Query("SELECT * FROM discharge_events ORDER BY start_timestamp DESC")
    fun getAllEvents(): Flow<List<DischargeEventEntity>>

    @Query("SELECT * FROM discharge_events ORDER BY start_timestamp DESC LIMIT 1")
    fun getLatestEvent(): Flow<DischargeEventEntity?>

    @Query("SELECT * FROM discharge_events WHERE id = :id")
    suspend fun getById(id: Long): DischargeEventEntity?

    @Query("SELECT * FROM discharge_events WHERE start_timestamp >= :since ORDER BY start_timestamp ASC")
    fun getEventsSince(since: Long): Flow<List<DischargeEventEntity>>

    @Query("SELECT * FROM discharge_events ORDER BY start_timestamp ASC")
    suspend fun getAllEventsSnapshot(): List<DischargeEventEntity>

    @Query("""
        UPDATE discharge_events SET
            end_timestamp          = :endTimestamp,
            end_percent            = :endPercent,
            end_charge_counter_uah = :endChargeCounter,
            duration_minutes       = :durationMinutes,
            avg_current_ua         = :avgCurrentMicroAmps
        WHERE id = :eventId
    """)
    suspend fun closeEvent(
        eventId: Long,
        endTimestamp: Long,
        endPercent: Int,
        endChargeCounter: Int?,
        durationMinutes: Int,
        avgCurrentMicroAmps: Int?
    )

    @Query("UPDATE discharge_events SET has_anomalous_background = 1 WHERE id = :eventId")
    suspend fun markAnomalousBackground(eventId: Long)

    @Query("DELETE FROM discharge_events")
    suspend fun deleteAll()
}
