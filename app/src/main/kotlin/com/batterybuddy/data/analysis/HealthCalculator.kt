package com.batterybuddy.data.analysis

import com.batterybuddy.data.model.ChargeSession
import com.batterybuddy.data.model.HealthSummary
import com.batterybuddy.data.model.HealthVerdict

/**
 * Derives a capacity/health estimate from completed charge sessions.
 *
 * The fuel gauge's charge counter reports charge *remaining*, so it only equals
 * full capacity at the top of a charge. Sessions that stopped short of full are
 * therefore useless as capacity evidence — counting them would make every user
 * who charges to 80% look like they have a failing battery.
 */
object HealthCalculator {

    /** A session must reach at least this percentage to be treated as a full charge. */
    const val FULL_CHARGE_PERCENT = 99

    fun summarize(sessions: List<ChargeSession>, ratedMah: Int): HealthSummary? {
        if (ratedMah <= 0) return null
        val completed = sessions.filter { !it.isOpen }
        if (completed.isEmpty()) return null

        val fullCharges = completed.filter { (it.endPercent ?: 0) >= FULL_CHARGE_PERCENT }
        val observedCapacityUah = fullCharges
            .mapNotNull { it.maxChargeCounterMicroAmpHours }
            .maxOrNull()
            ?: return null

        val currentCapacityMah = observedCapacityUah / 1000
        val healthPercent = (currentCapacityMah.toFloat() / ratedMah) * 100f
        val firstStart = completed.minOf { it.startTimestamp }
        val daysSince = ((System.currentTimeMillis() - firstStart) / 86_400_000L).toInt()

        return HealthSummary(
            currentCapacityMah    = currentCapacityMah,
            ratedMah              = ratedMah,
            healthPercent         = healthPercent,
            verdict               = verdictFor(healthPercent),
            daysSinceFirstSession = daysSince,
            sessionCount          = completed.size,
            fullChargeCount       = fullCharges.size
        )
    }

    fun verdictFor(healthPercent: Float): HealthVerdict = when {
        healthPercent >= 80f -> HealthVerdict.HEALTHY
        healthPercent >= 70f -> HealthVerdict.WATCH_IT
        healthPercent >= 60f -> HealthVerdict.PLAN_REPLACEMENT
        else                 -> HealthVerdict.REPLACE_NOW
    }

    /** Summed weighted cycle cost across completed sessions. */
    fun lifetimeWear(sessions: List<ChargeSession>): Float =
        sessions.filter { !it.isOpen }.mapNotNull { it.weightedCycleCost }.sum()
}
