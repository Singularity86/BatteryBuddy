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

    @Query("SELECT * FROM discharge_events WHERE end_timestamp IS NULL ORDER BY start_timestamp DESC LIMIT 1")
    suspend fun getLatestOpenEvent(): DischargeEventEntity?

    @Query("SELECT * FROM discharge_events WHERE end_timestamp IS NULL ORDER BY start_timestamp DESC")
    suspend fun getOpenEvents(): List<DischargeEventEntity>

    @Query("SELECT * FROM discharge_events ORDER BY start_timestamp ASC")
    suspend fun getAllEventsSnapshot(): List<DischargeEventEntity>

    /**
     * Updates a still-running discharge event in place. Deliberately leaves
     * `end_timestamp` NULL — an event stays open from unplug until the next
     * plug-in, and these rolling values let the UI show live progress without
     * the event having to be closed and reopened on every poll.
     */
    @Query("""
        UPDATE discharge_events SET
            end_percent            = :endPercent,
            end_charge_counter_uah = :endChargeCounter,
            duration_minutes       = :durationMinutes,
            avg_current_ua         = :avgCurrentMicroAmps
        WHERE id = :eventId AND end_timestamp IS NULL
    """)
    suspend fun updateProgress(
        eventId: Long,
        endPercent: Int,
        endChargeCounter: Int?,
        durationMinutes: Int,
        avgCurrentMicroAmps: Int?
    )

    /** Closes an event. Idempotent — a second close updates zero rows. */
    @Query("""
        UPDATE discharge_events SET
            end_timestamp          = :endTimestamp,
            end_percent            = :endPercent,
            end_charge_counter_uah = :endChargeCounter,
            duration_minutes       = :durationMinutes,
            avg_current_ua         = :avgCurrentMicroAmps
        WHERE id = :eventId AND end_timestamp IS NULL
    """)
    suspend fun closeEvent(
        eventId: Long,
        endTimestamp: Long,
        endPercent: Int,
        endChargeCounter: Int?,
        durationMinutes: Int,
        avgCurrentMicroAmps: Int?
    ): Int

    @Query("UPDATE discharge_events SET has_anomalous_background = 1 WHERE id = :eventId")
    suspend fun markAnomalousBackground(eventId: Long)

    @Query("UPDATE discharge_events SET battery_id = :batteryId WHERE start_timestamp >= :since")
    suspend fun reassignBatterySince(since: Long, batteryId: Long)

    @Query("DELETE FROM discharge_events")
    suspend fun deleteAll()
}
