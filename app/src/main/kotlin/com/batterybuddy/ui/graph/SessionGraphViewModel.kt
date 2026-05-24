package com.batterybuddy.ui.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.repository.BatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionGraphViewModel @Inject constructor(
    private val repository: BatteryRepository
) : ViewModel() {

    private val _state = MutableStateFlow<GraphUiState>(GraphUiState.Loading)
    val state: StateFlow<GraphUiState> = _state.asStateFlow()

    private var collectJob: Job? = null

    fun loadCharge(sessionId: Long) {
        collectJob?.cancel()
        _state.value = GraphUiState.Loading
        collectJob = viewModelScope.launch {
            repository.getReadingsForSession(sessionId).collect { readings ->
                _state.value = if (readings.isEmpty()) GraphUiState.Empty
                               else GraphUiState.Content(readings, isCharge = true)
            }
        }
    }

    fun loadDischarge(eventId: Long) {
        collectJob?.cancel()
        _state.value = GraphUiState.Loading
        collectJob = viewModelScope.launch {
            val event = repository.getDischargeEventById(eventId)
            if (event == null) {
                _state.value = GraphUiState.Empty
                return@launch
            }
            val endTs = event.endTimestamp ?: System.currentTimeMillis()
            repository.getReadingsBetween(event.startTimestamp, endTs).collect { readings ->
                _state.value = if (readings.isEmpty()) GraphUiState.Empty
                               else GraphUiState.Content(readings, isCharge = false)
            }
        }
    }
}

sealed interface GraphUiState {
    object Loading : GraphUiState
    object Empty : GraphUiState
    data class Content(
        val readings: List<BatteryReading>,
        val isCharge: Boolean
    ) : GraphUiState
}
