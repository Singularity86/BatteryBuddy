package com.batterybuddy.data.model

data class ChargeSession(
    val id: Long,
    val startTimestamp: Long,
    val endTimestamp: Long?,
    val startPercent: Int,
    val endPercent: Int?,
    val peakTempTenthsCelsius: Int?,
    val maxChargeCounterMicroAmpHours: Int?,
    val minChargeCounterMicroAmpHours: Int?,
    val chargeSource: ChargeSource,
    val isOvernightHold: Boolean,
    val hasAbusiveTemp: Boolean,
    val durationMinutes: Int?,
    val energyAddedMilliWattHours: Long?,
    val depthOfDischarge: Float?,
    val weightedCycleCost: Float?,
    val chargerFingerprint: String?,
    val chargerLabel: String?
) {
    val isOpen: Boolean get() = endTimestamp == null
    val peakTemperatureCelsius: Float? get() = peakTempTenthsCelsius?.div(10f)
    val energyAddedWattHours: Double? get() = energyAddedMilliWattHours?.div(1000.0)
}
