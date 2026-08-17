package com.batterybuddy.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterybuddy.data.analysis.DrainCalculator
import com.batterybuddy.data.analysis.DrainSummary
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
        repository.getLatestDischargeEvent(),
        repository.getReadingsSince(System.currentTimeMillis() - DRAIN_WINDOW_MS)
    ) { live, stored, latestDischarge, recentReadings ->
        DashboardUiState.Content(
            reading          = live,
            chargeSessionId  = stored?.sessionId,
            dischargeEventId = latestDischarge?.takeIf { it.isOpen }?.id,
            drain            = DrainCalculator.summarize(recentReadings, live.batteryPercent)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.Loading
    )

    private companion object {
        // Far enough back to find the last full charge for most usage patterns.
        const val DRAIN_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    }
}

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    data class Content(
        val reading: BatteryReading,
        val chargeSessionId: Long? = null,
        val dischargeEventId: Long? = null,
        val drain: DrainSummary? = null
    ) : DashboardUiState
}
