package com.king.mob_app_assignment.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.Report
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ReportsMapUiState(
    val reports: List<Report> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class ReportsMapViewModel @Inject constructor(
    reportRepository: ReportRepository,
) : ViewModel() {

    val uiState: StateFlow<ReportsMapUiState> = reportRepository.getAllReports()
        .catch { emit(emptyList()) }
        .map { ReportsMapUiState(reports = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsMapUiState())
}
