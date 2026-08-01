package com.king.mob_app_assignment.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.NavigationRow
import com.king.mob_app_assignment.util.ShakePrefsKeys

@Composable
fun SettingsScreen(
    onOpenPermissionDashboard: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val threshold by viewModel.threshold.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = "Settings") },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(text = "Shake sensitivity", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Lower = more sensitive (easier to trigger a shake report). Higher = requires a harder shake.",
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
                        text = "Current threshold: ${threshold.toInt()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                NavigationRow(
                    label = "Permission Dashboard",
                    icon = Icons.Filled.Security,
                    onClick = onOpenPermissionDashboard,
                )
            }
        }
    }
}
