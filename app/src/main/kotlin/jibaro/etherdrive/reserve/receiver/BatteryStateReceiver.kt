package jibaro.etherdrive.reserve.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import jibaro.etherdrive.reserve.data.analysis.SessionRanker
import jibaro.etherdrive.reserve.data.model.BatteryReading
import jibaro.etherdrive.reserve.data.model.ChargeSource
import jibaro.etherdrive.reserve.data.model.ChargeState
import jibaro.etherdrive.reserve.data.preferences.UserPreferencesStore
import jibaro.etherdrive.reserve.data.repository.BatteryRepository
import jibaro.etherdrive.reserve.notification.BatteryNotifications
import jibaro.etherdrive.reserve.service.BatteryPollingService
import jibaro.etherdrive.reserve.worker.BatterySchedule
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Owns the charge/discharge boundary.
 *
 * Plug and unplug are the only moments a session genuinely starts or ends, so
 * this receiver — not the service — closes charge sessions and fires the summary
 * notification. That single ownership is what makes the close idempotent and
 * stops duplicate notifications when the service is torn down concurrently.
 */
@AndroidEntryPoint
class BatteryStateReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: BatteryRepository
    @Inject lateinit var prefs: UserPreferencesStore
    @Inject lateinit var notifications: BatteryNotifications

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
        BatterySchedule.cancelPeriodic(context)

        // A discharge event runs from unplug to plug-in. Close it here or it stays
        // open forever and overlaps every subsequent charge session in History.
        repository.closeOpenDischargeEvents(
            endPercent       = readCurrentPercent(context),
            endChargeCounter = readChargeCounter(context)
        )

        ContextCompat.startForegroundService(
            context,
            Intent(context, BatteryPollingService::class.java)
        )
    }

    private suspend fun handleUnplugged(context: Context) {
        val percent       = readCurrentPercent(context)
        val chargeCounter = readChargeCounter(context)

        context.stopService(Intent(context, BatteryPollingService::class.java))

        // Whichever sessions this call actually closes are the ones we report on.
        repository.closeOpenChargeSessions(percent)
            .firstOrNull()
            ?.let { closed ->
                val recent = repository.getCompletedSessionsSince(
                    System.currentTimeMillis() - RANKING_WINDOW_MS
                )
                val standing = SessionRanker.rankByTemperature(closed, recent)
                notifications.showSessionSummary(closed, standing?.describe())
            }

        repository.insertReading(readCurrentReading(context))
        repository.startDischargeEvent(
            startPercent       = percent,
            startChargeCounter = chargeCounter
        )

        BatterySchedule.enqueuePeriodic(context, prefs.backgroundPollingIntervalMinutes.first())
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

    /** A single reading marking the moment the cable came out. */
    private fun readCurrentReading(context: Context): BatteryReading {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val chargeState = when (sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1) {
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeState.DISCHARGING
            else                                      -> ChargeState.NOT_CHARGING
        }

        return BatteryReading(
            id = 0,
            timestamp = Instant.now().toEpochMilli(),
            voltageMillivolts = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            temperatureTenthsCelsius = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0,
            chargeCounterMicroAmpHours = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            currentMicroAmps = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            batteryPercent = readCurrentPercent(context),
            chargeSource = ChargeSource.NONE,
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

    private companion object {
        /** Charges are compared against the last month of history. */
        const val RANKING_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    }
}
