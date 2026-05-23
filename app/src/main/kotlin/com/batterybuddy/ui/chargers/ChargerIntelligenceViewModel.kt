package com.batterybuddy.ui.chargers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterybuddy.data.model.BatteryReading
import com.batterybuddy.data.model.ChargeSession
import com.batterybuddy.data.model.ChargeSource
import com.batterybuddy.data.preferences.UserPreferencesStore
import com.batterybuddy.data.repository.BatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChargerIntelligenceViewModel @Inject constructor(
    private val repository: BatteryRepository,
    private val prefs: UserPreferencesStore
) : ViewModel() {

    // Fingerprints the user dismissed this session — won't prompt again until restart.
    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<ChargerUiState> = combine(
        repository.getAllChargeSessions(),
        repository.getReadingsSince(System.currentTimeMillis() - RECENT_WINDOW_MS),
        prefs.chargerLabels
    ) { sessions, readings, userLabels ->
        val chargerSessions = sessions.filter { it.chargeSource != ChargeSource.NONE }
        val sessionsByFingerprint = chargerSessions
            .filter { it.chargerFingerprint != null }
            .groupBy { it.chargerFingerprint!! }

        val samples = chargerSessions.map { it.toSample(userLabels) } +
            readings.filter { it.chargeSource != ChargeSource.NONE }.map { it.toSample(userLabels) }

        if (samples.isEmpty()) {
            ChargerUiState.Empty
        } else {
            val stats = samples.groupBy { it.fingerprint }
                .map { (fp, group) ->
                    val history = (sessionsByFingerprint[fp] ?: emptyList())
                        .filter { !it.isOpen }
                        .map { it.toSummary() }
                    buildStats(fp, group, history, userLabels)
                }
                .sortedByDescending { it.efficiencyScore }
            ChargerUiState.Content(stats)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChargerUiState.Loading
    )

    // Non-null when there's a completed session whose fingerprint has no user label yet.
    // Pair is (fingerprint, autoLabel) so the dialog can pre-fill a sensible default.
    val pendingLabelPrompt: StateFlow<Pair<String, String>?> = combine(
        repository.getAllChargeSessions(),
        prefs.chargerLabels,
        _dismissed
    ) { sessions, userLabels, dismissed ->
        sessions
            .filter { !it.isOpen && it.chargerFingerprint != null }
            .sortedByDescending { it.startTimestamp }
            .firstOrNull { it.chargerFingerprint!! !in userLabels && it.chargerFingerprint!! !in dismissed }
            ?.let { session ->
                val autoLabel = session.chargerLabel
                    ?: (session.chargeSource.name.lowercase().replaceFirstChar { it.uppercase() } + " charger")
                session.chargerFingerprint!! to autoLabel
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveChargerLabel(fingerprint: String, label: String) {
        viewModelScope.launch {
            prefs.setChargerLabel(fingerprint, label)
            repository.updateChargerLabelForFingerprint(fingerprint, label)
            _dismissed.update { it + fingerprint }
        }
    }

    fun dismissLabelPrompt(fingerprint: String) {
        _dismissed.update { it + fingerprint }
    }

    private fun buildStats(
        fingerprint: String,
        samples: List<ChargerSample>,
        sessions: List<SessionSummary>,
        userLabels: Map<String, String>
    ): ChargerStats {
        val avgTemp = samples.map { it.tempC }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
        val avgWatts = samples.map { it.watts }.filter { it > 0f }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
        val abusiveCount = samples.count { it.hasAbusiveTemp }
        val tempPenalty = (avgTemp - 30f).coerceAtLeast(0f) * 5f
        val abusivePenalty = (abusiveCount.toFloat() / samples.size) * 50f
        return ChargerStats(
            fingerprint = fingerprint,
            label = userLabels[fingerprint] ?: samples.first().label,
            sessionCount = samples.size,
            averagePeakTempCelsius = avgTemp,
            averageWatts = avgWatts,
            abusiveSessionCount = abusiveCount,
            efficiencyScore = (100f - tempPenalty - abusivePenalty).coerceIn(0f, 100f),
            sessions = sessions
        )
    }

    private fun ChargeSession.toSample(userLabels: Map<String, String>): ChargerSample {
        val fp = chargerFingerprint ?: "${chargeSource.name}|UNCLASSIFIED"
        val watts = energyAddedWattHours?.let { wh ->
            val h = (durationMinutes ?: 0) / 60f
            if (h > 0f) (wh / h).toFloat() else 0f
        } ?: 0f
        val sourceLabel = chargeSource.name.lowercase().replaceFirstChar { it.uppercase() }
        val autoLabel = chargerLabel ?: if (isOpen) "Active ${chargeSource.name.lowercase()} charger" else "$sourceLabel charger"
        return ChargerSample(fp, userLabels[fp] ?: autoLabel, peakTemperatureCelsius ?: 0f, watts, hasAbusiveTemp)
    }

    private fun BatteryReading.toSample(userLabels: Map<String, String>): ChargerSample {
        val type = chargerType ?: chargeSource.name
        val protocol = chargeProtocolLabel ?: "NONE"
        val fp = if (chargerType != null || chargeProtocolLabel != null)
            "$type|${chargerVoltageMillivolts ?: "AUTO"}|${chargerCurrentMaxMilliamps ?: "AUTO"}|$protocol"
        else
            "${chargeSource.name}|LIVE"
        val autoLabel = chargeProtocolLabel ?: chargerType ?: "Live ${chargeSource.name.lowercase()} charger"
        return ChargerSample(fp, userLabels[fp] ?: autoLabel, temperatureCelsius, chargingPowerWatts, temperatureCelsius > 38f)
    }

    private fun ChargeSession.toSummary(): SessionSummary {
        val watts = energyAddedWattHours?.let { wh ->
            val h = (durationMinutes ?: 0) / 60f
            if (h > 0f) (wh / h).toFloat() else 0f
        } ?: 0f
        return SessionSummary(id, startTimestamp, durationMinutes, startPercent, endPercent, watts, peakTemperatureCelsius, hasAbusiveTemp)
    }

    private data class ChargerSample(
        val fingerprint: String,
        val label: String,
        val tempC: Float,
        val watts: Float,
        val hasAbusiveTemp: Boolean
    )

    companion object {
        private const val RECENT_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L
    }
}

sealed interface ChargerUiState {
    object Loading : ChargerUiState
    object Empty : ChargerUiState
    data class Content(val chargers: List<ChargerStats>) : ChargerUiState
}
