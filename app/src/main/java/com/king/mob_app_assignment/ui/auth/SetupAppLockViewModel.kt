package com.king.mob_app_assignment.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.util.AppLockPreferences
import com.king.mob_app_assignment.util.AppPinStore
import com.king.mob_app_assignment.util.BiometricCredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class SetupStep { CREATE_PIN, CONFIRM_PIN, OFFER_BIOMETRIC, DONE }

data class SetupAppLockUiState(
    val enteredPin: String = "",
    val errorMessage: String? = null,
    // Held only in memory between CREATE_PIN and CONFIRM_PIN - never written to AppPinStore
    // until the confirmation step actually matches, so a typo on the first attempt never
    // silently becomes the real stored PIN.
    val isConfirmStep: Boolean = false,
    val showBiometricOffer: Boolean = false,
    val pendingBiometricChallenge: Boolean = false,
    val isComplete: Boolean = false,
)

/**
 * Runs once, right after the very first successful email/password login/registration on a
 * device (see AppLockPreferences.hasCompletedSetupFlow) - creates the required PIN fallback,
 * then optionally offers biometric enrollment on top of it. Every later app open uses
 * AppLockScreen instead of ever coming back through here or through email/password again.
 */
@HiltViewModel
class SetupAppLockViewModel @Inject constructor(
    private val appPinStore: AppPinStore,
    private val appLockPreferences: AppLockPreferences,
    private val biometricCredentialStore: BiometricCredentialStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupAppLockUiState())
    val uiState: StateFlow<SetupAppLockUiState> = _uiState.asStateFlow()

    private var firstPinEntry: String? = null
    private var accountEmail: String? = null
    private var accountPassword: String? = null

    /** Called once by the screen right after composition - carries what's needed for biometric enrollment. */
    fun setAccountCredentials(email: String, password: String) {
        accountEmail = email
        accountPassword = password
    }

    fun onDigit(digit: Char) {
        val state = _uiState.value
        if (state.enteredPin.length >= PIN_LENGTH) return
        val newPin = state.enteredPin + digit
        _uiState.update { it.copy(enteredPin = newPin, errorMessage = null) }
        if (newPin.length == PIN_LENGTH) submitStep(newPin)
    }

    fun onBackspace() {
        _uiState.update { it.copy(enteredPin = it.enteredPin.dropLast(1), errorMessage = null) }
    }

    private fun submitStep(pin: String) {
        val state = _uiState.value
        if (!state.isConfirmStep) {
            firstPinEntry = pin
            _uiState.update { it.copy(enteredPin = "", isConfirmStep = true) }
        } else {
            if (pin == firstPinEntry) {
                appPinStore.setPin(pin)
                offerBiometricOrFinish()
            } else {
                // Mismatch: restart from the first entry, not a hard failure - matches the
                // familiar "confirm PIN" UX every OS-level PIN setup uses.
                firstPinEntry = null
                _uiState.update {
                    it.copy(
                        enteredPin = "",
                        isConfirmStep = false,
                        errorMessage = "PINs didn't match - try again.",
                    )
                }
            }
        }
    }

    private fun offerBiometricOrFinish() {
        _uiState.update { it.copy(showBiometricOffer = true) }
    }

    fun onEnableBiometricRequested() {
        _uiState.update { it.copy(pendingBiometricChallenge = true) }
    }

    fun onBiometricChallengeLaunched() {
        _uiState.update { it.copy(pendingBiometricChallenge = false) }
    }

    /** See BiometricCredentialStore.buildEnableCryptoObject's doc comment. */
    fun buildEnableCryptoObject() = biometricCredentialStore.buildEnableCryptoObject()

    fun onBiometricChallengeSucceeded(authenticatedCipher: javax.crypto.Cipher) {
        val email = accountEmail
        val password = accountPassword
        if (email != null && password != null) {
            biometricCredentialStore.enable(email, password, authenticatedCipher)
        }
        finishSetup()
    }

    fun onBiometricChallengeFailedOrCancelled() {
        _uiState.update { it.copy(pendingBiometricChallenge = false) }
    }

    fun onSkipBiometric() {
        finishSetup()
    }

    private fun finishSetup() {
        viewModelScope.launch {
            appLockPreferences.setSetupCompleted()
            _uiState.update { it.copy(isComplete = true) }
        }
    }
}
