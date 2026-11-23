package com.king.mob_app_assignment.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.ai.ChatRole
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.theme.translatedStringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBackClick: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var showNewChatDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.messages.size, uiState.isWaitingForReply) {
        val lastIndex = uiState.messages.size - 1 + if (uiState.isWaitingForReply) 1 else 0
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    if (showNewChatDialog) {
        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            title = { Text(translatedStringResource(R.string.chat_new_chat_dialog_title)) },
            text = { Text(translatedStringResource(R.string.chat_new_chat_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.startNewChat()
                    showNewChatDialog = false
                }) {
                    Text(translatedStringResource(R.string.chat_new_chat_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewChatDialog = false }) {
                    Text(translatedStringResource(R.string.chat_new_chat_dialog_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = translatedStringResource(R.string.chat_title),
                onBackClick = onBackClick,
                actions = {
                    // Only offer "New chat" once there's an actual conversation to end - matches
                    // the user's own framing ("end current message and create new chat").
                    if (uiState.messages.isNotEmpty()) {
                        IconButton(onClick = { showNewChatDialog = true }) {
                            Icon(
                                Icons.Filled.AddComment,
                                contentDescription = translatedStringResource(R.string.chat_new_chat_action),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        // BUG FIX (found via manual testing, 2026-11-17): Scaffold already reserves IME space in
        // its own contentWindowInsets by default - adding imePadding() here on top of that double-
        // applied the IME inset, which pushed this Column (and the input row's Send button inside
        // it) up past its own topBar's reserved space and under the status bar once the keyboard
        // opened, putting Send in the status bar's own gesture-interception zone where taps never
        // reached it at all - the chat couldn't be used with the on-screen keyboard.
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.messages.isEmpty()) {
                // weight(1f), not fillMaxSize() - a fillMaxSize() child inside a plain Column
                // claims the ENTIRE column height (no room left for the input Row below), which
                // pushed the input bar off-screen entirely rather than just visually covering it.
                ChatEmptyState(modifier = Modifier.weight(1f).fillMaxWidth())
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                ) {
                    items(uiState.messages) { message ->
                        ChatBubble(message = message)
                    }
                    if (uiState.isWaitingForReply) {
                        item { TypingIndicatorBubble() }
                    }
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
                    placeholder = { Text(translatedStringResource(R.string.chat_input_placeholder)) },
                    shape = MaterialTheme.shapes.large,
                    singleLine = true,
                )
                IconButton(
                    onClick = viewModel::sendMessage,
                    enabled = uiState.inputText.isNotBlank() && !uiState.isWaitingForReply,
                ) {
                    Icon(Icons.Filled.Send, contentDescription = translatedStringResource(R.string.chat_send_action))
                }
            }
        }
    }
}

@Composable
private fun ChatEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.SmartToy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Text(
            text = translatedStringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = translatedStringResource(R.string.chat_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ChatBubble(message: ChatUiMessage) {
    val isUser = message.role == ChatRole.USER
    val bubbleColor = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun TypingIndicatorBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Card(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
            }
        }
    }
}
