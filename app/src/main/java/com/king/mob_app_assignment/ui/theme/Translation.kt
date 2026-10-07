package com.king.mob_app_assignment.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.king.mob_app_assignment.util.MlKitLanguages
import com.king.mob_app_assignment.util.TranslationRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Which ML Kit language (its display BCP-47 tag, e.g. "de") the whole composition should
 * translate its text into via translatedStringResource() - empty string means "none active",
 * i.e. the app should render its own native stringResource() text (English/Spanish/French,
 * driven by AppCompatDelegate's locale as it always has been). Provided once near the app root
 * (see AppTheme/MainActivity) from SettingsViewModel/TranslationPreferences' own flow, so every
 * screen below it picks the active language up automatically without manual plumbing.
 */
val LocalActiveMlKitLanguageTag = staticCompositionLocalOf { "" }

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface TranslationRepositoryEntryPoint {
    fun translationRepository(): TranslationRepository
}

/**
 * Drop-in replacement for stringResource() at any call site that wants ML Kit "more languages"
 * support: renders the normal English resource string immediately (so there's never a blank/
 * loading flash), then swaps in the ML Kit translation once it resolves, if a pack language is
 * currently active. When no ML Kit language is active this is functionally identical to plain
 * stringResource() - the produceState block below short-circuits without ever calling the
 * translator.
 *
 * Deliberately NOT a full replacement for all 362 existing stringResource() call sites across
 * the app in one pass - callers opt in screen-by-screen (see rollout note in SettingsScreen),
 * since ML Kit's live-translated text will read a little more stiffly than the hand-reviewed
 * English/Spanish/French and every call site needs its own visual check for wrapping/truncation
 * once translated.
 */
@Composable
fun translatedStringResource(id: Int): String {
    val fallback = stringResource(id)
    val activeTag = LocalActiveMlKitLanguageTag.current
    if (activeTag.isEmpty()) return fallback

    val context = LocalContext.current
    val repository = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            TranslationRepositoryEntryPoint::class.java,
        ).translationRepository()
    }
    val mlKitLanguage = remember(activeTag) { MlKitLanguages.findByDisplayTag(activeTag) }

    val translated by produceState(initialValue = fallback, key1 = activeTag, key2 = fallback) {
        val language = mlKitLanguage
        if (language == null) {
            value = fallback
            return@produceState
        }
        runCatching { repository.translate(fallback, language.mlKitCode) }
            .onSuccess { value = it }
        // On failure, deliberately leave `value` as the English fallback it started with -
        // silently showing English is a far better failure mode here than a crash or blank text.
    }
    return translated
}

@Composable
fun translatedStringResource(id: Int, vararg formatArgs: Any): String {
    val fallback = stringResource(id, *formatArgs)
    val activeTag = LocalActiveMlKitLanguageTag.current
    if (activeTag.isEmpty()) return fallback

    val context = LocalContext.current
    val repository = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            TranslationRepositoryEntryPoint::class.java,
        ).translationRepository()
    }
    val mlKitLanguage = remember(activeTag) { MlKitLanguages.findByDisplayTag(activeTag) }

    val translated by produceState(initialValue = fallback, key1 = activeTag, key2 = fallback) {
        val language = mlKitLanguage
        if (language == null) {
            value = fallback
            return@produceState
        }
        runCatching { repository.translate(fallback, language.mlKitCode) }
            .onSuccess { value = it }
    }
    return translated
}
