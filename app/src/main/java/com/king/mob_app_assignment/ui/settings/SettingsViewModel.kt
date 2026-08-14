package com.king.mob_app_assignment.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.util.BiometricCredentialStore
import com.king.mob_app_assignment.util.LANGUAGE_TAG_SYSTEM_DEFAULT
import com.king.mob_app_assignment.util.ShakePrefsKeys
import com.king.mob_app_assignment.util.ShakePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val shakePreferences: ShakePreferences,
    private val biometricCredentialStore: BiometricCredentialStore,
) : ViewModel() {

    val threshold: StateFlow<Float> = shakePreferences.thresholdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShakePrefsKeys.DEFAULT_THRESHOLD.toFloat())

    private val _biometricLoginEnabled = MutableStateFlow(biometricCredentialStore.isEnabled())
    val biometricLoginEnabled: StateFlow<Boolean> = _biometricLoginEnabled.asStateFlow()

    // Only ever turns it OFF from here - there is no "turn on" path in Settings, since enabling
    // requires the plaintext password (only available right after a manual login, see
    // LoginViewModel.onEnableBiometricLogin) which Settings never has access to.
    fun disableBiometricLogin() {
        biometricCredentialStore.disable()
        _biometricLoginEnabled.value = false
    }

    val languageTag: StateFlow<String> = shakePreferences.languageTagFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LANGUAGE_TAG_SYSTEM_DEFAULT)

    val backgroundShakeEnabled: StateFlow<Boolean> = shakePreferences.backgroundShakeEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBackgroundShakeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            shakePreferences.setBackgroundShakeEnabled(enabled)
        }
    }

    fun onThresholdChange(value: Float) {
        viewModelScope.launch {
            shakePreferences.setThreshold(value)
        }
    }

    // AppCompatDelegate.setApplicationLocales() only auto-recreates the Activity (correctly,
    // preserving nav/back-stack state) when it extends AppCompatActivity, which MainActivity now
    // does specifically to support this. No manual recreate()/event-plumbing is needed here -
    // AppCompat handles the Activity recreation itself once the locale change is applied.
    fun onLanguageChange(tag: String) {
        viewModelScope.launch {
            shakePreferences.setLanguageTag(tag)
        }
        val locales = if (tag == LANGUAGE_TAG_SYSTEM_DEFAULT) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
