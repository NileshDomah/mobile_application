package com.king.mob_app_assignment.ui.settings

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.king.mob_app_assignment.util.PermissionManager
import com.king.mob_app_assignment.util.ShakePrefsKeys

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
    val context = LocalContext.current

    fun startBackgroundShakeService() {
        val intent = Intent(context, ShakeBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
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
        topBar = { AppTopBar(title = stringResource(R.string.settings_title), onBackClick = onBackClick) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(text = stringResource(R.string.settings_shake_sensitivity_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.settings_shake_sensitivity_description),
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
                        text = stringResource(R.string.settings_current_threshold, threshold.toInt()),
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
                    Text(text = stringResource(R.string.settings_language_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.settings_language_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = languageTag == LANGUAGE_TAG_ENGLISH,
                            onClick = { viewModel.onLanguageChange(LANGUAGE_TAG_ENGLISH) },
                            label = { Text(stringResource(R.string.settings_language_english)) },
                        )
                        FilterChip(
                            selected = languageTag == LANGUAGE_TAG_SPANISH,
                            onClick = { viewModel.onLanguageChange(LANGUAGE_TAG_SPANISH) },
                            label = { Text(stringResource(R.string.settings_language_spanish)) },
                        )
                        FilterChip(
                            selected = languageTag == LANGUAGE_TAG_FRENCH,
                            onClick = { viewModel.onLanguageChange(LANGUAGE_TAG_FRENCH) },
                            label = { Text(stringResource(R.string.settings_language_french)) },
                        )
                    }
                }
            }

            if (biometricLoginEnabled) {
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
                                text = "Turn off fingerprint/face sign-in on this device. You can re-enable it " +
                                    "next time you sign in with your password.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Switch(
                            checked = biometricLoginEnabled,
                            onCheckedChange = { if (!it) viewModel.disableBiometricLogin() },
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                NavigationRow(
                    label = stringResource(R.string.settings_permission_dashboard),
                    icon = Icons.Filled.Security,
                    onClick = onOpenPermissionDashboard,
                )
            }
        }
    }
}
