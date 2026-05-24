package com.batterybuddy.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterybuddy.data.model.ChargeSession
import com.batterybuddy.data.model.DischargeEvent
import com.batterybuddy.data.model.HealthSummary
import com.batterybuddy.data.repository.BatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

import java.time.LocalDate
import java.time.format.DateTimeFormatter

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val repository: BatteryRepository,
    private val prefs: com.batterybuddy.data.preferences.UserPreferencesStore
) : ViewModel() {

    val uiState: StateFlow<TrendsUiState> = combine(
        repository.getAllChargeSessions(),
        repository.getAllDischargeEvents(),
        prefs.ratedMahOverride
    ) { sessions, discharges, mahOverride ->
        val ratedMah = mahOverride ?: 4500
        val health = repository.computeHealthSummary(ratedMah)
        
        if (sessions.isEmpty() && discharges.isEmpty()) {
            TrendsUiState.Empty
        } else {
            val newestOpenChargeId = sessions
                .filter { it.isOpen }
                .maxByOrNull { it.startTimestamp }
                ?.id
            val newestOpenDischargeId = discharges
                .filter { it.isOpen }
                .maxByOrNull { it.startTimestamp }
                ?.id
            val history = (sessions.map { HistoryItem.Charge(it) } + 
                          discharges.map { HistoryItem.Discharge(it) })
                          .sortedByDescending { it.timestamp }

            val groupedHistory = history.groupBy { item ->
                val isCurrent = when (item) {
                    is HistoryItem.Charge -> item.session.id == newestOpenChargeId
                    is HistoryItem.Discharge -> item.event.id == newestOpenDischargeId
                }
                
                if (isCurrent) {
                    "Currently Tracking"
                } else {
                    val date = java.time.Instant.ofEpochMilli(item.timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    
                    if (date == java.time.LocalDate.now()) "Today"
                    else if (date == java.time.LocalDate.now().minusDays(1)) "Yesterday"
                    else date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d"))
                }
            }

            val lifetimeWear = sessions
                .filter { !it.isOpen }
                .mapNotNull { it.weightedCycleCost }
                .sum()

            TrendsUiState.Content(
                groupedHistory = groupedHistory,
                healthSummary = health,
                completedChargeSessionCount = sessions.count { !it.isOpen },
                currentChargeSessionId = newestOpenChargeId,
                currentDischargeEventId = newestOpenDischargeId,
                lifetimeWear = lifetimeWear
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrendsUiState.Loading
    )
}

sealed interface HistoryItem {
    val timestamp: Long

    data class Charge(val session: ChargeSession) : HistoryItem {
        override val timestamp: Long get() = session.startTimestamp
    }

    data class Discharge(val event: DischargeEvent) : HistoryItem {
        override val timestamp: Long get() = event.startTimestamp
    }
}

sealed interface TrendsUiState {
    object Loading : TrendsUiState
    object Empty : TrendsUiState
    data class Content(
        val groupedHistory: Map<String, List<HistoryItem>>,
        val healthSummary: HealthSummary?,
        val completedChargeSessionCount: Int,
        val currentChargeSessionId: Long?,
        val currentDischargeEventId: Long?,
        val lifetimeWear: Float = 0f
    ) : TrendsUiState
}
