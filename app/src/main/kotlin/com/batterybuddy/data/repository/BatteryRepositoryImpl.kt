package com.batterybuddy.data.repository

import com.batterybuddy.data.analysis.ChargeMath
import com.batterybuddy.data.db.dao.AppUsageDao
import com.batterybuddy.data.db.dao.BatteryProfileDao
import com.batterybuddy.data.db.dao.BatteryReadingDao
import com.batterybuddy.data.db.dao.ChargeSessionDao
import com.batterybuddy.data.db.dao.DischargeEventDao
import com.batterybuddy.data.db.dao.OvernightHoldDao
import com.batterybuddy.data.db.entity.AppUsageEntity
import com.batterybuddy.data.db.entity.BatteryProfileEntity
import com.batterybuddy.data.db.entity.BatteryReadingEntity
import com.batterybuddy.data.db.entity.ChargeSessionEntity
import com.batterybuddy.data.db.entity.DischargeEventEntity
import com.batterybuddy.data.db.entity.OvernightHoldEntity
import com.batterybuddy.data.model.AppCorrelation
import com.batterybuddy.data.model.BatteryProfile
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.model.ChargeSession
import com.batterybuddy.data.model.ChargeSource
import com.batterybuddy.data.model.ChargeState
import com.batterybuddy.data.model.DischargeEvent
import com.batterybuddy.data.model.OvernightHoldEvent
import com.batterybuddy.data.preferences.UserPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class BatteryRepositoryImpl @Inject constructor(
    private val readingDao: BatteryReadingDao,
    private val sessionDao: ChargeSessionDao,
    private val dischargeDao: DischargeEventDao,
    private val overnightHoldDao: OvernightHoldDao,
    private val appUsageDao: AppUsageDao,
    private val batteryProfileDao: BatteryProfileDao,
    private val prefs: UserPreferencesStore
) : BatteryRepository {

    // region Readings

    override suspend fun insertReading(reading: BatteryReading): Long =
        readingDao.insert(reading.toEntity())

    override fun getReadingsForSession(sessionId: Long): Flow<List<BatteryReading>> =
        readingDao.getReadingsForSession(sessionId).map { list -> list.map { it.toDomain() } }

    override fun getReadingsSince(since: Long): Flow<List<BatteryReading>> =
        readingDao.getReadingsSince(since).map { list -> list.map { it.toDomain() } }

    override fun getReadingsBetween(start: Long, end: Long): Flow<List<BatteryReading>> =
        readingDao.getReadingsBetween(start, end).map { list -> list.map { it.toDomain() } }

    override fun observeLatestReading(): Flow<BatteryReading?> =
        readingDao.observeLatestReading().map { it?.toDomain() }

    // endregion

    // region Charge sessions

    override suspend fun startChargeSession(startPercent: Int, source: ChargeSource): Long =
        sessionDao.insert(
            ChargeSessionEntity(
                batteryId      = prefs.activeBatteryId.first(),
                startTimestamp = System.currentTimeMillis(),
                startPercent   = startPercent,
                chargeSource   = source.name
            )
        )

    override suspend fun updateLiveSessionFields(
        sessionId: Long,
        peakTempTenthsCelsius: Int,
        chargeCounterMicroAmpHours: Int,
        hasAbusiveTemp: Boolean
    ) = sessionDao.updateLiveFields(
        sessionId      = sessionId,
        peakTemp       = peakTempTenthsCelsius,
        counter        = chargeCounterMicroAmpHours,
        hasAbusiveTemp = hasAbusiveTemp
    )

    override suspend fun markOvernightHold(sessionId: Long) =
        sessionDao.markOvernightHold(sessionId)

    override suspend fun updateChargerIdentity(sessionId: Long, fingerprint: String, label: String) =
        sessionDao.updateChargerIdentity(sessionId, fingerprint, label)

    override suspend fun closeChargeSession(sessionId: Long, endPercent: Int): Boolean {
        val session = sessionDao.getById(sessionId) ?: return false
        if (session.endTimestamp != null) return false

        val now = System.currentTimeMillis()
        val chargeFraction = ChargeMath.chargeFraction(session.startPercent, endPercent)
        val rowsClosed = sessionDao.closeSession(
            sessionId         = sessionId,
            endTimestamp      = now,
            endPercent        = endPercent,
            durationMinutes   = ((now - session.startTimestamp) / 60_000L).toInt(),
            energyAddedMwh    = ChargeMath.energyAddedMilliWattHours(
                minCounterMicroAmpHours = session.minChargeCounterMicroAmpHours,
                maxCounterMicroAmpHours = session.maxChargeCounterMicroAmpHours
            ),
            chargeFraction    = chargeFraction,
            weightedCycleCost = ChargeMath.cycleCost(chargeFraction, session.peakTempTenthsCelsius)
        )
        return rowsClosed > 0
    }

    override suspend fun closeOpenChargeSessions(endPercent: Int): List<ChargeSession> =
        sessionDao.getOpenSessions()
            .filter { closeChargeSession(it.id, endPercent) }
            .mapNotNull { sessionDao.getById(it.id)?.toDomain() }
            .sortedByDescending { it.startTimestamp }

    override suspend fun getLatestOpenChargeSession(): ChargeSession? =
        sessionDao.getLatestOpenSession()?.toDomain()

    override fun getAllChargeSessions(): Flow<List<ChargeSession>> =
        sessionDao.getAllSessions().map { list -> list.map { it.toDomain() } }

    override fun getLatestChargeSession(): Flow<ChargeSession?> =
        sessionDao.getLatestSession().map { it?.toDomain() }

    override suspend fun updateChargerLabelForFingerprint(fingerprint: String, label: String) =
        sessionDao.updateLabelForFingerprint(fingerprint, label)

    // endregion

    // region Discharge events

    override suspend fun getDischargeEventById(id: Long): DischargeEvent? =
        dischargeDao.getById(id)?.toDomain()

    override suspend fun getLatestOpenDischargeEvent(): DischargeEvent? =
        dischargeDao.getLatestOpenEvent()?.toDomain()

    override suspend fun startDischargeEvent(startPercent: Int, startChargeCounter: Int?): Long =
        dischargeDao.insert(
            DischargeEventEntity(
                batteryId                       = prefs.activeBatteryId.first(),
                startTimestamp                  = System.currentTimeMillis(),
                startPercent                    = startPercent,
                startChargeCounterMicroAmpHours = startChargeCounter
            )
        )

    override suspend fun updateDischargeProgress(
        eventId: Long,
        endPercent: Int,
        endChargeCounter: Int?
    ): Int? {
        val event = dischargeDao.getById(eventId) ?: return null
        if (event.endTimestamp != null) return null

        val now = System.currentTimeMillis()
        val avgCurrent = averageCurrentSince(event.startTimestamp, now)
        dischargeDao.updateProgress(
            eventId             = eventId,
            endPercent          = endPercent,
            endChargeCounter    = endChargeCounter,
            durationMinutes     = ((now - event.startTimestamp) / 60_000L).toInt(),
            avgCurrentMicroAmps = avgCurrent
        )
        return avgCurrent
    }

    override suspend fun closeOpenDischargeEvents(endPercent: Int, endChargeCounter: Int?) {
        val now = System.currentTimeMillis()
        dischargeDao.getOpenEvents().forEach { event ->
            dischargeDao.closeEvent(
                eventId             = event.id,
                endTimestamp        = now,
                endPercent          = endPercent,
                endChargeCounter    = endChargeCounter,
                durationMinutes     = ((now - event.startTimestamp) / 60_000L).toInt(),
                avgCurrentMicroAmps = averageCurrentSince(event.startTimestamp, now)
            )
        }
    }

    override suspend fun markDischargeEventAnomalous(eventId: Long) =
        dischargeDao.markAnomalousBackground(eventId)

    override fun getAllDischargeEvents(): Flow<List<DischargeEvent>> =
        dischargeDao.getAllEvents().map { list -> list.map { it.toDomain() } }

    override fun getLatestDischargeEvent(): Flow<DischargeEvent?> =
        dischargeDao.getLatestEvent().map { it?.toDomain() }

    /** Mean measured current over a window — a real average, not the last sample. */
    private suspend fun averageCurrentSince(start: Long, end: Long): Int? =
        readingDao.averageCurrentBetween(start, end)?.roundToInt()

    // endregion

    // region Overnight holds

    override suspend fun recordOvernightHold(sessionId: Long, durationMinutes: Int): Long =
        overnightHoldDao.insert(
            OvernightHoldEntity(
                sessionId                 = sessionId,
                detectedTimestamp         = System.currentTimeMillis(),
                durationMinutes           = durationMinutes,
                estimatedCycleCostPenalty = ChargeMath.overnightPenalty(durationMinutes)
            )
        )

    override fun getAllOvernightHolds(): Flow<List<OvernightHoldEvent>> =
        overnightHoldDao.getAllEvents().map { list -> list.map { it.toDomain() } }

    // endregion

    // region App usage

    override suspend fun insertAppUsageSamples(samples: List<AppUsageEntity>) =
        appUsageDao.insertAll(samples)

    override suspend fun getLatestAppUsageTimestamp(): Long? =
        appUsageDao.getLatestWindowEnd()

    override suspend fun getTopAppCorrelations(windowStart: Long, windowEnd: Long): List<AppCorrelation> {
        val totals  = appUsageDao.getTopAppsSince(windowStart)
        if (totals.isEmpty()) return emptyList()
        val samples = appUsageDao.getSamplesInWindow(windowStart, windowEnd)
        if (samples.isEmpty()) return emptyList()

        val windowCount = samples.map { it.windowStartTimestamp }.distinct().size.coerceAtLeast(1)
        return totals.map { total ->
            val appWindows = samples
                .filter { it.packageName == total.packageName }
                .map { it.windowStartTimestamp }
                .distinct()
            AppCorrelation(
                packageName           = total.packageName,
                windowsPresent        = appWindows.size,
                windowsTotal          = windowCount,
                totalForegroundMillis = total.total
            )
        }.sortedByDescending { it.totalForegroundMillis }
    }

    // endregion

    // region Battery profiles

    override fun getBatteryProfiles(): Flow<List<BatteryProfile>> =
        batteryProfileDao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getBatteryProfile(id: Long): BatteryProfile? =
        batteryProfileDao.getById(id)?.toDomain()

    override suspend fun addBatteryProfile(label: String, ratedMah: Int): Long =
        batteryProfileDao.insert(
            BatteryProfileEntity(
                label = label,
                ratedMah = ratedMah,
                createdAt = System.currentTimeMillis()
            )
        )

    override suspend fun renameBatteryProfile(id: Long, label: String) =
        batteryProfileDao.rename(id, label)

    override suspend fun setBatteryRatedMah(id: Long, ratedMah: Int) =
        batteryProfileDao.setRatedMah(id, ratedMah)

    override suspend fun reassignRecordsSince(since: Long, batteryId: Long) {
        sessionDao.reassignBatterySince(since, batteryId)
        dischargeDao.reassignBatterySince(since, batteryId)
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

            printer("id","battery_id","start_timestamp","end_timestamp","start_percent","end_percent",
                "peak_temp_tenths_c","max_charge_counter_uah","min_charge_counter_uah",
                "charge_source","is_overnight_hold","has_abusive_temp","duration_minutes",
                "energy_added_mwh","charge_fraction","weighted_cycle_cost",
                "charger_fingerprint","charger_label").use { p ->
                sessions.forEach { s ->
                    p.printRecord(s.id, s.batteryId, s.startTimestamp, s.endTimestamp, s.startPercent,
                        s.endPercent, s.peakTempTenthsCelsius, s.maxChargeCounterMicroAmpHours,
                        s.minChargeCounterMicroAmpHours, s.chargeSource, s.isOvernightHold,
                        s.hasAbusiveTemp, s.durationMinutes, s.energyAddedMilliWattHours,
                        s.chargeFraction, s.weightedCycleCost, s.chargerFingerprint, s.chargerLabel)
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

            printer("id","battery_id","start_timestamp","end_timestamp","start_percent","end_percent",
                "duration_minutes","avg_current_ua","has_anomalous_background").use { p ->
                discharge.forEach { d ->
                    p.printRecord(d.id, d.batteryId, d.startTimestamp, d.endTimestamp, d.startPercent,
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
        id = id, batteryId = batteryId, startTimestamp = startTimestamp, endTimestamp = endTimestamp,
        startPercent = startPercent, endPercent = endPercent,
        peakTempTenthsCelsius = peakTempTenthsCelsius,
        maxChargeCounterMicroAmpHours = maxChargeCounterMicroAmpHours,
        minChargeCounterMicroAmpHours = minChargeCounterMicroAmpHours,
        chargeSource = ChargeSource.fromString(chargeSource),
        isOvernightHold = isOvernightHold, hasAbusiveTemp = hasAbusiveTemp,
        durationMinutes = durationMinutes, energyAddedMilliWattHours = energyAddedMilliWattHours,
        chargeFraction = chargeFraction, weightedCycleCost = weightedCycleCost,
        chargerFingerprint = chargerFingerprint, chargerLabel = chargerLabel
    )

    private fun DischargeEventEntity.toDomain() = DischargeEvent(
        id = id, batteryId = batteryId, startTimestamp = startTimestamp, endTimestamp = endTimestamp,
        startPercent = startPercent, endPercent = endPercent,
        startChargeCounterMicroAmpHours = startChargeCounterMicroAmpHours,
        endChargeCounterMicroAmpHours   = endChargeCounterMicroAmpHours,
        durationMinutes = durationMinutes, averageCurrentMicroAmps = averageCurrentMicroAmps,
        hasAnomalousBackground = hasAnomalousBackground
    )

    private fun BatteryProfileEntity.toDomain() = BatteryProfile(
        id = id, label = label, ratedMah = ratedMah, createdAt = createdAt
    )

    private fun OvernightHoldEntity.toDomain() = OvernightHoldEvent(
        id = id, sessionId = sessionId, detectedTimestamp = detectedTimestamp,
        durationMinutes = durationMinutes, estimatedCycleCostPenalty = estimatedCycleCostPenalty
    )

    // endregion
}
