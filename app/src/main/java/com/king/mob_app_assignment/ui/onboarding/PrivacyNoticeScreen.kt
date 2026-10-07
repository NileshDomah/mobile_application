package com.king.mob_app_assignment.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.ui.components.PrimaryActionButton
import com.king.mob_app_assignment.ui.theme.translatedStringResource

private data class DataPoint(val icon: ImageVector, val titleRes: Int, val bodyRes: Int)

private val dataPoints = listOf(
    DataPoint(Icons.Filled.CameraAlt, R.string.privacy_notice_photos_title, R.string.privacy_notice_photos_body),
    DataPoint(Icons.Filled.LocationOn, R.string.privacy_notice_location_title, R.string.privacy_notice_location_body),
    DataPoint(Icons.Filled.SmartToy, R.string.privacy_notice_ai_title, R.string.privacy_notice_ai_body),
    DataPoint(Icons.Filled.Person, R.string.privacy_notice_account_title, R.string.privacy_notice_account_body),
    DataPoint(Icons.Filled.Notifications, R.string.privacy_notice_notifications_title, R.string.privacy_notice_notifications_body),
)

/**
 * A required (not skippable the way OnboardingScreen's pager can be skipped) disclosure of what
 * the app collects and why, shown once before a citizen can sign in/register - see
 * PrivacyNoticePreferences for how "shown once" is tracked. Added specifically to close the
 * "privacy" sub-topic of the module's Ethics-in-Mobile-Development material: permissions and
 * data-security are already handled elsewhere (PermissionExplainerScreen,
 * SecurePrefsManager/BiometricCredentialStore), but nothing previously told a citizen up front
 * what's actually collected and why, in plain language.
 */
@Composable
fun PrivacyNoticeScreen(
    onAcknowledge: () -> Unit,
) {
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = translatedStringResource(R.string.privacy_notice_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = translatedStringResource(R.string.privacy_notice_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )

                dataPoints.forEach { point ->
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                        Icon(
                            imageVector = point.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(text = stringResource(point.titleRes), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(point.bodyRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }

                Text(
                    text = translatedStringResource(R.string.privacy_notice_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PrimaryActionButton(
                text = translatedStringResource(R.string.privacy_notice_acknowledge),
                onClick = onAcknowledge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }
}
