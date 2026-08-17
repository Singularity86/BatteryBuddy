package com.batterybuddy.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads foreground time per app from UsageStatsManager.
 *
 * Usage access is not a normal runtime permission — it can only be granted by
 * the user in system settings, so everything here degrades to "no data" rather
 * than failing when access is absent.
 */
@Singleton
class UsageStatsReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Settings screen where the user can grant usage access. */
    fun permissionSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Total foreground milliseconds per package within a window.
     *
     * Built from raw events rather than `queryUsageStats`, whose buckets don't
     * line up with arbitrary windows and would smear usage across our samples.
     */
    fun foregroundTimeBetween(start: Long, end: Long): Map<String, Long> {
        if (!hasPermission() || end <= start) return emptyMap()
        val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val totals = mutableMapOf<String, Long>()
        val openedAt = mutableMapOf<String, Long>()

        val events = runCatching { usageStats.queryEvents(start, end) }.getOrNull() ?: return emptyMap()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            when (event.eventType) {
                // ACTIVITY_RESUMED / ACTIVITY_PAUSED share these numeric values and
                // only exist as named constants from API 29; these work from API 21.
                UsageEvents.Event.MOVE_TO_FOREGROUND -> openedAt[packageName] = event.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    openedAt.remove(packageName)?.let { openedTime ->
                        totals[packageName] = (totals[packageName] ?: 0L) + (event.timeStamp - openedTime)
                    }
                }
            }
        }

        // Anything still in the foreground when the window ended counts up to the edge.
        openedAt.forEach { (packageName, openedTime) ->
            totals[packageName] = (totals[packageName] ?: 0L) + (end - openedTime)
        }

        return totals.filterValues { it > 0L }
    }

    /** Human-readable app name, falling back to the package id. */
    fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}
