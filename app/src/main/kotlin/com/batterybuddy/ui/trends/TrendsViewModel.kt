package com.batterybuddy.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterybuddy.data.analysis.HealthCalculator
import com.batterybuddy.data.model.ChargeSession
import com.batterybuddy.data.model.DischargeEvent
import com.batterybuddy.data.model.HealthSummary
import com.batterybuddy.data.model.OvernightHoldEvent
import com.batterybuddy.data.preferences.UserPreferencesStore
import com.batterybuddy.data.repository.BatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TrendsViewModel @Inject constructor(
    repository: BatteryRepository,
    prefs: UserPreferencesStore
) : ViewModel() {

    val uiState: StateFlow<TrendsUiState> = combine(
        repository.getAllChargeSessions(),
        repository.getAllDischargeEvents(),
        repository.getAllOvernightHolds(),
        repository.getBatteryProfiles(),
        prefs.activeBatteryId
    ) { sessions, discharges, holds, profiles, activeBatteryId ->
        if (sessions.isEmpty() && discharges.isEmpty()) {
            return@combine TrendsUiState.Empty
        }

        // Health and wear describe the battery currently installed. History shows
        // everything, so a swap doesn't make earlier sessions disappear.
        val activeSessions = sessions.filter { it.batteryId == activeBatteryId }
        val ratedMah = profiles.firstOrNull { it.id == activeBatteryId }?.ratedMah

        TrendsUiState.Content(
            groupedHistory              = groupHistory(sessions, discharges),
            healthSummary               = ratedMah?.let { HealthCalculator.summarize(activeSessions, it) },
            completedChargeSessionCount = activeSessions.count { !it.isOpen },
            currentChargeSessionId      = sessions.filter { it.isOpen }.maxByOrNull { it.startTimestamp }?.id,
            currentDischargeEventId     = discharges.filter { it.isOpen }.maxByOrNull { it.startTimestamp }?.id,
            lifetimeWear                = HealthCalculator.lifetimeWear(activeSessions),
            recentOvernightHolds        = recentHolds(holds),
            overnightWindowNights       = OVERNIGHT_WINDOW_DAYS
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrendsUiState.Loading
    )

    private fun groupHistory(
        sessions: List<ChargeSession>,
        discharges: List<DischargeEvent>
    ): Map<String, List<HistoryItem>> {
        val openChargeId = sessions.filter { it.isOpen }.maxByOrNull { it.startTimestamp }?.id
        val openDischargeId = discharges.filter { it.isOpen }.maxByOrNull { it.startTimestamp }?.id

        val items = (sessions.map { HistoryItem.Charge(it) } + discharges.map { HistoryItem.Discharge(it) })
            .sortedByDescending { it.timestamp }

        return items.groupBy { item ->
            val isCurrent = when (item) {
                is HistoryItem.Charge    -> item.session.id == openChargeId
                is HistoryItem.Discharge -> item.event.id == openDischargeId
            }
            if (isCurrent) CURRENT_GROUP else dayLabel(item.timestamp)
        }
    }

    private fun dayLabel(timestamp: Long): String {
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when (date) {
            today                -> "Today"
            today.minusDays(1)   -> "Yesterday"
            else                 -> date.format(DAY_FORMAT)
        }
    }

    /** Holds detected within the reporting window, newest first. */
    private fun recentHolds(holds: List<OvernightHoldEvent>): List<OvernightHoldEvent> {
        val cutoff = System.currentTimeMillis() - OVERNIGHT_WINDOW_DAYS * 86_400_000L
        return holds.filter { it.detectedTimestamp >= cutoff }.sortedByDescending { it.detectedTimestamp }
    }

    private companion object {
        const val CURRENT_GROUP = "Currently Tracking"
        const val OVERNIGHT_WINDOW_DAYS = 30L
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")
    }
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
        val lifetimeWear: Float,
        val recentOvernightHolds: List<OvernightHoldEvent>,
        val overnightWindowNights: Long
    ) : TrendsUiState {

        /** Distinct nights with a hold — several holds can share one night. */
        val overnightNights: Int
            get() = recentOvernightHolds
                .map {
                    Instant.ofEpochMilli(it.detectedTimestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                }
                .distinct()
                .size
    }
}
