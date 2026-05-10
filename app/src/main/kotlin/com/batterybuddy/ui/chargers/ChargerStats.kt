package com.batterybuddy.ui.chargers

data class ChargerStats(
    val fingerprint: String,
    val label: String,
    val sessionCount: Int,
    val averagePeakTempCelsius: Float,
    val averageWatts: Float,
    val abusiveSessionCount: Int,
    val efficiencyScore: Float // 0 to 100
)
