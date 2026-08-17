package com.batterybuddy.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterybuddy.data.battery.BatteryStateProvider
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.repository.BatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Live tab state.
 *
 * The displayed reading comes from the system broadcast so it tracks the battery
 * in real time; the stored reading is consulted only for the id of the session
 * currently being recorded, which is what the graph drill-down needs.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: BatteryRepository,
    batteryState: BatteryStateProvider
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        batteryState.observe(),
        repository.observeLatestReading(),
        repository.getLatestDischargeEvent()
    ) { live, stored, latestDischarge ->
        DashboardUiState.Content(
            reading          = live,
            chargeSessionId  = stored?.sessionId,
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
    data class Content(
        val reading: BatteryReading,
        val chargeSessionId: Long? = null,
        val dischargeEventId: Long? = null
    ) : DashboardUiState
}
