package com.king.mob_app_assignment.ui.auth

/**
 * Pure, Android-framework-free validation rules shared by the registration form - extracted out
 * of [RegisterViewModel] specifically so this logic can be unit-tested directly (a `private fun`
 * on a `@HiltViewModel` can't be called from a plain JUnit test without reflection). Email
 * validation is NOT included here - it goes through `android.util.Patterns.EMAIL_ADDRESS`, which
 * throws in a plain JUnit test (the real Android framework stub is unmocked) unless Robolectric
 * is added to the project, which wasn't judged worth the added test-infrastructure dependency for
 * one regex call.
 */
object RegistrationValidators {

    private val USERNAME_ALLOWED_CHARS = Regex("^[a-zA-Z0-9_]+\$")

    fun validateUsername(username: String): String? = when {
        username.isBlank() -> "Username is required"
        username.trim().length < 3 -> "Username must be at least 3 characters"
        !username.trim().matches(USERNAME_ALLOWED_CHARS) -> "Username can only contain letters, numbers, and underscores"
        else -> null
    }

    fun validatePassword(password: String): String? =
        if (password.length < 6) "Password must be at least 6 characters" else null

    fun validateConfirmPassword(password: String, confirmPassword: String): String? =
        if (confirmPassword != password) "Passwords do not match" else null

    fun validateDisplayName(displayName: String): String? =
        if (displayName.isBlank()) "Name is required" else null
}
