package com.king.mob_app_assignment.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.User
import com.king.mob_app_assignment.domain.model.UserRole
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.EmptyState
import com.king.mob_app_assignment.ui.components.InfoCard
import com.king.mob_app_assignment.ui.components.InfoCardSeverity
import com.king.mob_app_assignment.ui.theme.translatedStringResource

/**
 * Admin-only screen (reached via a NavigationRow on AdminDashboardScreen, gated by the same
 * AdminRouteGuard as every other admin route) letting an existing admin browse every account and
 * grant admin rights to one of them - the one in-app path to do this, calling the
 * promoteToAdmin Cloud Function rather than any direct Firestore write. See
 * AuthRepository.promoteToAdmin's doc comment for why this has to be a server-side call.
 */
@Composable
fun PromoteAdminScreen(
    onBackClick: () -> Unit,
    viewModel: PromoteAdminViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = translatedStringResource(R.string.promote_admin_title), onBackClick = onBackClick) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            Text(
                text = translatedStringResource(R.string.promote_admin_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text(translatedStringResource(R.string.promote_admin_search_placeholder)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = translatedStringResource(R.string.report_list_clear_search))
                        }
                    }
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            )

            val lastPromotedName = uiState.lastPromotedDisplayName
            if (lastPromotedName != null && uiState.lastResultAlreadyAdmin != null) {
                InfoCard(
                    title = translatedStringResource(R.string.promote_admin_title),
                    message = if (uiState.lastResultAlreadyAdmin == true) {
                        translatedStringResource(R.string.promote_admin_already_admin, lastPromotedName)
                    } else {
                        translatedStringResource(R.string.promote_admin_success, lastPromotedName)
                    },
                    icon = Icons.Filled.CheckCircle,
                    severity = if (uiState.lastResultAlreadyAdmin == true) InfoCardSeverity.Warning else InfoCardSeverity.Positive,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            uiState.errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            when {
                uiState.isLoadingUsers -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 48.dp))
                    }
                }
                uiState.filteredUsers.isEmpty() -> {
                    EmptyState(message = translatedStringResource(R.string.promote_admin_no_results))
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.filteredUsers, key = { it.uid }) { user ->
                            UserAccountRow(
                                user = user,
                                isPromoting = uiState.promotingUid == user.uid,
                                onPromoteClick = { viewModel.promote(user) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserAccountRow(
    user: User,
    isPromoting: Boolean,
    onPromoteClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = com.king.mob_app_assignment.ui.theme.Elevation.resting),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName.ifBlank { user.username },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (user.username.isNotBlank()) "@${user.username} · ${user.email}" else user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (user.role == UserRole.ADMIN) {
                Text(
                    text = translatedStringResource(R.string.promote_admin_role_admin_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            } else if (isPromoting) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp).size(24.dp))
            } else {
                TextButton(onClick = onPromoteClick) {
                    Text(translatedStringResource(R.string.promote_admin_button))
                }
            }
        }
    }
}
