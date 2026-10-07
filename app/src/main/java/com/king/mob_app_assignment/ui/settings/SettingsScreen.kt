package com.king.mob_app_assignment.ui.settings

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.sensor.ShakeBackgroundService
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.NavigationRow
import com.king.mob_app_assignment.util.AppPermission
import com.king.mob_app_assignment.util.LANGUAGE_TAG_ENGLISH
import com.king.mob_app_assignment.util.LANGUAGE_TAG_FRENCH
import com.king.mob_app_assignment.util.LANGUAGE_TAG_SPANISH
import com.king.mob_app_assignment.util.MlKitLanguage
import com.king.mob_app_assignment.util.PermissionManager
import com.king.mob_app_assignment.util.ShakePrefsKeys
import com.king.mob_app_assignment.ui.theme.translatedStringResource

@Composable
fun SettingsScreen(
    onOpenPermissionDashboard: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val threshold by viewModel.threshold.collectAsState()
    val languageTag by viewModel.languageTag.collectAsState()
    val backgroundShakeEnabled by viewModel.backgroundShakeEnabled.collectAsState()
    val biometricLoginEnabled by viewModel.biometricLoginEnabled.collectAsState()
    val activeMlKitLanguageTag by viewModel.activeMlKitLanguageTag.collectAsState()
    val downloadedMlKitLanguageTags by viewModel.downloadedMlKitLanguageTags.collectAsState()
    val mlKitDownloadInProgressTag by viewModel.mlKitDownloadInProgressTag.collectAsState()
    val mlKitDownloadError by viewModel.mlKitDownloadError.collectAsState()
    var showMoreLanguagesDialog by remember { mutableStateOf(false) }
    var showEnableBiometricDialog by remember { mutableStateOf(false) }
    val enableBiometricState by viewModel.enableBiometricState.collectAsState()
    val context = LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity

    LaunchedEffect(enableBiometricState.isComplete) {
        if (enableBiometricState.isComplete) {
            showEnableBiometricDialog = false
        }
    }

    // See BiometricCredentialStore.buildEnableCryptoObject's doc comment for the full root-cause
    // history ("enable biometric keeps asking for password and doesn't enable") - the password
    // verification alone was never enough, AND a BiometricPrompt authenticated in isolation (no
    // CryptoObject) was ALSO not enough. The prompt must authenticate the exact same Cipher that
    // BiometricCredentialStore.enable() then uses to encrypt the password, or the Keystore key's
    // per-operation auth gate throws every time regardless of how the prompt itself resolved.
    LaunchedEffect(enableBiometricState.pendingBiometricChallenge) {
        if (!enableBiometricState.pendingBiometricChallenge) return@LaunchedEffect
        val fragmentActivity = activity
        if (fragmentActivity == null) {
            viewModel.onBiometricChallengeFailedOrCancelled()
            return@LaunchedEffect
        }
        val cryptoObject = viewModel.buildEnableCryptoObject()
        if (cryptoObject == null) {
            viewModel.onBiometricChallengeFailedOrCancelled()
            return@LaunchedEffect
        }
        val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
        val callback = object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
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
        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm it's you")
            .setSubtitle("Use your fingerprint or face to turn on biometric sign-in")
            .setAllowedAuthenticators(com.king.mob_app_assignment.util.BiometricAuthManager.AUTHENTICATORS)
            .setNegativeButtonText("Cancel")
            .build()
        androidx.biometric.BiometricPrompt(fragmentActivity, executor, callback).authenticate(promptInfo, cryptoObject)
    }

    fun startBackgroundShakeService() {
        com.king.mob_app_assignment.sensor.startShakeService(context)
    }

    fun stopBackgroundShakeService() {
        context.stopService(Intent(context, ShakeBackgroundService::class.java))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setBackgroundShakeEnabled(true)
            startBackgroundShakeService()
        }
    }

    fun onBackgroundShakeToggle(enabled: Boolean) {
        if (!enabled) {
            viewModel.setBackgroundShakeEnabled(false)
            stopBackgroundShakeService()
            return
        }
        val notificationsGranted = PermissionManager.isGranted(context, AppPermission.NOTIFICATIONS)
        if (notificationsGranted) {
            viewModel.setBackgroundShakeEnabled(true)
            startBackgroundShakeService()
        } else {
            notificationPermissionLauncher.launch(AppPermission.NOTIFICATIONS.manifestPermission)
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = translatedStringResource(R.string.settings_title), onBackClick = onBackClick) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(text = translatedStringResource(R.string.settings_shake_sensitivity_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = translatedStringResource(R.string.settings_shake_sensitivity_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    )
                    Slider(
                        value = threshold,
                        onValueChange = viewModel::onThresholdChange,
                        valueRange = ShakePrefsKeys.MIN_THRESHOLD.toFloat()..ShakePrefsKeys.MAX_THRESHOLD.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                    Text(
                        text = translatedStringResource(R.string.settings_current_threshold, threshold.toInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Shake in background", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Detect a shake even when the app is closed. Shows a persistent " +
                                "notification while active, and uses more battery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Switch(
                        checked = backgroundShakeEnabled,
                        onCheckedChange = ::onBackgroundShakeToggle,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(text = translatedStringResource(R.string.settings_language_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = translatedStringResource(R.string.settings_language_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = languageTag == LANGUAGE_TAG_ENGLISH,
                            onClick = { viewModel.onLanguageChange(LANGUAGE_TAG_ENGLISH) },
                            label = { Text(translatedStringResource(R.string.settings_language_english)) },
                        )
                        FilterChip(
                            selected = languageTag == LANGUAGE_TAG_SPANISH,
                            onClick = { viewModel.onLanguageChange(LANGUAGE_TAG_SPANISH) },
                            label = { Text(translatedStringResource(R.string.settings_language_spanish)) },
                        )
                        FilterChip(
                            selected = languageTag == LANGUAGE_TAG_FRENCH,
                            onClick = { viewModel.onLanguageChange(LANGUAGE_TAG_FRENCH) },
                            label = { Text(translatedStringResource(R.string.settings_language_french)) },
                        )
                    }

                    val activeMlKitLanguage = remember(activeMlKitLanguageTag) {
                        SettingsViewModel.mlKitLanguages.firstOrNull { it.displayTag == activeMlKitLanguageTag }
                    }
                    OutlinedButton(
                        onClick = { showMoreLanguagesDialog = true },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            text = activeMlKitLanguage?.displayName
                                ?: translatedStringResource(R.string.settings_more_languages),
                        )
                    }
                    Text(
                        text = translatedStringResource(R.string.settings_more_languages_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            val biometricAvailable = remember { com.king.mob_app_assignment.util.BiometricAuthManager.isAvailable(context) }
            val biometricUnavailableReason = remember {
                com.king.mob_app_assignment.util.BiometricAuthManager.unavailableReason(context)
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Biometric sign-in", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = when {
                                !biometricAvailable -> biometricUnavailableReason.orEmpty()
                                biometricLoginEnabled -> "Turn off fingerprint/face sign-in on this device. " +
                                    "You can re-enable it anytime with your password."
                                else -> "Sign in with your fingerprint or face instead of typing your " +
                                    "password every time."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Switch(
                        checked = biometricLoginEnabled,
                        enabled = biometricAvailable,
                        onCheckedChange = { enable ->
                            if (enable) {
                                viewModel.resetEnableBiometricState()
                                showEnableBiometricDialog = true
                            } else {
                                viewModel.disableBiometricLogin()
                            }
                        },
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                NavigationRow(
                    label = translatedStringResource(R.string.settings_permission_dashboard),
                    icon = Icons.Filled.Security,
                    onClick = onOpenPermissionDashboard,
                )
            }
        }
    }

    if (showMoreLanguagesDialog) {
        MoreLanguagesDialog(
            languages = SettingsViewModel.mlKitLanguages,
            activeTag = activeMlKitLanguageTag,
            downloadedTags = downloadedMlKitLanguageTags,
            downloadInProgressTag = mlKitDownloadInProgressTag,
            downloadError = mlKitDownloadError,
            onDismissError = viewModel::clearMlKitDownloadError,
            onSelectNative = {
                viewModel.onMlKitLanguageChange("")
                showMoreLanguagesDialog = false
            },
            onSelectLanguage = { language ->
                viewModel.onMlKitLanguageSelect(language, wifiOnly = false)
            },
            onDismiss = { showMoreLanguagesDialog = false },
        )
    }

    if (showEnableBiometricDialog) {
        EnableBiometricDialog(
            state = enableBiometricState,
            onPasswordChange = viewModel::onEnableBiometricPasswordChange,
            onConfirm = viewModel::onEnableBiometricSubmit,
            onDismiss = { showEnableBiometricDialog = false },
        )
    }
}

@Composable
private fun EnableBiometricDialog(
    state: EnableBiometricState,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable biometric sign-in") },
        text = {
            Column {
                Text(
                    text = "Confirm your password to turn on fingerprint/face sign-in on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    isError = state.errorMessage != null,
                    supportingText = { state.errorMessage?.let { Text(it) } },
                    visualTransformation = if (passwordVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.isLoading && !state.pendingBiometricChallenge) {
                if (state.isLoading || state.pendingBiometricChallenge) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp))
                } else {
                    Text("Enable")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun MoreLanguagesDialog(
    languages: List<MlKitLanguage>,
    activeTag: String,
    downloadedTags: Set<String>,
    downloadInProgressTag: String?,
    downloadError: String?,
    onDismissError: () -> Unit,
    onSelectNative: () -> Unit,
    onSelectLanguage: (MlKitLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(translatedStringResource(R.string.settings_more_languages)) },
        text = {
            Column {
                if (downloadError != null) {
                    Text(
                        text = translatedStringResource(R.string.settings_more_languages_download_failed, downloadError),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(
                    text = translatedStringResource(R.string.settings_more_languages_dialog_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = activeTag.isEmpty(), onClick = onSelectNative)
                            Text(
                                text = translatedStringResource(R.string.settings_more_languages_use_default),
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                    items(languages, key = { it.displayTag }) { language ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                RadioButton(
                                    selected = activeTag == language.displayTag,
                                    onClick = { onSelectLanguage(language) },
                                )
                                Text(text = language.displayName, modifier = Modifier.padding(start = 4.dp))
                            }
                            Box(modifier = Modifier.padding(start = 8.dp)) {
                                when {
                                    downloadInProgressTag == language.displayTag ->
                                        CircularProgressIndicator(modifier = Modifier.height(20.dp).padding(2.dp))
                                    downloadedTags.contains(language.displayTag) ->
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    else ->
                                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismissError(); onDismiss() }) {
                Text(translatedStringResource(R.string.action_done))
            }
        },
    )
}
