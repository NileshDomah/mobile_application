package com.king.mob_app_assignment.domain.model

/**
 * Lifecycle independent from an individual Report's status (SUBMITTED/IN_REVIEW/RESOLVED) - an
 * incident aggregating 17 reports can be IN_PROGRESS while some of those reports are still
 * individually SUBMITTED (their owners haven't been notified of resolution yet) and others are
 * RESOLVED (an admin closed them directly before the incident concept existed, or a citizen's
 * own report happened to get fixed independently). The two state machines are related but
 * deliberately not the same one - see REPORT_NOTES.md for the reasoning.
 */
enum class IncidentStatus {
    DETECTED,
    VALIDATING,
    CONFIRMED,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    VERIFIED,
    CLOSED,
    // Not part of the brief's happy-path lifecycle list, but required: an admin can determine a
    // cluster of reports was NOT actually the same underlying problem (e.g. coincidental
    // same-category reports that aren't related) and dismiss the incident without resolving
    // anything - the member reports remain untouched and can be unmerged/re-clustered later.
    DISMISSED;

    companion object {
        fun fromFirestoreValue(value: String?): IncidentStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DETECTED
    }
}

/**
 * How an incident's member-report list came to include a given report - distinguishes
 * automatic clustering (which must stay reversible/inspectable, per the brief's "do not
 * automatically merge if confidence is low" requirement) from a deliberate admin action.
 */
enum class IncidentMembershipSource { AUTO_CLUSTERED, ADMIN_MERGED }

data class IncidentMember(
    val reportId: String = "",
    val source: IncidentMembershipSource = IncidentMembershipSource.AUTO_CLUSTERED,
    // The confidence score (0f-1f) the clustering pass computed when this report was
    // auto-associated - always 1f for ADMIN_MERGED (an explicit admin decision is definitionally
    // full-confidence). Kept per-member, not just on the incident as a whole, since an incident's
    // members can be added incrementally over time by different clustering passes with
    // different scores.
    val confidence: Float = 1f,
    val addedAt: Long = System.currentTimeMillis(),
)

data class IncidentHistoryEntry(
    val status: IncidentStatus = IncidentStatus.DETECTED,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val performedByUid: String = "",
)

/** Explicit admin validate/reject decision - separate from IncidentStatus's DETECTED/VALIDATING/
 * CONFIRMED progression, which describes WHERE an incident is in the lifecycle, not WHETHER an
 * admin actually looked at it and agreed it's real. PENDING is the default for a brand-new
 * auto-detected incident nobody has reviewed yet. */
enum class IncidentValidationState { PENDING, VALIDATED, REJECTED }

/**
 * One line-item of a transparent priority score breakdown, e.g. {label="Report count", points=18,
 * explanation="9 reports x 2 points, capped at 20"} - rendered directly on IncidentDetailScreen
 * so "why was this priority calculated" (an explicit brief requirement) has a real, inspectable
 * answer rather than a single opaque number. See PriorityScorer.kt for how these are computed.
 */
data class PriorityFactor(
    val label: String = "",
    val points: Int = 0,
    val explanation: String = "",
)

/**
 * The incident layer sits ABOVE individual reports - see REPORT_NOTES.md for the full rationale.
 * A report keeps existing and remains independently viewable/editable exactly as before
 * (Report.incidentId just POINTS at this, it does not replace anything on Report); this document
 * is the aggregate view an authority manages once several reports are recognized as describing
 * the same underlying infrastructure problem.
 */
data class Incident(
    val id: String = "",
    // Human-readable reference, e.g. "INC-2026-0F3A2C" - same derivation approach as
    // reportReferenceCode(), see incidentReferenceCode().
    val title: String = "",
    val category: ReportCategory = ReportCategory.OTHER,
    val customLabel: String = "",
    val status: IncidentStatus = IncidentStatus.DETECTED,
    val severity: ReportPriority = ReportPriority.NORMAL,
    val department: Department? = null,
    // Centroid of the member reports' coordinates at the time it was last recomputed - "the
    // area this incident is about", not any single report's exact location.
    val centerLatitude: Double = 0.0,
    val centerLongitude: Double = 0.0,
    val address: String = "",
    // Denormalized count, kept in sync by IncidentRepositoryImpl on every membership/report
    // change - avoids every screen that lists incidents needing to separately fetch and count
    // each incident's member reports just to show "17 reports".
    val reportCount: Int = 0,
    val members: List<IncidentMember> = emptyList(),
    val history: List<IncidentHistoryEntry> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Set once validation happens (see IncidentValidation) - kept on the incident itself rather
    // than only in history, so "why was this confirmed/rejected" is visible without scanning the
    // whole history list.
    val validationNote: String = "",
    val validationState: IncidentValidationState = IncidentValidationState.PENDING,
    // 0-100 transparent priority score, recomputed by PriorityScorer whenever the incident's
    // inputs change (report count, severity, age, department risk) - see PriorityFactor's doc
    // for why the breakdown, not just this number, is what's actually shown to admins.
    val priorityScore: Int = 0,
    val priorityFactors: List<PriorityFactor> = emptyList(),
)

fun incidentReferenceCode(incidentId: String): String {
    if (incidentId.isBlank()) return "INC-PENDING"
    val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val slice = incidentId.filter { it.isLetterOrDigit() }.take(6).uppercase()
    return "INC-$year-$slice"
}

fun IncidentStatus.displayLabel(): String = when (this) {
    IncidentStatus.DETECTED -> "Detected"
    IncidentStatus.VALIDATING -> "Validating"
    IncidentStatus.CONFIRMED -> "Confirmed"
    IncidentStatus.ASSIGNED -> "Assigned"
    IncidentStatus.IN_PROGRESS -> "In Progress"
    IncidentStatus.RESOLVED -> "Resolved"
    IncidentStatus.VERIFIED -> "Verified"
    IncidentStatus.CLOSED -> "Closed"
    IncidentStatus.DISMISSED -> "Dismissed"
}
