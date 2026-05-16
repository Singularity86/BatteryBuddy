package com.batterybuddy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.model.ChargeSource
import com.batterybuddy.data.model.ChargeState
import com.batterybuddy.data.repository.BatteryRepository
import com.batterybuddy.service.BatteryPollingService
import com.batterybuddy.worker.BatteryDataWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class BatteryStateReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: BatteryRepository

    // BroadcastReceiver.goAsync() pattern: keep the process alive long enough to launch coroutines.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        scope.launch {
            try {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED    -> handlePluggedIn(context)
                    Intent.ACTION_POWER_DISCONNECTED -> handleUnplugged(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handlePluggedIn(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(BatteryDataWorker.WORK_NAME)

        val serviceIntent = Intent(context, BatteryPollingService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private suspend fun handleUnplugged(context: Context) {
        val percent      = readCurrentPercent(context)
        val chargeCounter = readChargeCounter(context)

        context.stopService(Intent(context, BatteryPollingService::class.java))

        repository.getLatestOpenChargeSession()?.let { session ->
            repository.closeChargeSession(
                sessionId = session.id,
                endPercent = percent,
                chargerFingerprint = session.chargerFingerprint,
                chargerLabel = session.chargerLabel
            )
        }

        repository.insertReading(readCurrentReading(context, ChargeSource.NONE))

        repository.startDischargeEvent(
            startPercent       = percent,
            startChargeCounter = chargeCounter
        )

        val request = PeriodicWorkRequestBuilder<BatteryDataWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BatteryDataWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    // ── BatteryManager helpers ────────────────────────────────────────────────

    private fun readCurrentPercent(context: Context): Int {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level  = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)  ?: -1
        val scale  = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    }

    private fun readChargeCounter(context: Context): Int? {
        val bm    = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val value = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        return value.takeIf { it > 0 }
    }

    private fun readCurrentReading(context: Context, source: ChargeSource): BatteryReading {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val rawStatus = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val chargeState = if (source == ChargeSource.NONE) {
            when (rawStatus) {
                BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeState.DISCHARGING
                else -> ChargeState.NOT_CHARGING
            }
        } else {
            when (rawStatus) {
                BatteryManager.BATTERY_STATUS_FULL -> ChargeState.FULL
                BatteryManager.BATTERY_STATUS_CHARGING -> ChargeState.CHARGING
                BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeState.DISCHARGING
                else -> ChargeState.NOT_CHARGING
            }
        }

        return BatteryReading(
            id = 0,
            timestamp = Instant.now().toEpochMilli(),
            voltageMillivolts = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            temperatureTenthsCelsius = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0,
            chargeCounterMicroAmpHours = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            currentMicroAmps = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            batteryPercent = readCurrentPercent(context),
            chargeSource = source,
            chargeState = chargeState,
            isScreenOn = false,
            sessionId = null,
            chargerVoltageMillivolts = null,
            chargerCurrentMaxMilliamps = null,
            isPdActive = null,
            chargerType = null,
            chargeProtocolLabel = null
        )
    }
}
