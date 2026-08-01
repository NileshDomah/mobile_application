package com.king.mob_app_assignment.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.Report
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val recentReports: List<Report> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    firebaseAuth: FirebaseAuth,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = firebaseAuth.currentUser?.uid
        ?.let { uid -> reportRepository.getReportsForUser(uid) }
        .let { flow -> flow ?: flowOf(emptyList()) }
        .catch { emit(emptyList()) }
        .map { reports -> HomeUiState(recentReports = reports.take(3), isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

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
