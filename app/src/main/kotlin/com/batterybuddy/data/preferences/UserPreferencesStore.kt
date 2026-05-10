package com.batterybuddy.data.preferences

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
        val DEFAULT_EXPLANATION_DEPTH    = stringPreferencesKey("default_explanation_depth")
        val HAS_COMPLETED_ONBOARDING     = booleanPreferencesKey("has_completed_onboarding")
        val HAS_DISMISSED_PROTECT_TIP    = booleanPreferencesKey("has_dismissed_protect_tip")
        val BG_POLLING_INTERVAL_MIN      = intPreferencesKey("bg_polling_interval_min")
    }

    val ratedMahOverride: Flow<Int?> =
        context.dataStore.data.map { it[Keys.RATED_MAH_OVERRIDE] }

    val deviceModel: Flow<String> =
        context.dataStore.data.map { it[Keys.DEVICE_MODEL] ?: Build.MODEL }

    val tempAlertThresholdCelsius: Flow<Int> =
        context.dataStore.data.map { it[Keys.TEMP_ALERT_THRESHOLD_C] ?: 38 }

    val overnightHoldThresholdMinutes: Flow<Int> =
        context.dataStore.data.map { it[Keys.OVERNIGHT_HOLD_THRESHOLD_MIN] ?: 120 }

    val defaultExplanationDepth: Flow<String> =
        context.dataStore.data.map { it[Keys.DEFAULT_EXPLANATION_DEPTH] ?: "SURFACE" }

    val hasCompletedOnboarding: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.HAS_COMPLETED_ONBOARDING] ?: false }

    val hasProtectBatteryTipDismissed: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.HAS_DISMISSED_PROTECT_TIP] ?: false }

    val backgroundPollingIntervalMinutes: Flow<Int> =
        context.dataStore.data.map { it[Keys.BG_POLLING_INTERVAL_MIN] ?: 15 }

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

    suspend fun setDefaultExplanationDepth(depth: String) =
        context.dataStore.edit { it[Keys.DEFAULT_EXPLANATION_DEPTH] = depth }

    suspend fun setHasCompletedOnboarding(completed: Boolean) =
        context.dataStore.edit { it[Keys.HAS_COMPLETED_ONBOARDING] = completed }

    suspend fun setHasProtectBatteryTipDismissed(dismissed: Boolean) =
        context.dataStore.edit { it[Keys.HAS_DISMISSED_PROTECT_TIP] = dismissed }

    suspend fun setBackgroundPollingIntervalMinutes(minutes: Int) =
        context.dataStore.edit { it[Keys.BG_POLLING_INTERVAL_MIN] = minutes }
}
