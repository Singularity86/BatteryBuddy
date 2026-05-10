package com.batterybuddy.data.model

data class HealthSummary(
    val currentCapacityMah: Int,
    val ratedMah: Int,
    val healthPercent: Float,
    val verdict: HealthVerdict,
    val daysSinceFirstSession: Int,
    val sessionCount: Int
)

enum class HealthVerdict { HEALTHY, WATCH_IT, PLAN_REPLACEMENT, REPLACE_NOW }

enum class ExplanationDepth { SURFACE, EXPLAINED, DEEP_DIVE }
