package com.batterybuddy.data.repository

import com.batterybuddy.data.db.dao.AppUsageDao
import com.batterybuddy.data.db.dao.BatteryReadingDao
import com.batterybuddy.data.db.dao.ChargeSessionDao
import com.batterybuddy.data.db.dao.DischargeEventDao
import com.batterybuddy.data.db.dao.OvernightHoldDao
import com.batterybuddy.data.db.entity.AppUsageEntity
import com.batterybuddy.data.db.entity.BatteryReadingEntity
import com.batterybuddy.data.db.entity.ChargeSessionEntity
import com.batterybuddy.data.db.entity.DischargeEventEntity
import com.batterybuddy.data.db.entity.OvernightHoldEntity
import com.batterybuddy.data.model.AppCorrelation
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.model.ChargeSession
import com.batterybuddy.data.model.ChargeSource
import com.batterybuddy.data.model.ChargeState
import com.batterybuddy.data.model.DischargeEvent
import com.batterybuddy.data.model.HealthSummary
import com.batterybuddy.data.model.HealthVerdict
import com.batterybuddy.data.model.OvernightHoldEvent
import com.batterybuddy.data.preferences.UserPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryRepositoryImpl @Inject constructor(
    private val readingDao: BatteryReadingDao,
    private val sessionDao: ChargeSessionDao,
    private val dischargeDao: DischargeEventDao,
    private val overnightHoldDao: OvernightHoldDao,
    private val appUsageDao: AppUsageDao,
    private val prefs: UserPreferencesStore
) : BatteryRepository {

    // region Readings

    override suspend fun insertReading(reading: BatteryReading): Long =
        readingDao.insert(reading.toEntity())

    override fun getReadingsForSession(sessionId: Long): Flow<List<BatteryReading>> =
        readingDao.getReadingsForSession(sessionId).map { list -> list.map { it.toDomain() } }

    override fun getReadingsSince(since: Long): Flow<List<BatteryReading>> =
        readingDao.getReadingsSince(since).map { list -> list.map { it.toDomain() } }

    override suspend fun getLatestReading(): BatteryReading? =
        readingDao.getLatestReading()?.toDomain()

    override fun observeLatestReading(): Flow<BatteryReading?> =
        readingDao.observeLatestReading().map { it?.toDomain() }

    // endregion

    // region Charge sessions

    override suspend fun startChargeSession(startPercent: Int, source: ChargeSource): Long =
        sessionDao.insert(
            ChargeSessionEntity(
                startTimestamp = System.currentTimeMillis(),
                startPercent   = startPercent,
                chargeSource   = source.name
            )
        )

    override suspend fun updateChargeSession(
        sessionId: Long,
        peakTempTenthsCelsius: Int,
        chargeCounterMicroAmpHours: Int,
        isOvernightHold: Boolean,
        hasAbusiveTemp: Boolean
    ) {
        sessionDao.updateLiveFields(
            sessionId       = sessionId,
            peakTemp        = peakTempTenthsCelsius,
            counter         = chargeCounterMicroAmpHours,
            isOvernightHold = isOvernightHold,
            hasAbusiveTemp  = hasAbusiveTemp
        )
    }

    override suspend fun closeChargeSession(
        sessionId: Long,
        endPercent: Int,
        chargerFingerprint: String?,
        chargerLabel: String?
    ) {
        val session = sessionDao.getById(sessionId) ?: return
        val now = System.currentTimeMillis()
        val durationMinutes = ((now - session.startTimestamp) / 60_000L).toInt()
        val maxCounter = session.maxChargeCounterMicroAmpHours
        val minCounter = session.minChargeCounterMicroAmpHours
        val energyAddedMwh = if (maxCounter != null && minCounter != null && maxCounter > minCounter) {
            // µAh × nominal cell voltage (3700 mV) / 1000 → mWh (approximation)
            ((maxCounter - minCounter).toLong() * 3700L) / 1_000L
        } else null
        val dod = (100 - session.startPercent) / 100f
        sessionDao.closeSession(
            sessionId          = sessionId,
            endTimestamp       = now,
            endPercent         = endPercent,
            durationMinutes    = durationMinutes,
            energyAddedMwh     = energyAddedMwh,
            depthOfDischarge   = dod,
            weightedCycleCost  = computeCycleCost(dod, session.peakTempTenthsCelsius),
            chargerFingerprint = chargerFingerprint,
            chargerLabel       = chargerLabel
        )
    }

    override suspend fun getLatestOpenChargeSession(): ChargeSession? =
        sessionDao.getLatestOpenSession()?.toDomain()

    override fun getAllChargeSessions(): Flow<List<ChargeSession>> =
        sessionDao.getAllSessions().map { list -> list.map { it.toDomain() } }

    override fun getLatestChargeSession(): Flow<ChargeSession?> =
        sessionDao.getLatestSession().map { it?.toDomain() }

    override fun getCompletedSessionCount(): Flow<Int> =
        sessionDao.getCompletedSessionCount()

    override fun getSessionsSince(since: Long): Flow<List<ChargeSession>> =
        sessionDao.getSessionsSince(since).map { list -> list.map { it.toDomain() } }

    // endregion

    // region Discharge events

    override suspend fun startDischargeEvent(startPercent: Int, startChargeCounter: Int?): Long =
        dischargeDao.insert(
            DischargeEventEntity(
                startTimestamp                 = System.currentTimeMillis(),
                startPercent                   = startPercent,
                startChargeCounterMicroAmpHours = startChargeCounter
            )
        )

    override suspend fun closeDischargeEvent(
        eventId: Long,
        endPercent: Int,
        endChargeCounter: Int?,
        avgCurrentMicroAmps: Int?
    ) {
        val event = dischargeDao.getById(eventId) ?: return
        val now = System.currentTimeMillis()
        dischargeDao.closeEvent(
            eventId            = eventId,
            endTimestamp       = now,
            endPercent         = endPercent,
            endChargeCounter   = endChargeCounter,
            durationMinutes    = ((now - event.startTimestamp) / 60_000L).toInt(),
            avgCurrentMicroAmps = avgCurrentMicroAmps
        )
    }

    override suspend fun markDischargeEventAnomalous(eventId: Long) =
        dischargeDao.markAnomalousBackground(eventId)

    override fun getAllDischargeEvents(): Flow<List<DischargeEvent>> =
        dischargeDao.getAllEvents().map { list -> list.map { it.toDomain() } }

    override fun getLatestDischargeEvent(): Flow<DischargeEvent?> =
        dischargeDao.getLatestEvent().map { it?.toDomain() }

    // endregion

    // region Overnight holds

    override suspend fun recordOvernightHold(sessionId: Long, durationMinutes: Int): Long =
        overnightHoldDao.insert(
            OvernightHoldEntity(
                sessionId                = sessionId,
                detectedTimestamp        = System.currentTimeMillis(),
                durationMinutes          = durationMinutes,
                estimatedCycleCostPenalty = computeOvernightPenalty(durationMinutes)
            )
        )

    override fun getAllOvernightHolds(): Flow<List<OvernightHoldEvent>> =
        overnightHoldDao.getAllEvents().map { list -> list.map { it.toDomain() } }

    // endregion

    // region App usage

    override suspend fun insertAppUsageSamples(samples: List<AppUsageEntity>) =
        appUsageDao.insertAll(samples)

    override suspend fun getTopAppCorrelations(windowStart: Long, windowEnd: Long): List<AppCorrelation> {
        val totals  = appUsageDao.getTopAppsSince(windowStart)
        val samples = appUsageDao.getSamplesInWindow(windowStart, windowEnd)
        val totalSamples = samples.size.coerceAtLeast(1)
        return totals.map { total ->
            val count = samples.count { it.packageName == total.packageName }
            AppCorrelation(
                packageName            = total.packageName,
                correlationScore       = count.toFloat() / totalSamples,
                sampleCount            = count,
                totalForegroundMillis  = total.total
            )
        }
    }

    // endregion

    // region Health

    override suspend fun computeHealthSummary(ratedMah: Int): HealthSummary? {
        val sessions = sessionDao.getAllSessionsSnapshot()
        val completed = sessions.filter { it.endTimestamp != null }
        if (completed.isEmpty()) return null
        val maxObservedUah = completed.mapNotNull { it.maxChargeCounterMicroAmpHours }.maxOrNull()
            ?: return null
        val currentCapacityMah = maxObservedUah / 1000
        val healthPercent = (currentCapacityMah.toFloat() / ratedMah) * 100f
        val daysSince = ((System.currentTimeMillis() - sessions.first().startTimestamp) / 86_400_000L).toInt()
        val verdict = when {
            healthPercent >= 80f -> HealthVerdict.HEALTHY
            healthPercent >= 70f -> HealthVerdict.WATCH_IT
            healthPercent >= 60f -> HealthVerdict.PLAN_REPLACEMENT
            else                 -> HealthVerdict.REPLACE_NOW
        }
        return HealthSummary(
            currentCapacityMah    = currentCapacityMah,
            ratedMah              = ratedMah,
            healthPercent         = healthPercent,
            verdict               = verdict,
            daysSinceFirstSession = daysSince,
            sessionCount          = completed.size
        )
    }

    // endregion

    // region Maintenance

    override suspend fun clearAllData() {
        // Delete in FK-safe order: children before parents
        readingDao.deleteAll()
        overnightHoldDao.deleteAll()
        sessionDao.deleteAll()
        dischargeDao.deleteAll()
        appUsageDao.deleteAll()
    }

    override suspend fun exportToCsv(outputStream: OutputStream): Result<Unit> = runCatching {
        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).buffered().use { writer ->
            val sessions  = sessionDao.getAllSessionsSnapshot()
            val readings  = readingDao.getAllReadings()
            val holds     = overnightHoldDao.getAllEventsSnapshot()
            val discharge = dischargeDao.getAllEventsSnapshot()
            val usage     = appUsageDao.getAllSamplesSnapshot()

            fun printer(vararg headers: String) =
                CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(*headers).build())

            printer("id","start_timestamp","end_timestamp","start_percent","end_percent",
                "peak_temp_tenths_c","max_charge_counter_uah","min_charge_counter_uah",
                "charge_source","is_overnight_hold","has_abusive_temp","duration_minutes",
                "energy_added_mwh","depth_of_discharge","weighted_cycle_cost",
                "charger_fingerprint","charger_label").use { p ->
                sessions.forEach { s ->
                    p.printRecord(s.id, s.startTimestamp, s.endTimestamp, s.startPercent,
                        s.endPercent, s.peakTempTenthsCelsius, s.maxChargeCounterMicroAmpHours,
                        s.minChargeCounterMicroAmpHours, s.chargeSource, s.isOvernightHold,
                        s.hasAbusiveTemp, s.durationMinutes, s.energyAddedMilliWattHours,
                        s.depthOfDischarge, s.weightedCycleCost, s.chargerFingerprint, s.chargerLabel)
                }
            }
            writer.write("\n")

            printer("id","timestamp","voltage_mv","temp_tenths_c","charge_counter_uah","current_ua",
                "battery_percent","charge_source","charge_state","is_screen_on","session_id",
                "charger_voltage_mv","charger_current_max_ma","is_pd_active","charger_type",
                "charge_protocol_label").use { p ->
                readings.forEach { r ->
                    p.printRecord(r.id, r.timestamp, r.voltageMillivolts, r.temperatureTenthsCelsius,
                        r.chargeCounterMicroAmpHours, r.currentMicroAmps, r.batteryPercent,
                        r.chargeSource, r.chargeState, r.isScreenOn, r.sessionId,
                        r.chargerVoltageMillivolts, r.chargerCurrentMaxMilliamps, r.isPdActive,
                        r.chargerType, r.chargeProtocolLabel)
                }
            }
            writer.write("\n")

            printer("id","session_id","detected_timestamp","duration_minutes","cycle_cost_penalty").use { p ->
                holds.forEach { h ->
                    p.printRecord(h.id, h.sessionId, h.detectedTimestamp, h.durationMinutes, h.estimatedCycleCostPenalty)
                }
            }
            writer.write("\n")

            printer("id","start_timestamp","end_timestamp","start_percent","end_percent",
                "duration_minutes","avg_current_ua","has_anomalous_background").use { p ->
                discharge.forEach { d ->
                    p.printRecord(d.id, d.startTimestamp, d.endTimestamp, d.startPercent,
                        d.endPercent, d.durationMinutes, d.averageCurrentMicroAmps, d.hasAnomalousBackground)
                }
            }
            writer.write("\n")

            printer("id","timestamp","package_name","foreground_time_ms","window_start","window_end").use { p ->
                usage.forEach { u ->
                    p.printRecord(u.id, u.timestamp, u.packageName, u.foregroundTimeMillis,
                        u.windowStartTimestamp, u.windowEndTimestamp)
                }
            }
        }
    }

    // endregion

    // region Helpers

    private fun computeCycleCost(dod: Float, peakTempTenthsCelsius: Int?): Float {
        val dodFactor  = dod * dod  // quadratic DoD weighting per published degradation models
        val tempC      = (peakTempTenthsCelsius ?: 250) / 10f
        val tempFactor = when {
            tempC > 45f -> 1.5f
            tempC > 38f -> 1.2f
            tempC < 10f -> 1.1f
            else        -> 1.0f
        }
        return dodFactor * tempFactor
    }

    private fun computeOvernightPenalty(durationMinutes: Int): Float =
        (durationMinutes / 60f * 0.01f).coerceAtMost(0.1f)

    // endregion

    // region Entity <-> Domain mappers

    private fun BatteryReadingEntity.toDomain() = BatteryReading(
        id = id, timestamp = timestamp,
        voltageMillivolts = voltageMillivolts, temperatureTenthsCelsius = temperatureTenthsCelsius,
        chargeCounterMicroAmpHours = chargeCounterMicroAmpHours, currentMicroAmps = currentMicroAmps,
        batteryPercent = batteryPercent,
        chargeSource = ChargeSource.fromString(chargeSource),
        chargeState  = ChargeState.fromString(chargeState),
        isScreenOn = isScreenOn, sessionId = sessionId,
        chargerVoltageMillivolts = chargerVoltageMillivolts,
        chargerCurrentMaxMilliamps = chargerCurrentMaxMilliamps,
        isPdActive = isPdActive, chargerType = chargerType, chargeProtocolLabel = chargeProtocolLabel
    )

    private fun BatteryReading.toEntity() = BatteryReadingEntity(
        id = id, timestamp = timestamp,
        voltageMillivolts = voltageMillivolts, temperatureTenthsCelsius = temperatureTenthsCelsius,
        chargeCounterMicroAmpHours = chargeCounterMicroAmpHours, currentMicroAmps = currentMicroAmps,
        batteryPercent = batteryPercent,
        chargeSource = chargeSource.name, chargeState = chargeState.name,
        isScreenOn = isScreenOn, sessionId = sessionId,
        chargerVoltageMillivolts = chargerVoltageMillivolts,
        chargerCurrentMaxMilliamps = chargerCurrentMaxMilliamps,
        isPdActive = isPdActive, chargerType = chargerType, chargeProtocolLabel = chargeProtocolLabel
    )

    private fun ChargeSessionEntity.toDomain() = ChargeSession(
        id = id, startTimestamp = startTimestamp, endTimestamp = endTimestamp,
        startPercent = startPercent, endPercent = endPercent,
        peakTempTenthsCelsius = peakTempTenthsCelsius,
        maxChargeCounterMicroAmpHours = maxChargeCounterMicroAmpHours,
        minChargeCounterMicroAmpHours = minChargeCounterMicroAmpHours,
        chargeSource = ChargeSource.fromString(chargeSource),
        isOvernightHold = isOvernightHold, hasAbusiveTemp = hasAbusiveTemp,
        durationMinutes = durationMinutes, energyAddedMilliWattHours = energyAddedMilliWattHours,
        depthOfDischarge = depthOfDischarge, weightedCycleCost = weightedCycleCost,
        chargerFingerprint = chargerFingerprint, chargerLabel = chargerLabel
    )

    private fun DischargeEventEntity.toDomain() = DischargeEvent(
        id = id, startTimestamp = startTimestamp, endTimestamp = endTimestamp,
        startPercent = startPercent, endPercent = endPercent,
        startChargeCounterMicroAmpHours = startChargeCounterMicroAmpHours,
        endChargeCounterMicroAmpHours   = endChargeCounterMicroAmpHours,
        durationMinutes = durationMinutes, averageCurrentMicroAmps = averageCurrentMicroAmps,
        hasAnomalousBackground = hasAnomalousBackground
    )

    private fun OvernightHoldEntity.toDomain() = OvernightHoldEvent(
        id = id, sessionId = sessionId, detectedTimestamp = detectedTimestamp,
        durationMinutes = durationMinutes, estimatedCycleCostPenalty = estimatedCycleCostPenalty
    )

    // endregion
}
