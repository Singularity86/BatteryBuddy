package com.batterybuddy.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.Service
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.batterybuddy.data.battery.ChargerClassifier
import com.batterybuddy.data.battery.ChargerInfoReader
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.model.ChargeSource
import com.batterybuddy.data.model.ChargeState
import com.batterybuddy.data.preferences.UserPreferencesStore
import com.batterybuddy.data.repository.BatteryRepository
import com.batterybuddy.notification.BatteryNotifications
import com.batterybuddy.widget.BatteryWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Samples the battery while a charger is attached.
 *
 * Deliberately does *not* close the charge session on teardown: the unplug
 * receiver owns closing and the summary notification, so a stop/destroy race
 * can't produce two closes or two notifications. A session left open by a kill
 * or timeout is closed by the next `closeOpenChargeSessions` call.
 */
@AndroidEntryPoint
class BatteryPollingService : Service() {

    @Inject lateinit var repository: BatteryRepository
    @Inject lateinit var prefs: UserPreferencesStore
    @Inject lateinit var chargerInfoReader: ChargerInfoReader
    @Inject lateinit var notifications: BatteryNotifications

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    private var activeSessionId: Long = -1L

    /** When the battery first reached 100% in this session. */
    private var firstReached100Ms: Long = 0L
    private var overnightHoldRecorded = false

    /** Thermal alerts are rate-limited to one per cooldown window. */
    private var lastThermalAlertMs = 0L

    /** True once we have a real kernel-backed charger identity, not a power-bucket guess. */
    private var chargerIdentified = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(BatteryNotifications.ID_FOREGROUND, notifications.monitoringNotification())
        if (pollingJob?.isActive != true) {
            pollingJob = scope.launch { runPollingLoop() }
        }
        return START_STICKY
    }

    /**
     * Android 15+ enforces a daily runtime budget on timed foreground service
     * types. Without handling this the process is killed outright, so we shut
     * down cleanly and let the unplug receiver close the session later.
     */
    override fun onTimeout(startId: Int) {
        stopPollingAndSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopPollingAndSelf()
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopPollingAndSelf() {
        pollingJob?.cancel()
        stopSelf()
    }

    // ── Polling loop ──────────────────────────────────────────────────────────

    private suspend fun runPollingLoop() {
        val status = readBatteryStatus()

        // Reuse a session that's still open from this same charge, otherwise start one.
        val openSession = repository.getLatestOpenChargeSession()
            ?.takeIf { it.chargeSource != ChargeSource.NONE }
        activeSessionId = openSession?.id
            ?: repository.startChargeSession(status.percent, status.chargeSource)

        while (currentCoroutineContext().isActive) {
            val latest = collectAndStore()
            val interval = if (latest.percent >= FAST_POLL_PERCENT || latest.tempTenthsC >= FAST_POLL_TEMP_TENTHS) {
                INTERVAL_FAST_MS
            } else {
                INTERVAL_NORMAL_MS
            }
            delay(interval)
        }
    }

    private suspend fun collectAndStore(): BatteryStatus {
        val status        = readBatteryStatus()
        val chargerInfo   = chargerInfoReader.read()
        val tempThreshold = prefs.tempAlertThresholdCelsius.first()
        val holdThreshold = prefs.overnightHoldThresholdMinutes.first()

        repository.insertReading(
            BatteryReading(
                id                         = 0,
                timestamp                  = Instant.now().toEpochMilli(),
                voltageMillivolts          = status.voltageMv,
                temperatureTenthsCelsius   = status.tempTenthsC,
                chargeCounterMicroAmpHours = status.chargeCounterUah,
                currentMicroAmps           = status.currentUa,
                batteryPercent             = status.percent,
                chargeSource               = status.chargeSource,
                chargeState                = status.chargeState,
                isScreenOn                 = isScreenOn(),
                sessionId                  = activeSessionId.takeIf { it > 0 },
                chargerVoltageMillivolts   = chargerInfo.chargerVoltageMillivolts,
                chargerCurrentMaxMilliamps = chargerInfo.chargerCurrentMaxMilliamps,
                isPdActive                 = chargerInfo.isPdActive,
                chargerType                = chargerInfo.chargerType,
                chargeProtocolLabel        = chargerInfo.chargeProtocolLabel
            )
        )
        refreshWidget()

        if (activeSessionId > 0) {
            val isAbusive = status.tempTenthsC > tempThreshold * 10
            repository.updateLiveSessionFields(
                sessionId                  = activeSessionId,
                peakTempTenthsCelsius      = status.tempTenthsC,
                chargeCounterMicroAmpHours = status.chargeCounterUah,
                hasAbusiveTemp             = isAbusive
            )

            // Identify the charger while the cable is still attached — by the time
            // the session closes on unplug, sysfs has already forgotten it.
            if (!chargerIdentified) {
                val profile = ChargerClassifier.classify(chargerInfo, status.voltageMv, status.currentUa)
                if (profile.fingerprint.isNotEmpty()) {
                    repository.updateChargerIdentity(activeSessionId, profile.fingerprint, profile.label)
                    chargerIdentified = !profile.fingerprint.startsWith("VIRTUAL|")
                }
            }

            if (isAbusive) maybeFireThermalAlert(status.tempTenthsC)
            checkOvernightHold(status.percent, holdThreshold)
        }

        return status
    }

    private suspend fun refreshWidget() {
        try {
            GlanceAppWidgetManager(this)
                .getGlanceIds(BatteryWidget::class.java)
                .forEach { BatteryWidget().update(this, it) }
        } catch (_: Exception) {
            // Widget refresh is best-effort; never let it break sampling.
        }
    }

    // ── Overnight hold detection ──────────────────────────────────────────────

    private suspend fun checkOvernightHold(percent: Int, holdThresholdMinutes: Int) {
        if (overnightHoldRecorded) return
        if (percent < 100) {
            firstReached100Ms = 0L
            return
        }
        if (firstReached100Ms == 0L) {
            firstReached100Ms = System.currentTimeMillis()
            return
        }
        val elapsedMinutes = ((System.currentTimeMillis() - firstReached100Ms) / 60_000L).toInt()
        if (elapsedMinutes < holdThresholdMinutes) return

        repository.recordOvernightHold(activeSessionId, elapsedMinutes)
        repository.markOvernightHold(activeSessionId)
        overnightHoldRecorded = true
        notifications.showOvernightHoldAlert(elapsedMinutes)
    }

    private fun maybeFireThermalAlert(tempTenthsC: Int) {
        val now = System.currentTimeMillis()
        if (now - lastThermalAlertMs < THERMAL_ALERT_COOLDOWN_MS) return
        lastThermalAlertMs = now
        notifications.showThermalAlert(tempTenthsC)
    }

    // ── BatteryManager helpers ────────────────────────────────────────────────

    private data class BatteryStatus(
        val percent: Int,
        val voltageMv: Int,
        val tempTenthsC: Int,
        val chargeCounterUah: Int,
        val currentUa: Int,
        val chargeState: ChargeState,
        val chargeSource: ChargeSource
    )

    /** Reads the sticky battery intent once and derives everything from it. */
    private fun readBatteryStatus(): BatteryStatus {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val chargeState = when (sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1) {
            BatteryManager.BATTERY_STATUS_FULL        -> ChargeState.FULL
            BatteryManager.BATTERY_STATUS_CHARGING    -> ChargeState.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeState.DISCHARGING
            else                                      -> ChargeState.NOT_CHARGING
        }
        val chargeSource = when (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargeSource.WIRELESS
            0                                       -> ChargeSource.NONE
            else                                    -> ChargeSource.USB
        }

        return BatteryStatus(
            percent          = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            voltageMv        = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            tempTenthsC      = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0,
            chargeCounterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            currentUa        = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            chargeState      = chargeState,
            chargeSource     = chargeSource
        )
    }

    private fun isScreenOn(): Boolean =
        (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    companion object {
        private const val INTERVAL_FAST_MS          = 60_000L      // 1 minute
        private const val INTERVAL_NORMAL_MS        = 5 * 60_000L  // 5 minutes
        private const val FAST_POLL_PERCENT         = 80
        private const val FAST_POLL_TEMP_TENTHS     = 350
        private const val THERMAL_ALERT_COOLDOWN_MS = 30 * 60_000L
    }
}
