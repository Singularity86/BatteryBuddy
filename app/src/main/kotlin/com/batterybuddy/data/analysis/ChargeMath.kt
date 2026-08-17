package com.batterybuddy.data.analysis

/**
 * Pure charge/wear arithmetic. Kept free of Android and Room types so it can be
 * unit tested — every historical bug in this app has been a units bug in here.
 */
object ChargeMath {

    /** Nominal Li-ion cell voltage used when converting charge (µAh) to energy. */
    const val NOMINAL_CELL_MILLIVOLTS = 3700

    /**
     * Energy added over a charge session, in milliwatt-hours.
     *
     * µAh × mV = 1e-6 Ah × 1e-3 V = 1e-9 Wh = 1e-6 mWh, so the product must be
     * divided by 1_000_000 to land in mWh. Returns null when the counters are
     * missing or did not increase (nothing measurable was added).
     */
    fun energyAddedMilliWattHours(
        minCounterMicroAmpHours: Int?,
        maxCounterMicroAmpHours: Int?,
        nominalMillivolts: Int = NOMINAL_CELL_MILLIVOLTS
    ): Long? {
        if (minCounterMicroAmpHours == null || maxCounterMicroAmpHours == null) return null
        if (maxCounterMicroAmpHours <= minCounterMicroAmpHours) return null
        val deltaMicroAmpHours = (maxCounterMicroAmpHours - minCounterMicroAmpHours).toLong()
        return deltaMicroAmpHours * nominalMillivolts / 1_000_000L
    }

    /**
     * Fraction of the battery refilled by a session, 0f..1f.
     * This is charge *added*, not depth of discharge.
     */
    fun chargeFraction(startPercent: Int, endPercent: Int): Float =
        (endPercent - startPercent).coerceIn(0, 100) / 100f

    /**
     * Weighted cycle cost for one charge session.
     *
     * Quadratic in the charge fraction so partial top-ups cost proportionally
     * less than full cycles, scaled by a peak-temperature penalty. This is a
     * heuristic for relative comparison between sessions, not a calibrated
     * measure of absolute capacity loss.
     */
    fun cycleCost(chargeFraction: Float, peakTempTenthsCelsius: Int?): Float =
        chargeFraction * chargeFraction * temperatureFactor(peakTempTenthsCelsius)

    /** Multiplier applied to a session's cycle cost based on its peak temperature. */
    fun temperatureFactor(peakTempTenthsCelsius: Int?): Float {
        val tempC = (peakTempTenthsCelsius ?: DEFAULT_TEMP_TENTHS_C) / 10f
        return when {
            tempC > 45f -> 1.5f
            tempC > 38f -> 1.2f
            tempC < 10f -> 1.1f
            else        -> 1.0f
        }
    }

    /** Human-readable explanation of the temperature factor, for detail views. */
    fun temperatureFactorLabel(peakTempTenthsCelsius: Int?): String {
        val tempC = (peakTempTenthsCelsius ?: DEFAULT_TEMP_TENTHS_C) / 10f
        return when {
            tempC > 45f -> "1.5× (very hot)"
            tempC > 38f -> "1.2× (warm)"
            tempC < 10f -> "1.1× (cold)"
            else        -> "1.0× (normal)"
        }
    }

    /**
     * Relative wear band for one session. Shared by the history list and the
     * post-session notification so the same charge can't be described two ways.
     */
    fun wearBand(cycleCost: Float): WearBand = when {
        cycleCost > 0.8f -> WearBand.HIGH
        cycleCost > 0.4f -> WearBand.MEDIUM
        else             -> WearBand.LOW
    }

    /** Extra wear attributed to sitting at 100%, capped so a long hold can't dominate. */
    fun overnightPenalty(durationMinutes: Int): Float =
        (durationMinutes / 60f * 0.01f).coerceIn(0f, 0.1f)

    /** Charging/discharging power in watts from battery-side voltage and current. */
    fun batterySideWatts(voltageMillivolts: Int, currentMicroAmps: Int): Float =
        voltageMillivolts.toFloat() * kotlin.math.abs(currentMicroAmps) / 1_000_000_000f

    /** Charger-side power in watts, when the kernel exposes negotiated values. */
    fun chargerSideWatts(chargerMillivolts: Int?, chargerMilliamps: Int?): Float? {
        if (chargerMillivolts == null || chargerMilliamps == null) return null
        if (chargerMillivolts <= 0 || chargerMilliamps <= 0) return null
        return chargerMillivolts.toFloat() * chargerMilliamps / 1_000_000f
    }

    private const val DEFAULT_TEMP_TENTHS_C = 250
}

enum class WearBand(val label: String) {
    LOW("Low impact"),
    MEDIUM("Medium impact"),
    HIGH("High impact")
}
