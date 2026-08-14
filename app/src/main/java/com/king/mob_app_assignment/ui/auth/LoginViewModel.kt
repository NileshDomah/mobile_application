package com.king.mob_app_assignment.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.domain.model.UserRole
import com.king.mob_app_assignment.util.BiometricCredentialStore
import com.king.mob_app_assignment.util.SecurePrefsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    // Accepts either the account's email OR its username - resolved to a real email in login()
    // before ever calling AuthRepository.login(), since Firebase Auth itself only ever signs in
    // by email/password. Kept as one field (not two separate inputs) since from the user's
    // perspective it's a single "how do you want to identify yourself" box, matching how most
    // real apps that support both present a single combined field.
    val emailOrUsername: String = "",
    val password: String = "",
    val rememberMe: Boolean = true,
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false,
    // "Sign in as admin" toggle - purely a UX affordance, not a security gate: the actual
    // permission check is the account's real Firestore role field, verified after login below.
    // There is no admin self-registration anywhere in the app - see RegisterScreen/
    // AuthRepositoryImpl.register(), which always hardcodes CITIZEN - admin accounts only ever
    // come from an admin manually setting role: ADMIN on a user doc in the Firestore console.
    val isAdminLogin: Boolean = false,
    // True once a biometric credential is stored for this device (see BiometricCredentialStore)
    // - drives whether LoginScreen shows the "Sign in with biometrics" button at all. Device-level
    // hardware/enrollment availability is checked separately by the screen via
    // BiometricAuthManager, since that can change without this ViewModel's state changing.
    val isBiometricLoginEnabled: Boolean = false,
    // Shown once, right after a successful manual login, only when biometric hardware is
    // available and not already enabled - "would you like to enable biometric login?"
    val showEnableBiometricPrompt: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val securePrefsManager: SecurePrefsManager,
    private val biometricCredentialStore: BiometricCredentialStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LoginUiState(
            emailOrUsername = securePrefsManager.lastUsedEmail.orEmpty(),
            rememberMe = securePrefsManager.isRememberMeEnabled,
            isBiometricLoginEnabled = biometricCredentialStore.isEnabled(),
        ),
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailOrUsernameChange(value: String) {
        _uiState.update { it.copy(emailOrUsername = value, emailError = null, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    }

    fun onRememberMeChange(value: Boolean) {
        _uiState.update { it.copy(rememberMe = value) }
    }

    fun onAdminLoginToggle(value: Boolean) {
        _uiState.update { it.copy(isAdminLogin = value, errorMessage = null) }
    }

    fun login() {
        val state = _uiState.value
        val identifierError = if (state.emailOrUsername.isBlank()) "Email or username is required" else null
        val passwordError = if (state.password.isBlank()) "Password is required" else null
        if (identifierError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = identifierError, passwordError = passwordError) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            // A username never contains '@' (enforced by RegisterViewModel.validateUsername's
            // letters/digits/underscore-only rule) while every email does - a cheap, reliable
            // way to tell which one the user typed without needing a separate toggle/tab.
            val typedIdentifier = state.emailOrUsername.trim()
            val resolvedEmail = if (typedIdentifier.contains("@")) {
                typedIdentifier
            } else {
                authRepository.resolveUsernameToEmail(typedIdentifier)
            }

            if (resolvedEmail == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "No account found for that username.")
                }
                return@launch
            }

            val result = authRepository.login(resolvedEmail, state.password)
            result
                .onSuccess { user ->
                    // "Sign in as admin" is a UX affordance, not the real gate - but if the user
                    // explicitly chose it and the account turns out to be a regular CITIZEN,
                    // reject here rather than silently dropping them into the citizen experience
                    // (which would look like a bug: "I signed in as admin but see the citizen
                    // app"). Sign back out immediately so no partially-admin session lingers.
                    if (state.isAdminLogin && user.role != UserRole.ADMIN) {
                        authRepository.logout()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "This account doesn't have admin access.",
                            )
                        }
                        return@onSuccess
                    }
                    securePrefsManager.isRememberMeEnabled = state.rememberMe
                    securePrefsManager.lastUsedEmail = if (state.rememberMe) resolvedEmail else null
                    // Offer to enable biometric login right after a normal successful login,
                    // while the plaintext password is still in hand - only if not already
                    // enabled for this device (device-level hardware/enrollment availability is
                    // checked by the screen before it even shows this prompt).
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoginSuccessful = true,
                            showEnableBiometricPrompt = !it.isBiometricLoginEnabled,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = throwable.message ?: "Login failed")
                    }
                }
        }
    }

    /** Called right before launching BiometricPrompt for the "sign in with biometrics" button. */
    fun biometricCryptoObject() = biometricCredentialStore.buildDecryptCryptoObject()

    fun biometricEnabledEmail(): String? = biometricCredentialStore.enabledEmail()

    /**
     * Called from the BiometricPrompt success callback with the now-authenticated Cipher -
     * decrypts the stored password and re-runs the exact same login() flow a manual entry would,
     * so admin-role verification/remember-me/etc all still apply identically.
     */
    fun onBiometricAuthSucceeded(cipher: javax.crypto.Cipher) {
        val email = biometricCredentialStore.enabledEmail()
        val password = biometricCredentialStore.decryptPassword(cipher)
        if (email == null || password == null) {
            _uiState.update { it.copy(errorMessage = "Biometric login failed - please sign in with your password.") }
            return
        }
        _uiState.update { it.copy(email = email, password = password) }
        login()
    }

    fun onEnableBiometricLogin() {
        val state = _uiState.value
        val success = biometricCredentialStore.enable(state.email.trim(), state.password)
        _uiState.update {
            it.copy(
                showEnableBiometricPrompt = false,
                isBiometricLoginEnabled = success || it.isBiometricLoginEnabled,
            )
        }
    }

    fun dismissEnableBiometricPrompt() {
        _uiState.update { it.copy(showEnableBiometricPrompt = false) }
    }

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Email is required"
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Enter a valid email"
        else -> null
    }
}
