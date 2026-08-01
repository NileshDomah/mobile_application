package com.king.mob_app_assignment.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteSanitizerTest {

    @Test
    fun trimsWhitespace() {
        assertEquals("hello", NoteSanitizer.sanitizeNote("  hello  "))
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
