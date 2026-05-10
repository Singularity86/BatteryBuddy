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
import com.batterybuddy.service.BatteryPollingService
import com.batterybuddy.worker.BatteryDataWorker
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val sticky    = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val rawStatus = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        val isCharging = rawStatus == BatteryManager.BATTERY_STATUS_CHARGING
                      || rawStatus == BatteryManager.BATTERY_STATUS_FULL

        if (isCharging) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BatteryPollingService::class.java)
            )
        } else {
            val request = PeriodicWorkRequestBuilder<BatteryDataWorker>(15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                BatteryDataWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
