package com.batterybuddy.ui.chargers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterybuddy.data.model.ChargeSession
import com.batterybuddy.data.repository.BatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChargerIntelligenceViewModel @Inject constructor(
    private val repository: BatteryRepository
) : ViewModel() {

    val uiState: StateFlow<ChargerUiState> = repository.getAllChargeSessions()
        .map { sessions ->
            val validSessions = sessions.filter { it.chargerFingerprint != null && it.endTimestamp != null }
            if (validSessions.isEmpty()) {
                ChargerUiState.Empty
            } else {
                val stats = validSessions.groupBy { it.chargerFingerprint!! }
                    .map { (fingerprint, group) ->
                        calculateChargerStats(fingerprint, group)
                    }
                    .sortedByDescending { it.efficiencyScore }
                
                ChargerUiState.Content(stats)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ChargerUiState.Loading
        )

    private fun calculateChargerStats(fingerprint: String, sessions: List<ChargeSession>): ChargerStats {
        val avgTemp = sessions.mapNotNull { it.peakTemperatureCelsius }.average().toFloat()
        val avgWatts = sessions.mapNotNull { session ->
            val wh = session.energyAddedWattHours ?: return@mapNotNull null
            val hours = (session.durationMinutes ?: 0) / 60f
            if (hours > 0) wh / hours else null
        }.average().toFloat().takeIf { !it.isNaN() } ?: 0f

        val abusiveCount = sessions.count { it.hasAbusiveTemp }
        
        // Efficiency Score Logic:
        // Base 100. Subtract 10 for every degree over 35C average. Subtract 20% if abusive sessions exist.
        val tempPenalty = ((avgTemp - 30f).coerceAtLeast(0f) * 5f)
        val abusivePenalty = (abusiveCount.toFloat() / sessions.size) * 50f
        val score = (100f - tempPenalty - abusivePenalty).coerceIn(0f, 100f)

        return ChargerStats(
            fingerprint = fingerprint,
            label = sessions.first().chargerLabel ?: "Unknown Charger",
            sessionCount = sessions.size,
            averagePeakTempCelsius = avgTemp,
            averageWatts = avgWatts,
            abusiveSessionCount = abusiveCount,
            efficiencyScore = score
        )
    }
}

sealed interface ChargerUiState {
    object Loading : ChargerUiState
    object Empty : ChargerUiState
    data class Content(val chargers: List<ChargerStats>) : ChargerUiState
}
