package com.king.mob_app_assignment.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.data.repository.SupportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// Drives the citizen-facing Profile bottom-nav badge: total count of unread admin replies across
// all of the signed-in citizen's own support tickets (summed, not just "how many tickets have an
// unread reply" - a ticket with 3 new admin messages contributes 3, matching what a real
// messaging app's badge counts). Hoisted here rather than into ProfileViewModel for the same
// reason PendingReportsBadgeViewModel is hoisted separately from HomeViewModel - MainScaffold
// (which owns the bottom nav) is not scoped to any one screen's own ViewModel.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SupportUnreadBadgeViewModel @Inject constructor(
    authRepository: AuthRepository,
    supportRepository: SupportRepository,
) : ViewModel() {

    val unreadCount: StateFlow<Int> = authRepository.currentUser
        .flatMapLatest { user ->
            val uid = user?.uid
            if (uid.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                supportRepository.getTicketsForUser(uid)
            }
        }
        .map { tickets -> tickets.sumOf { it.unreadByCitizen } }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
