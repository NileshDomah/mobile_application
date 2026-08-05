package com.king.mob_app_assignment.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.Report
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

enum class ReportsScope { ALL, MINE, ADMIN_ALL }

data class ReportsListUiState(
    val reports: List<Report> = emptyList(),
    val filter: ReportFilterState = ReportFilterState(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class ReportsListViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val filterFlow = MutableStateFlow(ReportFilterState())
    private val scopeFlow = MutableStateFlow(ReportsScope.ALL)
    private val isRefreshingFlow = MutableStateFlow(false)
    private val refreshTriggerFlow = MutableStateFlow(0)

    // Reports arrive via a live Firestore snapshot listener, so there is no traditional
    // one-shot "fetch" to repeat. Combining in a refresh counter and keying flatMapLatest off
    // both lets a pull-to-refresh tear down and re-establish the snapshot listener - a genuine
    // re-subscribe that also serves as a manual retry path if the previous listener died from a
    // connectivity drop.
    private val reportsFlow = combine(scopeFlow, refreshTriggerFlow) { s, _ -> s }
        .flatMapLatest { s ->
            when (s) {
                ReportsScope.MINE -> reportRepository.getReportsForUser(firebaseAuth.currentUser?.uid.orEmpty())
                ReportsScope.ALL, ReportsScope.ADMIN_ALL -> reportRepository.getAllReports()
            }
        }.catch { emit(emptyList()) }

    val uiState: StateFlow<ReportsListUiState> = combine(
        reportsFlow,
        filterFlow,
        isRefreshingFlow,
    ) { reports, filter, isRefreshing ->
        ReportsListUiState(
            reports = reports.applyFilter(filter),
            filter = filter,
            isLoading = false,
            isRefreshing = isRefreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsListUiState())

    fun setScope(scope: ReportsScope) {
        scopeFlow.value = scope
    }

    fun onFilterChange(filter: ReportFilterState) {
        filterFlow.value = filter
    }

    fun refresh() {
        if (isRefreshingFlow.value) return
        viewModelScope.launch {
            isRefreshingFlow.value = true
            refreshTriggerFlow.value += 1
            delay(400)
            isRefreshingFlow.value = false
        }
    }

    fun toggleUpvote(reportId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            reportRepository.toggleUpvote(reportId, uid)
        }
    }
}
