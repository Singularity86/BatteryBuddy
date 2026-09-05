package jibaro.etherdrive.reserve.data.model

data class DischargeEvent(
    val id: Long,
    val batteryId: Long?,
    val startTimestamp: Long,
    val endTimestamp: Long?,
    val startPercent: Int,
    val endPercent: Int?,
    val startChargeCounterMicroAmpHours: Int?,
    val endChargeCounterMicroAmpHours: Int?,
    val durationMinutes: Int?,
    val averageCurrentMicroAmps: Int?,
    val hasAnomalousBackground: Boolean
) {
    val isOpen: Boolean get() = endTimestamp == null
    val percentDepleted: Int? get() = endPercent?.let { startPercent - it }

    val depletionRateMahPerHour: Double?
        get() {
            val start = startChargeCounterMicroAmpHours ?: return null
            val end = endChargeCounterMicroAmpHours ?: return null
            val minutes = durationMinutes?.takeIf { it > 0 } ?: return null
            return (start - end) / 1000.0 / (minutes / 60.0)
        }
}
