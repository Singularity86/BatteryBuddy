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
import com.batterybuddy.data.model.ChargeSource
import com.batterybuddy.data.repository.BatteryRepository
import com.batterybuddy.service.BatteryPollingService
import com.batterybuddy.worker.BatteryDataWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
        val percent = readCurrentPercent(context)
        val source  = resolveChargeSource(context)

        repository.startChargeSession(percent, source)

        WorkManager.getInstance(context).cancelUniqueWork(BatteryDataWorker.WORK_NAME)

        val serviceIntent = Intent(context, BatteryPollingService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private suspend fun handleUnplugged(context: Context) {
        val percent      = readCurrentPercent(context)
        val chargeCounter = readChargeCounter(context)

        context.stopService(Intent(context, BatteryPollingService::class.java))

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

    private fun resolveChargeSource(context: Context): ChargeSource {
        val sticky  = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargeSource.WIRELESS
            0                                       -> ChargeSource.NONE
            else                                    -> ChargeSource.USB
        }
    }
}
