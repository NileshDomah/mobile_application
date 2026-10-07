package com.king.mob_app_assignment.util

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the on-device ML Kit Translator for whichever "more languages" pack is currently active
 * (see TranslationPreferences) and caches translated strings so the same English source string
 * is never re-translated on every recomposition - translatedStringResource() calls this on
 * every call site that opts in, and Compose recomposes constantly, so caching here (not just
 * relying on ML Kit's own internal caching, which is undocumented/not guaranteed) is what keeps
 * this from re-running a model inference per frame.
 *
 * A genuinely new mechanism from the app's existing values-es/values-fr resource files (see
 * MlKitLanguages' own doc comment for why those two stay untouched) - this translates raw TEXT
 * at runtime via a downloaded model, it has no knowledge of Android string resources at all.
 */
@Singleton
class TranslationRepository @Inject constructor(
    private val translationPreferences: TranslationPreferences,
) {
    private var currentTranslator: Translator? = null
    private var currentLanguageCode: String? = null

    // Keyed by "languageCode:sourceText" so switching the active language doesn't need the cache
    // cleared - a stale translation for a language that's no longer active is simply never read
    // again, and this avoids the memory churn of wiping the whole cache on every language switch.
    private val translationCache = ConcurrentHashMap<String, String>()

    /**
     * Downloads the model for [language] if not already present on-device, respecting
     * [wifiOnly]. Suspends until the download completes (or fails) - the caller (the language
     * picker screen) is expected to show its own progress UI while this is in flight, ML Kit's
     * Task API doesn't expose incremental byte-level progress, only completion.
     */
    suspend fun downloadLanguage(language: MlKitLanguage, wifiOnly: Boolean): Result<Unit> = runCatching {
        val translator = translatorFor(language.mlKitCode)
        val conditions = DownloadConditions.Builder()
            .apply { if (wifiOnly) requireWifi() }
            .build()
        translator.downloadModelIfNeeded(conditions).await()
        translationPreferences.markDownloaded(language.displayTag)
    }

    /**
     * Translates [text] from English into [languageCode] (an ML Kit TranslateLanguage constant,
     * not the display BCP-47 tag - see MlKitLanguage's own doc comment on the distinction).
     * Assumes the model is already downloaded (translatedStringResource only calls this once the
     * active language's pack download has completed - see SettingsViewModel/the language
     * picker's own gating) - if it isn't, ML Kit surfaces a clear "model not downloaded"
     * exception rather than silently downloading on the UI thread, which this deliberately does
     * not catch-and-hide, so a genuine misuse doesn't fail silently into showing raw English with
     * no explanation.
     */
    suspend fun translate(text: String, languageCode: String): String {
        if (text.isBlank()) return text
        val cacheKey = "$languageCode:$text"
        translationCache[cacheKey]?.let { return it }

        val translator = translatorFor(languageCode)
        val result = translator.translate(text).await()
        translationCache[cacheKey] = result
        return result
    }

    private fun translatorFor(languageCode: String): Translator {
        val existing = currentTranslator
        if (existing != null && currentLanguageCode == languageCode) return existing

        existing?.close()
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(languageCode)
            .build()
        val translator = Translation.getClient(options)
        currentTranslator = translator
        currentLanguageCode = languageCode
        return translator
    }
}
