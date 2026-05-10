package com.batterybuddy.data.model

data class AppCorrelation(
    val packageName: String,
    val correlationScore: Float,       // fraction of high-drain windows where this app was foreground
    val sampleCount: Int,
    val totalForegroundMillis: Long
)
