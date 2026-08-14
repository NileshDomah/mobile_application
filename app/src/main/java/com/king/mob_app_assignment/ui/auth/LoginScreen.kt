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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAvailable = remember { BiometricAuthManager.isAvailable(context) }

    LaunchedEffect(uiState.isLoginSuccessful) {
        if (uiState.isLoginSuccessful) onLoginSuccess()
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

    if (uiState.showEnableBiometricPrompt && biometricAvailable) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEnableBiometricPrompt,
            title = { Text("Enable biometric sign-in?") },
            text = { Text("Next time, sign in instantly with your fingerprint or face instead of typing your password.") },
            confirmButton = {
                TextButton(onClick = viewModel::onEnableBiometricLogin) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEnableBiometricPrompt) { Text("Not now") }
            },
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            AuthBrandHeader(
                title = stringResource(R.string.login_welcome_back),
                subtitle = stringResource(R.string.login_subtitle),
            )

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(R.string.login_email_label)) },
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
                label = { Text(stringResource(R.string.login_password_label)) },
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
                    text = stringResource(R.string.login_remember_me),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // "Sign in as admin" is purely a UX affordance - the real gate is the account's
            // Firestore role field, checked in LoginViewModel.login() after auth succeeds. There
            // is deliberately no equivalent option on the register screen - admin accounts are
            // never self-service, only ever created by editing Firestore directly.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                FilterChip(
                    selected = uiState.isAdminLogin,
                    onClick = { viewModel.onAdminLoginToggle(!uiState.isAdminLogin) },
                    label = { Text("Sign in as admin") },
                    leadingIcon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null) },
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

            PrimaryActionButton(
                text = stringResource(R.string.login_button),
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
                    Text(text = "Sign in with biometrics", modifier = Modifier.padding(start = 8.dp))
                }
            }

            TextButton(
                onClick = onNavigateToRegister,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.login_register_prompt))
            }
        }
    }
}
