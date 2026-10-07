package com.king.mob_app_assignment.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.util.ShakePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [NotificationSettingsScreen] - lets a citizen turn off either of the two notification
 * categories this app actually sends (report status updates, AI classification results), gated
 * in NotificationHelper via the same ShakePreferences flows this reads/writes. There is no third
 * "new support message" toggle here since that notification is admin-only (see
 * AdminSupportNotifier), not something a citizen's own device ever receives.
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val shakePreferences: ShakePreferences,
) : ViewModel() {

    val statusUpdatesEnabled: StateFlow<Boolean> = shakePreferences.notifyStatusUpdatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val aiClassificationEnabled: StateFlow<Boolean> = shakePreferences.notifyAiClassificationFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun onStatusUpdatesToggle(enabled: Boolean) {
        viewModelScope.launch { shakePreferences.setNotifyStatusUpdates(enabled) }
    }

    fun onAiClassificationToggle(enabled: Boolean) {
        viewModelScope.launch { shakePreferences.setNotifyAiClassification(enabled) }
    }
}
