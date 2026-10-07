package com.king.mob_app_assignment.ui.auth

import androidx.lifecycle.ViewModel
import com.king.mob_app_assignment.util.AppPinStore
import com.king.mob_app_assignment.util.BiometricCredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class AppLockUiState(
    val enteredPin: String = "",
    val errorMessage: String? = null,
    val isUnlocked: Boolean = false,
    // Set true after AppLockScreen has auto-fired the biometric prompt once on entry, so it
    // never re-fires on every recomposition - the user can still retry via the fingerprint
    // button shown alongside the PIN pad.
    val hasAutoFiredBiometric: Boolean = false,
)

/**
 * The daily-use unlock gate - shown instead of email/password whenever a Firebase session
 * already persists but this process hasn't unlocked app-lock yet (see MainActivity's own
 * isAppUnlockedThisSession, which is intentionally NOT persisted - only setup completion is).
 * Biometric auto-fires on entry when available (see AppLockScreen); PIN is always the fallback,
 * required at setup time, so there's never a dead end.
 */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val appPinStore: AppPinStore,
    private val biometricCredentialStore: BiometricCredentialStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    fun onDigit(digit: Char) {
        val state = _uiState.value
        if (state.enteredPin.length >= PIN_LENGTH) return
        val newPin = state.enteredPin + digit
        _uiState.update { it.copy(enteredPin = newPin, errorMessage = null) }
        if (newPin.length == PIN_LENGTH) {
            if (appPinStore.verifyPin(newPin)) {
                _uiState.update { it.copy(isUnlocked = true) }
            } else {
                _uiState.update {
                    it.copy(enteredPin = "", errorMessage = "Incorrect PIN. Try again.")
                }
            }
        }
    }

    fun onBackspace() {
        _uiState.update { it.copy(enteredPin = it.enteredPin.dropLast(1), errorMessage = null) }
    }

    fun onBiometricUnlockSucceeded() {
        _uiState.update { it.copy(isUnlocked = true) }
    }

    fun onAutoFiredBiometric() {
        _uiState.update { it.copy(hasAutoFiredBiometric = true) }
    }

    fun onBiometricAuthenticationFailed() {
        _uiState.update {
            it.copy(errorMessage = "Fingerprint/face not recognized. Try again or use your PIN.")
        }
    }

    // Called when biometricCryptoObject() below returns null - see BiometricCredentialStore's own
    // doc comment on buildDecryptCryptoObject for the real root cause this handles
    // (KeyPermanentlyInvalidatedException, most commonly hit by an "existing account" whose
    // device biometric enrollment changed since biometric login was first enabled). Surfaces a
    // clear message rather than silently doing nothing, which is what "keeps saying couldn't be
    // verified" with no visible recovery path actually was.
    fun onBiometricUnavailableAtRuntime() {
        _uiState.update {
            it.copy(errorMessage = "Biometric sign-in needs to be set up again - use your PIN, then re-enable it in Settings.")
        }
    }

    /** Only meaningful for launching the actual BiometricPrompt from the screen - not used to gate anything here. */
    fun biometricCryptoObject() = biometricCredentialStore.buildDecryptCryptoObject()

    // Re-read live (not cached at screen-entry time) so a stale-key auto-disable from a failed
    // buildDecryptCryptoObject() call (see BiometricCredentialStore) is reflected immediately -
    // the fingerprint button/auto-fire correctly stop being offered on the very next check rather
    // than needing the screen to be re-entered first.
    fun isBiometricEnabled(): Boolean = biometricCredentialStore.isEnabled()
}
