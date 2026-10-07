package com.king.mob_app_assignment.ui.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.data.repository.SupportRepository
import com.king.mob_app_assignment.domain.model.SupportMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [ContactSupportScreen] - now a list of the citizen's own support tickets (each one a
 * multi-turn chat thread, see [SupportMessage]'s doc comment), plus starting a brand-new one.
 * Opening an existing ticket's actual chat thread is [SupportThreadScreen]/
 * [SupportThreadViewModel]'s job, not this one's.
 */
data class ContactSupportUiState(
    val messageText: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val tickets: List<SupportMessage> = emptyList(),
    // Set right after a new ticket is created, so the screen can navigate into its thread
    // immediately - consumed once by the screen then cleared, same pattern as a one-shot
    // navigation event elsewhere in this app.
    val newlyCreatedTicketId: String? = null,
)

@HiltViewModel
class ContactSupportViewModel @Inject constructor(
    private val supportRepository: SupportRepository,
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val messageTextFlow = MutableStateFlow("")
    private val isSendingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val newlyCreatedTicketIdFlow = MutableStateFlow<String?>(null)

    private val ticketsFlow = firebaseAuth.currentUser?.uid
        ?.let { uid -> supportRepository.getTicketsForUser(uid) }
        ?.catch { emit(emptyList()) }
        ?: flowOf(emptyList())

    val uiState: StateFlow<ContactSupportUiState> = combine(
        messageTextFlow, isSendingFlow, errorFlow, ticketsFlow, newlyCreatedTicketIdFlow,
    ) { text, isSending, error, tickets, newTicketId ->
        ContactSupportUiState(
            messageText = text,
            isSending = isSending,
            errorMessage = error,
            tickets = tickets,
            newlyCreatedTicketId = newTicketId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactSupportUiState())

    fun onMessageChange(value: String) {
        messageTextFlow.value = value
        errorFlow.value = null
    }

    fun onNewTicketNavigationConsumed() {
        newlyCreatedTicketIdFlow.value = null
    }

    fun startNewTicket() {
        val text = messageTextFlow.value.trim()
        if (text.isBlank()) {
            errorFlow.value = "Please write a message before sending"
            return
        }
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            errorFlow.value = "You must be signed in to contact support"
            return
        }

        isSendingFlow.value = true
        viewModelScope.launch {
            // fetchCurrentUserProfile() rather than trusting a possibly-stale cached User from
            // elsewhere - the ticket stores a snapshot of the sender's email/display name at
            // creation time, since an admin reading it later has no other convenient way to
            // identify who sent it (the /users/{uid} doc could theoretically change afterward).
            val profile = authRepository.fetchCurrentUserProfile().getOrNull()
            val ticket = SupportMessage(
                userId = uid,
                userEmail = profile?.email.orEmpty(),
                userDisplayName = profile?.displayName.orEmpty(),
            )
            val result = supportRepository.startTicket(ticket, text)
            isSendingFlow.value = false
            result.onSuccess { newTicketId ->
                messageTextFlow.value = ""
                newlyCreatedTicketIdFlow.value = newTicketId
            }.onFailure {
                errorFlow.value = "Couldn't send your message - please try again"
            }
        }
    }
}
