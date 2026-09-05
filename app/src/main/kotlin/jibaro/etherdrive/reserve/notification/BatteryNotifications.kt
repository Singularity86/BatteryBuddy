package jibaro.etherdrive.reserve.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import jibaro.etherdrive.reserve.BatteryTruthApp
import jibaro.etherdrive.reserve.data.analysis.ChargeMath
import jibaro.etherdrive.reserve.data.model.ChargeSession
import jibaro.etherdrive.reserve.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All user-facing battery notifications in one place.
 *
 * Shared by the polling service (which owns the live alerts) and the unplug
 * receiver (which owns the post-session summary), so wording and channels stay
 * consistent regardless of which component fires.
 */
@Singleton
class BatteryNotifications @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val manager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    /** The ongoing notification that keeps the polling service in the foreground. */
    fun monitoringNotification(): Notification =
        NotificationCompat.Builder(context, BatteryTruthApp.CHANNEL_MONITORING)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("BatteryTruth")
            .setContentText("Monitoring battery while charging")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .build()

    fun showThermalAlert(tempTenthsCelsius: Int) {
        val notification = NotificationCompat.Builder(context, BatteryTruthApp.CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Battery is running hot")
            .setContentText(
                "%.1f °C while charging. Taking off a case or moving it out of the sun helps."
                    .format(tempTenthsCelsius / 10f)
            )
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        manager.notify(ID_THERMAL, notification)
    }

    /**
     * The one habit nudge the app actively recommends. Fired at most once per
     * charge session, and phrased as an option rather than an instruction —
     * charging to 100% is sometimes exactly what someone needs.
     */
    fun showChargeTargetReached(percent: Int) {
        val notification = NotificationCompat.Builder(context, BatteryTruthApp.CHANNEL_CHARGE_TARGET)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Battery at $percent%")
            .setContentText("A good place to unplug if you don't need a full charge today.")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        manager.notify(ID_CHARGE_TARGET, notification)
    }

    fun showOvernightHoldAlert(elapsedMinutes: Int) {
        val notification = NotificationCompat.Builder(context, BatteryTruthApp.CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Still charging at 100%")
            .setContentText(
                "It's been at full for ${formatDuration(elapsedMinutes)}. Unplugging now is easier on the battery."
            )
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        manager.notify(ID_OVERNIGHT, notification)
    }

    /**
     * Post-charge summary. Fired once, by whichever component actually closed the
     * session. [comparison] is supplied only when the charge stood out against
     * recent history — most charges are unremarkable and should read that way.
     */
    fun showSessionSummary(session: ChargeSession, comparison: String? = null) {
        val endPercent = session.endPercent ?: return
        val band = ChargeMath.wearBand(session.weightedCycleCost ?: 0f)
        val duration = formatDuration(session.durationMinutes ?: 0)
        val charger = session.chargerLabel ?: session.chargeSource.name.lowercase()
        val summary = "${session.startPercent}% → $endPercent% in $duration · $charger"

        val notification = NotificationCompat.Builder(context, BatteryTruthApp.CHANNEL_REPORTS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Charge complete · ${band.label}")
            .setContentText(comparison ?: summary)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(if (comparison != null) "$summary\n$comparison" else summary)
            )
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        manager.notify(ID_SESSION_SUMMARY, notification)
    }

    private fun formatDuration(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0             -> "${hours}h"
            else                  -> "${mins}m"
        }
    }

    companion object {
        const val ID_FOREGROUND = 1001
        private const val ID_THERMAL = 1002
        private const val ID_OVERNIGHT = 1003
        private const val ID_SESSION_SUMMARY = 1004
        private const val ID_CHARGE_TARGET = 1005
    }
}
