package com.king.mob_app_assignment.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.king.mob_app_assignment.data.local.PendingReportDao
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.ImpactStats
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.toImpactStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val recentReports: List<Report> = emptyList(),
    val impactStats: ImpactStats = ImpactStats(),
    val isLoading: Boolean = true,
    val pendingSyncCount: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val pendingReportDao: PendingReportDao,
    firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val reportsFlow = firebaseAuth.currentUser?.uid
        ?.let { uid -> reportRepository.getReportsForUser(uid) }
        .let { flow -> flow ?: flowOf(emptyList()) }
        .catch { emit(emptyList()) }

    val uiState: StateFlow<HomeUiState> = combine(
        reportsFlow,
        pendingReportDao.getAll().catch { emit(emptyList()) },
    ) { reports, pending ->
        HomeUiState(
            recentReports = reports.take(3),
            impactStats = reports.toImpactStats(),
            isLoading = false,
            // Reports queued locally (submitted while offline / Firestore write failed) are
            // invisible in every Firestore-backed list until they sync - surfaced here so a
            // report never silently "disappears" from the user's perspective. See
            // CaptureViewModel.doSubmit()'s fallback path and MobAppAssignment's sync-on-reconnect
            // collector, which drains this queue automatically once connectivity returns.
            pendingSyncCount = pending.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        // Started here (rather than Application) since it needs the logged-in uid, and this is
        // the ViewModel scoped to the first screen shown after auth-gated navigation lets the
        // user in; the underlying Firestore listener persists at repository/singleton scope so
        // it survives Home being backgrounded, and is explicitly stopped on logout (ProfileViewModel).
        firebaseAuth.currentUser?.uid?.let { uid ->
            reportRepository.startStatusChangeListener(uid)
        }
    }
}
