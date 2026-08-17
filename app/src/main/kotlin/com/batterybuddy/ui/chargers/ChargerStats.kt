package com.batterybuddy.ui.chargers

data class SessionSummary(
    val id: Long,
    val startTimestamp: Long,
    val durationMinutes: Int?,
    val startPercent: Int,
    val endPercent: Int?,
    val avgWatts: Float,
    val peakTempCelsius: Float?,
    val hasAbusiveTemp: Boolean
)

data class ChargerStats(
    val fingerprint: String,
    val label: String,
    val sessionCount: Int,
    val averagePeakTempCelsius: Float,
    val averageWatts: Float,
    val abusiveSessionCount: Int,
    /**
     * 0–100, entirely a measure of how cool this charger runs — it says nothing
     * about electrical efficiency. Higher is gentler on the battery.
     */
    val coolRunningScore: Float,
    val sessions: List<SessionSummary> = emptyList()
)
