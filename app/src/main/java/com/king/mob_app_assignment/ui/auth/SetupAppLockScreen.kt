package com.king.mob_app_assignment.ui.auth

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.ui.components.PrimaryActionButton
import com.king.mob_app_assignment.util.BiometricAuthManager

/**
 * Runs once, right after the very first successful email/password login/registration - creates
 * the required PIN fallback, then (if biometric hardware/enrollment exists) offers biometric on
 * top of it. Neither Settings nor LoginScreen re-enter this flow; see AppLockPreferences for how
 * "already set up" is tracked and SetupAppLockViewModel for the actual PIN/biometric logic.
 */
@Composable
fun SetupAppLockScreen(
    accountEmail: String,
    accountPassword: String,
    onSetupComplete: () -> Unit,
    viewModel: SetupAppLockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAvailable = remember { BiometricAuthManager.isAvailable(context) }

    LaunchedEffect(Unit) {
        viewModel.setAccountCredentials(accountEmail, accountPassword)
    }

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onSetupComplete()
    }

    // If biometric hardware/enrollment doesn't exist at all, skip the offer step entirely rather
    // than showing a screen with no usable action - PIN alone is a complete, working setup.
    LaunchedEffect(uiState.showBiometricOffer, biometricAvailable) {
        if (uiState.showBiometricOffer && !biometricAvailable) {
            viewModel.onSkipBiometric()
        }
    }

    // See BiometricCredentialStore.buildEnableCryptoObject's doc comment for the root cause this
    // fixes ("keeps asking whether I want to enable it but never does") - a BiometricPrompt
    // authenticated with no CryptoObject leaves the Keystore key's own per-operation auth gate
    // untouched, so the Cipher that enable() used to create fresh would throw
    // UserNotAuthenticatedException every time regardless of how the prompt itself resolved.
    fun launchBiometricEnrollChallenge() {
        val fragmentActivity = activity ?: return
        val cryptoObject = viewModel.buildEnableCryptoObject()
        if (cryptoObject == null) {
            viewModel.onBiometricChallengeFailedOrCancelled()
            return
        }
        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val cipher = result.cryptoObject?.cipher
                if (cipher != null) {
                    viewModel.onBiometricChallengeSucceeded(cipher)
                } else {
                    viewModel.onBiometricChallengeFailedOrCancelled()
                }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                viewModel.onBiometricChallengeFailedOrCancelled()
            }
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.login_biometric_enable_challenge_title))
            .setSubtitle(context.getString(R.string.login_biometric_enable_challenge_subtitle))
            .setAllowedAuthenticators(BiometricAuthManager.AUTHENTICATORS)
            .setNegativeButtonText(context.getString(R.string.login_biometric_enable_challenge_cancel))
            .build()
        BiometricPrompt(fragmentActivity, executor, callback).authenticate(promptInfo, cryptoObject)
    }

    LaunchedEffect(uiState.pendingBiometricChallenge) {
        if (uiState.pendingBiometricChallenge) {
            launchBiometricEnrollChallenge()
            viewModel.onBiometricChallengeLaunched()
        }
    }

    Scaffold { padding ->
        if (uiState.showBiometricOffer && biometricAvailable) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.padding(bottom = 24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.app_lock_setup_biometric_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.app_lock_setup_biometric_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
                )
                PrimaryActionButton(
                    text = stringResource(R.string.app_lock_setup_biometric_enable),
                    onClick = viewModel::onEnableBiometricRequested,
                )
                TextButton(onClick = viewModel::onSkipBiometric, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.app_lock_setup_biometric_skip))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.padding(bottom = 16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (uiState.isConfirmStep) {
                        stringResource(R.string.app_lock_setup_confirm_title)
                    } else {
                        stringResource(R.string.app_lock_setup_create_title)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.app_lock_setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
                uiState.errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                PinPad(
                    enteredLength = uiState.enteredPin.length,
                    onDigit = viewModel::onDigit,
                    onBackspace = viewModel::onBackspace,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
        }
    }
}
