package com.king.mob_app_assignment.data.repository

import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.RiskScore
import kotlinx.coroutines.flow.Flow

interface RiskScoreRepository {

    fun getAllRiskScores(): Flow<List<RiskScore>>

    fun getRiskScoreById(id: String): Flow<RiskScore?>

    // Upserts one (area, category) bucket's score, appending a RiskHistoryPoint rather than
    // overwriting history - called by RiskRefreshWorker once per bucket on every refresh run.
    suspend fun upsertRiskScore(riskScore: RiskScore): Result<Unit>

    fun idFor(areaKey: String, category: ReportCategory): String
}
