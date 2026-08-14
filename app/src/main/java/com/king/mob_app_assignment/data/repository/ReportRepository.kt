package com.king.mob_app_assignment.data.repository

import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.ReportPriority
import com.king.mob_app_assignment.domain.model.ReportStatus
import kotlinx.coroutines.flow.Flow
import java.io.File

interface ReportRepository {

    // One-shot events (no replay) for a report-list snapshot listener failing - e.g. a genuine
    // PERMISSION_DENIED, not the brief null-snapshot blips already filtered out below. Meant to
    // drive a dismissible Snackbar/banner in the UI, not to replace the list with a full error
    // screen (the list still shows whatever it last had, which is the friendlier behavior).
    val reportListErrors: Flow<String>

    fun getAllReports(): Flow<List<Report>>

    fun getReportsForUser(uid: String): Flow<List<Report>>

    fun getReportById(id: String): Flow<Report?>

    // Superseded by resolveWithNote for IN_REVIEW/RESOLVED transitions, which pairs the status
    // change with a required resolution note. Kept for any other status transition path.
    suspend fun updateReportStatus(id: String, status: ReportStatus): Result<Unit>

    // Admin-only (enforced by firestore.rules isAdmin() branch). Triages a report to a
    // department without touching status/priority/note.
    suspend fun assignDepartment(id: String, department: Department?): Result<Unit>

    // Admin-only. Sets the urgency indicator shown in the admin list/detail views.
    suspend fun setPriority(id: String, priority: ReportPriority): Result<Unit>

    // Admin-only. The primary way an admin moves a report to IN_REVIEW or RESOLVED: writes
    // status + resolutionNote atomically, and - only when resolving with an attached photo -
    // resolutionPhotoPath, mirroring the device-local-only photo storage used for the original
    // report capture (no Firebase Storage in this project).
    suspend fun resolveWithNote(
        id: String,
        status: ReportStatus,
        note: String,
        resolutionPhotoFile: File?,
    ): Result<Unit>

    // Owner-only, and only while the report is still SUBMITTED (enforced both here and by
    // firestore.rules) - lets a citizen fix a mis-classified category/note before it's reviewed.
    // Photo/location/timestamp/status are intentionally not editable through this path since
    // they are the evidentiary capture of the report.
    suspend fun updateReport(id: String, category: ReportCategory, note: String, customLabel: String = ""): Result<Unit>

    // Owner-only, and only while the report is still SUBMITTED - a citizen "I made a mistake"
    // self-service action. Once a report is in review/resolved, deletion is disallowed.
    suspend fun deleteReport(id: String): Result<Unit>

    // Called by AiReviewCoordinator after background classification disagrees with (or can't
    // confidently confirm) the user's chosen category. aiCategory is null when the classifier
    // itself failed/timed out rather than actively disagreeing.
    suspend fun flagForAiReview(id: String, aiCategory: ReportCategory?, aiCustomLabel: String = ""): Result<Unit>

    // Called by AiReviewCoordinator when a report was submitted as UNCATEGORIZED (user skipped
    // the category picker) and the AI confidently classifies it - sets the real category
    // directly, no review flag needed since the user never made a choice to disagree with.
    // priority is the AI's severity guess (only ever non-NORMAL from the Gemma vision path, see
    // CategorySuggestion.priority) - applied alongside category since both come from the same
    // classification pass, rather than a second separate write.
    suspend fun applyAiCategory(
        id: String,
        category: ReportCategory,
        customLabel: String = "",
        priority: ReportPriority = ReportPriority.NORMAL,
    ): Result<Unit>

    // Owner-only: clears needsAiReview after the user updates the category/note in response to
    // an AI flag, and marks the report as having gone through that resubmit-after-AI-review path
    // (kept as a permanent, non-clearable audit trail field, not toggled back off later).
    suspend fun resubmitAfterAiReview(id: String, category: ReportCategory, note: String, customLabel: String = ""): Result<Unit>

    suspend fun findNearbyRecentReports(
        category: ReportCategory,
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        withinMinutes: Long,
    ): List<Report>

    // Checks connectivity once at call time; if offline, queues into Room PendingReportEntity
    // and returns success immediately, otherwise uploads to Storage + writes to Firestore directly.
    suspend fun submitReport(report: Report, photoFile: File?): Result<Report>

    suspend fun createReport(report: Report): Result<Report>

    // Drains the Room pending-report queue: attempts Storage upload + Firestore write for each
    // row, deleting it on success and leaving it in place (for the next retry) on failure.
    suspend fun syncPendingReports()

    // Any signed-in user other than the report's owner may toggle their upvote (see
    // firestore.rules for the matching update-rule branch). Implemented as a plain read-then-
    // write rather than a transaction, per the scope of this feature - see PLACEHOLDERS_TODO.csv
    // for the race-condition caveat under concurrent upvotes.
    suspend fun toggleUpvote(id: String, uid: String): Result<Unit>

    fun startStatusChangeListener(uid: String)

    fun stopStatusChangeListener()
}
