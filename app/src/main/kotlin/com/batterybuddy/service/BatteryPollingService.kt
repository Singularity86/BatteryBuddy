package com.batterybuddy.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.batterybuddy.data.battery.ChargerClassifier
import com.batterybuddy.data.battery.ChargerInfoReader
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.model.ChargeSource
import com.batterybuddy.data.model.ChargeState
import com.batterybuddy.data.preferences.UserPreferencesStore
import com.batterybuddy.data.repository.BatteryRepository
import com.batterybuddy.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class BatteryPollingService : Service() {

    @Inject lateinit var repository: BatteryRepository
    @Inject lateinit var prefs: UserPreferencesStore
    @Inject lateinit var chargerInfoReader: ChargerInfoReader

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    // Tracks the open charge session created when the service starts.
    private var activeSessionId: Long = -1L

    // Timestamp when the battery first hit 100% in the current session.
    private var firstReached100Ms: Long = 0L

    // Tracks whether the overnight hold alert has already fired this session.
    private var overnightHoldRecorded = false

    // Rolling window for thermal-alert deduplication — alert fires at most once per 30 min.
    private var lastThermalAlertMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        postForegroundNotification()
        if (pollingJob?.isActive != true) {
            pollingJob = scope.launch {
                runPollingLoop()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        runBlocking(Dispatchers.IO + NonCancellable) {
            closeActiveSession()
        }
        scope.cancel()
        super.onDestroy()
    }

    // ── Polling loop ──────────────────────────────────────────────────────────

    private suspend fun runPollingLoop() {
        val status = readBatteryStatus()
        val source = resolveChargeSource()

        val openSession = repository.getLatestOpenChargeSession()
            ?.takeIf { it.chargeSource != ChargeSource.NONE }
        activeSessionId = openSession?.id ?: repository.startChargeSession(status.percent, source)

        repository.updateChargeSession(
            sessionId              = activeSessionId,
            peakTempTenthsCelsius  = status.tempTenthsC,
            chargeCounterMicroAmpHours = status.chargeCounterUah,
            isOvernightHold        = false,
            hasAbusiveTemp         = false
        )

        while (scope.isActive) {
            val lastStatus = collectAndStore()
            
            val interval = if (lastStatus.percent >= 80 || lastStatus.tempTenthsC >= 350) {
                INTERVAL_FAST_MS
            } else {
                INTERVAL_NORMAL_MS
            }
            delay(interval)
        }
    }

    private suspend fun collectAndStore(): BatteryStatus {
        val batteryStatus  = readBatteryStatus()
        val chargerInfo    = chargerInfoReader.read()
        val tempThreshold  = prefs.tempAlertThresholdCelsius.first()
        val holdThreshold  = prefs.overnightHoldThresholdMinutes.first()

        val reading = BatteryReading(
            id                         = 0,
            timestamp                  = Instant.now().toEpochMilli(),
            voltageMillivolts          = batteryStatus.voltageMv,
            temperatureTenthsCelsius   = batteryStatus.tempTenthsC,
            chargeCounterMicroAmpHours = batteryStatus.chargeCounterUah,
            currentMicroAmps           = batteryStatus.currentUa,
            batteryPercent             = batteryStatus.percent,
            chargeSource               = resolveChargeSource(),
            chargeState                = batteryStatus.chargeState,
            isScreenOn                 = isScreenOn(),
            sessionId                  = activeSessionId.takeIf { it > 0 },
            chargerVoltageMillivolts   = chargerInfo.chargerVoltageMillivolts,
            chargerCurrentMaxMilliamps = chargerInfo.chargerCurrentMaxMilliamps,
            isPdActive                 = chargerInfo.isPdActive,
            chargerType                = chargerInfo.chargerType,
            chargeProtocolLabel        = chargerInfo.chargeProtocolLabel
        )

        repository.insertReading(reading)

        if (activeSessionId > 0) {
            val isAbusive = batteryStatus.tempTenthsC > tempThreshold * 10
            repository.updateChargeSession(
                sessionId              = activeSessionId,
                peakTempTenthsCelsius  = batteryStatus.tempTenthsC,
                chargeCounterMicroAmpHours = batteryStatus.chargeCounterUah,
                isOvernightHold        = overnightHoldRecorded,
                hasAbusiveTemp         = isAbusive
            )

            if (isAbusive) maybeFireThermalAlert(batteryStatus.tempTenthsC)
            checkOvernightHold(batteryStatus.percent, holdThreshold)
        }
        
        return batteryStatus
    }

    private suspend fun closeActiveSession() {
        val sessionId = activeSessionId.takeIf { it > 0 } ?: return
        activeSessionId = -1L

        val openSession = repository.getLatestOpenChargeSession()
        val batteryStatus = readBatteryStatus()
        val info = chargerInfoReader.read()
        val profile = ChargerClassifier.classify(
            info,
            batteryStatus.voltageMv,
            batteryStatus.currentUa
        )

        repository.closeChargeSession(
            sessionId          = sessionId,
            endPercent         = batteryStatus.percent,
            chargerFingerprint = profile.fingerprint,
            chargerLabel       = profile.label
        )

        openSession?.let { session ->
            fireSessionSummaryNotification(
                startPercent  = session.startPercent,
                endPercent    = batteryStatus.percent,
                startMs       = session.startTimestamp,
                peakTempTenthsC = session.peakTempTenthsCelsius,
                chargerLabel  = profile.label
            )
        }
    }

    private fun fireSessionSummaryNotification(
        startPercent: Int,
        endPercent: Int,
        startMs: Long,
        peakTempTenthsC: Int?,
        chargerLabel: String
    ) {
        val durationMin = ((System.currentTimeMillis() - startMs) / 60_000L).toInt()
        val durationStr = if (durationMin >= 60) "${durationMin / 60}h ${durationMin % 60}m"
                          else "${durationMin}m"
        val dod = (endPercent - startPercent).coerceAtLeast(0) / 100f
        val tempC = (peakTempTenthsC ?: 250) / 10f
        val tempFactor = when {
            tempC > 45f -> 1.5f
            tempC > 38f -> 1.2f
            tempC < 10f -> 1.1f
            else        -> 1.0f
        }
        val cost = dod * dod * tempFactor
        val impactLabel = when {
            cost > 0.8f -> "High Impact"
            cost > 0.4f -> "Medium Impact"
            else        -> "Low Impact"
        }

        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_REPORTS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Charge complete · $impactLabel")
            .setContentText("${startPercent}%→${endPercent}% in $durationStr · $chargerLabel")
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()
        nm.notify(NOTIFICATION_ID_SESSION_SUMMARY, notification)
    }

    // ── Overnight hold detection ──────────────────────────────────────────────

    private suspend fun checkOvernightHold(percent: Int, holdThresholdMinutes: Int) {
        if (overnightHoldRecorded) return
        if (percent >= 100) {
            if (firstReached100Ms == 0L) {
                firstReached100Ms = System.currentTimeMillis()
            }
            val elapsedMinutes = ((System.currentTimeMillis() - firstReached100Ms) / 60_000L).toInt()
            if (elapsedMinutes >= holdThresholdMinutes) {
                repository.recordOvernightHold(activeSessionId, elapsedMinutes)
                repository.updateChargeSession(
                    sessionId              = activeSessionId,
                    peakTempTenthsCelsius  = 0,
                    chargeCounterMicroAmpHours = 0,
                    isOvernightHold        = true,
                    hasAbusiveTemp         = false
                )
                overnightHoldRecorded = true
                fireOvernightHoldAlert(elapsedMinutes)
            }
        } else {
            firstReached100Ms = 0L
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun postForegroundNotification() {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_MONITORING)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("BatteryTruth")
            .setContentText("Monitoring battery while charging")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(tapIntent)
            .build()

        startForeground(NOTIFICATION_ID_FOREGROUND, notification)
    }

    private fun maybeFireThermalAlert(tempTenthsC: Int) {
        val now = System.currentTimeMillis()
        if (now - lastThermalAlertMs < THERMAL_ALERT_COOLDOWN_MS) return
        lastThermalAlertMs = now

        val tempC = tempTenthsC / 10f
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Battery temperature warning")
            .setContentText("Battery is at %.1f °C — this can reduce long-term capacity.".format(tempC))
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID_THERMAL, notification)
    }

    private fun fireOvernightHoldAlert(elapsedMinutes: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val hours = elapsedMinutes / 60
        val mins  = elapsedMinutes % 60
        val timeLabel = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Charging overnight detected")
            .setContentText("Battery has been at 100 % for $timeLabel. Unplugging extends battery life.")
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID_OVERNIGHT, notification)
    }

    // ── BatteryManager helpers ────────────────────────────────────────────────

    private data class BatteryStatus(
        val percent: Int,
        val voltageMv: Int,
        val tempTenthsC: Int,
        val chargeCounterUah: Int,
        val currentUa: Int,
        val chargeState: ChargeState
    )

    private fun readBatteryStatus(): BatteryStatus {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val rawStatus = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        val chargeState = when {
            rawStatus == BatteryManager.BATTERY_STATUS_FULL        -> ChargeState.FULL
            rawStatus == BatteryManager.BATTERY_STATUS_CHARGING    -> ChargeState.CHARGING
            rawStatus == BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeState.DISCHARGING
            else -> ChargeState.NOT_CHARGING
        }

        return BatteryStatus(
            percent        = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            voltageMv      = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            tempTenthsC    = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0,
            chargeCounterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            currentUa      = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            chargeState    = chargeState
        )
    }

    private fun resolveChargeSource(): ChargeSource {
        val sticky  = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargeSource.WIRELESS
            0                                       -> ChargeSource.NONE
            else                                    -> ChargeSource.USB
        }
    }

    private fun isScreenOn(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    companion object {
        private const val INTERVAL_FAST_MS        = 60_000L      // 1 minute
        private const val INTERVAL_NORMAL_MS      = 5 * 60_000L  // 5 minutes
        private const val THERMAL_ALERT_COOLDOWN_MS = 30 * 60_000L
        private const val CHANNEL_MONITORING        = "battery_monitoring"
        private const val CHANNEL_ALERTS            = "battery_alerts"
        private const val CHANNEL_REPORTS           = "battery_reports"
        private const val NOTIFICATION_ID_FOREGROUND     = 1001
        private const val NOTIFICATION_ID_THERMAL        = 1002
        private const val NOTIFICATION_ID_OVERNIGHT      = 1003
        private const val NOTIFICATION_ID_SESSION_SUMMARY = 1004
    }
}
