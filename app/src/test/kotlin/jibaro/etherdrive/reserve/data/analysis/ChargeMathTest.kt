package jibaro.etherdrive.reserve.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargeMathTest {

    // The original bug: µAh × mV was divided by 1_000 instead of 1_000_000,
    // making every energy figure 1000× too large.
    @Test
    fun `energy for a 2000 mAh top-up is about 7_4 Wh`() {
        val minUah = 1_000_000   // 1000 mAh remaining at the start
        val maxUah = 3_000_000   // 3000 mAh remaining at the end

        val mWh = ChargeMath.energyAddedMilliWattHours(minUah, maxUah)

        // 2.0 Ah × 3.7 V = 7.4 Wh = 7400 mWh
        assertEquals(7_400L, mWh)
    }

    @Test
    fun `energy scales linearly with charge added`() {
        val single = ChargeMath.energyAddedMilliWattHours(0, 1_000_000)!!
        val double = ChargeMath.energyAddedMilliWattHours(0, 2_000_000)!!
        assertEquals(single * 2, double)
    }

    @Test
    fun `energy is null when counters are missing or did not rise`() {
        assertNull(ChargeMath.energyAddedMilliWattHours(null, 3_000_000))
        assertNull(ChargeMath.energyAddedMilliWattHours(1_000_000, null))
        assertNull(ChargeMath.energyAddedMilliWattHours(3_000_000, 3_000_000))
        assertNull(ChargeMath.energyAddedMilliWattHours(3_000_000, 1_000_000))
    }

    @Test
    fun `charge fraction is the percentage added, clamped at zero`() {
        assertEquals(0.6f, ChargeMath.chargeFraction(20, 80), EPSILON)
        assertEquals(1.0f, ChargeMath.chargeFraction(0, 100), EPSILON)
        assertEquals(0f, ChargeMath.chargeFraction(80, 20), EPSILON)
    }

    @Test
    fun `cycle cost is quadratic so partial charges cost proportionally less`() {
        val full = ChargeMath.cycleCost(1.0f, NORMAL_TEMP)
        val half = ChargeMath.cycleCost(0.5f, NORMAL_TEMP)

        assertEquals(1.0f, full, EPSILON)
        assertEquals(0.25f, half, EPSILON)
    }

    @Test
    fun `temperature factor penalises heat and mild cold`() {
        assertEquals(1.0f, ChargeMath.temperatureFactor(250), EPSILON)   // 25 °C
        assertEquals(1.2f, ChargeMath.temperatureFactor(400), EPSILON)   // 40 °C
        assertEquals(1.5f, ChargeMath.temperatureFactor(460), EPSILON)   // 46 °C
        assertEquals(1.1f, ChargeMath.temperatureFactor(50), EPSILON)    // 5 °C
    }

    @Test
    fun `temperature factor falls back to a normal reading when unknown`() {
        assertEquals(1.0f, ChargeMath.temperatureFactor(null), EPSILON)
    }

    @Test
    fun `a hot full charge costs more than a cool one`() {
        val cool = ChargeMath.cycleCost(1.0f, 250)
        val hot  = ChargeMath.cycleCost(1.0f, 460)
        assert(hot > cool)
    }

    @Test
    fun `wear bands follow the documented thresholds`() {
        assertEquals(WearBand.LOW, ChargeMath.wearBand(0.4f))
        assertEquals(WearBand.MEDIUM, ChargeMath.wearBand(0.5f))
        assertEquals(WearBand.MEDIUM, ChargeMath.wearBand(0.8f))
        assertEquals(WearBand.HIGH, ChargeMath.wearBand(0.9f))
    }

    @Test
    fun `overnight penalty accrues hourly and is capped`() {
        assertEquals(0.01f, ChargeMath.overnightPenalty(60), EPSILON)
        assertEquals(0.05f, ChargeMath.overnightPenalty(300), EPSILON)
        assertEquals(0.1f, ChargeMath.overnightPenalty(6000), EPSILON)
    }

    @Test
    fun `charger-side watts use negotiated voltage and current`() {
        // 9 V × 3 A = 27 W
        assertEquals(27f, ChargeMath.chargerSideWatts(9000, 3000)!!, EPSILON)
    }

    @Test
    fun `charger-side watts are null without usable kernel values`() {
        assertNull(ChargeMath.chargerSideWatts(null, 3000))
        assertNull(ChargeMath.chargerSideWatts(9000, null))
        assertNull(ChargeMath.chargerSideWatts(0, 3000))
    }

    @Test
    fun `battery-side watts ignore current direction`() {
        // 3.7 V × 2 A = 7.4 W, whether charging or discharging
        assertEquals(7.4f, ChargeMath.batterySideWatts(3700, 2_000_000), EPSILON)
        assertEquals(7.4f, ChargeMath.batterySideWatts(3700, -2_000_000), EPSILON)
    }

    private companion object {
        const val EPSILON = 0.0001f
        const val NORMAL_TEMP = 250
    }
}
