package jibaro.etherdrive.reserve.data.analysis

import jibaro.etherdrive.reserve.data.model.BatteryReading
import jibaro.etherdrive.reserve.data.model.ChargeSource
import jibaro.etherdrive.reserve.data.model.ChargeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrainCalculatorTest {

    @Test
    fun `a single reading is not enough`() {
        assertNull(DrainCalculator.summarize(listOf(reading(0, 100)), 100))
    }

    @Test
    fun `screen-off drain is measured separately from screen-on`() {
        // 60 min screen-on losing 20%, then 60 min screen-off losing 2%.
        val readings = listOf(
            reading(minutes = 0, percent = 100, screenOn = true),
            reading(minutes = 60, percent = 80, screenOn = false),
            reading(minutes = 120, percent = 78, screenOn = false)
        )

        val summary = DrainCalculator.summarize(readings, 78)!!

        assertEquals(20f, summary.screenOnDrainPerHour!!, 0.1f)
        assertEquals(2f, summary.screenOffDrainPerHour!!, 0.1f)
        assertEquals(22, summary.percentUsed)
    }

    @Test
    fun `the window starts at the last full charge`() {
        val readings = listOf(
            reading(minutes = 0, percent = 40),
            reading(minutes = 30, percent = 100),   // charged back to full here
            reading(minutes = 90, percent = 90)
        )

        val summary = DrainCalculator.summarize(readings, 90)!!

        assertTrue(summary.anchoredToFullCharge)
        // Only the 100 -> 90 leg counts; the earlier 40% is before the anchor.
        assertEquals(10, summary.percentUsed)
    }

    @Test
    fun `without a full charge the whole window is used and flagged`() {
        val readings = listOf(
            reading(minutes = 0, percent = 70),
            reading(minutes = 60, percent = 60)
        )

        val summary = DrainCalculator.summarize(readings, 60)!!

        assertFalse(summary.anchoredToFullCharge)
        assertEquals(10, summary.percentUsed)
    }

    // Charging intervals would otherwise show up as negative drain, or as huge
    // amounts of "screen off time at 0%/h" that flatten the averages.
    @Test
    fun `charging intervals are excluded`() {
        val readings = listOf(
            reading(minutes = 0, percent = 100, screenOn = false),
            reading(minutes = 60, percent = 90, screenOn = false),
            reading(minutes = 120, percent = 95, screenOn = false, source = ChargeSource.USB),
            reading(minutes = 180, percent = 99, screenOn = false, source = ChargeSource.USB)
        )

        val summary = DrainCalculator.summarize(readings, 99)!!

        // Only the first hour of genuine discharge is counted.
        assertEquals(60L, summary.screenOffMinutes)
        assertEquals(10, summary.percentUsed)
    }

    @Test
    fun `a long sampling gap is capped so it cannot dominate`() {
        // Eight hours of doze between two samples, capped to one hour.
        val readings = listOf(
            reading(minutes = 0, percent = 100, screenOn = false),
            reading(minutes = 480, percent = 92, screenOn = false)
        )

        val summary = DrainCalculator.summarize(readings, 92)!!

        assertEquals(DrainCalculator.MAX_INTERVAL_MINUTES, summary.screenOffMinutes)
    }

    @Test
    fun `rates are withheld until there is enough observed time`() {
        // Only five minutes of screen-on time: too little to state a rate.
        val readings = listOf(
            reading(minutes = 0, percent = 100, screenOn = true),
            reading(minutes = 5, percent = 99, screenOn = false),
            reading(minutes = 65, percent = 95, screenOn = false)
        )

        val summary = DrainCalculator.summarize(readings, 95)!!

        assertNull(summary.screenOnDrainPerHour)
        assertEquals(4f, summary.screenOffDrainPerHour!!, 0.1f)
    }

    @Test
    fun `remaining time follows the observed overall rate`() {
        // 10%/h overall, currently at 50% → about 5 hours left.
        val readings = listOf(
            reading(minutes = 0, percent = 100, screenOn = false),
            reading(minutes = 60, percent = 90, screenOn = false)
        )

        val summary = DrainCalculator.summarize(readings, 50)!!

        assertEquals(5f, summary.estimatedHoursRemaining!!, 0.1f)
    }

    @Test
    fun `no measurable drain means no remaining-time estimate`() {
        val readings = listOf(
            reading(minutes = 0, percent = 100, screenOn = false),
            reading(minutes = 60, percent = 100, screenOn = false)
        )

        assertNull(DrainCalculator.summarize(readings, 100)!!.estimatedHoursRemaining)
    }

    private fun reading(
        minutes: Long,
        percent: Int,
        screenOn: Boolean = false,
        source: ChargeSource = ChargeSource.NONE
    ) = BatteryReading(
        id = 0,
        timestamp = minutes * 60_000L,
        voltageMillivolts = 3900,
        temperatureTenthsCelsius = 300,
        chargeCounterMicroAmpHours = percent * 40_000,
        currentMicroAmps = -500_000,
        batteryPercent = percent,
        chargeSource = source,
        chargeState = if (source == ChargeSource.NONE) ChargeState.DISCHARGING else ChargeState.CHARGING,
        isScreenOn = screenOn,
        sessionId = null,
        chargerVoltageMillivolts = null,
        chargerCurrentMaxMilliamps = null,
        isPdActive = null,
        chargerType = null,
        chargeProtocolLabel = null
    )
}
