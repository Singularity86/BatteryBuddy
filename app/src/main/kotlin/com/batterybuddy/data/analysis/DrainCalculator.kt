package com.batterybuddy.data.analysis

import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.model.ChargeSource

/**
 * How the battery has been spent since it was last full.
 *
 * Users think in hours and in "what was I doing", not milliamps, so this splits
 * drain by screen state: screen-on drain is the cost of using the phone,
 * screen-off drain is what the phone costs you while you aren't touching it —
 * the number that actually exposes a misbehaving app.
 */
data class DrainSummary(
    /** Start of the measured window. */
    val sinceTimestamp: Long,
    /** True when the window starts at a genuine full charge rather than a cutoff. */
    val anchoredToFullCharge: Boolean,
    val screenOnMinutes: Long,
    val screenOffMinutes: Long,
    val percentUsed: Int,
    val screenOnDrainPerHour: Float?,
    val screenOffDrainPerHour: Float?,
    val overallDrainPerHour: Float?,
    val estimatedHoursRemaining: Float?
)

object DrainCalculator {

    /** A session counts as full at or above this level. */
    const val FULL_CHARGE_PERCENT = 99

    /**
     * Sampling can stop for hours under Doze. Intervals longer than this are
     * counted only up to the cap, so one long gap can't dominate the averages.
     */
    const val MAX_INTERVAL_MINUTES = 60L

    /** Below this much observed time a rate is too noisy to show. */
    const val MIN_MINUTES_FOR_RATE = 10L

    fun summarize(readings: List<BatteryReading>, currentPercent: Int): DrainSummary? {
        if (readings.size < 2) return null
        val ordered = readings.sortedBy { it.timestamp }

        // Everything after the last moment the battery was full.
        val lastFullIndex = ordered.indexOfLast { it.batteryPercent >= FULL_CHARGE_PERCENT }
        val anchored = lastFullIndex >= 0 && lastFullIndex < ordered.lastIndex
        val window = if (anchored) ordered.subList(lastFullIndex, ordered.size) else ordered
        if (window.size < 2) return null

        var screenOnMinutes = 0L
        var screenOffMinutes = 0L
        var screenOnDrop = 0
        var screenOffDrop = 0

        window.zipWithNext { earlier, later ->
            // An interval only counts as discharge if the cable was out at *both*
            // ends. Checking just the earlier reading lets the interval that spans
            // plugging in through, adding charging time to the drain denominator
            // and understating the rate.
            if (earlier.chargeSource != ChargeSource.NONE || later.chargeSource != ChargeSource.NONE) {
                return@zipWithNext
            }

            val elapsedMinutes = ((later.timestamp - earlier.timestamp) / 60_000L)
                .coerceAtMost(MAX_INTERVAL_MINUTES)
            if (elapsedMinutes <= 0L) return@zipWithNext

            val drop = (earlier.batteryPercent - later.batteryPercent).coerceAtLeast(0)
            if (earlier.isScreenOn) {
                screenOnMinutes += elapsedMinutes
                screenOnDrop += drop
            } else {
                screenOffMinutes += elapsedMinutes
                screenOffDrop += drop
            }
        }

        val totalMinutes = screenOnMinutes + screenOffMinutes
        if (totalMinutes <= 0L) return null
        val totalDrop = screenOnDrop + screenOffDrop

        val overallRate = ratePerHour(totalDrop, totalMinutes)
        return DrainSummary(
            sinceTimestamp          = window.first().timestamp,
            anchoredToFullCharge    = anchored,
            screenOnMinutes         = screenOnMinutes,
            screenOffMinutes        = screenOffMinutes,
            percentUsed             = totalDrop,
            screenOnDrainPerHour    = ratePerHour(screenOnDrop, screenOnMinutes),
            screenOffDrainPerHour   = ratePerHour(screenOffDrop, screenOffMinutes),
            overallDrainPerHour     = overallRate,
            estimatedHoursRemaining = overallRate
                ?.takeIf { it > 0f }
                ?.let { currentPercent / it }
        )
    }

    private fun ratePerHour(percentDropped: Int, minutes: Long): Float? {
        if (minutes < MIN_MINUTES_FOR_RATE) return null
        return percentDropped / (minutes / 60f)
    }
}
