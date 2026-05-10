package com.batterybuddy.data.model

data class OvernightHoldEvent(
    val id: Long,
    val sessionId: Long,
    val detectedTimestamp: Long,
    val durationMinutes: Int,
    val estimatedCycleCostPenalty: Float
)
