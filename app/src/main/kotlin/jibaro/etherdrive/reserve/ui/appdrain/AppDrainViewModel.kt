package jibaro.etherdrive.reserve.ui.appdrain

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jibaro.etherdrive.reserve.data.repository.BatteryRepository
import jibaro.etherdrive.reserve.data.usage.UsageStatsReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppDrainRow(
    val packageName: String,
    val label: String,
    val foregroundMinutes: Long,
    /** Share of sampling windows in which this app was in the foreground, 0f..1f. */
    val presence: Float
)

sealed interface AppDrainUiState {
    object Loading : AppDrainUiState
    /** Usage access is a settings-only grant, so we have to ask for it explicitly. */
    object PermissionNeeded : AppDrainUiState
    object Collecting : AppDrainUiState
    data class Content(val rows: List<AppDrainRow>) : AppDrainUiState
}

@HiltViewModel
class AppDrainViewModel @Inject constructor(
    private val repository: BatteryRepository,
    private val usageStatsReader: UsageStatsReader
) : ViewModel() {

    private val _state = MutableStateFlow<AppDrainUiState>(AppDrainUiState.Loading)
    val state: StateFlow<AppDrainUiState> = _state.asStateFlow()

    fun permissionSettingsIntent(): Intent = usageStatsReader.permissionSettingsIntent()

    /** Recomputed on demand — cheap, and the permission can change while we're backgrounded. */
    fun refresh() {
        viewModelScope.launch {
            if (!usageStatsReader.hasPermission()) {
                _state.value = AppDrainUiState.PermissionNeeded
                return@launch
            }

            val now = System.currentTimeMillis()
            val correlations = repository.getTopAppCorrelations(now - WINDOW_MS, now)
            if (correlations.isEmpty()) {
                _state.value = AppDrainUiState.Collecting
                return@launch
            }

            val rows = withContext(Dispatchers.IO) {
                correlations
                    .filter { it.totalForegroundMillis >= MIN_FOREGROUND_MS }
                    .take(MAX_ROWS)
                    .map {
                        AppDrainRow(
                            packageName       = it.packageName,
                            label             = usageStatsReader.appLabel(it.packageName),
                            foregroundMinutes = it.totalForegroundMillis / 60_000L,
                            presence          = it.presence
                        )
                    }
            }
            _state.value = if (rows.isEmpty()) AppDrainUiState.Collecting else AppDrainUiState.Content(rows)
        }
    }

    private companion object {
        const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000
        const val MIN_FOREGROUND_MS = 60_000L
        const val MAX_ROWS = 5
    }
}
