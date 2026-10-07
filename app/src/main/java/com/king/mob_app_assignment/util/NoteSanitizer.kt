package com.king.mob_app_assignment.util

object NoteSanitizer {

    const val MAX_NOTE_LENGTH = 500

    private val TAG_REGEX = Regex("<[^>]*>")

    // Called on every keystroke (see CaptureViewModel.onNoteChange) - a full .trim() here used to
    // strip a TRAILING space the instant it was typed, since the cursor sits at the end of the
    // text while actively typing. That made it look like the space bar didn't work at all: you
    // could never type "word " and then continue with a second word, because the trailing space
    // was removed before the next keystroke ever landed. Only leading whitespace is stripped
    // live now (harmless to remove as you type, since nobody is relying on a leading space
    // surviving mid-composition); trailing whitespace is left alone here and trimmed once,
    // properly, at actual submit time instead - see sanitizeNoteForSubmit below.
    fun sanitizeNote(input: String): String {
        val stripped = TAG_REGEX.replace(input, "")
        val leadingTrimmed = stripped.trimStart()
        return if (leadingTrimmed.length > MAX_NOTE_LENGTH) leadingTrimmed.take(MAX_NOTE_LENGTH) else leadingTrimmed
    }

    // The real trim-both-ends pass, applied once when the note is actually submitted rather than
    // on every keystroke - trailing whitespace shouldn't be stored, just not stripped while the
    // user is still actively typing it.
    fun sanitizeNoteForSubmit(input: String): String = sanitizeNote(input).trim()
}
