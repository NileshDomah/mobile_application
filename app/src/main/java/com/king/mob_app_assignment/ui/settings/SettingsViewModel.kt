package com.king.mob_app_assignment.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.util.ShakePrefsKeys
import com.king.mob_app_assignment.util.ShakePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val shakePreferences: ShakePreferences,
) : ViewModel() {

    val threshold: StateFlow<Float> = shakePreferences.thresholdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShakePrefsKeys.DEFAULT_THRESHOLD.toFloat())

    fun onThresholdChange(value: Float) {
        viewModelScope.launch {
            shakePreferences.setThreshold(value)
        }
    }
}
