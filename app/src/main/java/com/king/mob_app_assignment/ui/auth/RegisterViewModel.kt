package com.king.mob_app_assignment.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.util.SecurePrefsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    // Optional - collected so admins can see who filed a report and, in future, contact them
    // directly, but registration does not hard-block on it (see validatePhoneNumber below).
    val phoneNumber: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val displayNameError: String? = null,
    val usernameError: String? = null,
    val emailError: String? = null,
    val phoneNumberError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isRegisterSuccessful: Boolean = false,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val securePrefsManager: SecurePrefsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onDisplayNameChange(value: String) {
        _uiState.update { it.copy(displayName = value, displayNameError = null, errorMessage = null) }
    }

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, usernameError = null, errorMessage = null) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }
    }

    fun onPhoneNumberChange(value: String) {
        _uiState.update { it.copy(phoneNumber = value, phoneNumberError = null, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, confirmPasswordError = null, errorMessage = null) }
    }

    fun register() {
        val state = _uiState.value
        val displayNameError = RegistrationValidators.validateDisplayName(state.displayName)
        val usernameError = RegistrationValidators.validateUsername(state.username)
        val emailError = validateEmail(state.email)
        val phoneNumberError = validatePhoneNumber(state.phoneNumber)
        val passwordError = RegistrationValidators.validatePassword(state.password)
        val confirmPasswordError = RegistrationValidators.validateConfirmPassword(state.password, state.confirmPassword)

        if (displayNameError != null || usernameError != null || emailError != null ||
            phoneNumberError != null || passwordError != null || confirmPasswordError != null
        ) {
            _uiState.update {
                it.copy(
                    displayNameError = displayNameError,
                    usernameError = usernameError,
                    emailError = emailError,
                    phoneNumberError = phoneNumberError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmPasswordError,
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            // Also re-checked transactionally inside register() itself, right before the real
            // reservation write - this earlier check is just for a faster/friendlier error
            // message in the common case, not the actual uniqueness guarantee.
            if (authRepository.isUsernameTaken(state.username.trim())) {
                _uiState.update {
                    it.copy(isLoading = false, usernameError = "That username is already taken")
                }
                return@launch
            }

            // See LoginViewModel's identical fix for the full root-cause explanation - set BEFORE
            // calling register(), not inside its .onSuccess{}. authRepository.register() calls
            // Firebase's own createUserWithEmailAndPassword() internally, which fires every
            // registered AuthStateListener (including MainActivity's completely separate one)
            // the instant account creation completes - possibly before this coroutine even
            // resumes past register()'s own await() to reach a line after it. Setting the
            // credentials pre-emptively means they're already in place no matter which listener
            // reacts first.
            com.king.mob_app_assignment.util.PendingAppLockCredentials.set(state.email.trim(), state.password)

            val result = authRepository.register(
                state.email.trim(),
                state.password,
                state.displayName.trim(),
                state.username.trim(),
                state.phoneNumber.trim(),
            )
            result
                .onSuccess {
                    securePrefsManager.lastUsedEmail = state.email.trim()
                    _uiState.update { it.copy(isLoading = false, isRegisterSuccessful = true) }
                }
                .onFailure { throwable ->
                    // A failed registration must not leave stale credentials sitting around.
                    com.king.mob_app_assignment.util.PendingAppLockCredentials.consume()
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = throwable.message ?: "Registration failed")
                    }
                }
        }
    }

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Email is required"
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Enter a valid email"
        else -> null
    }

    // Deliberately lenient (digits, spaces, +, -, parentheses only, 7-15 digits) rather than a
    // strict E.164/libphonenumber-style check - this project has no international-dialing-code
    // picker or SMS verification anywhere, so over-validating a format the rest of the app never
    // actually uses (no SMS is ever sent) would only frustrate real users typing a real number in
    // whatever local format they're used to.
    private fun validatePhoneNumber(phoneNumber: String): String? {
        val trimmed = phoneNumber.trim()
        if (trimmed.isBlank()) return "Phone number is required"
        val digitCount = trimmed.count { it.isDigit() }
        val hasOnlyAllowedChars = trimmed.all { it.isDigit() || it in "+ -()" }
        return if (!hasOnlyAllowedChars || digitCount < 7 || digitCount > 15) {
            "Enter a valid phone number"
        } else {
            null
        }
    }
}
