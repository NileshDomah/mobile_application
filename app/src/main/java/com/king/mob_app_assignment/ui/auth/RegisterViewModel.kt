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
    val password: String = "",
    val confirmPassword: String = "",
    val displayNameError: String? = null,
    val usernameError: String? = null,
    val emailError: String? = null,
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

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, confirmPasswordError = null, errorMessage = null) }
    }

    fun register() {
        val state = _uiState.value
        val displayNameError = if (state.displayName.isBlank()) "Name is required" else null
        val usernameError = validateUsername(state.username)
        val emailError = validateEmail(state.email)
        val passwordError = if (state.password.length < 6) "Password must be at least 6 characters" else null
        val confirmPasswordError = if (state.confirmPassword != state.password) "Passwords do not match" else null

        if (displayNameError != null || usernameError != null || emailError != null ||
            passwordError != null || confirmPasswordError != null
        ) {
            _uiState.update {
                it.copy(
                    displayNameError = displayNameError,
                    usernameError = usernameError,
                    emailError = emailError,
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

            val result = authRepository.register(
                state.email.trim(),
                state.password,
                state.displayName.trim(),
                state.username.trim(),
            )
            result
                .onSuccess {
                    securePrefsManager.lastUsedEmail = state.email.trim()
                    _uiState.update { it.copy(isLoading = false, isRegisterSuccessful = true) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = throwable.message ?: "Registration failed")
                    }
                }
        }
    }

    private fun validateUsername(username: String): String? = when {
        username.isBlank() -> "Username is required"
        username.trim().length < 3 -> "Username must be at least 3 characters"
        !username.trim().matches(Regex("^[a-zA-Z0-9_]+$")) -> "Username can only contain letters, numbers, and underscores"
        else -> null
    }

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Email is required"
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Enter a valid email"
        else -> null
    }
}
