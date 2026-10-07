package com.king.mob_app_assignment.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.SupportRepository
import com.king.mob_app_assignment.domain.model.SupportMessage
import com.king.mob_app_assignment.domain.model.SupportMessageStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminSupportMessagesUiState(
    val tickets: List<SupportMessage> = emptyList(),
    val showResolvedOnly: Boolean = false,
    val isLoading: Boolean = true,
    // Set while a delete confirmation dialog is up for this specific ticket - null means no
    // dialog showing. Only ever set for an already-RESOLVED ticket (see onDeleteRequested).
    val ticketPendingDelete: SupportMessage? = null,
    val deleteErrorMessage: String? = null,
)

/**
 * Backs [AdminSupportMessagesScreen] - now just an inbox list of every citizen's support
 * tickets, each opening into the shared [SupportThreadScreen] chat view (isAdmin = true) on tap.
 * Replying/closing moved there along with the citizen side - this ViewModel only needs the list
 * and the resolved-only filter now.
 */
@HiltViewModel
class AdminSupportMessagesViewModel @Inject constructor(
    private val supportRepository: SupportRepository,
) : ViewModel() {

    private val showResolvedFlow = MutableStateFlow(false)
    // Purely local UI state (which dialog/error is showing) - deliberately NOT part of the
    // combine() below, since that flow's whole point is to stay driven by the live Firestore
    // ticket list; folding transient dialog state into the same combine would mean it gets
    // reset to defaults every time the underlying ticket list re-emits, e.g. right as a delete
    // succeeds and the ticket disappears from the snapshot.
    private val dialogStateFlow = MutableStateFlow(DialogState())

    private data class DialogState(
        val ticketPendingDelete: SupportMessage? = null,
        val deleteErrorMessage: String? = null,
    )

    private val ticketsFlow = supportRepository.getAllTickets()
        .catch { emit(emptyList()) }

    val uiState: StateFlow<AdminSupportMessagesUiState> = combine(
        ticketsFlow, showResolvedFlow, dialogStateFlow,
    ) { tickets, showResolvedOnly, dialogState ->
        AdminSupportMessagesUiState(
            tickets = tickets.filter { if (showResolvedOnly) it.status == SupportMessageStatus.RESOLVED else true }
                .sortedWith(compareBy({ it.status }, { -it.lastMessageAt })),
            showResolvedOnly = showResolvedOnly,
            isLoading = false,
            ticketPendingDelete = dialogState.ticketPendingDelete,
            deleteErrorMessage = dialogState.deleteErrorMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdminSupportMessagesUiState())

    fun onShowResolvedOnlyToggle(value: Boolean) {
        showResolvedFlow.value = value
    }

    // Only meaningful for an already-RESOLVED ticket - the delete button itself is only ever
    // shown for one (see AdminSupportMessagesScreen), and firestore.rules independently enforces
    // this server-side regardless of what the client shows.
    fun onDeleteRequested(ticket: SupportMessage) {
        dialogStateFlow.update { it.copy(ticketPendingDelete = ticket, deleteErrorMessage = null) }
    }

    fun onDismissDeleteDialog() {
        dialogStateFlow.update { it.copy(ticketPendingDelete = null) }
    }

    fun onConfirmDelete() {
        val ticket = dialogStateFlow.value.ticketPendingDelete ?: return
        viewModelScope.launch {
            val result = supportRepository.deleteTicket(ticket.id)
            result
                .onSuccess { dialogStateFlow.update { DialogState() } }
                .onFailure {
                    dialogStateFlow.update {
                        it.copy(deleteErrorMessage = "Couldn't delete this conversation. Please try again.")
                    }
                }
        }
    }
}
