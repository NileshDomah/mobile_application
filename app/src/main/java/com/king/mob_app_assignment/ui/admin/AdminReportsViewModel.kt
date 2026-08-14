package com.king.mob_app_assignment.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.ui.reports.ReportFilterState
import com.king.mob_app_assignment.ui.reports.applyFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminReportsUiState(
    val reports: List<Report> = emptyList(),
    val filter: ReportFilterState = ReportFilterState(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AdminReportsViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val filterFlow = MutableStateFlow(ReportFilterState())
    private val isRefreshingFlow = MutableStateFlow(false)
    private val refreshTriggerFlow = MutableStateFlow(0)

    private val reportsFlow = refreshTriggerFlow
        .flatMapLatest { reportRepository.getAllReports() }
        .catch { emit(emptyList()) }

    private val errorFlow = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            reportRepository.reportListErrors.collect { message -> errorFlow.value = message }
        }
    }

    val uiState: StateFlow<AdminReportsUiState> = combine(
        reportsFlow,
        filterFlow,
        isRefreshingFlow,
        errorFlow,
    ) { reports, filter, isRefreshing, errorMessage ->
        AdminReportsUiState(
            reports = reports.applyFilter(filter),
            filter = filter,
            isLoading = false,
            isRefreshing = isRefreshing,
            errorMessage = errorMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdminReportsUiState())

    fun onFilterChange(filter: ReportFilterState) {
        filterFlow.value = filter
    }

    fun dismissError() {
        errorFlow.value = null
    }

    fun updateStatus(reportId: String, status: ReportStatus) {
        viewModelScope.launch {
            reportRepository.updateReportStatus(reportId, status)
        }
    }

    fun refresh() {
        if (isRefreshingFlow.value) return
        viewModelScope.launch {
            isRefreshingFlow.value = true
            errorFlow.value = null
            refreshTriggerFlow.value += 1
            delay(400)
            isRefreshingFlow.value = false
        }
    }
}
