package com.king.mob_app_assignment.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.shakeDataStore by preferencesDataStore(name = "shake_settings")

// Threshold expressed as an acceleration-magnitude delta in m/s^2 above gravity baseline.
// Range chosen empirically: 8 (very sensitive) .. 25 (requires a hard shake).
//
// DEFAULT_THRESHOLD lowered 15 -> 10 (2026-09-11, user-reported "shaking isn't working at all" on
// a real physical device, after the emulator tested fine). Root cause: ShakeDetector applies a
// single-pole low-pass filter (LOW_PASS_ALPHA) to the raw accelerometer signal BEFORE computing
// the delta compared against this threshold - that filter meaningfully attenuates the peak
// magnitude of a real, fast hand-shake (real hardware sensor noise/spikes are exactly what the
// filter is designed to smooth away, but a deliberate shake's own peak gets smoothed too, just
// less). The Android emulator's simulated accelerometer produces suspiciously clean, high-
// amplitude synthetic swings that sailed past threshold=15 even after filtering, which is why
// this looked fine on the emulator ("virtual build works") but a real hand shaking a real phone
// often never produced a post-filter delta above 15 at all. 10 leaves real headroom above
// typical walking/handling jitter while actually being reachable by a real shake gesture.
object ShakePrefsKeys {
    val SENSITIVITY = doublePreferencesKey("shake_sensitivity")
    const val MIN_THRESHOLD = 8.0
    const val MAX_THRESHOLD = 25.0
    const val DEFAULT_THRESHOLD = 10.0
}

// Empty tag means "follow system default" - mirrors what
// AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) expects.
private val APP_LANGUAGE_TAG = stringPreferencesKey("app_language_tag")
const val LANGUAGE_TAG_SYSTEM_DEFAULT = ""
const val LANGUAGE_TAG_ENGLISH = "en"
const val LANGUAGE_TAG_SPANISH = "es"
const val LANGUAGE_TAG_FRENCH = "fr"

private val BACKGROUND_SHAKE_ENABLED = booleanPreferencesKey("background_shake_enabled")

// Per-category notification toggles, backing NotificationSettingsScreen. Both default to true
// (opt-out, not opt-in) so a citizen who never opens this screen still gets the notifications
// they'd expect from a freshly-installed app - these gate whether NotificationHelper's own
// notifyStatusChanged/notifyClassificationFailed/notifyClassificationSucceeded calls actually
// post anything, not whether the underlying listeners/workers run at all (those still run
// regardless, since e.g. the status-change listener also drives other in-app state).
private val NOTIFY_STATUS_UPDATES = booleanPreferencesKey("notify_status_updates")
private val NOTIFY_AI_CLASSIFICATION = booleanPreferencesKey("notify_ai_classification")

@Singleton
class ShakePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val thresholdFlow: Flow<Float> = context.shakeDataStore.data.map { prefs ->
        (prefs[ShakePrefsKeys.SENSITIVITY] ?: ShakePrefsKeys.DEFAULT_THRESHOLD).toFloat()
    }

    suspend fun setThreshold(value: Float) {
        context.shakeDataStore.edit { prefs ->
            prefs[ShakePrefsKeys.SENSITIVITY] = value.toDouble()
        }
    }

    val languageTagFlow: Flow<String> = context.shakeDataStore.data.map { prefs ->
        prefs[APP_LANGUAGE_TAG] ?: LANGUAGE_TAG_SYSTEM_DEFAULT
    }

    suspend fun setLanguageTag(tag: String) {
        context.shakeDataStore.edit { prefs ->
            prefs[APP_LANGUAGE_TAG] = tag
        }
    }

    val backgroundShakeEnabledFlow: Flow<Boolean> = context.shakeDataStore.data.map { prefs ->
        prefs[BACKGROUND_SHAKE_ENABLED] ?: false
    }

    suspend fun setBackgroundShakeEnabled(enabled: Boolean) {
        context.shakeDataStore.edit { prefs ->
            prefs[BACKGROUND_SHAKE_ENABLED] = enabled
        }
    }

    val notifyStatusUpdatesFlow: Flow<Boolean> = context.shakeDataStore.data.map { prefs ->
        prefs[NOTIFY_STATUS_UPDATES] ?: true
    }

    suspend fun setNotifyStatusUpdates(enabled: Boolean) {
        context.shakeDataStore.edit { prefs -> prefs[NOTIFY_STATUS_UPDATES] = enabled }
    }

    val notifyAiClassificationFlow: Flow<Boolean> = context.shakeDataStore.data.map { prefs ->
        prefs[NOTIFY_AI_CLASSIFICATION] ?: true
    }

    suspend fun setNotifyAiClassification(enabled: Boolean) {
        context.shakeDataStore.edit { prefs -> prefs[NOTIFY_AI_CLASSIFICATION] = enabled }
    }
}
