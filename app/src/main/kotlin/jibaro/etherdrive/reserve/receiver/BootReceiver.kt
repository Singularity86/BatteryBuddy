package jibaro.etherdrive.reserve.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import jibaro.etherdrive.reserve.data.preferences.UserPreferencesStore
import jibaro.etherdrive.reserve.service.BatteryPollingService
import jibaro.etherdrive.reserve.worker.BatterySchedule
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: UserPreferencesStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val sticky    = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val rawStatus = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = rawStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                         rawStatus == BatteryManager.BATTERY_STATUS_FULL

        if (isCharging) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BatteryPollingService::class.java)
            )
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                BatterySchedule.enqueuePeriodic(context, prefs.backgroundPollingIntervalMinutes.first())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
