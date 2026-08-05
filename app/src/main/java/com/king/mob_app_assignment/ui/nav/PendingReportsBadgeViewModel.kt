package com.king.mob_app_assignment.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.ReportStatus
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

// Drives the "My Reports" bottom-nav badge: count of the current user's reports still
// awaiting review (status == SUBMITTED). Hoisted here rather than into HomeViewModel since
// MainScaffold (which owns the bottom nav) is not scoped to the Home screen's ViewModel.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PendingReportsBadgeViewModel @Inject constructor(
    authRepository: AuthRepository,
    reportRepository: ReportRepository,
) : ViewModel() {

    val pendingCount: StateFlow<Int> = authRepository.currentUser
        .flatMapLatest { user ->
            val uid = user?.uid
            if (uid.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                reportRepository.getReportsForUser(uid)
            }
        }
        .map { reports -> reports.count { it.status == ReportStatus.SUBMITTED } }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
