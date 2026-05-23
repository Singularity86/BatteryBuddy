package com.batterybuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.batterybuddy.data.db.entity.ChargeSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargeSessionDao {

    @Insert
    suspend fun insert(session: ChargeSessionEntity): Long

    @Update
    suspend fun update(session: ChargeSessionEntity)

    @Query("SELECT * FROM charge_sessions ORDER BY start_timestamp DESC")
    fun getAllSessions(): Flow<List<ChargeSessionEntity>>

    @Query("SELECT * FROM charge_sessions ORDER BY start_timestamp DESC LIMIT 1")
    fun getLatestSession(): Flow<ChargeSessionEntity?>

    @Query("SELECT * FROM charge_sessions WHERE id = :id")
    suspend fun getById(id: Long): ChargeSessionEntity?

    @Query("SELECT * FROM charge_sessions WHERE end_timestamp IS NULL ORDER BY start_timestamp DESC LIMIT 1")
    suspend fun getLatestOpenSession(): ChargeSessionEntity?

    @Query("SELECT * FROM charge_sessions WHERE end_timestamp IS NULL ORDER BY start_timestamp DESC")
    suspend fun getOpenSessions(): List<ChargeSessionEntity>

    @Query("SELECT COUNT(*) FROM charge_sessions WHERE end_timestamp IS NOT NULL")
    fun getCompletedSessionCount(): Flow<Int>

    @Query("SELECT * FROM charge_sessions WHERE start_timestamp >= :since ORDER BY start_timestamp ASC")
    fun getSessionsSince(since: Long): Flow<List<ChargeSessionEntity>>

    @Query("SELECT * FROM charge_sessions ORDER BY start_timestamp ASC")
    suspend fun getAllSessionsSnapshot(): List<ChargeSessionEntity>

    // Partial update during live session — avoids overwriting immutable start fields
    @Query("""
        UPDATE charge_sessions SET
            peak_temp_tenths_c   = CASE WHEN :peakTemp   > COALESCE(peak_temp_tenths_c,   -9999)   THEN :peakTemp   ELSE peak_temp_tenths_c   END,
            max_charge_counter_uah = CASE WHEN :counter > 0 AND :counter > COALESCE(max_charge_counter_uah, -9999)   THEN :counter ELSE max_charge_counter_uah END,
            min_charge_counter_uah = CASE WHEN :counter > 0 AND :counter < COALESCE(min_charge_counter_uah, 9999999) THEN :counter ELSE min_charge_counter_uah END,
            is_overnight_hold    = :isOvernightHold,
            has_abusive_temp     = :hasAbusiveTemp
        WHERE id = :sessionId
    """)
    suspend fun updateLiveFields(
        sessionId: Long,
        peakTemp: Int,
        counter: Int,
        isOvernightHold: Boolean,
        hasAbusiveTemp: Boolean
    )

    @Query("""
        UPDATE charge_sessions SET
            end_timestamp        = :endTimestamp,
            end_percent          = :endPercent,
            duration_minutes     = :durationMinutes,
            energy_added_mwh     = :energyAddedMwh,
            depth_of_discharge   = :depthOfDischarge,
            weighted_cycle_cost  = :weightedCycleCost,
            charger_fingerprint  = :chargerFingerprint,
            charger_label        = :chargerLabel
        WHERE id = :sessionId
    """)
    suspend fun closeSession(
        sessionId: Long,
        endTimestamp: Long,
        endPercent: Int,
        durationMinutes: Int,
        energyAddedMwh: Long?,
        depthOfDischarge: Float?,
        weightedCycleCost: Float?,
        chargerFingerprint: String?,
        chargerLabel: String?
    )

    @Query("SELECT * FROM charge_sessions WHERE charger_fingerprint = :fingerprint ORDER BY start_timestamp DESC")
    fun getSessionsByFingerprint(fingerprint: String): Flow<List<ChargeSessionEntity>>

    @Query("UPDATE charge_sessions SET charger_label = :label WHERE charger_fingerprint = :fingerprint")
    suspend fun updateLabelForFingerprint(fingerprint: String, label: String)

    @Query("DELETE FROM charge_sessions")
    suspend fun deleteAll()
}
