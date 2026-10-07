package com.king.mob_app_assignment.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.domain.model.User
import com.king.mob_app_assignment.domain.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PromoteAdminUiState(
    val isLoadingUsers: Boolean = true,
    val allUsers: List<User> = emptyList(),
    val searchQuery: String = "",
    val promotingUid: String? = null,
    val errorMessage: String? = null,
    // Null = nothing attempted yet. true = the account was already an admin (idempotent no-op on
    // the server, not an error - see promoteToAdmin's own doc comment). false = a genuine
    // promotion just happened.
    val lastResultAlreadyAdmin: Boolean? = null,
    val lastPromotedDisplayName: String? = null,
) {
    // Client-side filter over the already-loaded account list, matched against whichever of
    // displayName/username/email the admin is searching by - this project's user base is
    // coursework-scale, so a full Firestore query-per-keystroke would be solving a problem that
    // doesn't exist yet (see AuthRepository.listAllUsers's doc comment).
    val filteredUsers: List<User>
        get() {
            val query = searchQuery.trim().lowercase()
            if (query.isBlank()) return allUsers
            return allUsers.filter { user ->
                user.displayName.lowercase().contains(query) ||
                    user.username.lowercase().contains(query) ||
                    user.email.lowercase().contains(query)
            }
        }
}

/**
 * Backs [PromoteAdminScreen] - the one in-app path for an existing admin to grant admin rights to
 * another account, calling the server-side promoteToAdmin Cloud Function rather than writing
 * Firestore directly (see AuthRepository.promoteToAdmin's doc comment for why this has to be a
 * function call, not a client write). Presents every account as a searchable list rather than
 * requiring the admin to already know the exact email/username to type in.
 */
@HiltViewModel
class PromoteAdminViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PromoteAdminUiState())
    val uiState: StateFlow<PromoteAdminUiState> = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    private fun loadUsers() {
        _uiState.update { it.copy(isLoadingUsers = true, errorMessage = null) }
        viewModelScope.launch {
            authRepository.listAllUsers()
                .onSuccess { users ->
                    _uiState.update { it.copy(isLoadingUsers = false, allUsers = users) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoadingUsers = false,
                            errorMessage = throwable.message ?: "Couldn't load accounts - please try again.",
                        )
                    }
                }
        }
    }

    fun onSearchQueryChange(value: String) {
        _uiState.update { it.copy(searchQuery = value, errorMessage = null) }
    }

    fun promote(user: User) {
        if (user.role == UserRole.ADMIN) return
        val identifier = user.username.ifBlank { user.email }
        val isEmail = identifier.contains("@")

        _uiState.update { it.copy(promotingUid = user.uid, errorMessage = null) }
        viewModelScope.launch {
            val result = if (isEmail) {
                authRepository.promoteToAdmin(email = identifier, username = "")
            } else {
                authRepository.promoteToAdmin(email = "", username = identifier)
            }
            result
                .onSuccess { alreadyAdmin ->
                    _uiState.update { state ->
                        state.copy(
                            promotingUid = null,
                            lastResultAlreadyAdmin = alreadyAdmin,
                            lastPromotedDisplayName = user.displayName.ifBlank { user.username },
                            // Reflect the promotion immediately in the list, rather than waiting
                            // on a full reload, so the row's own "already admin" state updates
                            // right away.
                            allUsers = state.allUsers.map {
                                if (it.uid == user.uid) it.copy(role = UserRole.ADMIN) else it
                            },
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            promotingUid = null,
                            errorMessage = throwable.message ?: "Couldn't promote that account - please try again.",
                        )
                    }
                }
        }
    }
}
