package com.king.mob_app_assignment.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteSanitizerTest {

    // sanitizeNote() only trims LEADING whitespace live, on purpose - see its own doc comment.
    // Trailing whitespace must survive here so a user can type "word " and keep going without the
    // space they just typed vanishing before their next keystroke lands. The real trim-both-ends
    // pass only happens once, at submit time, via sanitizeNoteForSubmit() below.
    @Test
    fun trimsLeadingWhitespaceOnlyLive() {
        assertEquals("hello  ", NoteSanitizer.sanitizeNote("  hello  "))
    }

    @Test
    fun trimsBothEndsAtSubmitTime() {
        assertEquals("hello", NoteSanitizer.sanitizeNoteForSubmit("  hello  "))
    }

    @Test
    fun stripsHtmlAndScriptTags() {
        assertEquals(
            "alert",
            NoteSanitizer.sanitizeNote("<script>alert</script>"),
        )
    }

    @Test
    fun stripsPlainTags() {
        assertEquals("Bold text", NoteSanitizer.sanitizeNote("<b>Bold text</b>"))
    }

    @Test
    fun truncatesToMaxLength() {
        val input = "a".repeat(600)
        val result = NoteSanitizer.sanitizeNote(input)
        assertEquals(NoteSanitizer.MAX_NOTE_LENGTH, result.length)
    }

    @Test
    fun emptyInputReturnsEmpty() {
        assertEquals("", NoteSanitizer.sanitizeNote("   "))
    }
}
