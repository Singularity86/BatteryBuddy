package com.batterybuddy.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterybuddy.data.model.ChargeSession
import com.batterybuddy.data.model.HealthSummary
import com.batterybuddy.data.repository.BatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val repository: BatteryRepository
) : ViewModel() {

    // Ideally, ratedMah would come from user preferences or a device database.
    // For now, using a common placeholder like 4500mAh.
    private val ratedMah = 4500 

    val uiState: StateFlow<TrendsUiState> = combine(
        repository.getAllChargeSessions(),
        flow { emit(repository.computeHealthSummary(ratedMah)) }
    ) { sessions, health ->
        if (sessions.isEmpty()) {
            TrendsUiState.Empty
        } else {
            TrendsUiState.Content(
                sessions = sessions.takeLast(20), // Last 20 sessions for the trend
                healthSummary = health
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrendsUiState.Loading
    )
}

sealed interface TrendsUiState {
    object Loading : TrendsUiState
    object Empty : TrendsUiState
    data class Content(
        val sessions: List<ChargeSession>,
        val healthSummary: HealthSummary?
    ) : TrendsUiState
}
