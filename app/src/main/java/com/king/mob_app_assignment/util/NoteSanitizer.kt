package com.king.mob_app_assignment.util

object NoteSanitizer {

    const val MAX_NOTE_LENGTH = 500

    private val TAG_REGEX = Regex("<[^>]*>")

    fun sanitizeNote(input: String): String {
        val stripped = TAG_REGEX.replace(input, "")
        val trimmed = stripped.trim()
        return if (trimmed.length > MAX_NOTE_LENGTH) trimmed.take(MAX_NOTE_LENGTH) else trimmed
    }
}
