package com.batterybuddy.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterybuddy.data.preferences.UserPreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val prefs: UserPreferencesStore
) : ViewModel() {

    val hasCompletedOnboarding: StateFlow<Boolean> = prefs.hasCompletedOnboarding
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false 
        )

    fun completeOnboarding(deviceModel: String, ratedMah: Int) {
        viewModelScope.launch {
            prefs.setDeviceModel(deviceModel)
            prefs.setRatedMahOverride(ratedMah)
            prefs.setHasCompletedOnboarding(true)
        }
    }
}
