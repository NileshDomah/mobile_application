package com.king.mob_app_assignment.data.repository

import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.Incident
import com.king.mob_app_assignment.domain.model.IncidentStatus
import com.king.mob_app_assignment.domain.model.IncidentValidationState
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportPriority
import kotlinx.coroutines.flow.Flow

interface IncidentRepository {

    val incidentListErrors: Flow<String>

    fun getAllIncidents(): Flow<List<Incident>>

    fun getIncidentById(id: String): Flow<Incident?>

    // Live, so a citizen viewing a report that later gets clustered sees the incident card
    // appear without needing to reopen the screen.
    fun getIncidentForReport(incidentId: String): Flow<Incident?>

    /**
     * The core clustering decision, called by [com.king.mob_app_assignment.ai.IncidentClusteringWorker]
     * after every new report and re-usable from an admin "re-analyze" action. Looks for an
     * existing OPEN (not RESOLVED/VERIFIED/CLOSED/DISMISSED) incident this report plausibly
     * belongs to; if none exists and enough OTHER unclustered reports are nearby, proposes a new
     * one. Returns null (does nothing) when confidence is too low to act on automatically - per
     * the brief's explicit "do not automatically merge reports if confidence is low" requirement,
     * a low-confidence candidate is surfaced to admins for manual review rather than silently
     * ignored or silently merged; see AiReviewCoordinator-style needsReview flagging, mirrored
     * here as [ClusteringOutcome.LOW_CONFIDENCE_CANDIDATE].
     */
    suspend fun evaluateAndClusterReport(report: Report): ClusteringOutcome

    // Admin-only (enforced by firestore.rules isAdmin() branch) - explicit, always full-
    // confidence membership regardless of what any automatic score would have said.
    suspend fun manuallyMergeReport(incidentId: String, reportId: String, performedByUid: String): Result<Unit>

    // Removes a report from its incident (does not delete the report, does not delete the
    // incident even if it becomes empty - an admin who unmerges everything can still see the
    // incident existed, per the "maintain a complete incident history" requirement).
    suspend fun unmergeReport(incidentId: String, reportId: String, performedByUid: String): Result<Unit>

    suspend fun updateStatus(incidentId: String, status: IncidentStatus, note: String, performedByUid: String): Result<Unit>

    suspend fun assignDepartment(incidentId: String, department: Department?, performedByUid: String): Result<Unit>

    suspend fun setSeverity(incidentId: String, severity: ReportPriority): Result<Unit>

    suspend fun setValidationNote(incidentId: String, note: String): Result<Unit>

    // Explicit admin validate/reject decision (part B of the resolution lifecycle: Validation).
    // Distinct from IncidentStatus's own DETECTED/VALIDATING/CONFIRMED progression - this
    // records WHETHER an admin agreed the incident is real, not WHERE it sits in the pipeline.
    // Rejecting does NOT delete the incident or unmerge its reports (see "maintain a complete
    // incident history") - it just marks it REJECTED and, by convention, an admin typically also
    // moves the status to DISMISSED afterward via updateStatus.
    suspend fun setValidationState(incidentId: String, state: IncidentValidationState, note: String, performedByUid: String): Result<Unit>

    // Recomputes and persists the transparent priority score (see PriorityScorer) from the
    // incident's current inputs - called after any change that could affect it (membership,
    // severity, status). Kept as an explicit recompute step rather than a live-derived value so
    // the stored score reflects a specific point in time an admin can audit against, consistent
    // with how reportCount is already denormalized rather than always recomputed on read.
    suspend fun recomputePriorityScore(incidentId: String): Result<Unit>
}

/**
 * Result of [IncidentRepository.evaluateAndClusterReport] - a plain outcome type rather than a
 * boolean/nullable Incident, so callers (the clustering worker, an admin re-analyze action) can
 * distinguish "nothing to do" from "did something" from "found something but didn't act on it"
 * without three separate nullable fields.
 */
sealed interface ClusteringOutcome {
    data class AttachedToExisting(val incidentId: String, val confidence: Float) : ClusteringOutcome
    data class CreatedNew(val incidentId: String, val memberReportIds: List<String>) : ClusteringOutcome
    // Confidence was above the "worth mentioning" floor but below the auto-merge threshold - the
    // report is NOT attached to anything; incidentId is null. Surfaced to admins as a suggestion
    // to review manually (see AdminIncidentsViewModel), not silently discarded.
    data class LowConfidenceCandidate(val incidentId: String, val confidence: Float) : ClusteringOutcome
    // Nothing nearby at all worth mentioning - the normal case for most reports most of the time.
    data object NoMatch : ClusteringOutcome
}
