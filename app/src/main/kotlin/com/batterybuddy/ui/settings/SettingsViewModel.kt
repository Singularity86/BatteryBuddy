package com.batterybuddy.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.model.ChargeSession
import com.batterybuddy.data.preferences.UserPreferencesStore
import com.batterybuddy.data.repository.BatteryRepository
import com.batterybuddy.worker.BatterySchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferencesStore,
    private val repository: BatteryRepository
) : ViewModel() {

    /** Rated capacity belongs to the installed battery, not to the app as a whole. */
    private val activeRatedMah = combine(
        repository.getBatteryProfiles(),
        prefs.activeBatteryId
    ) { profiles, activeId -> profiles.firstOrNull { it.id == activeId }?.ratedMah }

    private val monitoring = combine(
        prefs.deviceModel,
        prefs.tempAlertThresholdCelsius,
        prefs.overnightHoldThresholdMinutes,
        prefs.backgroundPollingIntervalMinutes,
        activeRatedMah
    ) { model, tempC, holdMinutes, pollMinutes, ratedMah ->
        MonitoringSettings(model, tempC, holdMinutes, pollMinutes, ratedMah)
    }

    private val diagnostics = combine(
        repository.observeLatestReading(),
        repository.getLatestChargeSession()
    ) { reading, session -> reading to session }

    val uiState: StateFlow<SettingsUiState> = combine(
        monitoring,
        diagnostics
    ) { settings, (reading, session) ->
        SettingsUiState(
            deviceModel                      = settings.deviceModel,
            ratedMah                         = settings.ratedMah,
            tempAlertThresholdCelsius        = settings.tempAlertThresholdCelsius,
            overnightHoldThresholdMinutes    = settings.overnightHoldThresholdMinutes,
            backgroundPollingIntervalMinutes = settings.backgroundPollingIntervalMinutes,
            latestReading                    = reading,
            latestSession                    = session
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() { _message.value = null }

    fun updateDeviceModel(model: String) {
        viewModelScope.launch { prefs.setDeviceModel(model.trim()) }
    }

    fun updateRatedMah(value: Int?) {
        val mah = value?.takeIf { it > 0 } ?: return
        viewModelScope.launch {
            repository.setBatteryRatedMah(prefs.activeBatteryId.first(), mah)
        }
    }

    fun updateTempThreshold(value: Int) {
        viewModelScope.launch { prefs.setTempAlertThresholdCelsius(value.coerceIn(30, 55)) }
    }

    fun updateOvernightThreshold(value: Int) {
        viewModelScope.launch { prefs.setOvernightHoldThresholdMinutes(value.coerceIn(30, 720)) }
    }

    /** Persists the interval *and* reschedules, so the change takes effect now. */
    fun updatePollingInterval(value: Int) {
        val minutes = value.coerceIn(
            BatterySchedule.MIN_INTERVAL_MINUTES,
            BatterySchedule.MAX_INTERVAL_MINUTES
        )
        viewModelScope.launch {
            prefs.setBackgroundPollingIntervalMinutes(minutes)
            BatterySchedule.enqueuePeriodic(context, minutes)
        }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)
                    ?.use { stream -> repository.exportToCsv(stream).getOrThrow() }
                    ?: error("Could not open the selected file")
            }
            _message.value = result.fold(
                onSuccess = { "Exported to the file you chose." },
                onFailure = { "Export failed: ${it.message ?: "unknown error"}" }
            )
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            _message.value = "All recorded data deleted."
        }
    }

    private data class MonitoringSettings(
        val deviceModel: String,
        val tempAlertThresholdCelsius: Int,
        val overnightHoldThresholdMinutes: Int,
        val backgroundPollingIntervalMinutes: Int,
        val ratedMah: Int?
    )
}

data class SettingsUiState(
    val deviceModel: String = "",
    val ratedMah: Int? = null,
    val tempAlertThresholdCelsius: Int = 38,
    val overnightHoldThresholdMinutes: Int = 120,
    val backgroundPollingIntervalMinutes: Int = 15,
    val latestReading: BatteryReading? = null,
    val latestSession: ChargeSession? = null
)
