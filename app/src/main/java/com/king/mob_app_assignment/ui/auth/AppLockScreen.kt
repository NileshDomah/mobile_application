package com.king.mob_app_assignment.ui.auth

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.king.mob_app_assignment.util.BiometricAuthManager

/**
 * The daily "reopen the app" gate for an account that already has app-lock set up - replaces the
 * old "type your email and password every time" flow entirely for a returning, already-signed-in
 * user (see RootNavGraph for exactly when this shows instead of MAIN_GRAPH or AUTH_GRAPH).
 * Biometric fires automatically on entry when enrolled (per explicit product decision - fastest
 * path for the common case); "Use PIN instead" is always available below it, and is the only
 * option at all when biometric isn't enrolled/available on this device.
 */
@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit,
    onSignOutInstead: () -> Unit,
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAvailable = remember { BiometricAuthManager.isAvailable(context) }
    // Re-read on every recomposition (not remember{}'d once) - see AppLockViewModel's own doc
    // comment on isBiometricEnabled(): a stale/invalidated Keystore key auto-disables biometric
    // login the moment buildDecryptCryptoObject() first fails, and this needs to reflect that
    // immediately so the fingerprint button/auto-fire stop being offered right away.
    var biometricEnabled by remember { mutableStateOf(viewModel.isBiometricEnabled()) }

    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) onUnlocked()
    }

    fun launchBiometricPrompt() {
        val fragmentActivity = activity ?: return
        val cryptoObject = viewModel.biometricCryptoObject()
        if (cryptoObject == null) {
            // See BiometricCredentialStore.buildDecryptCryptoObject's own doc comment - this is
            // the actual root cause of "keeps saying couldn't be verified" for an existing
            // account: most commonly a KeyPermanentlyInvalidatedException because the device's
            // biometric enrollment changed since this was first enabled. That failure already
            // auto-disabled the stored credential (BiometricCredentialStore.disable()) - refresh
            // this screen's own local copy of that flag so the fingerprint option disappears
            // instead of staying visible and failing identically on every retry.
            biometricEnabled = viewModel.isBiometricEnabled()
            viewModel.onBiometricUnavailableAtRuntime()
            return
        }
        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModel.onBiometricUnlockSucceeded()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Includes the user tapping "Use PIN" (the negative button) - errString already
                // carries a reasonable system message in that case too, no need to special-case
                // it, and showing nothing here (as this used to) is what left a failed/cancelled
                // scan looking like it silently did nothing.
                viewModel.onBiometricAuthenticationFailed()
            }
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.app_lock_biometric_title))
            .setSubtitle(context.getString(R.string.app_lock_biometric_subtitle))
            .setAllowedAuthenticators(BiometricAuthManager.AUTHENTICATORS)
            .setNegativeButtonText(context.getString(R.string.app_lock_use_pin))
            .build()
        BiometricPrompt(fragmentActivity, executor, callback).authenticate(promptInfo, cryptoObject)
    }

    // Auto-fires exactly once per screen entry - per explicit product decision, biometric is the
    // fastest path for the common case, with PIN always available as an explicit fallback rather
    // than something the user has to dig for.
    LaunchedEffect(Unit) {
        if (biometricAvailable && biometricEnabled && !uiState.hasAutoFiredBiometric) {
            viewModel.onAutoFiredBiometric()
            launchBiometricPrompt()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_lock_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            uiState.errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            PinPad(
                enteredLength = uiState.enteredPin.length,
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )

            if (biometricAvailable && biometricEnabled) {
                IconButton(onClick = ::launchBiometricPrompt, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = stringResource(R.string.app_lock_biometric_title),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            TextButton(onClick = onSignOutInstead, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.app_lock_sign_out_instead))
            }
        }
    }
}
