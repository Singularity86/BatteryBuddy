package com.batterybuddy.ui.chargers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.model.ChargeSession
import com.batterybuddy.data.model.ChargeSource
import com.batterybuddy.data.repository.BatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChargerIntelligenceViewModel @Inject constructor(
    private val repository: BatteryRepository
) : ViewModel() {

    val uiState: StateFlow<ChargerUiState> = combine(
        repository.getAllChargeSessions(),
        repository.getReadingsSince(System.currentTimeMillis() - RECENT_READING_WINDOW_MS)
    ) { sessions, readings ->
            val samples = sessions
                .filter { it.chargeSource != ChargeSource.NONE }
                .map { it.toChargerSample() } +
                readings
                    .filter { it.chargeSource != ChargeSource.NONE }
                    .map { it.toChargerSample() }

            if (samples.isEmpty()) {
                ChargerUiState.Empty
            } else {
                val stats = samples.groupBy { it.fingerprint }
                    .map { (fingerprint, group) -> calculateChargerStats(fingerprint, group) }
                    .sortedByDescending { it.efficiencyScore }
                
                ChargerUiState.Content(stats)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ChargerUiState.Loading
        )

    private fun calculateChargerStats(fingerprint: String, samples: List<ChargerSample>): ChargerStats {
        val avgTemp = samples.map { it.temperatureCelsius }
            .average()
            .toFloat()
            .takeIf { !it.isNaN() }
            ?: 0f
        val avgWatts = samples.map { it.watts }
            .filter { it > 0f }
            .average()
            .toFloat()
            .takeIf { !it.isNaN() }
            ?: 0f

        val abusiveCount = samples.count { it.hasAbusiveTemp }
        
        // Efficiency Score Logic:
        // Base 100. Subtract 10 for every degree over 35C average. Subtract 20% if abusive sessions exist.
        val tempPenalty = ((avgTemp - 30f).coerceAtLeast(0f) * 5f)
        val abusivePenalty = (abusiveCount.toFloat() / samples.size) * 50f
        val score = (100f - tempPenalty - abusivePenalty).coerceIn(0f, 100f)

        return ChargerStats(
            fingerprint = fingerprint,
            label = samples.first().label,
            sessionCount = samples.size,
            averagePeakTempCelsius = avgTemp,
            averageWatts = avgWatts,
            abusiveSessionCount = abusiveCount,
            efficiencyScore = score
        )
    }

    private fun ChargeSession.toChargerSample(): ChargerSample {
        val sourceLabel = chargeSource.name.lowercase().replaceFirstChar { it.uppercase() }
        val watts = energyAddedWattHours?.let { wh ->
            val hours = (durationMinutes ?: 0) / 60f
            if (hours > 0) (wh / hours).toFloat() else 0f
        } ?: 0f
        return ChargerSample(
            fingerprint = chargerFingerprint ?: "${chargeSource.name}|UNCLASSIFIED",
            label = chargerLabel ?: if (isOpen) "Active ${chargeSource.name.lowercase()} charger" else "$sourceLabel charger",
            temperatureCelsius = peakTemperatureCelsius ?: 0f,
            watts = watts,
            hasAbusiveTemp = hasAbusiveTemp
        )
    }

    private fun BatteryReading.toChargerSample(): ChargerSample {
        val type = chargerType ?: chargeSource.name
        val protocol = chargeProtocolLabel ?: "NONE"
        val fingerprint = if (chargerType != null || chargeProtocolLabel != null) {
            "$type|${chargerVoltageMillivolts ?: "AUTO"}|${chargerCurrentMaxMilliamps ?: "AUTO"}|$protocol"
        } else {
            "${chargeSource.name}|LIVE"
        }
        val label = chargeProtocolLabel
            ?: chargerType
            ?: "Live ${chargeSource.name.lowercase()} charger"
        return ChargerSample(
            fingerprint = fingerprint,
            label = label,
            temperatureCelsius = temperatureCelsius,
            watts = chargingPowerWatts,
            hasAbusiveTemp = temperatureCelsius > 38f
        )
    }

    private data class ChargerSample(
        val fingerprint: String,
        val label: String,
        val temperatureCelsius: Float,
        val watts: Float,
        val hasAbusiveTemp: Boolean
    )

    companion object {
        private const val RECENT_READING_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L
    }
}

sealed interface ChargerUiState {
    object Loading : ChargerUiState
    object Empty : ChargerUiState
    data class Content(val chargers: List<ChargerStats>) : ChargerUiState
}
