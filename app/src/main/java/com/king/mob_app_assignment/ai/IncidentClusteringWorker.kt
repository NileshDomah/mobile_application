package com.king.mob_app_assignment.ai

import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.content.Context
import com.king.mob_app_assignment.data.repository.ClusteringOutcome
import com.king.mob_app_assignment.data.repository.IncidentRepository
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.util.CrashReporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Runs incident clustering as a WorkManager job, mirroring ClassificationWorker's shape exactly
 * (durable across process death, retried by WorkManager rather than hand-rolled retry logic).
 * Enqueued from CaptureViewModel right after a successful report submission - see
 * enqueueIncidentClusteringWork().
 *
 * There is no server-side clustering compute in this project (no Cloud Functions - see
 * REPORT_NOTES.md's "Infrastructure Incident Intelligence" entry for the full reasoning) - this
 * runs on whichever citizen's device happens to submit a report, using
 * IncidentRepository.evaluateAndClusterReport() to decide whether it belongs to an existing
 * incident, should start a new one, or is a low-confidence candidate left for admin review.
 */
@HiltWorker
class IncidentClusteringWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val incidentRepository: IncidentRepository,
    private val reportRepository: ReportRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reportId = inputData.getString(KEY_REPORT_ID) ?: return Result.failure()

        val report = runCatching { reportRepository.getReportById(reportId).first() }
            .getOrNull()
            ?: run {
                CrashReporter.log("IncidentClusteringWorker: report $reportId not found")
                return if (runAttemptCount >= MAX_ATTEMPTS_BEFORE_GIVING_UP) Result.failure() else Result.retry()
            }

        val outcome = runCatching { incidentRepository.evaluateAndClusterReport(report) }
            .onFailure {
                CrashReporter.log("IncidentClusteringWorker: unexpected exception clustering report $reportId")
                CrashReporter.recordException(it)
            }
            .getOrNull()

        return when (outcome) {
            is ClusteringOutcome.AttachedToExisting,
            is ClusteringOutcome.CreatedNew,
            is ClusteringOutcome.LowConfidenceCandidate,
            ClusteringOutcome.NoMatch,
            -> Result.success()
            null -> if (runAttemptCount >= MAX_ATTEMPTS_BEFORE_GIVING_UP) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val KEY_REPORT_ID = "report_id"
        private const val MAX_ATTEMPTS_BEFORE_GIVING_UP = 3
    }
}
