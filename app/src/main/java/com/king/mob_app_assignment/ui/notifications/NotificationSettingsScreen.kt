package com.king.mob_app_assignment.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.theme.translatedStringResource

/**
 * Lets a citizen turn off either notification category this app actually sends - report status
 * updates and AI classification results (see NotificationSettingsViewModel's doc comment for why
 * there's no third toggle here). Both notifications already work while the app is fully closed
 * (they're posted from a Firestore listener callback and a WorkManager job respectively, not a
 * plain in-process coroutine that would die with the app) - this screen only controls whether
 * they're shown at all, not their delivery mechanism.
 */
@Composable
fun NotificationSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val statusUpdatesEnabled by viewModel.statusUpdatesEnabled.collectAsState()
    val aiClassificationEnabled by viewModel.aiClassificationEnabled.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = translatedStringResource(R.string.notifications_title), onBackClick = onBackClick) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NotificationToggleCard(
                title = translatedStringResource(R.string.notifications_status_updates_title),
                description = translatedStringResource(R.string.notifications_status_updates_description),
                checked = statusUpdatesEnabled,
                onCheckedChange = viewModel::onStatusUpdatesToggle,
            )
            NotificationToggleCard(
                title = translatedStringResource(R.string.notifications_ai_classification_title),
                description = translatedStringResource(R.string.notifications_ai_classification_description),
                checked = aiClassificationEnabled,
                onCheckedChange = viewModel::onAiClassificationToggle,
            )
        }
    }
}

@Composable
private fun NotificationToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
