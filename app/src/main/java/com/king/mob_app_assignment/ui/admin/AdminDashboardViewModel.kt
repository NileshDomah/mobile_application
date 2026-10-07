package com.king.mob_app_assignment.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.IncidentRepository
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.data.repository.RiskScoreRepository
import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.Incident
import com.king.mob_app_assignment.domain.model.IncidentStatus
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.ReportPriority
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.domain.model.RiskScore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    // Active (not CLOSED/DISMISSED) incidents, most severe first - the entry point into the full
    // Incidents screen (see the "drill-down navigation" requirement: Dashboard -> Incident ->
    // Reports -> Work Order). Capped the same way needsAttention is, for the same reason.
    val activeIncidents: List<Incident> = emptyList(),
    val activeIncidentCount: Int = 0,
    // Highest-risk areas (HIGH/CRITICAL, real data only), the entry point into the full Risk
    // Areas screen - "Risk Area -> Incident/Reports" drill-down per the brief.
    val topRiskAreas: List<RiskScore> = emptyList(),
    val topRiskAreaCount: Int = 0,
)

private const val NEEDS_ATTENTION_LIMIT = 20
private const val DASHBOARD_INCIDENT_PREVIEW_LIMIT = 5
private const val DASHBOARD_RISK_PREVIEW_LIMIT = 5

// Reuses the exact same live getAllReports() flow AdminReportsViewModel already subscribes to -
// all aggregation here is plain client-side grouping over that same data, since report volume in
// this coursework project is small enough that a server-side aggregation query (Cloud Function /
// Firestore count()) isn't warranted. See PLACEHOLDERS_TODO.csv if report volume ever grows.
@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val incidentRepository: IncidentRepository,
    private val riskScoreRepository: RiskScoreRepository,
) : ViewModel() {

    val uiState: StateFlow<AdminDashboardUiState> = combine(
        reportRepository.getAllReports().catch { emit(emptyList()) },
        incidentRepository.getAllIncidents().catch { emit(emptyList()) },
        riskScoreRepository.getAllRiskScores().catch { emit(emptyList()) },
    ) { reports, incidents, riskScores ->
        reports.toDashboardState(incidents, riskScores)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdminDashboardUiState())

    private fun List<Report>.toDashboardState(incidents: List<Incident>, riskScores: List<RiskScore>): AdminDashboardUiState {
        val needsAttention = filter { report ->
            report.status != ReportStatus.RESOLVED &&
                (
                    report.department == null ||
                        report.priority == ReportPriority.URGENT ||
                        report.needsAiReview
                    )
        }.sortedByDescending { it.timestamp }.take(NEEDS_ATTENTION_LIMIT)

        val activeIncidents = incidents
            .filter { it.status !in setOf(IncidentStatus.CLOSED, IncidentStatus.DISMISSED) }
            .sortedWith(compareByDescending<Incident> { it.severity.ordinal }.thenByDescending { it.updatedAt })

        val elevatedRisk = riskScores
            .filter { !it.insufficientData && it.level.ordinal >= com.king.mob_app_assignment.domain.model.RiskLevel.HIGH.ordinal }
            .sortedByDescending { it.score }

        return AdminDashboardUiState(
            isLoading = false,
            totalReports = size,
            countByStatus = groupingBy { it.status }.eachCount(),
            countByCategory = groupingBy { it.category }.eachCount(),
            countByDepartment = groupingBy { it.department }.eachCount(),
            needsAttention = needsAttention,
            activeIncidents = activeIncidents.take(DASHBOARD_INCIDENT_PREVIEW_LIMIT),
            activeIncidentCount = activeIncidents.size,
            topRiskAreas = elevatedRisk.take(DASHBOARD_RISK_PREVIEW_LIMIT),
            topRiskAreaCount = elevatedRisk.size,
        )
    }
}
