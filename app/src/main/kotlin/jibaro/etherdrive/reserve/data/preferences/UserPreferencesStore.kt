package jibaro.etherdrive.reserve.data.preferences

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val RATED_MAH_OVERRIDE           = intPreferencesKey("rated_mah_override")
        val DEVICE_MODEL                 = stringPreferencesKey("device_model")
        val TEMP_ALERT_THRESHOLD_C       = intPreferencesKey("temp_alert_threshold_c")
        val OVERNIGHT_HOLD_THRESHOLD_MIN = intPreferencesKey("overnight_hold_threshold_min")
        val HAS_COMPLETED_ONBOARDING     = booleanPreferencesKey("has_completed_onboarding")
        val BG_POLLING_INTERVAL_MIN      = intPreferencesKey("bg_polling_interval_min")
        val CHARGE_ALARM_PERCENT         = intPreferencesKey("charge_alarm_percent")
        val CHARGER_LABELS_ENCODED       = stringPreferencesKey("charger_labels_encoded")
        val ACTIVE_BATTERY_ID            = longPreferencesKey("active_battery_id")
        val LAST_BOOT_ID                 = longPreferencesKey("last_boot_id")
    }

    // Which physical battery is currently installed. Defaults to the migration's
    // "Battery 1" (row id 1).
    val activeBatteryId: Flow<Long> =
        context.dataStore.data.map { it[Keys.ACTIVE_BATTERY_ID] ?: 1L }

    // Boot-time fingerprint (wall clock − uptime). Used to detect that the device
    // rebooted since the app last ran, which is the only moment a swap is possible.
    val lastBootId: Flow<Long> =
        context.dataStore.data.map { it[Keys.LAST_BOOT_ID] ?: 0L }

    /**
     * Legacy home for rated capacity. Rated mAh now lives on the battery profile;
     * this is read once at startup so an existing value can be adopted, then cleared.
     */
    val ratedMahOverride: Flow<Int?> =
        context.dataStore.data.map { it[Keys.RATED_MAH_OVERRIDE] }

    val deviceModel: Flow<String> =
        context.dataStore.data.map { it[Keys.DEVICE_MODEL] ?: Build.MODEL }

    val tempAlertThresholdCelsius: Flow<Int> =
        context.dataStore.data.map { it[Keys.TEMP_ALERT_THRESHOLD_C] ?: 38 }

    val overnightHoldThresholdMinutes: Flow<Int> =
        context.dataStore.data.map { it[Keys.OVERNIGHT_HOLD_THRESHOLD_MIN] ?: 120 }

    val hasCompletedOnboarding: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.HAS_COMPLETED_ONBOARDING] ?: false }

    val backgroundPollingIntervalMinutes: Flow<Int> =
        context.dataStore.data.map { it[Keys.BG_POLLING_INTERVAL_MIN] ?: 15 }

    /**
     * Charge level at which to nudge the user to unplug. 0 disables the alarm.
     *
     * Defaults on at 80% because it is the one habit change the app actively
     * recommends, and it costs at most one quiet notification per charge.
     */
    val chargeAlarmPercent: Flow<Int> =
        context.dataStore.data.map { it[Keys.CHARGE_ALARM_PERCENT] ?: DEFAULT_CHARGE_ALARM_PERCENT }

    // Charger labels keyed by fingerprint. Stored as "fp\tlabel\nfp\tlabel…"
    val chargerLabels: Flow<Map<String, String>> =
        context.dataStore.data.map { decodeLabels(it[Keys.CHARGER_LABELS_ENCODED] ?: "") }

    suspend fun setRatedMahOverride(mah: Int?) = context.dataStore.edit { prefs ->
        if (mah == null) prefs.remove(Keys.RATED_MAH_OVERRIDE)
        else prefs[Keys.RATED_MAH_OVERRIDE] = mah
    }

    suspend fun setDeviceModel(model: String) =
        context.dataStore.edit { it[Keys.DEVICE_MODEL] = model }

    suspend fun setTempAlertThresholdCelsius(threshold: Int) =
        context.dataStore.edit { it[Keys.TEMP_ALERT_THRESHOLD_C] = threshold }

    suspend fun setOvernightHoldThresholdMinutes(minutes: Int) =
        context.dataStore.edit { it[Keys.OVERNIGHT_HOLD_THRESHOLD_MIN] = minutes }

    suspend fun setHasCompletedOnboarding(completed: Boolean) =
        context.dataStore.edit { it[Keys.HAS_COMPLETED_ONBOARDING] = completed }

    suspend fun setBackgroundPollingIntervalMinutes(minutes: Int) =
        context.dataStore.edit { it[Keys.BG_POLLING_INTERVAL_MIN] = minutes }

    /** @param percent 0 to switch the alarm off entirely. */
    suspend fun setChargeAlarmPercent(percent: Int) =
        context.dataStore.edit { it[Keys.CHARGE_ALARM_PERCENT] = percent.coerceIn(0, 100) }

    suspend fun setActiveBatteryId(id: Long) =
        context.dataStore.edit { it[Keys.ACTIVE_BATTERY_ID] = id }

    suspend fun setLastBootId(bootId: Long) =
        context.dataStore.edit { it[Keys.LAST_BOOT_ID] = bootId }

    suspend fun setChargerLabel(fingerprint: String, label: String) {
        context.dataStore.edit { prefs ->
            val current = decodeLabels(prefs[Keys.CHARGER_LABELS_ENCODED] ?: "").toMutableMap()
            current[fingerprint] = label
            prefs[Keys.CHARGER_LABELS_ENCODED] = encodeLabels(current)
        }
    }

    // Entries separated by \n; key and value separated by \t.
    // Fingerprints and user labels never contain \n or \t in practice.
    private fun decodeLabels(encoded: String): Map<String, String> {
        if (encoded.isBlank()) return emptyMap()
        return encoded.lines().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab > 0) line.substring(0, tab) to line.substring(tab + 1) else null
        }.toMap()
    }

    private fun encodeLabels(map: Map<String, String>): String =
        map.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    companion object {
        const val DEFAULT_CHARGE_ALARM_PERCENT = 80
    }
}
