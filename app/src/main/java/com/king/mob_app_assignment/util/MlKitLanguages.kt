package com.king.mob_app_assignment.util

import com.google.mlkit.nl.translate.TranslateLanguage

/**
 * The "more languages" catalog for the on-device machine-translation path (see
 * TranslationRepository) - deliberately separate from LANGUAGE_TAG_ENGLISH/SPANISH/FRENCH in
 * ShakePreferences, which are the app's own hand-written, reviewed translations and need no
 * download. Everything here is instead translated live via a downloaded ML Kit model, on
 * explicit request ("can we get a language pack") rather than replacing the existing
 * ES/FR strings.xml files, which stay exactly as accurate as they already are.
 *
 * ML Kit's TranslateLanguage constants are its own internal short codes (mostly but not always
 * identical to BCP-47 - e.g. "iw" for Hebrew, not "he") - displayTag is the real BCP-47 tag this
 * app's preferences/AppCompatDelegate-adjacent code should store and display, separate from
 * whatever ML Kit's own API expects when opening a Translator.
 */
data class MlKitLanguage(
    val mlKitCode: String,
    val displayTag: String,
    val displayName: String,
)

object MlKitLanguages {
    // English/Spanish/French excluded on purpose - those already have real, reviewed
    // translations via the existing values-es/values-fr resource files, no download needed.
    val available: List<MlKitLanguage> = listOf(
        MlKitLanguage(TranslateLanguage.GERMAN, "de", "Deutsch"),
        MlKitLanguage(TranslateLanguage.PORTUGUESE, "pt", "Português"),
        MlKitLanguage(TranslateLanguage.ITALIAN, "it", "Italiano"),
        MlKitLanguage(TranslateLanguage.DUTCH, "nl", "Nederlands"),
        MlKitLanguage(TranslateLanguage.RUSSIAN, "ru", "Русский"),
        MlKitLanguage(TranslateLanguage.POLISH, "pl", "Polski"),
        MlKitLanguage(TranslateLanguage.TURKISH, "tr", "Türkçe"),
        MlKitLanguage(TranslateLanguage.ARABIC, "ar", "العربية"),
        MlKitLanguage(TranslateLanguage.HEBREW, "iw", "עברית"),
        MlKitLanguage(TranslateLanguage.HINDI, "hi", "हिन्दी"),
        MlKitLanguage(TranslateLanguage.BENGALI, "bn", "বাংলা"),
        MlKitLanguage(TranslateLanguage.URDU, "ur", "اردو"),
        MlKitLanguage(TranslateLanguage.CHINESE, "zh", "中文"),
        MlKitLanguage(TranslateLanguage.JAPANESE, "ja", "日本語"),
        MlKitLanguage(TranslateLanguage.KOREAN, "ko", "한국어"),
        MlKitLanguage(TranslateLanguage.VIETNAMESE, "vi", "Tiếng Việt"),
        MlKitLanguage(TranslateLanguage.THAI, "th", "ไทย"),
        MlKitLanguage(TranslateLanguage.INDONESIAN, "id", "Bahasa Indonesia"),
        MlKitLanguage(TranslateLanguage.MALAY, "ms", "Bahasa Melayu"),
        MlKitLanguage(TranslateLanguage.SWAHILI, "sw", "Kiswahili"),
        MlKitLanguage(TranslateLanguage.GREEK, "el", "Ελληνικά"),
        MlKitLanguage(TranslateLanguage.SWEDISH, "sv", "Svenska"),
        MlKitLanguage(TranslateLanguage.NORWEGIAN, "no", "Norsk"),
        MlKitLanguage(TranslateLanguage.DANISH, "da", "Dansk"),
        MlKitLanguage(TranslateLanguage.FINNISH, "fi", "Suomi"),
        MlKitLanguage(TranslateLanguage.CZECH, "cs", "Čeština"),
        MlKitLanguage(TranslateLanguage.ROMANIAN, "ro", "Română"),
        MlKitLanguage(TranslateLanguage.HUNGARIAN, "hu", "Magyar"),
        MlKitLanguage(TranslateLanguage.UKRAINIAN, "uk", "Українська"),
        MlKitLanguage(TranslateLanguage.PERSIAN, "fa", "فارسی"),
        MlKitLanguage(TranslateLanguage.TAGALOG, "tl", "Tagalog"),
        MlKitLanguage(TranslateLanguage.AFRIKAANS, "af", "Afrikaans"),
        MlKitLanguage(TranslateLanguage.CROATIAN, "hr", "Hrvatski"),
        MlKitLanguage(TranslateLanguage.BULGARIAN, "bg", "Български"),
        MlKitLanguage(TranslateLanguage.SLOVAK, "sk", "Slovenčina"),
        MlKitLanguage(TranslateLanguage.CATALAN, "ca", "Català"),
    ).sortedBy { it.displayName }

    fun findByDisplayTag(tag: String): MlKitLanguage? = available.firstOrNull { it.displayTag == tag }
}
