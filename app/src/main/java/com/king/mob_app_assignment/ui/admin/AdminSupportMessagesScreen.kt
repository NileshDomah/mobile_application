package com.king.mob_app_assignment.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.SupportMessage
import com.king.mob_app_assignment.domain.model.SupportMessageStatus
import com.king.mob_app_assignment.domain.model.reportReferenceCode
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.EmptyState
import com.king.mob_app_assignment.ui.components.LoadingIndicator
import com.king.mob_app_assignment.ui.theme.Elevation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.king.mob_app_assignment.ui.theme.translatedStringResource

/**
 * Admin inbox of every citizen's support ticket - each row opens the shared
 * [SupportThreadScreen] chat view (isAdmin = true) on tap, where replying and closing now
 * actually happen (moved off this screen along with the old single-message model - see
 * AdminSupportMessagesViewModel's doc comment).
 */
@Composable
fun AdminSupportMessagesScreen(
    onOpenTicket: (String) -> Unit,
    viewModel: AdminSupportMessagesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = translatedStringResource(R.string.admin_support_title)) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            FilterChip(
                selected = uiState.showResolvedOnly,
                onClick = { viewModel.onShowResolvedOnlyToggle(!uiState.showResolvedOnly) },
                label = { Text(translatedStringResource(R.string.admin_support_resolved_only)) },
            )

            if (uiState.isLoading) {
                LoadingIndicator()
            } else if (uiState.tickets.isEmpty()) {
                EmptyState(message = translatedStringResource(R.string.admin_support_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.tickets, key = { it.id }) { ticket ->
                        SupportTicketAdminCard(
                            ticket = ticket,
                            onClick = { onOpenTicket(ticket.id) },
                            onDeleteClick = { viewModel.onDeleteRequested(ticket) },
                        )
                    }
                }
            }
        }
    }

    val ticketPendingDelete = uiState.ticketPendingDelete
    if (ticketPendingDelete != null) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissDeleteDialog,
            title = { Text(translatedStringResource(R.string.admin_support_delete_dialog_title)) },
            text = {
                Column {
                    Text(translatedStringResource(R.string.admin_support_delete_dialog_message))
                    uiState.deleteErrorMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmDelete) {
                    Text(translatedStringResource(R.string.admin_support_delete_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissDeleteDialog) {
                    Text(translatedStringResource(R.string.admin_support_delete_dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun SupportTicketAdminCard(ticket: SupportMessage, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.resting),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = ticket.userDisplayName.ifBlank {
                            ticket.userEmail.ifBlank { translatedStringResource(R.string.admin_support_anonymous_user) }
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = ticket.userEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (ticket.status == SupportMessageStatus.RESOLVED) {
                            translatedStringResource(R.string.admin_support_status_resolved)
                        } else {
                            translatedStringResource(R.string.admin_support_status_open)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ticket.status == SupportMessageStatus.RESOLVED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    // Only offered once resolved - see firestore.rules' own delete rule for the
                    // same restriction enforced server-side, not just hidden client-side here.
                    if (ticket.status == SupportMessageStatus.RESOLVED) {
                        IconButton(onClick = onDeleteClick, modifier = Modifier.padding(start = 4.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = translatedStringResource(R.string.admin_support_delete_action),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            if (ticket.relatedReportId.isNotBlank()) {
                Text(
                    text = translatedStringResource(R.string.admin_support_re_report, reportReferenceCode(ticket.relatedReportId)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = dateFormat.format(Date(ticket.lastMessageAt.takeIf { it > 0 } ?: ticket.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = ticket.lastMessagePreview,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 2,
            )
            if (ticket.unreadByAdmin > 0) {
                Text(
                    text = translatedStringResource(R.string.admin_support_unread_count, ticket.unreadByAdmin),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
