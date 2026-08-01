package com.king.mob_app_assignment.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.ui.reports.ReportFilterState
import com.king.mob_app_assignment.ui.reports.applyFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminReportsUiState(
    val reports: List<Report> = emptyList(),
    val filter: ReportFilterState = ReportFilterState(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class AdminReportsViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val filterFlow = MutableStateFlow(ReportFilterState())

    val uiState: StateFlow<AdminReportsUiState> = combine(
        reportRepository.getAllReports().catch { emit(emptyList()) },
        filterFlow,
    ) { reports, filter ->
        AdminReportsUiState(reports = reports.applyFilter(filter), filter = filter, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdminReportsUiState())

    fun onFilterChange(filter: ReportFilterState) {
        filterFlow.value = filter
    }

    fun updateStatus(reportId: String, status: ReportStatus) {
        viewModelScope.launch {
            reportRepository.updateReportStatus(reportId, status)
        }
    }
}
