package com.batterybuddy.data.model

import com.batterybuddy.data.analysis.ChargeMath

data class BatteryReading(
    val id: Long,
    val timestamp: Long,
    val voltageMillivolts: Int,
    val temperatureTenthsCelsius: Int,
    val chargeCounterMicroAmpHours: Int,
    val currentMicroAmps: Int,
    val batteryPercent: Int,
    val chargeSource: ChargeSource,
    val chargeState: ChargeState,
    val isScreenOn: Boolean,
    val sessionId: Long?,
    // sysfs fields — all nullable
    val chargerVoltageMillivolts: Int?,
    val chargerCurrentMaxMilliamps: Int?,
    val isPdActive: Boolean?,
    val chargerType: String?,
    val chargeProtocolLabel: String?
) {
    val temperatureCelsius: Float
        get() = temperatureTenthsCelsius / 10f

    // Prefer charger-side calculation when sysfs data available; fall back to battery-side approximation
    val chargingPowerWatts: Float
        get() = ChargeMath.chargerSideWatts(chargerVoltageMillivolts, chargerCurrentMaxMilliamps)
            ?: ChargeMath.batterySideWatts(voltageMillivolts, currentMicroAmps)
}
