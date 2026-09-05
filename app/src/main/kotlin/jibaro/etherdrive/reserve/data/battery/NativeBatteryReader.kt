package jibaro.etherdrive.reserve.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The coarse health verdict the OS itself reports, distinct from our estimate. */
enum class NativeHealthStatus {
    GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, COLD, UNSPECIFIED_FAILURE
}

/**
 * Battery facts the operating system measures directly.
 *
 * Worth reading precisely because they aren't estimates: where the OS knows the
 * real cycle count, our weighted heuristic should step aside.
 *
 * Note on state of health: Android 16 shows a battery health percentage in
 * Settings on some devices, but there is no public API for it — `BatteryManager`
 * exposes no state-of-health property at compileSdk 36. Cycle count (API 34+)
 * and the coarse health enum are what third-party apps can actually read.
 */
data class NativeBatteryInfo(
    val cycleCount: Int?,
    val healthStatus: NativeHealthStatus?
) {
    val hasAnything: Boolean get() = cycleCount != null || healthStatus != null
}

@Singleton
class NativeBatteryReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun read(): NativeBatteryInfo {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return NativeBatteryInfo(null, null)

        return NativeBatteryInfo(
            cycleCount = readCycleCount(sticky),
            healthStatus = readHealth(sticky)
        )
    }

    /** EXTRA_CYCLE_COUNT arrived in API 34, and not every device populates it. */
    private fun readCycleCount(sticky: Intent): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return sticky.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it > 0 }
    }

    private fun readHealth(sticky: Intent): NativeHealthStatus? =
        when (sticky.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD                -> NativeHealthStatus.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT            -> NativeHealthStatus.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD                -> NativeHealthStatus.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE        -> NativeHealthStatus.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_COLD                -> NativeHealthStatus.COLD
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> NativeHealthStatus.UNSPECIFIED_FAILURE
            else                                              -> null
        }
}
