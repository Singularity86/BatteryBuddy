package com.batterybuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.batterybuddy.data.db.entity.ChargeSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargeSessionDao {

    @Insert
    suspend fun insert(session: ChargeSessionEntity): Long

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

    @Query("SELECT * FROM charge_sessions ORDER BY start_timestamp ASC")
    suspend fun getAllSessionsSnapshot(): List<ChargeSessionEntity>

    @Query("""
        SELECT * FROM charge_sessions
        WHERE end_timestamp IS NOT NULL AND start_timestamp >= :since
        ORDER BY start_timestamp DESC
    """)
    suspend fun getCompletedSessionsSince(since: Long): List<ChargeSessionEntity>

    // Partial update during a live session — avoids overwriting immutable start
    // fields, and keeps peak/min/max as high- and low-water marks.
    //
    // has_abusive_temp is sticky: once a session has overheated, a later cool
    // reading must not clear the flag.
    @Query("""
        UPDATE charge_sessions SET
            peak_temp_tenths_c   = CASE WHEN :peakTemp   > COALESCE(peak_temp_tenths_c,   -9999)   THEN :peakTemp   ELSE peak_temp_tenths_c   END,
            max_charge_counter_uah = CASE WHEN :counter > 0 AND :counter > COALESCE(max_charge_counter_uah, -9999)   THEN :counter ELSE max_charge_counter_uah END,
            min_charge_counter_uah = CASE WHEN :counter > 0 AND :counter < COALESCE(min_charge_counter_uah, 9999999) THEN :counter ELSE min_charge_counter_uah END,
            has_abusive_temp     = CASE WHEN :hasAbusiveTemp THEN 1 ELSE has_abusive_temp END
        WHERE id = :sessionId
    """)
    suspend fun updateLiveFields(
        sessionId: Long,
        peakTemp: Int,
        counter: Int,
        hasAbusiveTemp: Boolean
    )

    /** Flags an overnight hold without touching any other live field. */
    @Query("UPDATE charge_sessions SET is_overnight_hold = 1 WHERE id = :sessionId")
    suspend fun markOvernightHold(sessionId: Long)

    /**
     * Records which charger this session is running on. Captured while the cable
     * is still attached — sysfs charger values are gone by the time we close.
     */
    @Query("""
        UPDATE charge_sessions SET
            charger_fingerprint = :fingerprint,
            charger_label       = COALESCE(charger_label, :label)
        WHERE id = :sessionId
    """)
    suspend fun updateChargerIdentity(sessionId: Long, fingerprint: String, label: String)

    /**
     * Closes a session. The `end_timestamp IS NULL` guard makes this idempotent:
     * whichever of the unplug receiver or the service teardown gets there second
     * updates zero rows, so the summary notification fires exactly once.
     *
     * @return number of rows actually closed (0 or 1).
     */
    @Query("""
        UPDATE charge_sessions SET
            end_timestamp        = :endTimestamp,
            end_percent          = :endPercent,
            duration_minutes     = :durationMinutes,
            energy_added_mwh     = :energyAddedMwh,
            depth_of_discharge   = :chargeFraction,
            weighted_cycle_cost  = :weightedCycleCost
        WHERE id = :sessionId AND end_timestamp IS NULL
    """)
    suspend fun closeSession(
        sessionId: Long,
        endTimestamp: Long,
        endPercent: Int,
        durationMinutes: Int,
        energyAddedMwh: Long?,
        chargeFraction: Float?,
        weightedCycleCost: Float?
    ): Int

    @Query("UPDATE charge_sessions SET charger_label = :label WHERE charger_fingerprint = :fingerprint")
    suspend fun updateLabelForFingerprint(fingerprint: String, label: String)

    @Query("UPDATE charge_sessions SET battery_id = :batteryId WHERE start_timestamp >= :since")
    suspend fun reassignBatterySince(since: Long, batteryId: Long)

    @Query("DELETE FROM charge_sessions")
    suspend fun deleteAll()
}
