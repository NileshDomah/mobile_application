package com.king.mob_app_assignment.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.ReportPriority
import com.king.mob_app_assignment.domain.model.ReportStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AdminDashboardUiState(
    val isLoading: Boolean = true,
    val totalReports: Int = 0,
    val countByStatus: Map<ReportStatus, Int> = emptyMap(),
    val countByCategory: Map<ReportCategory, Int> = emptyMap(),
    val countByDepartment: Map<Department?, Int> = emptyMap(),
    // "Needs attention" queue: unassigned-department reports, urgent-priority reports, and
    // AI-flagged (needsAiReview) reports still SUBMITTED/IN_REVIEW - the reports an admin most
    // likely wants to act on first, surfaced without needing to hunt through the full list with
    // filters. Deliberately capped and deduplicated by id, sorted newest-first.
    val needsAttention: List<Report> = emptyList(),
    val totalUpvotes: Int = 0,
)

private const val NEEDS_ATTENTION_LIMIT = 20

// Reuses the exact same live getAllReports() flow AdminReportsViewModel already subscribes to -
// all aggregation here is plain client-side grouping over that same data, since report volume in
// this coursework project is small enough that a server-side aggregation query (Cloud Function /
// Firestore count()) isn't warranted. See PLACEHOLDERS_TODO.csv if report volume ever grows.
@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
) : ViewModel() {

    val uiState: StateFlow<AdminDashboardUiState> = reportRepository.getAllReports()
        .map { reports -> reports.toDashboardState() }
        .catch { emit(AdminDashboardUiState(isLoading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdminDashboardUiState())

    private fun List<Report>.toDashboardState(): AdminDashboardUiState {
        val needsAttention = filter { report ->
            report.status != ReportStatus.RESOLVED &&
                (
                    report.department == null ||
                        report.priority == ReportPriority.URGENT ||
                        report.needsAiReview
                    )
        }.sortedByDescending { it.timestamp }.take(NEEDS_ATTENTION_LIMIT)

        return AdminDashboardUiState(
            isLoading = false,
            totalReports = size,
            countByStatus = groupingBy { it.status }.eachCount(),
            countByCategory = groupingBy { it.category }.eachCount(),
            countByDepartment = groupingBy { it.department }.eachCount(),
            needsAttention = needsAttention,
            totalUpvotes = sumOf { it.upvoteCount },
        )
    }
}
