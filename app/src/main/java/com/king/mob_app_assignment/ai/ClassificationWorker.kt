package com.king.mob_app_assignment.ai

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.util.CrashReporter
import com.king.mob_app_assignment.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs AI report classification as a WorkManager job instead of a plain appScope coroutine, so
 * it reliably completes even if the app process is killed right after submit (user swipes the
 * app away, low-memory kill, etc) - a plain coroutine on an app-scoped CoroutineScope dies with
 * the process; a WorkManager job is persisted and resumed by the OS. Enqueued with
 * NetworkType.CONNECTED as a constraint (see CaptureViewModel) so it doesn't even attempt to run
 * until the device has connectivity, and WorkManager's built-in exponential backoff handles
 * transient failures (e.g. the LAN AI server briefly unreachable) without any custom retry logic
 * here.
 *
 * On genuine failure (both the Gemma vision LLM and the ML Kit fallback failed - see
 * AiReviewOutcome.FAILED) after retries are exhausted, sends a notification telling the user
 * manual category input is needed rather than leaving the report silently uncategorized forever.
 */
@HiltWorker
class ClassificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val aiReviewCoordinator: AiReviewCoordinator,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reportId = inputData.getString(KEY_REPORT_ID) ?: return Result.failure()
        val photoUriString = inputData.getString(KEY_PHOTO_URI) ?: return Result.failure()
        val userSelectedCategoryName = inputData.getString(KEY_USER_SELECTED_CATEGORY)
        val userSelectedCategory = ReportCategory.entries.firstOrNull { it.name == userSelectedCategoryName }
            ?: ReportCategory.UNCATEGORIZED

        val result = runCatching {
            aiReviewCoordinator.review(reportId, Uri.parse(photoUriString), userSelectedCategory)
        }.getOrElse {
            CrashReporter.log("ClassificationWorker: unexpected exception classifying report $reportId")
            CrashReporter.recordException(it)
            null
        }

        return when (result?.outcome) {
            AiReviewOutcome.AUTO_CATEGORIZED, AiReviewOutcome.CONFIRMED -> {
                // "Tell me whether the AI could classify this or not" - fires even if the app is
                // fully closed, same as notifyClassificationFailed below, since this runs inside
                // a WorkManager job rather than a plain in-process coroutine. Only these two
                // outcomes actually wrote result.category to the report (see AiReviewCoordinator) -
                // this notification's confident wording is only accurate for them.
                result.category?.let { notificationHelper.notifyClassificationSucceeded(reportId, it.name) }
                Result.success()
            }
            AiReviewOutcome.FLAGGED -> {
                // BUG FIX (found via user report, 2026-11-18): previously fell into the same
                // branch as AUTO_CATEGORIZED/CONFIRMED and sent the same confident "We identified
                // this as X" notification - but FLAGGED means the AI's guess was NOT applied to
                // the report (flagForAiReview only ever writes needsAiReview/aiSuggestedCategory,
                // never the real category field), so the report stayed UNCATEGORIZED while the
                // notification claimed a definite category - a direct, visible contradiction.
                result.category?.let { notificationHelper.notifyClassificationNeedsReview(reportId, it.name) }
                Result.success()
            }
            AiReviewOutcome.FAILED, null -> {
                if (runAttemptCount >= MAX_ATTEMPTS_BEFORE_GIVING_UP) {
                    notificationHelper.notifyClassificationFailed(reportId)
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        }
    }

    companion object {
        const val KEY_REPORT_ID = "report_id"
        const val KEY_PHOTO_URI = "photo_uri"
        const val KEY_USER_SELECTED_CATEGORY = "user_selected_category"
        private const val MAX_ATTEMPTS_BEFORE_GIVING_UP = 3
    }
}
