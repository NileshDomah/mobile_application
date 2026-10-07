package com.king.mob_app_assignment.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.AuthRepository
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
    // True once a biometric credential is stored for this device (see BiometricCredentialStore)
    // - drives whether LoginScreen shows the "Sign in with biometrics" button at all. Device-level
    // hardware/enrollment availability is checked separately by the screen via
    // BiometricAuthManager, since that can change without this ViewModel's state changing.
    val isBiometricLoginEnabled: Boolean = false,
    // Shown once, right after a successful manual login, only when biometric hardware is
    // available, not already enabled, and the user hasn't permanently declined it before (see
    // SecurePrefsManager.hasDeclinedBiometricPromptPermanently) - "would you like to enable
    // biometric login?"
    val showEnableBiometricPrompt: Boolean = false,
    // Set the instant "Enable" is tapped on the prompt above - tells LoginScreen to actually
    // launch a real BiometricPrompt challenge (fingerprint/face) before biometric login is
    // switched on, rather than enabling it on a button tap alone. Cleared once the screen has
    // launched the prompt (see onEnableBiometricChallengeLaunched) so it fires exactly once per
    // tap, not on every recomposition.
    val pendingBiometricEnableChallenge: Boolean = false,
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

            // ROOT CAUSE FOUND 2026-09-10 for user-reported "can't login now, missing or
            // insufficient permissions" - this used to be called from inside .onSuccess{} below,
            // AFTER authRepository.login()'s own signInWithEmailAndPassword(...).await() call
            // returned. But Firebase notifies EVERY registered AuthStateListener the instant its
            // internal auth state actually changes - including MainActivity's own completely
            // separate currentUser collector - which happens as soon as the sign-in itself
            // completes, not when this particular suspend function resumes and its caller's
            // coroutine gets scheduled back onto the UI thread. That meant MainActivity's
            // RootNavGraph could observe isLoggedIn flip to true and try to consume the pending
            // credentials BEFORE this coroutine had even resumed past login()'s own await() to
            // reach the line that used to call PendingAppLockCredentials.set() - consume() saw
            // null every single time, on a real, correctly-succeeding login, which triggered the
            // "no credentials, sign out" fallback within milliseconds of every sign-in.
            // Fixed by setting the credentials BEFORE calling login() at all, using what's
            // already known and validated at this point (resolvedEmail, state.password) - by the
            // time Firebase's AuthStateListener can possibly fire, the credentials are already
            // sitting there waiting, no matter which listener wins the race to react first.
            com.king.mob_app_assignment.util.PendingAppLockCredentials.set(resolvedEmail, state.password)

            val result = authRepository.login(resolvedEmail, state.password)
            result
                .onSuccess { user ->
                    lastResolvedEmail = resolvedEmail
                    securePrefsManager.isRememberMeEnabled = state.rememberMe
                    securePrefsManager.lastUsedEmail = if (state.rememberMe) resolvedEmail else null
                    // Offer to enable biometric login right after a normal successful login,
                    // while the plaintext password is still in hand - only if not already
                    // enabled for this device, and only if the user hasn't permanently declined
                    // it before (device-level hardware/enrollment availability is checked by the
                    // screen before it even shows this prompt).
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoginSuccessful = true,
                            showEnableBiometricPrompt = !it.isBiometricLoginEnabled &&
                                !securePrefsManager.hasDeclinedBiometricPromptPermanently,
                        )
                    }
                }
                .onFailure { throwable ->
                    // Clear what was set pre-emptively above - a failed login (wrong password,
                    // no network) must not leave stale credentials sitting around for some LATER,
                    // unrelated sign-in to pick up via consume().
                    com.king.mob_app_assignment.util.PendingAppLockCredentials.consume()
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
        // Always the real resolved email (see enable() below), never a raw username - so
        // login()'s "does this contain '@'" check always takes the direct-email path here.
        _uiState.update { it.copy(emailOrUsername = email, password = password) }
        login()
    }

    // lastResolvedEmail is the real account email from the most recent successful login() call -
    // needed because state.emailOrUsername may still hold a raw username the user typed, not the
    // email login() actually resolved and authenticated with. Biometric storage must always hold
    // a real email (BiometricCredentialStore.enable/decryptPassword feed straight into
    // AuthRepository.login(), which never accepts a username).
    private var lastResolvedEmail: String? = null

    // Tapping "Enable" no longer turns biometric login on directly - it now requests a real
    // BiometricPrompt challenge first (see LoginScreen's collection of
    // pendingBiometricEnableChallenge), so "enable biometric login" always means the user just
    // proved they can actually authenticate with their fingerprint/face, not merely tapped a
    // button. onEnableBiometricChallengeSucceeded (below) is what actually calls
    // BiometricCredentialStore.enable() once that challenge succeeds.
    fun onEnableBiometricLoginRequested() {
        _uiState.update { it.copy(pendingBiometricEnableChallenge = true) }
    }

    /** Called by LoginScreen right after it launches the BiometricPrompt challenge, so this fires exactly once. */
    fun onEnableBiometricChallengeLaunched() {
        _uiState.update { it.copy(pendingBiometricEnableChallenge = false) }
    }

    /** See BiometricCredentialStore.buildEnableCryptoObject's doc comment. */
    fun buildEnableCryptoObject() = biometricCredentialStore.buildEnableCryptoObject()

    /** Called from the BiometricPrompt success callback for the enable-challenge (not the sign-in one). */
    fun onEnableBiometricChallengeSucceeded(authenticatedCipher: javax.crypto.Cipher) {
        val state = _uiState.value
        val emailToStore = lastResolvedEmail ?: state.emailOrUsername.trim()
        val success = biometricCredentialStore.enable(emailToStore, state.password, authenticatedCipher)
        _uiState.update {
            it.copy(
                showEnableBiometricPrompt = false,
                isBiometricLoginEnabled = success || it.isBiometricLoginEnabled,
            )
        }
    }

    fun onEnableBiometricChallengeFailedOrCancelled() {
        // Left showEnableBiometricPrompt as-is (still showing) - a cancelled/failed fingerprint
        // scan is not the same thing as the user declining the OFFER itself; they may just have
        // fumbled the scan and want to try Enable again, or now pick Not now/Never instead.
        _uiState.update { it.copy(pendingBiometricEnableChallenge = false) }
    }

    // "Not now": ask again next successful login - simply hides the prompt without recording any
    // permanent preference, since the ViewModel/uiState instance itself doesn't survive past this
    // screen (a fresh LoginViewModel re-derives showEnableBiometricPrompt from scratch next login).
    fun dismissEnableBiometricPromptForNow() {
        _uiState.update { it.copy(showEnableBiometricPrompt = false) }
    }

    // "Never": permanently stop offering this prompt on future logins - the user can still turn
    // biometric login on later from Settings (see SettingsViewModel.onEnableBiometricSubmit),
    // this only ever silences the LOGIN-time offer.
    fun declineEnableBiometricPromptPermanently() {
        securePrefsManager.hasDeclinedBiometricPromptPermanently = true
        _uiState.update { it.copy(showEnableBiometricPrompt = false) }
    }
}
