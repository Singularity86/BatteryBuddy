package com.batterybuddy.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Single place that schedules background battery sampling.
 *
 * Both receivers and the settings screen go through here so the user's chosen
 * interval is honoured everywhere — it used to be hardcoded at 15 minutes in two
 * separate call sites while the setting silently did nothing.
 */
object BatterySchedule {

    const val PERIODIC_WORK_NAME = "battery_discharge_polling"
    const val ONE_SHOT_WORK_NAME = "battery_live_refresh"

    /** WorkManager's floor for periodic work; anything lower is silently raised. */
    const val MIN_INTERVAL_MINUTES = 15
    const val MAX_INTERVAL_MINUTES = 120

    /**
     * (Re)schedules periodic sampling. Uses UPDATE so changing the interval takes
     * effect on the existing chain instead of being ignored until the next unplug.
     */
    fun enqueuePeriodic(context: Context, intervalMinutes: Int) {
        val minutes = intervalMinutes
            .coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
            .toLong()
        val request = PeriodicWorkRequestBuilder<BatteryDataWorker>(minutes, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    /** Immediate one-off sample, used to populate the UI as soon as the app opens. */
    fun requestImmediateSample(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_SHOT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BatteryDataWorker>().build()
        )
    }
}
