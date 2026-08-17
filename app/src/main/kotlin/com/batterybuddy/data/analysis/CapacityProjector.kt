package com.batterybuddy.data.analysis

import com.batterybuddy.data.model.ChargeSession

/** One measured full-charge capacity, and when it was taken. */
data class CapacityObservation(val timestamp: Long, val capacityMah: Int)

data class CapacityProjection(
    val observationCount: Int,
    val spanDays: Int,
    val currentCapacityMah: Int,
    /** Negative when capacity is declining. */
    val changeMahPerMonth: Float,
    /** Months until capacity reaches the replacement threshold, when it is declining. */
    val monthsUntilThreshold: Float?,
    val thresholdMah: Int
)

/**
 * Projects when the battery will reach the point people usually replace at.
 *
 * This is the question a health tracker exists to answer — "should I replace
 * this?" — but it is only honest with enough measurements over enough time. A
 * trend drawn from three readings a week apart is noise dressed as a forecast,
 * so this refuses to project until it has real spread.
 */
object CapacityProjector {

    /** Conventional end-of-life point: 80% of the battery's rated capacity. */
    const val REPLACEMENT_THRESHOLD_FRACTION = 0.8f

    const val MIN_OBSERVATIONS = 5
    const val MIN_SPAN_DAYS = 21

    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val DAYS_PER_MONTH = 30.4f

    /** Capacity evidence from sessions that actually reached full. */
    fun observationsFrom(sessions: List<ChargeSession>): List<CapacityObservation> =
        sessions
            .filter { !it.isOpen && (it.endPercent ?: 0) >= HealthCalculator.FULL_CHARGE_PERCENT }
            .mapNotNull { session ->
                session.maxChargeCounterMicroAmpHours?.let {
                    CapacityObservation(session.endTimestamp ?: session.startTimestamp, it / 1000)
                }
            }
            .sortedBy { it.timestamp }

    fun project(observations: List<CapacityObservation>, ratedMah: Int): CapacityProjection? {
        if (ratedMah <= 0) return null
        val ordered = observations.sortedBy { it.timestamp }
        if (ordered.size < MIN_OBSERVATIONS) return null

        val firstTimestamp = ordered.first().timestamp
        val spanDays = ((ordered.last().timestamp - firstTimestamp) / MILLIS_PER_DAY).toInt()
        if (spanDays < MIN_SPAN_DAYS) return null

        val days = ordered.map { (it.timestamp - firstTimestamp) / MILLIS_PER_DAY }
        val capacities = ordered.map { it.capacityMah.toDouble() }

        val meanDay = days.average()
        val meanCapacity = capacities.average()
        var covariance = 0.0
        var variance = 0.0
        for (i in ordered.indices) {
            val dx = days[i] - meanDay
            covariance += dx * (capacities[i] - meanCapacity)
            variance += dx * dx
        }
        if (variance == 0.0) return null

        val slopePerDay = covariance / variance
        val intercept = meanCapacity - slopePerDay * meanDay
        val thresholdMah = (ratedMah * REPLACEMENT_THRESHOLD_FRACTION).toInt()

        // Fitted value today, which is steadier than the single latest reading.
        val latestDay = days.last()
        val fittedNow = intercept + slopePerDay * latestDay

        val monthsUntilThreshold = if (slopePerDay < 0) {
            val dayAtThreshold = (thresholdMah - intercept) / slopePerDay
            ((dayAtThreshold - latestDay) / DAYS_PER_MONTH).toFloat().takeIf { it > 0f }
        } else {
            null
        }

        return CapacityProjection(
            observationCount     = ordered.size,
            spanDays             = spanDays,
            currentCapacityMah   = fittedNow.toInt(),
            changeMahPerMonth    = (slopePerDay * DAYS_PER_MONTH).toFloat(),
            monthsUntilThreshold = monthsUntilThreshold,
            thresholdMah         = thresholdMah
        )
    }
}
