package com.king.mob_app_assignment.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.util.LANGUAGE_TAG_SYSTEM_DEFAULT
import com.king.mob_app_assignment.util.ShakePrefsKeys
import com.king.mob_app_assignment.util.ShakePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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

    val languageTag: StateFlow<String> = shakePreferences.languageTagFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LANGUAGE_TAG_SYSTEM_DEFAULT)

    // AppCompatDelegate.setApplicationLocales() only auto-recreates the Activity when it
    // extends AppCompatActivity - MainActivity is a plain ComponentActivity (this app is
    // Compose-only, no other AppCompat/View dependency), so the locale was being persisted
    // correctly but nothing ever told the UI to re-resolve string resources against it: every
    // stringResource() call kept returning the stale strings until something else happened to
    // recreate the Activity (device rotation, app kill). This event tells MainActivity to call
    // recreate() itself right after the locale change is applied, fixing "language stays English".
    private val _recreateActivityEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val recreateActivityEvents: SharedFlow<Unit> = _recreateActivityEvents

    fun onThresholdChange(value: Float) {
        viewModelScope.launch {
            shakePreferences.setThreshold(value)
        }
    }

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
        _recreateActivityEvents.tryEmit(Unit)
    }
}
