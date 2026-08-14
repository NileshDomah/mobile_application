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
// Range chosen empirically: 8 (very sensitive) .. 25 (requires a hard shake), 15 is the default.
object ShakePrefsKeys {
    val SENSITIVITY = doublePreferencesKey("shake_sensitivity")
    const val MIN_THRESHOLD = 8.0
    const val MAX_THRESHOLD = 25.0
    const val DEFAULT_THRESHOLD = 15.0
}

// Empty tag means "follow system default" - mirrors what
// AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) expects.
private val APP_LANGUAGE_TAG = stringPreferencesKey("app_language_tag")
const val LANGUAGE_TAG_SYSTEM_DEFAULT = ""
const val LANGUAGE_TAG_ENGLISH = "en"
const val LANGUAGE_TAG_SPANISH = "es"
const val LANGUAGE_TAG_FRENCH = "fr"

private val BACKGROUND_SHAKE_ENABLED = booleanPreferencesKey("background_shake_enabled")

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
}
