package com.batterybuddy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.model.ChargeSession
import com.batterybuddy.data.preferences.UserPreferencesStore
import com.batterybuddy.data.repository.BatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesStore,
    repository: BatteryRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        prefs.deviceModel,
        prefs.ratedMahOverride,
        prefs.tempAlertThresholdCelsius,
        prefs.overnightHoldThresholdMinutes,
        prefs.backgroundPollingIntervalMinutes,
        repository.observeLatestReading(),
        repository.getLatestChargeSession()
    ) { values ->
        SettingsUiState(
            deviceModel = values[0] as String,
            ratedMahOverride = values[1] as Int?,
            tempAlertThresholdCelsius = values[2] as Int,
            overnightHoldThresholdMinutes = values[3] as Int,
            backgroundPollingIntervalMinutes = values[4] as Int,
            latestReading = values[5] as BatteryReading?,
            latestSession = values[6] as ChargeSession?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun updateDeviceModel(model: String) {
        viewModelScope.launch { prefs.setDeviceModel(model.trim()) }
    }

    fun updateRatedMah(value: Int?) {
        viewModelScope.launch { prefs.setRatedMahOverride(value) }
    }

    fun updateTempThreshold(value: Int) {
        viewModelScope.launch { prefs.setTempAlertThresholdCelsius(value.coerceIn(30, 55)) }
    }

    fun updateOvernightThreshold(value: Int) {
        viewModelScope.launch { prefs.setOvernightHoldThresholdMinutes(value.coerceIn(30, 720)) }
    }

    fun updatePollingInterval(value: Int) {
        viewModelScope.launch { prefs.setBackgroundPollingIntervalMinutes(value.coerceIn(15, 120)) }
    }
}

data class SettingsUiState(
    val deviceModel: String = "",
    val ratedMahOverride: Int? = null,
    val tempAlertThresholdCelsius: Int = 38,
    val overnightHoldThresholdMinutes: Int = 120,
    val backgroundPollingIntervalMinutes: Int = 15,
    val latestReading: BatteryReading? = null,
    val latestSession: ChargeSession? = null
)
