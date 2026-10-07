package com.king.mob_app_assignment.ui.support

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.SupportMessage
import com.king.mob_app_assignment.domain.model.SupportMessageStatus
import com.king.mob_app_assignment.domain.model.SupportTurnSender
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.EmptyState
import com.king.mob_app_assignment.ui.components.PrimaryActionButton
import com.king.mob_app_assignment.ui.theme.Elevation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.king.mob_app_assignment.ui.theme.translatedStringResource

/**
 * The citizen's own support tickets - a list of past/ongoing conversations (see
 * ContactSupportViewModel's doc comment), each opening into a real chat thread
 * ([SupportThreadScreen]) on tap, plus a form at the top to start a brand-new one.
 */
@Composable
fun ContactSupportScreen(
    onBackClick: () -> Unit,
    onOpenTicket: (String) -> Unit,
    viewModel: ContactSupportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.newlyCreatedTicketId) {
        val ticketId = uiState.newlyCreatedTicketId ?: return@LaunchedEffect
        onOpenTicket(ticketId)
        viewModel.onNewTicketNavigationConsumed()
    }

    Scaffold(
        topBar = { AppTopBar(title = translatedStringResource(R.string.contact_support_title), onBackClick = onBackClick) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                text = translatedStringResource(R.string.contact_support_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = uiState.messageText,
                onValueChange = viewModel::onMessageChange,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                label = { Text(translatedStringResource(R.string.contact_support_message_label)) },
                minLines = 3,
                supportingText = { Text(translatedStringResource(R.string.contact_support_char_count, uiState.messageText.length, 1000)) },
            )

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            PrimaryActionButton(
                text = translatedStringResource(R.string.contact_support_send),
                onClick = viewModel::startNewTicket,
                isLoading = uiState.isSending,
                modifier = Modifier.padding(top = 16.dp),
            )

            Text(
                text = translatedStringResource(R.string.contact_support_your_messages),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
            )

            if (uiState.tickets.isEmpty()) {
                EmptyState(message = translatedStringResource(R.string.contact_support_no_tickets))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.tickets, key = { it.id }) { ticket ->
                        SupportTicketCard(ticket = ticket, onClick = { onOpenTicket(ticket.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportTicketCard(ticket: SupportMessage, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
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
                Text(
                    text = if (ticket.status == SupportMessageStatus.RESOLVED) {
                        translatedStringResource(R.string.contact_support_status_closed)
                    } else {
                        translatedStringResource(R.string.contact_support_status_open)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ticket.status == SupportMessageStatus.RESOLVED) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    text = dateFormat.format(Date(ticket.lastMessageAt.takeIf { it > 0 } ?: ticket.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (ticket.lastMessageSender == SupportTurnSender.ADMIN) {
                    translatedStringResource(R.string.contact_support_preview_from_admin, ticket.lastMessagePreview)
                } else {
                    ticket.lastMessagePreview
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 2,
            )
        }
    }
}
