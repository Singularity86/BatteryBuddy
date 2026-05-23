package com.batterybuddy.data.repository

import com.batterybuddy.data.db.entity.AppUsageEntity
import com.batterybuddy.data.model.AppCorrelation
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.model.ChargeSession
import com.batterybuddy.data.model.ChargeSource
import com.batterybuddy.data.model.DischargeEvent
import com.batterybuddy.data.model.HealthSummary
import com.batterybuddy.data.model.OvernightHoldEvent
import kotlinx.coroutines.flow.Flow
import java.io.OutputStream

interface BatteryRepository {

    // --- Readings ---
    suspend fun insertReading(reading: BatteryReading): Long
    fun getReadingsForSession(sessionId: Long): Flow<List<BatteryReading>>
    fun getReadingsSince(since: Long): Flow<List<BatteryReading>>
    suspend fun getLatestReading(): BatteryReading?
    fun observeLatestReading(): Flow<BatteryReading?>

    // --- Charge sessions ---
    suspend fun startChargeSession(startPercent: Int, source: ChargeSource): Long
    suspend fun updateChargeSession(
        sessionId: Long,
        peakTempTenthsCelsius: Int,
        chargeCounterMicroAmpHours: Int,
        isOvernightHold: Boolean,
        hasAbusiveTemp: Boolean
    )
    suspend fun closeChargeSession(
        sessionId: Long,
        endPercent: Int,
        chargerFingerprint: String?,
        chargerLabel: String?
    )
    suspend fun closeOpenChargeSessions(endPercent: Int)
    suspend fun getLatestOpenChargeSession(): ChargeSession?
    fun getAllChargeSessions(): Flow<List<ChargeSession>>
    fun getLatestChargeSession(): Flow<ChargeSession?>
    fun getCompletedSessionCount(): Flow<Int>
    fun getSessionsSince(since: Long): Flow<List<ChargeSession>>
    fun getSessionsByFingerprint(fingerprint: String): Flow<List<ChargeSession>>
    suspend fun updateChargerLabelForFingerprint(fingerprint: String, label: String)

    // --- Discharge events ---
    suspend fun startDischargeEvent(startPercent: Int, startChargeCounter: Int?): Long
    suspend fun closeDischargeEvent(
        eventId: Long,
        endPercent: Int,
        endChargeCounter: Int?,
        avgCurrentMicroAmps: Int?
    )
    suspend fun markDischargeEventAnomalous(eventId: Long)
    fun getAllDischargeEvents(): Flow<List<DischargeEvent>>
    fun getLatestDischargeEvent(): Flow<DischargeEvent?>

    // --- Overnight holds ---
    suspend fun recordOvernightHold(sessionId: Long, durationMinutes: Int): Long
    fun getAllOvernightHolds(): Flow<List<OvernightHoldEvent>>

    // --- App usage correlation ---
    suspend fun insertAppUsageSamples(samples: List<AppUsageEntity>)
    suspend fun getTopAppCorrelations(windowStart: Long, windowEnd: Long): List<AppCorrelation>

    // --- Computed health (not stored) ---
    suspend fun computeHealthSummary(ratedMah: Int): HealthSummary?

    // --- Maintenance ---
    suspend fun clearAllData()
    suspend fun exportToCsv(outputStream: OutputStream): Result<Unit>
}
