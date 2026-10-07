package com.king.mob_app_assignment.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForgotCredentialsUiState(
    val email: String = "",
    val emailError: String? = null,
    // Distinct loading flags rather than one shared isLoading - the two actions (username
    // lookup, password reset email) are independent and a user could plausibly want to trigger
    // one right after the other; sharing a single flag would disable both buttons while only one
    // request is in flight, for no real benefit.
    val isLookingUpUsername: Boolean = false,
    val isSendingResetEmail: Boolean = false,
    // Null = not yet attempted. Distinguished from "attempted but found nothing" (an empty
    // string is never a valid username here) so the UI only shows a result card after a real
    // attempt, not on first screen load.
    val foundUsername: String? = null,
    val usernameLookupAttempted: Boolean = false,
    val resetEmailSent: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ForgotCredentialsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotCredentialsUiState())
    val uiState: StateFlow<ForgotCredentialsUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                emailError = null,
                errorMessage = null,
                usernameLookupAttempted = false,
                foundUsername = null,
                resetEmailSent = false,
            )
        }
    }

    private fun validatedEmail(): String? {
        val email = _uiState.value.email.trim()
        if (email.isBlank() || !email.contains("@")) {
            _uiState.update { it.copy(emailError = "Enter a valid email address") }
            return null
        }
        return email
    }

    fun findUsername() {
        val email = validatedEmail() ?: return
        _uiState.update { it.copy(isLookingUpUsername = true, errorMessage = null) }
        viewModelScope.launch {
            val username = runCatching { authRepository.resolveEmailToUsername(email) }.getOrNull()
            _uiState.update {
                it.copy(
                    isLookingUpUsername = false,
                    usernameLookupAttempted = true,
                    foundUsername = username,
                )
            }
        }
    }

    fun sendPasswordResetEmail() {
        val email = validatedEmail() ?: return
        _uiState.update { it.copy(isSendingResetEmail = true, errorMessage = null, resetEmailSent = false) }
        viewModelScope.launch {
            authRepository.sendPasswordResetEmail(email)
                .onSuccess {
                    _uiState.update { it.copy(isSendingResetEmail = false, resetEmailSent = true) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isSendingResetEmail = false,
                            errorMessage = throwable.message ?: "Couldn't send the reset email - please try again.",
                        )
                    }
                }
        }
    }
}
