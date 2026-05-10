package com.batterybuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.batterybuddy.data.db.entity.OvernightHoldEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OvernightHoldDao {

    @Insert
    suspend fun insert(event: OvernightHoldEntity): Long

    @Query("SELECT * FROM overnight_hold_events ORDER BY detected_timestamp DESC")
    fun getAllEvents(): Flow<List<OvernightHoldEntity>>

    @Query("SELECT * FROM overnight_hold_events WHERE session_id = :sessionId")
    suspend fun getEventsForSession(sessionId: Long): List<OvernightHoldEntity>

    @Query("SELECT * FROM overnight_hold_events ORDER BY detected_timestamp ASC")
    suspend fun getAllEventsSnapshot(): List<OvernightHoldEntity>

    @Query("DELETE FROM overnight_hold_events")
    suspend fun deleteAll()
}
