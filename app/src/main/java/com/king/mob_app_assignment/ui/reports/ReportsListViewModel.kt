package com.king.mob_app_assignment.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.Report
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class ReportsScope { ALL, MINE, ADMIN_ALL }

data class ReportsListUiState(
    val reports: List<Report> = emptyList(),
    val filter: ReportFilterState = ReportFilterState(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class ReportsListViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val filterFlow = MutableStateFlow(ReportFilterState())
    private val scopeFlow = MutableStateFlow(ReportsScope.ALL)

    private val reportsFlow = scopeFlow.flatMapLatest { s ->
        when (s) {
            ReportsScope.MINE -> reportRepository.getReportsForUser(firebaseAuth.currentUser?.uid.orEmpty())
            ReportsScope.ALL, ReportsScope.ADMIN_ALL -> reportRepository.getAllReports()
        }
    }.catch { emit(emptyList()) }

    val uiState: StateFlow<ReportsListUiState> = combine(reportsFlow, filterFlow) { reports, filter ->
        ReportsListUiState(reports = reports.applyFilter(filter), filter = filter, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsListUiState())

    fun setScope(scope: ReportsScope) {
        scopeFlow.value = scope
    }

    fun onFilterChange(filter: ReportFilterState) {
        filterFlow.value = filter
    }
}
