package jibaro.etherdrive.reserve.data.model

data class HealthSummary(
    val currentCapacityMah: Int,
    val ratedMah: Int,
    val healthPercent: Float,
    val verdict: HealthVerdict,
    val daysSinceFirstSession: Int,
    val sessionCount: Int,
    /** How many completed sessions actually reached a full charge — the only ones
     *  that carry capacity information. Estimates firm up as this grows. */
    val fullChargeCount: Int
)

enum class HealthVerdict { HEALTHY, WATCH_IT, PLAN_REPLACEMENT, REPLACE_NOW }
