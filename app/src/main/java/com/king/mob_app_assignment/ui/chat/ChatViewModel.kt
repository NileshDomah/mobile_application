package com.king.mob_app_assignment.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.king.mob_app_assignment.ai.ChatMessage
import com.king.mob_app_assignment.ai.ChatMessageService
import com.king.mob_app_assignment.ai.ChatRole
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.displayLabel
import com.king.mob_app_assignment.domain.model.reportReferenceCode
import com.king.mob_app_assignment.ui.reports.categoryDisplayText
import com.king.mob_app_assignment.util.CrashReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// Rendered in the UI as chat bubbles - distinct from ChatMessage/ChatRole (the wire format sent
// to the LLM), since the UI also needs a per-message "isError" flag for showing a retry-style
// affordance without that ever being sent back to the model as conversation history.
data class ChatUiMessage(
    val role: ChatRole,
    val content: String,
    val isError: Boolean = false,
)

data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val isWaitingForReply: Boolean = false,
    val inputText: String = "",
)

private const val MAX_REPORTS_IN_CONTEXT = 5

// Scopes the assistant strictly to this app's own domain - civic issue reporting help - and
// instructs it to decline anything else, per the user's explicit requirement that this NOT be a
// general-purpose chatbot. Enforcement is prompt-based only (no keyword pre-filter) - a
// deliberate choice matching how real product assistants scope themselves, and simpler than a
// separate topic-classification pass; see PLACEHOLDERS_TODO.csv for the acknowledged limitation
// that a sufficiently adversarial prompt could still get the underlying model to go off-topic.
private const val SYSTEM_PROMPT_BASE = "You are the in-app help assistant for \"Shake to Report\", " +
    "a civic issue reporting app. You ONLY help with questions about using this app and about " +
    "reporting civic issues (potholes, streetlights, garbage, water leaks, and other " +
    "infrastructure problems) - how to submit a report, what the categories/statuses/priority " +
    "levels mean, how anonymous reporting works, how AI classification works, and questions " +
    "about the user's own reports (given below, if any). If the user asks anything unrelated to " +
    "this app or civic issue reporting - general knowledge, other topics, anything off-topic - " +
    "politely decline in one sentence and remind them you can only help with reporting issues in " +
    "this app. Keep answers short and conversational, a few sentences at most."

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatMessageService: ChatMessageService,
    private val reportRepository: ReportRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Full conversation history sent to the LLM, INCLUDING the system prompt - kept separate
    // from ChatUiState.messages (which is system-prompt-free, for display) so the system prompt
    // never has to be filtered back out before rendering.
    private var conversationHistory: List<ChatMessage> = emptyList()
    private var systemPromptBuilt = false

    fun onInputChange(value: String) {
        _uiState.update { it.copy(inputText = value) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isWaitingForReply) return

        _uiState.update {
            it.copy(
                messages = it.messages + ChatUiMessage(ChatRole.USER, text),
                inputText = "",
                isWaitingForReply = true,
            )
        }

        viewModelScope.launch {
            if (!systemPromptBuilt) {
                conversationHistory = listOf(ChatMessage(ChatRole.SYSTEM, buildSystemPrompt()))
                systemPromptBuilt = true
            }
            conversationHistory = conversationHistory + ChatMessage(ChatRole.USER, text)

            CrashReporter.log("ChatViewModel: sending message to assistant")
            val reply = runCatching { chatMessageService.sendMessage(conversationHistory) }
                .onFailure { CrashReporter.recordException(it) }
                .getOrNull()

            if (reply != null) {
                conversationHistory = conversationHistory + ChatMessage(ChatRole.ASSISTANT, reply)
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatUiMessage(ChatRole.ASSISTANT, reply),
                        isWaitingForReply = false,
                    )
                }
            } else {
                // Deliberately NOT added to conversationHistory - a failed turn shouldn't count
                // as part of the model's own conversation, so retrying (via sendMessage again)
                // doesn't have a phantom unanswered assistant turn baked into its context.
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatUiMessage(
                            role = ChatRole.ASSISTANT,
                            content = "Sorry, the assistant is unavailable right now - please try again in a moment.",
                            isError = true,
                        ),
                        isWaitingForReply = false,
                    )
                }
            }
        }
    }

    // Built once per screen session (not per-message) so the user's report snapshot doesn't
    // silently go stale mid-conversation if a report's status changes while they're chatting -
    // matches how a human support agent would work from the state of things when the
    // conversation started.
    private suspend fun buildSystemPrompt(): String {
        val uid = firebaseAuth.currentUser?.uid
        val reportsSummary = if (uid != null) {
            runCatching { reportRepository.getReportsForUser(uid).first() }
                .getOrNull()
                ?.sortedByDescending { it.timestamp }
                ?.take(MAX_REPORTS_IN_CONTEXT)
                ?.let(::formatReportsForContext)
        } else {
            null
        }

        return if (reportsSummary.isNullOrBlank()) {
            "$SYSTEM_PROMPT_BASE\n\nThe user has no reports on file yet."
        } else {
            "$SYSTEM_PROMPT_BASE\n\nHere are the user's own reports, most recent first:\n$reportsSummary"
        }
    }

    private fun formatReportsForContext(reports: List<Report>): String {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return reports.joinToString("\n") { report ->
            val reference = reportReferenceCode(report.id)
            val category = report.categoryDisplayText()
            val status = report.status.name
            val department = report.department?.displayLabel() ?: "unassigned"
            val date = dateFormat.format(Date(report.timestamp))
            "- $reference: $category, status $status, department $department, filed $date" +
                if (report.resolutionNote.isNotBlank()) ", resolution note: \"${report.resolutionNote}\"" else ""
        }
    }
}
