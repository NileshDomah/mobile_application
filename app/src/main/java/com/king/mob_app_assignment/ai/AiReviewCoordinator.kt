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
        appScope.launch {
            CrashReporter.log("AiReviewCoordinator: reviewing report $reportId in background")
            val suggestion = runCatching { categoryClassifier.classify(photoUri) }.getOrNull()
            val confidentCategory = suggestion?.takeIf { it.confidence >= AGREEMENT_CONFIDENCE_THRESHOLD }?.category

            if (userSelectedCategory == ReportCategory.UNCATEGORIZED) {
                if (confidentCategory != null) {
                    reportRepository.applyAiCategory(reportId, confidentCategory, suggestion.customLabel)
                    CrashReporter.log("AiReviewCoordinator: report $reportId auto-categorized as ${confidentCategory.name}")
                } else {
                    reportRepository.flagForAiReview(reportId, suggestion?.category, suggestion?.customLabel.orEmpty())
                    CrashReporter.log("AiReviewCoordinator: report $reportId left uncategorized, flagged for review")
                }
                return@launch
            }

            val disagreesOrUnsure = confidentCategory == null || confidentCategory != userSelectedCategory
            if (disagreesOrUnsure) {
                reportRepository.flagForAiReview(reportId, suggestion?.category, suggestion?.customLabel.orEmpty())
                CrashReporter.log("AiReviewCoordinator: report $reportId flagged for review")
            } else {
                CrashReporter.log("AiReviewCoordinator: report $reportId confirmed by AI, no action")
            }
        }
    }
}
