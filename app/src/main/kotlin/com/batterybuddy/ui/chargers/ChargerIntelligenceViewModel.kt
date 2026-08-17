package com.batterybuddy.ui.chargers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterybuddy.data.battery.ChargerClassifier
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
                .map { (fingerprint, group) ->
                    val history = (sessionsByFingerprint[fingerprint] ?: emptyList())
                        .filter { !it.isOpen }
                        .map { it.toSummary() }
                    buildStats(fingerprint, group, history, userLabels)
                }
                .sortedByDescending { it.coolRunningScore }
            ChargerUiState.Content(stats)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChargerUiState.Loading
    )

    // Non-null when there's a completed session whose fingerprint has no user label yet.
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
                val autoLabel = session.chargerLabel ?: defaultLabel(session.chargeSource)
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
        val temps = samples.map { it.tempC }.filter { it > 0f }
        val avgTemp = if (temps.isEmpty()) 0f else temps.average().toFloat()
        val watts = samples.map { it.watts }.filter { it > 0f }
        val avgWatts = if (watts.isEmpty()) 0f else watts.average().toFloat()
        val abusiveCount = samples.count { it.hasAbusiveTemp }

        // Purely thermal: how far above a comfortable 30 °C this charger tends to
        // push the battery, plus how often it crossed the abuse threshold.
        val tempPenalty = (avgTemp - COMFORTABLE_TEMP_C).coerceAtLeast(0f) * TEMP_PENALTY_PER_DEGREE
        val abusivePenalty = (abusiveCount.toFloat() / samples.size) * ABUSIVE_PENALTY_WEIGHT

        return ChargerStats(
            fingerprint            = fingerprint,
            label                  = userLabels[fingerprint] ?: samples.first().label,
            sessionCount           = samples.size,
            averagePeakTempCelsius = avgTemp,
            averageWatts           = avgWatts,
            abusiveSessionCount    = abusiveCount,
            coolRunningScore       = (100f - tempPenalty - abusivePenalty).coerceIn(0f, 100f),
            sessions               = sessions
        )
    }

    private fun defaultLabel(source: ChargeSource): String =
        source.name.lowercase().replaceFirstChar { it.uppercase() } + " charger"

    /** Mean power over a completed session, derived from measured energy and duration. */
    private fun ChargeSession.averageWatts(): Float {
        val wattHours = energyAddedWattHours ?: return 0f
        val hours = (durationMinutes ?: 0) / 60f
        return if (hours > 0f) (wattHours / hours).toFloat() else 0f
    }

    private fun ChargeSession.toSample(userLabels: Map<String, String>): ChargerSample {
        val fingerprint = chargerFingerprint ?: "${chargeSource.name}|UNCLASSIFIED"
        val autoLabel = chargerLabel
            ?: if (isOpen) "Active ${chargeSource.name.lowercase()} charger" else defaultLabel(chargeSource)
        return ChargerSample(
            fingerprint    = fingerprint,
            label          = userLabels[fingerprint] ?: autoLabel,
            tempC          = peakTemperatureCelsius ?: 0f,
            watts          = averageWatts(),
            hasAbusiveTemp = hasAbusiveTemp
        )
    }

    /**
     * Live readings go through the same classifier the polling service uses, so a
     * charger seen live and the same charger seen in history share one identity.
     */
    private fun BatteryReading.toSample(userLabels: Map<String, String>): ChargerSample {
        val profile = ChargerClassifier.classify(this)
        return ChargerSample(
            fingerprint    = profile.fingerprint,
            label          = userLabels[profile.fingerprint] ?: profile.label,
            tempC          = temperatureCelsius,
            watts          = chargingPowerWatts,
            hasAbusiveTemp = temperatureCelsius > ABUSIVE_TEMP_C
        )
    }

    private fun ChargeSession.toSummary(): SessionSummary = SessionSummary(
        id = id,
        startTimestamp = startTimestamp,
        durationMinutes = durationMinutes,
        startPercent = startPercent,
        endPercent = endPercent,
        avgWatts = averageWatts(),
        peakTempCelsius = peakTemperatureCelsius,
        hasAbusiveTemp = hasAbusiveTemp
    )

    private data class ChargerSample(
        val fingerprint: String,
        val label: String,
        val tempC: Float,
        val watts: Float,
        val hasAbusiveTemp: Boolean
    )

    private companion object {
        const val RECENT_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L
        const val COMFORTABLE_TEMP_C = 30f
        const val ABUSIVE_TEMP_C = 38f
        const val TEMP_PENALTY_PER_DEGREE = 5f
        const val ABUSIVE_PENALTY_WEIGHT = 50f
    }
}

sealed interface ChargerUiState {
    object Loading : ChargerUiState
    object Empty : ChargerUiState
    data class Content(val chargers: List<ChargerStats>) : ChargerUiState
}
