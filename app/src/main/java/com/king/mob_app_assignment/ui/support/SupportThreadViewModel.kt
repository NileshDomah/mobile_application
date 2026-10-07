package com.king.mob_app_assignment.ui.support

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.SupportRepository
import com.king.mob_app_assignment.domain.model.SupportTurn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SupportThreadUiState(
    val turns: List<SupportTurn> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
)

/**
 * Backs [SupportThreadScreen] - the open chat view for ONE support ticket, shared between the
 * citizen (isAdmin = false, sends CITIZEN turns) and the admin inbox's own thread view
 * (isAdmin = true, sends ADMIN turns) - both sides watch the same live turn history, they just
 * differ in which send method they call and whose messages render as "mine" vs "theirs" (handled
 * by SupportThreadScreen comparing each turn's sender against isAdmin, not by this ViewModel).
 */
@HiltViewModel
class SupportThreadViewModel @Inject constructor(
    private val supportRepository: SupportRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val ticketId: String = checkNotNull(savedStateHandle["ticketId"])

    private val inputTextFlow = MutableStateFlow("")
    private val isSendingFlow = MutableStateFlow(false)

    private val turnsFlow = supportRepository.getTurnsForTicket(ticketId)
        .catch { emit(emptyList()) }

    val uiState: StateFlow<SupportThreadUiState> = combine(
        turnsFlow, inputTextFlow, isSendingFlow,
    ) { turns, inputText, isSending ->
        SupportThreadUiState(turns = turns, inputText = inputText, isSending = isSending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SupportThreadUiState())

    fun markRead(viewerIsAdmin: Boolean) {
        viewModelScope.launch { supportRepository.markTicketRead(ticketId, viewerIsAdmin) }
    }

    fun onInputChange(value: String) {
        inputTextFlow.value = value
    }

    fun sendAsCitizen() {
        val text = inputTextFlow.value.trim()
        if (text.isBlank() || isSendingFlow.value) return
        isSendingFlow.value = true
        viewModelScope.launch {
            supportRepository.sendCitizenTurn(ticketId, text)
            isSendingFlow.value = false
            inputTextFlow.value = ""
        }
    }

    fun sendAsAdmin() {
        val text = inputTextFlow.value.trim()
        if (text.isBlank() || isSendingFlow.value) return
        isSendingFlow.value = true
        viewModelScope.launch {
            supportRepository.sendAdminReply(ticketId, text)
            isSendingFlow.value = false
            inputTextFlow.value = ""
        }
    }

    fun closeTicket() {
        viewModelScope.launch { supportRepository.closeTicket(ticketId) }
    }
}
