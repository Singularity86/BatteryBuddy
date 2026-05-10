package com.batterybuddy

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BatteryTruthApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        NotificationChannel(
            CHANNEL_MONITORING,
            "Battery Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while a charge session is being monitored"
            setShowBadge(false)
            nm.createNotificationChannel(this)
        }

        NotificationChannel(
            CHANNEL_ALERTS,
            "Battery Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High temperature or extended time at 100% alerts"
            nm.createNotificationChannel(this)
        }

        NotificationChannel(
            CHANNEL_REPORTS,
            "Battery Reports",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Monthly health reports and post-session summaries"
            nm.createNotificationChannel(this)
        }
    }

    companion object {
        const val CHANNEL_MONITORING = "battery_monitoring"
        const val CHANNEL_ALERTS     = "battery_alerts"
        const val CHANNEL_REPORTS    = "battery_reports"
    }
}
