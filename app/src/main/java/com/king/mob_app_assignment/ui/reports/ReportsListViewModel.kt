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

// Only MINE is used now - the global/community "all reports" scope (and its own list+map tab)
// was removed entirely; citizens only ever see their own reports (see MyReportsScreen.kt). The
// enum is kept single-valued rather than deleted outright so setScope()'s call shape doesn't need
// touching if a future admin-facing use of this same ViewModel is ever reintroduced.
enum class ReportsScope { MINE }

data class ReportsListUiState(
    val reports: List<Report> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ReportsListViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val scopeFlow = MutableStateFlow(ReportsScope.MINE)
    private val isRefreshingFlow = MutableStateFlow(false)
    private val refreshTriggerFlow = MutableStateFlow(0)

    // Reports arrive via a live Firestore snapshot listener, so there is no traditional
    // one-shot "fetch" to repeat. Combining in a refresh counter and keying flatMapLatest off
    // both lets a pull-to-refresh tear down and re-establish the snapshot listener - a genuine
    // re-subscribe that also serves as a manual retry path if the previous listener died from a
    // connectivity drop.
    private val reportsFlow = combine(scopeFlow, refreshTriggerFlow) { s, _ -> s }
        .flatMapLatest {
            reportRepository.getReportsForUser(firebaseAuth.currentUser?.uid.orEmpty())
        }.catch { emit(emptyList()) }

    private val errorFlow = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            reportRepository.reportListErrors.collect { message -> errorFlow.value = message }
        }
    }

    val uiState: StateFlow<ReportsListUiState> = combine(
        reportsFlow,
        isRefreshingFlow,
        errorFlow,
    ) { reports, isRefreshing, errorMessage ->
        ReportsListUiState(
            reports = reports.sortedByDescending { it.timestamp },
            isLoading = false,
            isRefreshing = isRefreshing,
            errorMessage = errorMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsListUiState())

    fun dismissError() {
        errorFlow.value = null
    }

    fun setScope(scope: ReportsScope) {
        scopeFlow.value = scope
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
