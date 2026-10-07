package com.king.mob_app_assignment.ui.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.data.repository.RiskScoreRepository
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.RiskScore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RiskAreaDetailUiState(
    val riskScore: RiskScore? = null,
    val contributingReports: List<Report> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class RiskAreaDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    riskScoreRepository: RiskScoreRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val riskScoreId: String = checkNotNull(savedStateHandle["riskScoreId"])

    private val riskScoreFlow = riskScoreRepository.getRiskScoreById(riskScoreId).catch { emit(null) }

    // Drill-down into the exact reports that produced this score - "Risk Area -> Incident ->
    // Reports" navigation per the brief's command-center UI requirement (an incident-level hop
    // isn't meaningful here since a risk bucket spans many incidents/uncategorized reports at
    // once, so this goes straight to the underlying reports).
    private val contributingReportsFlow = riskScoreFlow.flatMapLatest { riskScore ->
        val ids = riskScore?.contributingReportIds?.toSet().orEmpty()
        if (ids.isEmpty()) {
            flowOf(emptyList())
        } else {
            reportRepository.getAllReports().map { all -> all.filter { it.id in ids } }
        }
    }.catch { emit(emptyList()) }

    val uiState: StateFlow<RiskAreaDetailUiState> = combine(riskScoreFlow, contributingReportsFlow) { riskScore, reports ->
        RiskAreaDetailUiState(
            riskScore = riskScore,
            contributingReports = reports.sortedByDescending { it.timestamp },
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RiskAreaDetailUiState())
}
