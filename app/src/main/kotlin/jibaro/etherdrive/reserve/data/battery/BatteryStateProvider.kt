package jibaro.etherdrive.reserve.data.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import jibaro.etherdrive.reserve.data.model.BatteryReading
import jibaro.etherdrive.reserve.data.model.ChargeSource
import jibaro.etherdrive.reserve.data.model.ChargeState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The battery as it is *right now*, straight from the system broadcast.
 *
 * Stored readings exist for history and are written on a 1–15 minute cadence,
 * which is far too slow to drive a screen labelled "Live". This provider is
 * display-only: nothing here is persisted.
 */
@Singleton
class BatteryStateProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chargerInfoReader: ChargerInfoReader
) {

    fun observe(): Flow<BatteryReading> = batteryIntents()
        .map { intent -> withContext(Dispatchers.IO) { toReading(intent) } }
        .conflate()

    private fun batteryIntents(): Flow<Intent> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let { trySend(it) }
            }
        }
        // registerReceiver returns the current sticky intent, so the first value
        // is available immediately rather than waiting for the next change.
        // NOT_EXPORTED is explicit rather than relying on the protected-broadcast
        // exemption to the API 34+ flag requirement.
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sticky?.let { trySend(it) }

        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    private fun toReading(intent: Intent): BatteryReading {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val chargerInfo = chargerInfoReader.read()

        val chargeState = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_FULL        -> ChargeState.FULL
            BatteryManager.BATTERY_STATUS_CHARGING    -> ChargeState.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeState.DISCHARGING
            else                                      -> ChargeState.NOT_CHARGING
        }
        val chargeSource = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargeSource.WIRELESS
            0                                       -> ChargeSource.NONE
            else                                    -> ChargeSource.USB
        }

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale
                      else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        return BatteryReading(
            id                         = 0,
            timestamp                  = Instant.now().toEpochMilli(),
            voltageMillivolts          = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            temperatureTenthsCelsius   = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0),
            chargeCounterMicroAmpHours = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            currentMicroAmps           = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            batteryPercent             = percent,
            chargeSource               = chargeSource,
            chargeState                = chargeState,
            isScreenOn                 = pm.isInteractive,
            sessionId                  = null,
            chargerVoltageMillivolts   = chargerInfo.chargerVoltageMillivolts,
            chargerCurrentMaxMilliamps = chargerInfo.chargerCurrentMaxMilliamps,
            isPdActive                 = chargerInfo.isPdActive,
            chargerType                = chargerInfo.chargerType,
            chargeProtocolLabel        = chargerInfo.chargeProtocolLabel
        )
    }
}
