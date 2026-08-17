package com.batterybuddy.data.analysis

import com.batterybuddy.data.model.ChargeSession
import com.batterybuddy.data.model.ChargeSource
import com.batterybuddy.data.model.HealthVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthCalculatorTest {

    @Test
    fun `no sessions means no estimate`() {
        assertNull(HealthCalculator.summarize(emptyList(), RATED_MAH))
    }

    @Test
    fun `an open session alone is not enough`() {
        val session = session(endPercent = null, endTimestamp = null, maxCounterUah = 4_000_000)
        assertNull(HealthCalculator.summarize(listOf(session), RATED_MAH))
    }

    // The core correctness rule: charge_counter reports charge *remaining*, so it
    // only equals capacity at the top of a charge. Partial charges must not be
    // treated as capacity evidence or every 80%-charger looks like a dying battery.
    @Test
    fun `partial charges are ignored as capacity evidence`() {
        val partial = session(endPercent = 80, maxCounterUah = 3_600_000)
        assertNull(HealthCalculator.summarize(listOf(partial), RATED_MAH))
    }

    @Test
    fun `a full charge yields capacity and health`() {
        val full = session(endPercent = 100, maxCounterUah = 4_000_000)

        val summary = HealthCalculator.summarize(listOf(full), RATED_MAH)!!

        assertEquals(4_000, summary.currentCapacityMah)
        assertEquals(80f, summary.healthPercent, 0.01f)
        assertEquals(1, summary.fullChargeCount)
    }

    @Test
    fun `a partial charge cannot drag down a full charge reading`() {
        val full = session(endPercent = 100, maxCounterUah = 4_500_000)
        val partial = session(endPercent = 50, maxCounterUah = 2_200_000)

        val summary = HealthCalculator.summarize(listOf(full, partial), RATED_MAH)!!

        assertEquals(4_500, summary.currentCapacityMah)
        assertEquals(1, summary.fullChargeCount)
        assertEquals(2, summary.sessionCount)
    }

    @Test
    fun `the best full charge observed wins`() {
        val worse = session(endPercent = 100, maxCounterUah = 4_100_000)
        val better = session(endPercent = 100, maxCounterUah = 4_400_000)

        val summary = HealthCalculator.summarize(listOf(worse, better), RATED_MAH)!!

        assertEquals(4_400, summary.currentCapacityMah)
        assertEquals(2, summary.fullChargeCount)
    }

    @Test
    fun `a charge to 99 percent still counts as full`() {
        val session = session(endPercent = 99, maxCounterUah = 4_500_000)
        assertEquals(4_500, HealthCalculator.summarize(listOf(session), RATED_MAH)!!.currentCapacityMah)
    }

    @Test
    fun `verdicts follow the documented capacity bands`() {
        assertEquals(HealthVerdict.HEALTHY, HealthCalculator.verdictFor(85f))
        assertEquals(HealthVerdict.HEALTHY, HealthCalculator.verdictFor(80f))
        assertEquals(HealthVerdict.WATCH_IT, HealthCalculator.verdictFor(75f))
        assertEquals(HealthVerdict.PLAN_REPLACEMENT, HealthCalculator.verdictFor(65f))
        assertEquals(HealthVerdict.REPLACE_NOW, HealthCalculator.verdictFor(50f))
    }

    @Test
    fun `an unknown rated capacity produces no estimate`() {
        val full = session(endPercent = 100, maxCounterUah = 4_000_000)
        assertNull(HealthCalculator.summarize(listOf(full), ratedMah = 0))
    }

    @Test
    fun `lifetime wear sums only closed sessions`() {
        val closed = session(endPercent = 100, cycleCost = 0.5f)
        val alsoClosed = session(endPercent = 100, cycleCost = 0.25f)
        val open = session(endPercent = null, endTimestamp = null, cycleCost = 9f)

        assertEquals(0.75f, HealthCalculator.lifetimeWear(listOf(closed, alsoClosed, open)), 0.0001f)
    }

    private fun session(
        endPercent: Int?,
        endTimestamp: Long? = 1_000L,
        maxCounterUah: Int? = null,
        cycleCost: Float? = null
    ) = ChargeSession(
        id = 0,
        batteryId = 1,
        startTimestamp = 0,
        endTimestamp = endTimestamp,
        startPercent = 10,
        endPercent = endPercent,
        peakTempTenthsCelsius = 250,
        maxChargeCounterMicroAmpHours = maxCounterUah,
        minChargeCounterMicroAmpHours = 500_000,
        chargeSource = ChargeSource.USB,
        isOvernightHold = false,
        hasAbusiveTemp = false,
        durationMinutes = 60,
        energyAddedMilliWattHours = null,
        chargeFraction = null,
        weightedCycleCost = cycleCost,
        chargerFingerprint = null,
        chargerLabel = null
    )

    private companion object {
        const val RATED_MAH = 5000
    }
}
