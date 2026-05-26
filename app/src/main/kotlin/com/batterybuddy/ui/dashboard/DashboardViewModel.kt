package com.batterybuddy.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.repository.BatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: BatteryRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeLatestReading(),
        repository.getLatestDischargeEvent()
    ) { reading, latestDischarge ->
        if (reading == null) DashboardUiState.Empty
        else DashboardUiState.Content(
            reading = reading,
            chargeSessionId = reading.sessionId,
            dischargeEventId = latestDischarge?.takeIf { it.isOpen }?.id
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.Loading
    )
}

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    object Empty : DashboardUiState
    data class Content(
        val reading: BatteryReading,
        val chargeSessionId: Long? = null,
        val dischargeEventId: Long? = null
    ) : DashboardUiState
}
