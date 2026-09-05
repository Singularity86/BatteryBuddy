package jibaro.etherdrive.reserve.data.analysis

import jibaro.etherdrive.reserve.data.model.ChargeSession
import jibaro.etherdrive.reserve.data.model.ChargeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRankerTest {

    @Test
    fun `no ranking without enough history`() {
        val target = session(id = 1, tempTenths = 400)
        val recent = listOf(target, session(id = 2, tempTenths = 300))

        assertNull(SessionRanker.rankByTemperature(target, recent))
    }

    @Test
    fun `a session with no temperature cannot be ranked`() {
        val target = session(id = 1, tempTenths = null)
        val recent = (2..7).map { session(id = it.toLong(), tempTenths = 300) }

        assertNull(SessionRanker.rankByTemperature(target, recent))
    }

    @Test
    fun `the hottest charge ranks first`() {
        val target = session(id = 1, tempTenths = 450)
        val recent = listOf(target) + (2..7).map { session(id = it.toLong(), tempTenths = 300) }

        val standing = SessionRanker.rankByTemperature(target, recent)!!

        assertEquals(1, standing.temperatureRank)
        assertTrue(standing.isNotable)
        assertEquals("Your hottest charge of the last 7", standing.describe())
    }

    // The session being ranked is usually already in the recent list; counting it
    // twice would inflate the comparison size and shift every rank.
    @Test
    fun `the ranked session is counted exactly once`() {
        val target = session(id = 1, tempTenths = 450)
        val recent = listOf(target) + (2..6).map { session(id = it.toLong(), tempTenths = 300) }

        val standing = SessionRanker.rankByTemperature(target, recent)!!

        assertEquals(6, standing.comparedAgainst)
    }

    @Test
    fun `ranking works when the session is absent from the recent list`() {
        val target = session(id = 99, tempTenths = 450)
        val recent = (1..6).map { session(id = it.toLong(), tempTenths = 300) }

        val standing = SessionRanker.rankByTemperature(target, recent)!!

        assertEquals(1, standing.temperatureRank)
        assertEquals(7, standing.comparedAgainst)
    }

    @Test
    fun `an ordinary charge says nothing at all`() {
        val target = session(id = 1, tempTenths = 250)
        val recent = listOf(target) + (2..8).map { session(id = it.toLong(), tempTenths = 400) }

        val standing = SessionRanker.rankByTemperature(target, recent)!!

        assertFalse(standing.isNotable)
        assertNull(standing.describe())
    }

    @Test
    fun `third hottest is still worth mentioning`() {
        val target = session(id = 1, tempTenths = 380)
        val recent = listOf(target) +
            listOf(session(id = 2, tempTenths = 450), session(id = 3, tempTenths = 400)) +
            (4..8).map { session(id = it.toLong(), tempTenths = 250) }

        val standing = SessionRanker.rankByTemperature(target, recent)!!

        assertEquals(3, standing.temperatureRank)
        assertEquals("Your 3rd-hottest charge of the last 8", standing.describe())
    }

    @Test
    fun `open sessions are not comparable`() {
        val target = session(id = 1, tempTenths = 450)
        val recent = listOf(target) + (2..7).map { session(id = it.toLong(), tempTenths = 300, open = true) }

        assertNull(SessionRanker.rankByTemperature(target, recent))
    }

    private fun session(id: Long, tempTenths: Int?, open: Boolean = false) = ChargeSession(
        id = id,
        batteryId = 1,
        startTimestamp = id * 1000,
        endTimestamp = if (open) null else id * 1000 + 500,
        startPercent = 20,
        endPercent = if (open) null else 100,
        peakTempTenthsCelsius = tempTenths,
        maxChargeCounterMicroAmpHours = 4_000_000,
        minChargeCounterMicroAmpHours = 800_000,
        chargeSource = ChargeSource.USB,
        isOvernightHold = false,
        hasAbusiveTemp = false,
        durationMinutes = 60,
        energyAddedMilliWattHours = null,
        chargeFraction = null,
        weightedCycleCost = null,
        chargerFingerprint = null,
        chargerLabel = null
    )
}
