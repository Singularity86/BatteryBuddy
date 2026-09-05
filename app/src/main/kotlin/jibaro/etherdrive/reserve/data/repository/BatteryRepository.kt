package jibaro.etherdrive.reserve.data.repository

import jibaro.etherdrive.reserve.data.db.entity.AppUsageEntity
import jibaro.etherdrive.reserve.data.model.AppCorrelation
import jibaro.etherdrive.reserve.data.model.BatteryProfile
import jibaro.etherdrive.reserve.data.model.BatteryReading
import jibaro.etherdrive.reserve.data.model.ChargeSession
import jibaro.etherdrive.reserve.data.model.ChargeSource
import jibaro.etherdrive.reserve.data.model.DischargeEvent
import jibaro.etherdrive.reserve.data.model.OvernightHoldEvent
import kotlinx.coroutines.flow.Flow
import java.io.OutputStream

interface BatteryRepository {

    // --- Readings ---
    suspend fun insertReading(reading: BatteryReading): Long
    fun getReadingsForSession(sessionId: Long): Flow<List<BatteryReading>>
    fun getReadingsSince(since: Long): Flow<List<BatteryReading>>
    fun getReadingsBetween(start: Long, end: Long): Flow<List<BatteryReading>>
    fun observeLatestReading(): Flow<BatteryReading?>

    // --- Charge sessions ---
    suspend fun startChargeSession(startPercent: Int, source: ChargeSource): Long

    /** High/low-water-mark update for a running session. `hasAbusiveTemp` is sticky. */
    suspend fun updateLiveSessionFields(
        sessionId: Long,
        peakTempTenthsCelsius: Int,
        chargeCounterMicroAmpHours: Int,
        hasAbusiveTemp: Boolean
    )

    suspend fun markOvernightHold(sessionId: Long)

    /** Records the charger a session is running on, while the cable is still attached. */
    suspend fun updateChargerIdentity(sessionId: Long, fingerprint: String, label: String)

    /** @return true if this call is the one that closed the session. */
    suspend fun closeChargeSession(sessionId: Long, endPercent: Int): Boolean

    /** @return the sessions this call actually closed, newest first. */
    suspend fun closeOpenChargeSessions(endPercent: Int): List<ChargeSession>

    suspend fun getLatestOpenChargeSession(): ChargeSession?
    suspend fun getCompletedSessionsSince(since: Long): List<ChargeSession>
    fun getAllChargeSessions(): Flow<List<ChargeSession>>
    fun getLatestChargeSession(): Flow<ChargeSession?>
    suspend fun updateChargerLabelForFingerprint(fingerprint: String, label: String)

    // --- Discharge events ---
    suspend fun getDischargeEventById(id: Long): DischargeEvent?
    suspend fun getLatestOpenDischargeEvent(): DischargeEvent?
    suspend fun startDischargeEvent(startPercent: Int, startChargeCounter: Int?): Long

    /**
     * Updates a running event's rolling values without closing it.
     * @return the mean current over the event so far, in µA, or null if unknown.
     */
    suspend fun updateDischargeProgress(
        eventId: Long,
        endPercent: Int,
        endChargeCounter: Int?
    ): Int?

    suspend fun closeOpenDischargeEvents(endPercent: Int, endChargeCounter: Int?)
    suspend fun markDischargeEventAnomalous(eventId: Long)
    fun getAllDischargeEvents(): Flow<List<DischargeEvent>>
    fun getLatestDischargeEvent(): Flow<DischargeEvent?>

    // --- Overnight holds ---
    suspend fun recordOvernightHold(sessionId: Long, durationMinutes: Int): Long
    fun getAllOvernightHolds(): Flow<List<OvernightHoldEvent>>

    // --- App usage correlation ---
    suspend fun insertAppUsageSamples(samples: List<AppUsageEntity>)
    suspend fun getLatestAppUsageTimestamp(): Long?
    suspend fun getTopAppCorrelations(windowStart: Long, windowEnd: Long): List<AppCorrelation>

    // --- Battery profiles (multi-battery swap support) ---
    fun getBatteryProfiles(): Flow<List<BatteryProfile>>
    suspend fun getBatteryProfile(id: Long): BatteryProfile?
    suspend fun addBatteryProfile(label: String, ratedMah: Int): Long
    suspend fun renameBatteryProfile(id: Long, label: String)
    suspend fun setBatteryRatedMah(id: Long, ratedMah: Int)
    suspend fun reassignRecordsSince(since: Long, batteryId: Long)

    // --- Maintenance ---
    suspend fun clearAllData()
    suspend fun exportToCsv(outputStream: OutputStream): Result<Unit>
}
