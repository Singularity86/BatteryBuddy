package com.batterybuddy.data.battery

import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.model.ChargeSource
import com.batterybuddy.data.model.ChargeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargerClassifierTest {

    @Test
    fun `kernel-identified chargers get a stable structured fingerprint`() {
        val info = info(chargerType = "USB_PD", voltageMv = 9000, currentMaxMa = 2777, protocol = "Super Fast")

        val profile = ChargerClassifier.classify(info, BATTERY_MV, CURRENT_UA)

        assertEquals("USB_PD|9000|2777|Super Fast", profile.fingerprint)
    }

    @Test
    fun `the same charger classified twice produces the same fingerprint`() {
        val info = info(chargerType = "USB_PD", voltageMv = 9000, currentMaxMa = 2777)

        val first = ChargerClassifier.classify(info, BATTERY_MV, CURRENT_UA)
        val second = ChargerClassifier.classify(info, BATTERY_MV, CURRENT_UA)

        assertEquals(first.fingerprint, second.fingerprint)
    }

    // Sessions and live readings used to derive fingerprints separately, so one
    // physical charger showed up as two entries on the Chargers screen.
    @Test
    fun `a reading and its raw charger info agree on identity`() {
        val info = info(chargerType = "USB_PD", voltageMv = 9000, currentMaxMa = 2777, protocol = "Super Fast")
        val reading = reading(info)

        val fromInfo = ChargerClassifier.classify(info, reading.voltageMillivolts, reading.currentMicroAmps)
        val fromReading = ChargerClassifier.classify(reading)

        assertEquals(fromInfo.fingerprint, fromReading.fingerprint)
        assertEquals(fromInfo.label, fromReading.label)
    }

    @Test
    fun `chargers with no kernel identity fall back to power buckets`() {
        val unknown = info(chargerType = null, voltageMv = null, currentMaxMa = null)

        // 3.7 V × 6 A ≈ 22 W — battery-side estimate, no charger data at all.
        val high = ChargerClassifier.classify(unknown, 3700, 6_000_000)
        val low = ChargerClassifier.classify(unknown, 3700, 1_000_000)

        assertEquals("VIRTUAL|HighPower", high.fingerprint)
        assertEquals("VIRTUAL|LowPower", low.fingerprint)
    }

    @Test
    fun `voltage and current absent from the kernel are recorded as AUTO`() {
        val partial = info(chargerType = "USB_DCP", voltageMv = null, currentMaxMa = null)

        val profile = ChargerClassifier.classify(partial, BATTERY_MV, CURRENT_UA)

        assertEquals("USB_DCP|AUTO|AUTO|NONE", profile.fingerprint)
    }

    @Test
    fun `the protocol label leads the human-readable name when present`() {
        val info = info(chargerType = "USB_PD", voltageMv = 9000, currentMaxMa = 3000, protocol = "Super Fast")

        val profile = ChargerClassifier.classify(info, BATTERY_MV, CURRENT_UA)

        assertTrue(profile.label.startsWith("Super Fast"))
        assertTrue(profile.label.contains("27W"))
    }

    @Test
    fun `wattage prefers negotiated charger values over battery-side estimates`() {
        val info = info(chargerType = "USB_PD", voltageMv = 9000, currentMaxMa = 3000)

        // Battery side would suggest ~3.7 W; the charger reports 27 W.
        val watts = ChargerClassifier.computeWatts(info, 3700, 1_000_000)

        assertEquals(27f, watts, 0.01f)
    }

    private fun info(
        chargerType: String? = null,
        voltageMv: Int? = null,
        currentMaxMa: Int? = null,
        protocol: String? = null
    ) = ChargerInfo(
        chargerVoltageMillivolts = voltageMv,
        chargerCurrentMaxMilliamps = currentMaxMa,
        isPdActive = null,
        chargerType = chargerType,
        chargeProtocolLabel = protocol
    )

    private fun reading(info: ChargerInfo) = BatteryReading(
        id = 0,
        timestamp = 0,
        voltageMillivolts = BATTERY_MV,
        temperatureTenthsCelsius = 300,
        chargeCounterMicroAmpHours = 3_000_000,
        currentMicroAmps = CURRENT_UA,
        batteryPercent = 50,
        chargeSource = ChargeSource.USB,
        chargeState = ChargeState.CHARGING,
        isScreenOn = false,
        sessionId = null,
        chargerVoltageMillivolts = info.chargerVoltageMillivolts,
        chargerCurrentMaxMilliamps = info.chargerCurrentMaxMilliamps,
        isPdActive = info.isPdActive,
        chargerType = info.chargerType,
        chargeProtocolLabel = info.chargeProtocolLabel
    )

    private companion object {
        const val BATTERY_MV = 3900
        const val CURRENT_UA = 2_000_000
    }
}
