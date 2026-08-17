package com.batterybuddy.data.model

/**
 * How strongly an app's foreground use lines up with measured battery drain.
 *
 * This is deliberately *not* a per-app milliamp figure. Android does not expose
 * per-app power draw to normal apps (that needs BATTERY_STATS, which is not
 * grantable), so any mAh number attributed to a single app would be invented.
 * What we can honestly say is how often an app was in the foreground during the
 * windows where drain was measured.
 */
data class AppCorrelation(
    val packageName: String,
    /** Sampling windows in which this app had foreground time. */
    val windowsPresent: Int,
    /** Total sampling windows considered. */
    val windowsTotal: Int,
    val totalForegroundMillis: Long
) {
    /** 0f..1f share of measured windows featuring this app. */
    val presence: Float
        get() = if (windowsTotal <= 0) 0f else windowsPresent.toFloat() / windowsTotal
}
