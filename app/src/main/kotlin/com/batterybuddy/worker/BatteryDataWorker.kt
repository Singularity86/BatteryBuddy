package com.batterybuddy.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.model.ChargeSource
import com.batterybuddy.data.model.ChargeState
import com.batterybuddy.data.repository.BatteryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant

@HiltWorker
class BatteryDataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: BatteryRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val status  = readBatteryStatus()
            val reading = BatteryReading(
                id                         = 0,
                timestamp                  = Instant.now().toEpochMilli(),
                voltageMillivolts          = status.voltageMv,
                temperatureTenthsCelsius   = status.tempTenthsC,
                chargeCounterMicroAmpHours = status.chargeCounterUah,
                currentMicroAmps           = status.currentUa,
                batteryPercent             = status.percent,
                chargeSource               = ChargeSource.NONE,
                chargeState                = status.chargeState,
                isScreenOn                 = false,
                sessionId                  = null,
                chargerVoltageMillivolts   = null,
                chargerCurrentMaxMilliamps = null,
                isPdActive                 = null,
                chargerType                = null,
                chargeProtocolLabel        = null
            )
            repository.insertReading(reading)

            maybeUpdateDischargeEvent(status)

            Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }

    private suspend fun maybeUpdateDischargeEvent(status: BatteryStatus) {
        val latestEvent = repository.getLatestDischargeEvent().first() ?: return
        if (!latestEvent.isOpen) return

        repository.closeDischargeEvent(
            eventId         = latestEvent.id,
            endPercent      = status.percent,
            endChargeCounter = status.chargeCounterUah.takeIf { it > 0 },
            avgCurrentMicroAmps = status.currentUa.takeIf { it != 0 }
        )

        // Reopen with updated start values so the next poll has a fresh baseline.
        repository.startDischargeEvent(
            startPercent       = status.percent,
            startChargeCounter = status.chargeCounterUah.takeIf { it > 0 }
        )

        checkAnomalousDrain(latestEvent.id, status)
    }

    // Marks the closed event anomalous if the discharge rate is unusually high.
    private suspend fun checkAnomalousDrain(eventId: Long, status: BatteryStatus) {
        val currentMa = kotlin.math.abs(status.currentUa) / 1000f
        if (currentMa > ANOMALOUS_DRAIN_THRESHOLD_MA) {
            repository.markDischargeEventAnomalous(eventId)
        }
    }

    private fun readBatteryStatus(): BatteryStatus {
        val bm     = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val sticky = applicationContext.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val rawStatus = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val chargeState = when (rawStatus) {
            BatteryManager.BATTERY_STATUS_FULL        -> ChargeState.FULL
            BatteryManager.BATTERY_STATUS_CHARGING    -> ChargeState.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeState.DISCHARGING
            else                                      -> ChargeState.NOT_CHARGING
        }

        return BatteryStatus(
            percent          = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            voltageMv        = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            tempTenthsC      = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0,
            chargeCounterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            currentUa        = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            chargeState      = chargeState
        )
    }

    private data class BatteryStatus(
        val percent: Int,
        val voltageMv: Int,
        val tempTenthsC: Int,
        val chargeCounterUah: Int,
        val currentUa: Int,
        val chargeState: ChargeState
    )

    companion object {
        const val WORK_NAME = "battery_discharge_polling"

        // Flag a discharge event anomalous if instantaneous draw exceeds 1500 mA with screen off.
        private const val ANOMALOUS_DRAIN_THRESHOLD_MA = 1500f
    }
}
