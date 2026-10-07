package com.king.mob_app_assignment.ai

import android.net.Uri
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.util.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val AGREEMENT_CONFIDENCE_THRESHOLD = 0.5f

/**
 * Outcome of a single classification attempt, used by [ClassificationWorker] to decide whether
 * to retry or send the "classification failed, please choose manually" notification. Distinct
 * from the confident-vs-flagged split inside [AiReviewCoordinator.review] - a report can be
 * legitimately FLAGGED (AI ran fine but disagreed/was unsure) without that being a FAILURE (AI
 * couldn't run at all - both the Gemma server and ML Kit threw).
 */
enum class AiReviewOutcome { AUTO_CATEGORIZED, CONFIRMED, FLAGGED, FAILED }

// category is null only for AiReviewOutcome.FAILED (no classification result exists to report a
// category for). For every other outcome it's the category the AI actually settled on - the
// user's own pick for CONFIRMED/FLAGGED (FLAGGED still carries the classifier's own suggestion,
// not the user's, since that's the more useful one to show in a "here's what we detected"
// notification), or the newly-applied AI category for AUTO_CATEGORIZED.
data class AiReviewResult(val outcome: AiReviewOutcome, val category: ReportCategory?)

// Runs AI category classification AFTER a report has already been submitted, on an
// app-scoped CoroutineScope (passed in from MobAppAssignment) rather than a screen-scoped
// ViewModel one - classification must survive the user navigating away from CaptureScreen,
// since submit is no longer blocked on it.
//
// Two distinct cases:
// 1. userSelectedCategory == UNCATEGORIZED (user skipped the picker entirely): if the AI
//    produces a confident answer, that becomes the report's real category directly - there is
//    no "disagreement" to flag since the user never made a choice. If the AI is unsure, the
//    report is flagged for review so the citizen picks a category manually.
// 2. userSelectedCategory is a real pick: if the AI's classification disagrees with it (or the
//    classifier can't produce a confident answer at all), the report is flagged via
//    needsAiReview/aiSuggestedCategory so it surfaces in My Reports' "Needs Your Input" section.
@Singleton
class AiReviewCoordinator @Inject constructor(
    private val categoryClassifier: CategoryClassifier,
    private val reportRepository: ReportRepository,
) {
    fun reviewInBackground(appScope: CoroutineScope, reportId: String, photoUri: Uri, userSelectedCategory: ReportCategory) {
        appScope.launch { review(reportId, photoUri, userSelectedCategory) }
    }

    // Suspends until classification (and the resulting Firestore write) completes, and reports
    // what happened via AiReviewResult - used by ClassificationWorker, which needs a real result
    // to decide retry vs. give-up vs. which "classification succeeded/failed" notification to send.
    suspend fun review(reportId: String, photoUri: Uri, userSelectedCategory: ReportCategory): AiReviewResult {
        CrashReporter.log("AiReviewCoordinator: reviewing report $reportId")
        val suggestion = runCatching { categoryClassifier.classify(photoUri) }.getOrNull()
        if (suggestion == null) {
            CrashReporter.log("AiReviewCoordinator: report $reportId - both classifiers failed")
            return AiReviewResult(AiReviewOutcome.FAILED, category = null)
        }
        val confidentCategory = suggestion.takeIf { it.confidence >= AGREEMENT_CONFIDENCE_THRESHOLD }?.category

        if (userSelectedCategory == ReportCategory.UNCATEGORIZED) {
            return if (confidentCategory != null) {
                reportRepository.applyAiCategory(reportId, confidentCategory, suggestion.customLabel, suggestion.priority)
                CrashReporter.log("AiReviewCoordinator: report $reportId auto-categorized as ${confidentCategory.name}")
                AiReviewResult(AiReviewOutcome.AUTO_CATEGORIZED, confidentCategory)
            } else {
                reportRepository.flagForAiReview(reportId, suggestion.category, suggestion.customLabel)
                CrashReporter.log("AiReviewCoordinator: report $reportId left uncategorized, flagged for review")
                AiReviewResult(AiReviewOutcome.FLAGGED, suggestion.category)
            }
        }

        val disagreesOrUnsure = confidentCategory == null || confidentCategory != userSelectedCategory
        return if (disagreesOrUnsure) {
            reportRepository.flagForAiReview(reportId, suggestion.category, suggestion.customLabel)
            CrashReporter.log("AiReviewCoordinator: report $reportId flagged for review")
            AiReviewResult(AiReviewOutcome.FLAGGED, suggestion.category)
        } else {
            CrashReporter.log("AiReviewCoordinator: report $reportId confirmed by AI, no action")
            AiReviewResult(AiReviewOutcome.CONFIRMED, userSelectedCategory)
        }
    }
}
