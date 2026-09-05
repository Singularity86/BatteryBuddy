package jibaro.etherdrive.reserve.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import jibaro.etherdrive.reserve.data.db.entity.BatteryReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryReadingDao {

    @Insert
    suspend fun insert(reading: BatteryReadingEntity): Long

    @Query("SELECT * FROM battery_readings WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getReadingsForSession(sessionId: Long): Flow<List<BatteryReadingEntity>>

    @Query("SELECT * FROM battery_readings WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun getReadingsSince(since: Long): Flow<List<BatteryReadingEntity>>

    @Query("SELECT * FROM battery_readings WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    fun getReadingsBetween(start: Long, end: Long): Flow<List<BatteryReadingEntity>>

    @Query("SELECT * FROM battery_readings ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestReading(): Flow<BatteryReadingEntity?>

    @Query("SELECT * FROM battery_readings ORDER BY timestamp ASC")
    suspend fun getAllReadings(): List<BatteryReadingEntity>

    /**
     * Mean instantaneous current over a window. Used to give a discharge event a
     * real average instead of whichever value happened to be current when it closed.
     * Returns null when no readings fall inside the window.
     */
    @Query("""
        SELECT AVG(current_ua) FROM battery_readings
        WHERE timestamp >= :start AND timestamp <= :end AND current_ua != 0
    """)
    suspend fun averageCurrentBetween(start: Long, end: Long): Double?

    @Query("DELETE FROM battery_readings")
    suspend fun deleteAll()
}
