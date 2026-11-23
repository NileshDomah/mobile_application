package com.king.mob_app_assignment.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.UserRole
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.NavigationRow
import com.king.mob_app_assignment.ui.theme.Elevation
import com.king.mob_app_assignment.ui.theme.PillShape
import com.king.mob_app_assignment.ui.theme.Spacing
import com.king.mob_app_assignment.ui.theme.heroGradient
import com.king.mob_app_assignment.ui.theme.translatedStringResource

@Composable
private fun UserRole.label(): String = when (this) {
    UserRole.ADMIN -> translatedStringResource(R.string.profile_role_admin)
    UserRole.CITIZEN -> translatedStringResource(R.string.profile_role_citizen)
}

@Composable
fun ProfileScreen(
    onLoggedOut: () -> Unit,
    onOpenPermissionDashboard: () -> Unit,
    onOpenSettings: () -> Unit,
    onContactSupportClick: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val user by viewModel.currentUser.collectAsState()
    val supportUnreadBadgeViewModel: com.king.mob_app_assignment.ui.nav.SupportUnreadBadgeViewModel = hiltViewModel()
    val supportUnreadCount by supportUnreadBadgeViewModel.unreadCount.collectAsState()
    val impactStats by viewModel.impactStats.collectAsState()
    val role = user?.role ?: UserRole.CITIZEN
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val darkTheme = isSystemInDarkTheme()

    Scaffold(
        topBar = { AppTopBar(title = translatedStringResource(R.string.profile_title)) },
    ) { padding ->
        // BUG FIX 2026-10-08: this was previously a fixed, non-scrolling Column (compact spacing
        // by explicit earlier request, intended to fit every device without a scrollbar). On a
        // real device with a smaller screen, a larger system font-scale setting, or extra space
        // taken by a gesture-navigation bar, that assumption broke - the Logout button landed
        // below the visible viewport with no way to reach it at all. verticalScroll is a safety
        // net, not a reversal of the original compact-spacing intent: on a screen tall enough for
        // everything to already fit, nothing visibly scrolls: it is only reachable at all should
        // content run taller than the viewport, which is exactly the case this fixes.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            // The profile header sits on a gradient wash rather than a flat surfaceVariant fill -
            // the same civic-blue-to-teal hero treatment used on the auth screens, so a signed-in
            // citizen's own "home base" screen carries some of that same brand presence.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(heroGradient(darkTheme)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .shadow(elevation = Elevation.raised, shape = CircleShape)
                            .background(color = MaterialTheme.colorScheme.surface, shape = CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Text(
                        text = user?.displayName.orEmpty().ifBlank { translatedStringResource(R.string.profile_unnamed_user) },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    Text(
                        text = user?.email.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    if (!user?.username.isNullOrBlank()) {
                        Text(
                            text = "@${user?.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    RoleBadge(role)
                }
            }

            ImpactCard(reports = impactStats.totalReports, resolved = impactStats.resolvedReports, modifier = Modifier.padding(top = 10.dp))

            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column {
                    NavigationRow(
                        label = translatedStringResource(R.string.profile_notifications),
                        icon = Icons.Filled.Notifications,
                        onClick = onOpenNotificationSettings,
                    )
                    NavigationRow(
                        label = translatedStringResource(R.string.profile_contact_support),
                        icon = Icons.Filled.SupportAgent,
                        onClick = onContactSupportClick,
                        trailing = {
                            if (supportUnreadCount > 0) {
                                androidx.compose.material3.Badge(
                                    modifier = Modifier.padding(end = 8.dp),
                                ) { Text(supportUnreadCount.toString()) }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    NavigationRow(
                        label = translatedStringResource(R.string.profile_settings),
                        icon = Icons.Filled.Settings,
                        onClick = onOpenSettings,
                    )
                    NavigationRow(
                        label = translatedStringResource(R.string.profile_permission_dashboard),
                        icon = Icons.Filled.Security,
                        onClick = onOpenPermissionDashboard,
                    )
                }
            }

            OutlinedButton(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(text = translatedStringResource(R.string.profile_logout_button), modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (showLogoutConfirm) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirm = false },
                title = { Text(translatedStringResource(R.string.profile_logout_dialog_title)) },
                text = { Text(translatedStringResource(R.string.profile_logout_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showLogoutConfirm = false
                        viewModel.logout()
                        onLoggedOut()
                    }) { Text(translatedStringResource(R.string.profile_logout_dialog_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirm = false }) { Text(translatedStringResource(R.string.profile_logout_dialog_cancel)) }
                },
            )
        }
    }
}

/**
 * Dark "Your Impact" stat card, matching the reference mockup - deliberately a fixed dark surface
 * (MaterialTheme.colorScheme.inverseSurface, not the theme's own background/surface tokens) in
 * both light and dark app themes, since the mockup treats this as a distinct "always-dark" brand
 * moment rather than something that should invert with the rest of the screen. Only Reports/
 * Resolved are shown - the mockup's third "Points" stat and "View Badges" button were dropped on
 * explicit request, since there is no points/badges system anywhere in this app to back them.
 */
@Composable
private fun ImpactCard(reports: Int, resolved: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = translatedStringResource(R.string.profile_impact_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                ImpactStatColumn(label = translatedStringResource(R.string.profile_impact_reports), value = reports)
                ImpactStatColumn(label = translatedStringResource(R.string.profile_impact_resolved), value = resolved)
            }
        }
    }
}

@Composable
private fun ImpactStatColumn(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun RoleBadge(role: UserRole) {
    Text(
        text = role.label(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .padding(top = 10.dp)
            .background(color = MaterialTheme.colorScheme.primaryContainer, shape = PillShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
