package com.king.mob_app_assignment.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegistrationValidatorsTest {

    @Test
    fun `blank username is rejected`() {
        assertEquals("Username is required", RegistrationValidators.validateUsername(""))
        assertEquals("Username is required", RegistrationValidators.validateUsername("   "))
    }

    @Test
    fun `username shorter than 3 characters is rejected`() {
        assertEquals("Username must be at least 3 characters", RegistrationValidators.validateUsername("ab"))
    }

    @Test
    fun `username with disallowed characters is rejected`() {
        assertEquals(
            "Username can only contain letters, numbers, and underscores",
            RegistrationValidators.validateUsername("nilesh.d"),
        )
        assertEquals(
            "Username can only contain letters, numbers, and underscores",
            RegistrationValidators.validateUsername("nilesh d"),
        )
    }

    @Test
    fun `valid usernames pass with no error`() {
        assertNull(RegistrationValidators.validateUsername("nilesh_d"))
        assertNull(RegistrationValidators.validateUsername("Nilesh123"))
        assertNull(RegistrationValidators.validateUsername("abc"))
    }

    @Test
    fun `username validation trims surrounding whitespace before checking length and characters`() {
        assertNull(RegistrationValidators.validateUsername("  nilesh  "))
    }

    @Test
    fun `password shorter than 6 characters is rejected`() {
        assertEquals("Password must be at least 6 characters", RegistrationValidators.validatePassword("12345"))
        assertNull(RegistrationValidators.validatePassword("123456"))
    }

    @Test
    fun `confirm password must match the password exactly`() {
        assertEquals("Passwords do not match", RegistrationValidators.validateConfirmPassword("secret1", "secret2"))
        assertNull(RegistrationValidators.validateConfirmPassword("secret1", "secret1"))
    }

    @Test
    fun `blank display name is rejected`() {
        assertEquals("Name is required", RegistrationValidators.validateDisplayName(""))
        assertNull(RegistrationValidators.validateDisplayName("Nilesh"))
    }
}
