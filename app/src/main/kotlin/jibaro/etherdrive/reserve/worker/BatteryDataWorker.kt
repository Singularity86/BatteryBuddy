package jibaro.etherdrive.reserve.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import jibaro.etherdrive.reserve.data.db.entity.AppUsageEntity
import jibaro.etherdrive.reserve.data.model.BatteryReading
import jibaro.etherdrive.reserve.data.model.ChargeSource
import jibaro.etherdrive.reserve.data.model.ChargeState
import jibaro.etherdrive.reserve.data.repository.BatteryRepository
import jibaro.etherdrive.reserve.data.usage.UsageStatsReader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.math.abs

/**
 * Periodic sampling while unplugged.
 *
 * Updates the open discharge event in place rather than closing and reopening it
 * each run — an event represents the whole unplugged stretch, from unplug to the
 * next plug-in, not a fixed-length bucket.
 */
@HiltWorker
class BatteryDataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: BatteryRepository,
    private val usageStatsReader: UsageStatsReader
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val status = readBatteryStatus()
            repository.insertReading(
                BatteryReading(
                    id                         = 0,
                    timestamp                  = Instant.now().toEpochMilli(),
                    voltageMillivolts          = status.voltageMv,
                    temperatureTenthsCelsius   = status.tempTenthsC,
                    chargeCounterMicroAmpHours = status.chargeCounterUah,
                    currentMicroAmps           = status.currentUa,
                    batteryPercent             = status.percent,
                    chargeSource               = status.chargeSource,
                    chargeState                = status.chargeState,
                    isScreenOn                 = false,
                    sessionId                  = null,
                    chargerVoltageMillivolts   = null,
                    chargerCurrentMaxMilliamps = null,
                    isPdActive                 = null,
                    chargerType                = null,
                    chargeProtocolLabel        = null
                )
            )

            updateOpenDischargeEvent(status)
            sampleAppUsage()
            Result.success()
        } catch (_: Exception) {
            // Sampling is best-effort; a failed run must not retry-storm.
            Result.success()
        }
    }

    private suspend fun updateOpenDischargeEvent(status: BatteryStatus) {
        val event = repository.getLatestOpenDischargeEvent() ?: return

        val averageCurrentUa = repository.updateDischargeProgress(
            eventId          = event.id,
            endPercent       = status.percent,
            endChargeCounter = status.chargeCounterUah.takeIf { it > 0 }
        ) ?: return

        // Judged on the mean draw across the whole event, not one instantaneous
        // sample that a momentary spike could have inflated.
        val averageMilliAmps = abs(averageCurrentUa) / 1000f
        if (averageMilliAmps > ANOMALOUS_DRAIN_THRESHOLD_MA) {
            repository.markDischargeEventAnomalous(event.id)
        }
    }

    /**
     * Records which apps held the foreground since the last sample, so drain can
     * later be correlated against app use. Silently does nothing without usage
     * access — the feature is optional and must never block battery sampling.
     */
    private suspend fun sampleAppUsage() {
        if (!usageStatsReader.hasPermission()) return

        val now = System.currentTimeMillis()
        val lastSampled = repository.getLatestAppUsageTimestamp()
        val windowStart = (lastSampled ?: (now - MAX_USAGE_WINDOW_MS))
            .coerceAtLeast(now - MAX_USAGE_WINDOW_MS)
        if (now - windowStart < MIN_USAGE_WINDOW_MS) return

        val foregroundByPackage = usageStatsReader.foregroundTimeBetween(windowStart, now)
        if (foregroundByPackage.isEmpty()) return

        repository.insertAppUsageSamples(
            foregroundByPackage.map { (packageName, foregroundMillis) ->
                AppUsageEntity(
                    timestamp            = now,
                    packageName          = packageName,
                    foregroundTimeMillis = foregroundMillis,
                    windowStartTimestamp = windowStart,
                    windowEndTimestamp   = now
                )
            }
        )
    }

    private fun readBatteryStatus(): BatteryStatus {
        val bm     = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val sticky = applicationContext.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val chargeState = when (sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1) {
            BatteryManager.BATTERY_STATUS_FULL        -> ChargeState.FULL
            BatteryManager.BATTERY_STATUS_CHARGING    -> ChargeState.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeState.DISCHARGING
            else                                      -> ChargeState.NOT_CHARGING
        }
        val chargeSource = when (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargeSource.WIRELESS
            0                                       -> ChargeSource.NONE
            else                                    -> ChargeSource.USB
        }

        return BatteryStatus(
            percent          = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            voltageMv        = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            tempTenthsC      = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0,
            chargeCounterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            currentUa        = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            chargeState      = chargeState,
            chargeSource     = chargeSource
        )
    }

    private data class BatteryStatus(
        val percent: Int,
        val voltageMv: Int,
        val tempTenthsC: Int,
        val chargeCounterUah: Int,
        val currentUa: Int,
        val chargeState: ChargeState,
        val chargeSource: ChargeSource
    )

    private companion object {
        // Sustained mean draw above this suggests something is misbehaving.
        const val ANOMALOUS_DRAIN_THRESHOLD_MA = 1500f

        // Windows shorter than this carry too little signal; longer than this and
        // a first run after a long gap would swamp every later sample.
        const val MIN_USAGE_WINDOW_MS = 60_000L
        const val MAX_USAGE_WINDOW_MS = 6 * 60 * 60 * 1000L
    }
}
