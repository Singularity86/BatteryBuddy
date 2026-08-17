package com.batterybuddy.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapacityProjectorTest {

    @Test
    fun `too few observations means no projection`() {
        val observations = (0 until 4).map { observation(day = it * 10, capacity = 4500 - it * 10) }
        assertNull(CapacityProjector.project(observations, RATED))
    }

    // A trend drawn from readings taken over a few days is noise, however many
    // of them there are.
    @Test
    fun `observations crammed into a short span are refused`() {
        val observations = (0 until 10).map { observation(day = it, capacity = 4500 - it * 20) }
        assertNull(CapacityProjector.project(observations, RATED))
    }

    @Test
    fun `a steady decline projects a replacement horizon`() {
        // 4500 mAh losing 30 mAh a month over four months.
        val observations = (0 until 8).map {
            observation(day = it * 15, capacity = 4500 - (it * 15) )
        }

        val projection = CapacityProjector.project(observations, RATED)!!

        assertTrue("expected a declining trend", projection.changeMahPerMonth < 0f)
        assertEquals(4000, projection.thresholdMah)
        assertTrue("expected a horizon", projection.monthsUntilThreshold != null)
    }

    @Test
    fun `the horizon lands where the trend crosses the threshold`() {
        // Exactly 100 mAh lost per 30 days, starting at 4500, threshold 4000.
        val observations = (0 until 6).map { observation(day = it * 30, capacity = 4500 - it * 100) }

        val projection = CapacityProjector.project(observations, RATED)!!

        // Reported per calendar month (30.4 days), so 100 per 30 days is 101.3.
        assertEquals(-101.3f, projection.changeMahPerMonth, 0.5f)
        // Latest fitted value is 4000 already, so the horizon is essentially now.
        assertEquals(4000, projection.currentCapacityMah)
    }

    @Test
    fun `stable capacity produces no replacement horizon`() {
        val observations = (0 until 8).map { observation(day = it * 15, capacity = 4500) }

        val projection = CapacityProjector.project(observations, RATED)!!

        assertNull(projection.monthsUntilThreshold)
    }

    @Test
    fun `improving readings do not project a horizon`() {
        // Measurement noise can trend upward; that must not become a forecast.
        val observations = (0 until 8).map { observation(day = it * 15, capacity = 4300 + it * 10) }

        assertNull(CapacityProjector.project(observations, RATED)!!.monthsUntilThreshold)
    }

    @Test
    fun `an unknown rated capacity produces nothing`() {
        val observations = (0 until 8).map { observation(day = it * 15, capacity = 4500 - it * 15) }
        assertNull(CapacityProjector.project(observations, ratedMah = 0))
    }

    @Test
    fun `span and count are reported back for the UI to qualify the estimate`() {
        val observations = (0 until 6).map { observation(day = it * 20, capacity = 4500 - it * 20) }

        val projection = CapacityProjector.project(observations, RATED)!!

        assertEquals(6, projection.observationCount)
        assertEquals(100, projection.spanDays)
    }

    private fun observation(day: Int, capacity: Int) =
        CapacityObservation(timestamp = day * 86_400_000L, capacityMah = capacity)

    private companion object {
        const val RATED = 5000
    }
}
