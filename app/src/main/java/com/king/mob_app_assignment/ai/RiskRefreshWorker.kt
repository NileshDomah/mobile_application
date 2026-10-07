package com.king.mob_app_assignment.ai

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.data.repository.RiskScoreRepository
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.RiskLevel
import com.king.mob_app_assignment.domain.model.RiskScore
import com.king.mob_app_assignment.domain.model.RiskScorer
import com.king.mob_app_assignment.util.CrashReporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Periodic (scheduled, not per-report) WorkManager job that recomputes every (area, category)
 * risk bucket from current report/incident data - "Predictive Infrastructure Intelligence" per
 * the brief. Runs on-device, mirroring IncidentClusteringWorker/ClassificationWorker's shape;
 * there is no server-side compute in this project (see REPORT_NOTES.md). Enqueued as a unique
 * periodic work request from MobAppAssignment.onCreate() - see enqueueRiskRefreshWork().
 *
 * Deliberately reads the FULL reports/incidents collections once per run rather than per-report -
 * this is a periodic batch recompute, not a per-event trigger, so there's no need (and no
 * available API) to page/filter it further; the existing getAllReports()/getAllIncidents() flows
 * are already used the same way by admin list screens.
 */
@HiltWorker
class RiskRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reportRepository: ReportRepository,
    private val riskScoreRepository: RiskScoreRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = runCatching {
            val reports = reportRepository.getAllReports().first()

            val buckets = reports
                .filter { it.latitude != 0.0 || it.longitude != 0.0 }
                .groupBy { report -> RiskScorer.areaKeyFor(report.latitude, report.longitude) to report.category }

            var scoredCount = 0
            var insufficientCount = 0
            val now = System.currentTimeMillis()

            for ((key, bucketReports) in buckets) {
                val (areaKey, category) = key
                val id = riskScoreRepository.idFor(areaKey, category)
                val incidentCount = bucketReports.mapNotNull { it.incidentId.takeIf(String::isNotBlank) }.toSet().size

                if (bucketReports.size < RiskScorer.MIN_REPORTS_FOR_SCORE) {
                    insufficientCount++
                    riskScoreRepository.upsertRiskScore(
                        insufficientDataScore(id, areaKey, category, bucketReports, now),
                    )
                    continue
                }

                val factors = RiskScorer.score(bucketReports, incidentCount)
                val score = RiskScorer.totalScore(factors)
                val level = RiskLevel.forScore(score)
                val unresolved = bucketReports.count { it.status != com.king.mob_app_assignment.domain.model.ReportStatus.RESOLVED }
                val isRecurring = RiskScorer.isRecurring(bucketReports)
                val (centerLat, centerLng) = RiskScorer.areaCenterFor(bucketReports.first().latitude, bucketReports.first().longitude)

                val riskScore = RiskScore(
                    id = id,
                    areaKey = areaKey,
                    areaLabel = "Near ${"%.3f".format(centerLat)}, ${"%.3f".format(centerLng)}",
                    category = category,
                    centerLatitude = centerLat,
                    centerLongitude = centerLng,
                    score = score,
                    level = level,
                    factors = factors,
                    insufficientData = false,
                    contributingReportIds = bucketReports.map { it.id },
                    contributingIncidentCount = incidentCount,
                    unresolvedCount = unresolved,
                    averageResolutionHours = RiskScorer.averageResolutionHours(bucketReports) ?: 0.0,
                    isRecurring = isRecurring,
                    recommendation = RiskScorer.recommendationFor(level, isRecurring, unresolved),
                    updatedAt = now,
                )
                riskScoreRepository.upsertRiskScore(riskScore)
                scoredCount++
            }

            CrashReporter.log("RiskRefreshWorker: scored $scoredCount buckets, $insufficientCount insufficient-data buckets")
        }

        return if (result.isSuccess) {
            Result.success()
        } else {
            CrashReporter.log("RiskRefreshWorker: run failed")
            result.exceptionOrNull()?.let { CrashReporter.recordException(it) }
            if (runAttemptCount >= MAX_ATTEMPTS_BEFORE_GIVING_UP) Result.failure() else Result.retry()
        }
    }

    private fun insufficientDataScore(
        id: String,
        areaKey: String,
        category: com.king.mob_app_assignment.domain.model.ReportCategory,
        bucketReports: List<Report>,
        now: Long,
    ): RiskScore {
        val (centerLat, centerLng) = if (bucketReports.isNotEmpty()) {
            RiskScorer.areaCenterFor(bucketReports.first().latitude, bucketReports.first().longitude)
        } else 0.0 to 0.0
        return RiskScore(
            id = id,
            areaKey = areaKey,
            areaLabel = "Near ${"%.3f".format(centerLat)}, ${"%.3f".format(centerLng)}",
            category = category,
            centerLatitude = centerLat,
            centerLongitude = centerLng,
            insufficientData = true,
            contributingReportIds = bucketReports.map { it.id },
            recommendation = "Insufficient data - only ${bucketReports.size} report(s) recorded here, at least ${RiskScorer.MIN_REPORTS_FOR_SCORE} needed for a meaningful score.",
            updatedAt = now,
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "risk_refresh_periodic"
        private const val MAX_ATTEMPTS_BEFORE_GIVING_UP = 3
    }
}
