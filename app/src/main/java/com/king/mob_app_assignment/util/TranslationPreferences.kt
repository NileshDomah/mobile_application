package com.king.mob_app_assignment.util

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.translationDataStore by preferencesDataStore(name = "translation_settings")

// Empty means "no ML Kit language active" - the app is on one of its own native
// LANGUAGE_TAG_ENGLISH/SPANISH/FRENCH translations (or system default) instead. Distinct from
// ShakePreferences' own APP_LANGUAGE_TAG key: that one drives AppCompatDelegate's real locale
// (affects date/number formatting, RTL layout direction, the system's own UI chrome too), this
// one only decides whether translatedStringResource() calls should route text through a
// downloaded ML Kit model. The two are set together (see SettingsViewModel.onMlKitLanguageChange)
// but kept as separate keys/flows since they answer genuinely different questions.
private val ACTIVE_MLKIT_LANGUAGE_TAG = stringPreferencesKey("active_mlkit_language_tag")

// Every displayTag whose model has completed a download at least once - lets the language picker
// show "Downloaded" vs "Download" per language without re-querying ML Kit's own on-device model
// manager every time (RemoteModelManager.getDownloadedModels() is itself an async Task, this is
// just a faster, synchronous-enough cache of the same fact for the picker's own UI).
private val DOWNLOADED_LANGUAGE_TAGS = stringSetPreferencesKey("downloaded_mlkit_language_tags")

@Singleton
class TranslationPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val activeMlKitLanguageTagFlow: Flow<String> = context.translationDataStore.data.map { prefs ->
        prefs[ACTIVE_MLKIT_LANGUAGE_TAG] ?: ""
    }

    suspend fun setActiveMlKitLanguageTag(tag: String) {
        context.translationDataStore.edit { prefs -> prefs[ACTIVE_MLKIT_LANGUAGE_TAG] = tag }
    }

    val downloadedLanguageTagsFlow: Flow<Set<String>> = context.translationDataStore.data.map { prefs ->
        prefs[DOWNLOADED_LANGUAGE_TAGS] ?: emptySet()
    }

    suspend fun markDownloaded(tag: String) {
        context.translationDataStore.edit { prefs ->
            prefs[DOWNLOADED_LANGUAGE_TAGS] = (prefs[DOWNLOADED_LANGUAGE_TAGS] ?: emptySet()) + tag
        }
    }
}
