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
    val efficiencyScore: Float, // 0 to 100
    val sessions: List<SessionSummary> = emptyList()
)
