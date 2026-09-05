package jibaro.etherdrive.reserve.ui.battery

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jibaro.etherdrive.reserve.data.analysis.HealthCalculator
import jibaro.etherdrive.reserve.data.model.BatteryProfile
import jibaro.etherdrive.reserve.data.model.ChargeSession
import jibaro.etherdrive.reserve.data.preferences.UserPreferencesStore
import jibaro.etherdrive.reserve.data.repository.BatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/** Per-battery comparison stats, derived from that battery's completed charge sessions. */
data class BatteryComparison(
    val profile: BatteryProfile,
    val isActive: Boolean,
    val completedSessions: Int,
    val capacityMah: Int?,      // best observed full-charge counter, in mAh
    val cyclesUsed: Float,      // summed weighted cycle cost
    val avgPeakTempC: Float?,
    val healthPercent: Int?     // capacity / rated, null until we have data
) {
    // Health estimates are only meaningful after a few full sessions.
    val isCalibrating: Boolean get() = completedSessions < 5 || capacityMah == null
}

@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val repository: BatteryRepository,
    private val prefs: UserPreferencesStore
) : ViewModel() {

    val profiles: StateFlow<List<BatteryProfile>> =
        repository.getBatteryProfiles()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeBatteryId: StateFlow<Long> =
        prefs.activeBatteryId
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1L)

    val comparisons: StateFlow<List<BatteryComparison>> =
        combine(
            repository.getBatteryProfiles(),
            repository.getAllChargeSessions(),
            prefs.activeBatteryId
        ) { profs, sessions, activeId ->
            profs.map { p ->
                buildComparison(p, sessions.filter { it.batteryId == p.id }, activeId)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Reboot-gated swap prompt ---
    private val _swapPrompt = MutableStateFlow(false)
    val swapPrompt: StateFlow<Boolean> = _swapPrompt.asStateFlow()
    private var pendingBootId = 0L

    init {
        viewModelScope.launch {
            adoptLegacyRatedMah()
            detectReboot()
        }
    }

    /**
     * Rated capacity used to live in preferences and again on each battery
     * profile. The profile is the real source of truth — it has to be, since
     * two batteries can have different ratings — so move any preference value
     * onto the active profile once and clear it.
     */
    private suspend fun adoptLegacyRatedMah() {
        val legacy = prefs.ratedMahOverride.first() ?: return
        val activeId = prefs.activeBatteryId.first()
        if (repository.getBatteryProfile(activeId) != null) {
            repository.setBatteryRatedMah(activeId, legacy)
        }
        prefs.setRatedMahOverride(null)
    }

    fun renameBattery(id: Long, label: String) {
        viewModelScope.launch { repository.renameBatteryProfile(id, label.trim()) }
    }

    /**
     * A battery can only be swapped while the device is off, so a swap always
     * implies a reboot. We only prompt when the boot fingerprint changed since
     * the app last ran AND more than one battery exists.
     */
    private suspend fun detectReboot() {
        val bootId = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val last = prefs.lastBootId.first()
        val profileCount = repository.getBatteryProfiles().first().size
        if (last != 0L && abs(last - bootId) > 60_000L && profileCount >= 2) {
            pendingBootId = bootId
            _swapPrompt.value = true
        } else {
            // First run, or same boot, or single battery: record silently.
            prefs.setLastBootId(bootId)
        }
    }

    fun setActiveBattery(id: Long) {
        viewModelScope.launch { prefs.setActiveBatteryId(id) }
    }

    /**
     * @param installedNow true if the user just put this battery in. When set, it
     * becomes active and everything recorded since the device booted is attributed
     * to it — the common "swapped, then registered the new battery" flow, and the
     * only way to attribute the first swap before a second profile existed.
     */
    fun addBattery(label: String, ratedMah: Int, installedNow: Boolean) {
        viewModelScope.launch {
            val id = repository.addBatteryProfile(label, ratedMah)
            if (installedNow) {
                prefs.setActiveBatteryId(id)
                reassignSinceBoot(currentBootId(), id)
            }
        }
    }

    /** User picked which battery is installed after a reboot. */
    fun confirmSwap(batteryId: Long) {
        viewModelScope.launch {
            val boot = if (pendingBootId != 0L) pendingBootId else currentBootId()
            prefs.setActiveBatteryId(batteryId)
            // Any sessions/events recorded since this boot were stamped with the
            // previously-active battery — re-attribute them to the chosen one.
            reassignSinceBoot(boot, batteryId)
            prefs.setLastBootId(boot)
            _swapPrompt.value = false
        }
    }

    fun dismissSwap() {
        viewModelScope.launch {
            if (pendingBootId != 0L) prefs.setLastBootId(pendingBootId)
            _swapPrompt.value = false
        }
    }

    /**
     * Manually move everything recorded since the last device restart to a battery
     * and make it active. Recovers from a mis-tapped or dismissed swap prompt.
     */
    fun attributeSinceRestart(batteryId: Long) {
        viewModelScope.launch {
            prefs.setActiveBatteryId(batteryId)
            reassignSinceBoot(currentBootId(), batteryId)
        }
    }

    private fun currentBootId(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    // Reassign with a safety margin: the computed boot time can drift a few seconds
    // as the clock syncs after boot, which would otherwise miss the first session.
    // A reboot's downtime is far longer than the margin, so pre-reboot sessions are
    // never caught.
    private suspend fun reassignSinceBoot(bootId: Long, batteryId: Long) =
        repository.reassignRecordsSince(bootId - REASSIGN_MARGIN_MS, batteryId)

    private companion object {
        const val REASSIGN_MARGIN_MS = 60_000L
    }

    private fun buildComparison(
        profile: BatteryProfile,
        sessions: List<ChargeSession>,
        activeId: Long
    ): BatteryComparison {
        val completed = sessions.filter { !it.isOpen }
        // Only a session that actually reached full tells us anything about
        // capacity — the fuel gauge counts charge remaining, not total.
        val capacityMah = completed
            .filter { (it.endPercent ?: 0) >= HealthCalculator.FULL_CHARGE_PERCENT }
            .mapNotNull { it.maxChargeCounterMicroAmpHours }
            .maxOrNull()?.div(1000)
        val cyclesUsed = completed.mapNotNull { it.weightedCycleCost }.sum()
        val temps = completed.mapNotNull { it.peakTemperatureCelsius }
        val avgTemp = if (temps.isNotEmpty()) temps.average().toFloat() else null
        val healthPercent = capacityMah?.let {
            ((it.toFloat() / profile.ratedMah) * 100f).roundToInt().coerceIn(0, 100)
        }
        return BatteryComparison(
            profile = profile,
            isActive = profile.id == activeId,
            completedSessions = completed.size,
            capacityMah = capacityMah,
            cyclesUsed = cyclesUsed,
            avgPeakTempC = avgTemp,
            healthPercent = healthPercent
        )
    }
}
