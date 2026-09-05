package jibaro.etherdrive.reserve.data.model

data class ChargeSession(
    val id: Long,
    val batteryId: Long?,
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
    /** Fraction of the battery this session refilled, 0f..1f. */
    val chargeFraction: Float?,
    val weightedCycleCost: Float?,
    val chargerFingerprint: String?,
    val chargerLabel: String?
) {
    val isOpen: Boolean get() = endTimestamp == null
    val peakTemperatureCelsius: Float? get() = peakTempTenthsCelsius?.div(10f)
    val energyAddedWattHours: Double? get() = energyAddedMilliWattHours?.div(1000.0)
}
