package com.king.mob_app_assignment.ui.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.domain.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportDetailUiState(
    val report: Report? = null,
    val isAdmin: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reportRepository: ReportRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val reportId: String = checkNotNull(savedStateHandle["reportId"])

    private val isAdminFlow = authRepository.currentUser
        .map { it?.role == UserRole.ADMIN }
        .catch { emit(false) }

    val uiState: StateFlow<ReportDetailUiState> = combine(
        reportRepository.getReportById(reportId).catch { emit(null) },
        isAdminFlow,
    ) { report, isAdmin ->
        ReportDetailUiState(report = report, isAdmin = isAdmin, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportDetailUiState())

    fun updateStatus(status: ReportStatus) {
        viewModelScope.launch {
            reportRepository.updateReportStatus(reportId, status)
        }
    }
}
