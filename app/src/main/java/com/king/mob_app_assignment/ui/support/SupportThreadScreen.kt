package com.king.mob_app_assignment.ui.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.SupportTurn
import com.king.mob_app_assignment.domain.model.SupportTurnSender
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.theme.translatedStringResource

/**
 * Open chat thread for one support ticket - shared between the citizen's own
 * ContactSupportScreen (isAdmin = false) and the admin inbox's own thread view
 * (isAdmin = true). A turn renders as "mine" (right-aligned, primary color) when its sender
 * matches the viewer's own side, "theirs" otherwise - so the exact same composable looks correct
 * from either party's perspective without needing two separate screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportThreadScreen(
    isAdmin: Boolean,
    onBackClick: () -> Unit,
    viewModel: SupportThreadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.markRead(viewerIsAdmin = isAdmin)
    }
    LaunchedEffect(uiState.turns.size) {
        val lastIndex = uiState.turns.size - 1
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = translatedStringResource(R.string.support_thread_title),
                onBackClick = onBackClick,
                actions = {
                    if (isAdmin) {
                        TextButton(onClick = viewModel::closeTicket) {
                            Text(translatedStringResource(R.string.support_thread_close_action))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            ) {
                items(uiState.turns, key = { it.id }) { turn ->
                    SupportTurnBubble(turn = turn, isAdmin = isAdmin)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(translatedStringResource(R.string.support_thread_input_placeholder)) },
                    shape = MaterialTheme.shapes.large,
                )
                IconButton(
                    onClick = { if (isAdmin) viewModel.sendAsAdmin() else viewModel.sendAsCitizen() },
                    enabled = uiState.inputText.isNotBlank() && !uiState.isSending,
                ) {
                    Icon(Icons.Filled.Send, contentDescription = translatedStringResource(R.string.support_thread_send_action))
                }
            }
        }
    }
}

@Composable
private fun SupportTurnBubble(turn: SupportTurn, isAdmin: Boolean) {
    val isMine = (turn.sender == SupportTurnSender.ADMIN) == isAdmin
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp,
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
        ) {
            Text(
                text = turn.text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
