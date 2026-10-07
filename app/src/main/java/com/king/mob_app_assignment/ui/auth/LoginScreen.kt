package com.king.mob_app_assignment.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.ui.components.PrimaryActionButton
import com.king.mob_app_assignment.util.BiometricAuthManager
import com.king.mob_app_assignment.ui.theme.translatedStringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotCredentials: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAvailable = remember { BiometricAuthManager.isAvailable(context) }

    // Deliberately does NOT navigate while the enable-biometrics dialog is still up - it and
    // isLoginSuccessful are set together by the same login() success branch, and navigating away
    // immediately used to tear this screen (and the dialog on it) down before the user could even
    // read it, let alone tap a button - the "enable biometrics" popup that "closes too fast to
    // use." Navigation waits until the dialog has been dismissed one way or the other (see
    // onEnableBiometricLogin/dismissEnableBiometricPrompt, both of which clear the flag).
    //
    // Must also check biometricAvailable here, not just showEnableBiometricPrompt: the ViewModel
    // sets that flag purely based on "not already enabled for this device," with no knowledge of
    // hardware/enrollment - the dialog below only actually renders when biometricAvailable is
    // ALSO true. On a device with no biometric hardware/enrollment (e.g. most emulators), the
    // flag would be true forever with no dialog ever appearing to clear it - which silently
    // blocked navigation entirely and looked exactly like "the login button doesn't work."
    val awaitingBiometricDialog = uiState.showEnableBiometricPrompt && biometricAvailable
    LaunchedEffect(uiState.isLoginSuccessful, awaitingBiometricDialog) {
        if (uiState.isLoginSuccessful && !awaitingBiometricDialog) onLoginSuccess()
    }

    // BiometricPrompt requires a FragmentActivity - MainActivity extends AppCompatActivity,
    // which is one, so this should always resolve in practice; the null-safe cast just avoids a
    // hard crash if this screen is ever hosted somewhere unexpected (e.g. a preview harness).
    fun launchBiometricPrompt() {
        val fragmentActivity = activity ?: return
        val cryptoObject = viewModel.biometricCryptoObject() ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject?.cipher?.let(viewModel::onBiometricAuthSucceeded)
            }
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Sign in")
            .setSubtitle("Use your fingerprint or face to sign in")
            .setAllowedAuthenticators(BiometricAuthManager.AUTHENTICATORS)
            .setNegativeButtonText("Cancel")
            .build()
        BiometricPrompt(fragmentActivity, executor, callback).authenticate(promptInfo, cryptoObject)
    }

    // Separate from launchBiometricPrompt above: this is the ENABLE-time challenge, not the
    // sign-in one - this exists purely to make the user actually prove they can authenticate with
    // their fingerprint/face before biometric login is switched on, per explicit request ("it
    // should ask the user to input the biometric... first"), rather than switching it on from a
    // button tap alone.
    //
    // ROOT CAUSE FIX (2026-09-11, user-reported "keeps asking whether I want to enable it but
    // never does" - see BiometricCredentialStore.buildEnableCryptoObject's doc comment for the
    // full explanation): this used to call authenticate(promptInfo) with NO CryptoObject, on the
    // false assumption noted in the old comment here ("enable() encrypts, which the Keystore key
    // doesn't gate"). That's wrong - the key is generated with setUserAuthenticationRequired(true)
    // covering BOTH encrypt and decrypt, with no validity-duration grace period, so a
    // BiometricPrompt success that isn't tied to the specific Cipher used for encryption changes
    // nothing about that Cipher's own auth-gate state. The old onEnableBiometricChallengeSucceeded
    // then created a fresh, never-authenticated Cipher and immediately hit
    // UserNotAuthenticatedException, silently returning false every time. Now builds the
    // encrypt-mode CryptoObject FIRST and authenticates that specific Cipher.
    val enableChallengeTitle = translatedStringResource(R.string.login_biometric_enable_challenge_title)
    val enableChallengeSubtitle = translatedStringResource(R.string.login_biometric_enable_challenge_subtitle)
    val enableChallengeCancel = translatedStringResource(R.string.login_biometric_enable_challenge_cancel)

    fun launchEnableBiometricChallenge() {
        val fragmentActivity = activity ?: return
        val cryptoObject = viewModel.buildEnableCryptoObject()
        if (cryptoObject == null) {
            viewModel.onEnableBiometricChallengeFailedOrCancelled()
            return
        }
        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val cipher = result.cryptoObject?.cipher
                if (cipher != null) {
                    viewModel.onEnableBiometricChallengeSucceeded(cipher)
                } else {
                    viewModel.onEnableBiometricChallengeFailedOrCancelled()
                }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                viewModel.onEnableBiometricChallengeFailedOrCancelled()
            }
            override fun onAuthenticationFailed() {
                // A single failed scan attempt (wrong finger, etc) - BiometricPrompt itself keeps
                // showing its own UI for further attempts, so this deliberately does NOT clear
                // pendingBiometricEnableChallenge or touch uiState; only a terminal error/cancel
                // (onAuthenticationError above) or a real success ends this challenge.
            }
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(enableChallengeTitle)
            .setSubtitle(enableChallengeSubtitle)
            .setAllowedAuthenticators(BiometricAuthManager.AUTHENTICATORS)
            .setNegativeButtonText(enableChallengeCancel)
            .build()
        BiometricPrompt(fragmentActivity, executor, callback).authenticate(promptInfo, cryptoObject)
    }

    LaunchedEffect(uiState.pendingBiometricEnableChallenge) {
        if (uiState.pendingBiometricEnableChallenge) {
            launchEnableBiometricChallenge()
            viewModel.onEnableBiometricChallengeLaunched()
        }
    }

    if (awaitingBiometricDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEnableBiometricPromptForNow,
            title = { Text(translatedStringResource(R.string.login_biometric_dialog_title)) },
            text = {
                Column {
                    Text(translatedStringResource(R.string.login_biometric_dialog_message))
                    TextButton(
                        onClick = viewModel::declineEnableBiometricPromptPermanently,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(translatedStringResource(R.string.login_biometric_never))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::onEnableBiometricLoginRequested) { Text(translatedStringResource(R.string.login_biometric_enable)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEnableBiometricPromptForNow) { Text(translatedStringResource(R.string.login_biometric_not_now)) }
            },
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            AuthBrandHeader(
                title = translatedStringResource(R.string.login_welcome_back),
                subtitle = translatedStringResource(R.string.login_subtitle),
                modifier = Modifier.padding(top = 32.dp),
            )

            OutlinedTextField(
                value = uiState.emailOrUsername,
                onValueChange = viewModel::onEmailOrUsernameChange,
                label = { Text("Email or username") },
                singleLine = true,
                isError = uiState.emailError != null,
                supportingText = { uiState.emailError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(translatedStringResource(R.string.login_password_label)) },
                singleLine = true,
                isError = uiState.passwordError != null,
                supportingText = { uiState.passwordError?.let { Text(it) } },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                        )
                    }
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Checkbox(
                    checked = uiState.rememberMe,
                    onCheckedChange = viewModel::onRememberMeChange,
                )
                Text(
                    text = translatedStringResource(R.string.login_remember_me),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            uiState.errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            TextButton(
                onClick = onNavigateToForgotCredentials,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(translatedStringResource(R.string.login_forgot_credentials))
            }

            PrimaryActionButton(
                text = translatedStringResource(R.string.login_button),
                onClick = viewModel::login,
                isLoading = uiState.isLoading,
                modifier = Modifier.padding(top = 20.dp),
            )

            if (uiState.isBiometricLoginEnabled && biometricAvailable) {
                OutlinedButton(
                    onClick = ::launchBiometricPrompt,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Text(text = translatedStringResource(R.string.login_biometric_sign_in), modifier = Modifier.padding(start = 8.dp))
                }
            }

            TextButton(
                onClick = onNavigateToRegister,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(translatedStringResource(R.string.login_register_prompt))
            }
        }
    }
}
