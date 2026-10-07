package com.king.mob_app_assignment.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.RiskScoreRepository
import com.king.mob_app_assignment.domain.model.RiskScore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AdminRiskAreasUiState(
    val riskScores: List<RiskScore> = emptyList(),
    val isLoading: Boolean = true,
    val showInsufficientData: Boolean = false,
)

@HiltViewModel
class AdminRiskAreasViewModel @Inject constructor(
    riskScoreRepository: RiskScoreRepository,
) : ViewModel() {

    private val showInsufficientFlow = MutableStateFlow(false)
    private val riskScoresFlow = riskScoreRepository.getAllRiskScores().catch { emit(emptyList()) }

    val uiState: StateFlow<AdminRiskAreasUiState> = combine(riskScoresFlow, showInsufficientFlow) { scores, showInsufficient ->
        AdminRiskAreasUiState(
            riskScores = scores
                .filter { showInsufficient || !it.insufficientData }
                // Highest risk first - a command-center dashboard should surface what needs
                // attention, not bury it under low-risk/no-data buckets.
                .sortedWith(compareByDescending<RiskScore> { it.level.ordinal }.thenByDescending { it.score }),
            isLoading = false,
            showInsufficientData = showInsufficient,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdminRiskAreasUiState())

    fun onShowInsufficientToggle(value: Boolean) {
        showInsufficientFlow.value = value
    }
}
