package com.king.mob_app_assignment.domain.model

enum class ReportMediaType { PHOTO, VIDEO }

data class Report(
    val id: String = "",
    // Empty when isAnonymous == true - no owner is ever recorded for an anonymous report, by
    // design (see isAnonymous doc below). Always non-empty for a normal report.
    val userId: String = "",
    // Denormalized snapshot of the reporter's own displayName at submit time - see
    // FirestoreConstants.ReportFields.REPORTER_NAME's own doc comment for why (lets admin-facing
    // screens show who filed a report without a per-report /users/{userId} lookup). Always blank
    // for an anonymous report, same as userId above.
    val reporterName: String = "",
    val category: ReportCategory = ReportCategory.OTHER,
    // Free-text label the user (or AI classifier) provided when category == CUSTOM, e.g.
    // "Fallen tree blocking road" or "Burst water pipe". Ignored/blank for every other category.
    val customLabel: String = "",
    val photoUrl: String? = null,
    val localPhotoPath: String? = null,
    // Every photo after the first (localPhotoPath is always the primary/first one) - added via
    // the form-first capture flow's "Add more" tile. Always empty for a shake-to-report capture,
    // which only ever collects one photo. Kept as a separate additive field rather than replacing
    // localPhotoPath with a list, so every report document written before multi-photo support was
    // added (which only ever populated localPhotoPath) still reads back correctly with an empty
    // list here instead of needing a schema migration.
    val additionalLocalPhotoPaths: List<String> = emptyList(),
    val mediaType: ReportMediaType = ReportMediaType.PHOTO,
    // Only populated when mediaType == VIDEO - mirrors localPhotoPath/photoUrl's device-local-only
    // storage model (no Firebase Storage in this project, see PLACEHOLDERS_TODO.csv). A video
    // report's localPhotoPath is left blank; the two are mutually exclusive by mediaType.
    val localVideoPath: String? = null,
    val videoUrl: String? = null,
    // When true: userId is stored empty (see above), the report cannot be edited/deleted by
    // anyone after submission (there is no owner to authorize that), it's excluded from
    // duplicate-report userId checks, and it never triggers a status-change notification (no
    // uid to notify). Citizen-facing UI shows "Anonymous" instead of any submitter identity.
    val isAnonymous: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val headingDegrees: Float = 0f,
    val headingCardinal: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val status: ReportStatus = ReportStatus.SUBMITTED,
    val aiSuggestedCategory: ReportCategory? = null,
    // Only meaningful when aiSuggestedCategory == CUSTOM - the AI's proposed free-text label.
    val aiSuggestedCustomLabel: String = "",
    val needsAiReview: Boolean = false,
    val wasResubmittedAfterAiReview: Boolean = false,
    val department: Department? = null,
    val priority: ReportPriority = ReportPriority.NORMAL,
    val resolutionNote: String = "",
    val resolutionPhotoPath: String? = null,
    // Timestamped log of every status transition this report has gone through, oldest first,
    // starting with the initial SUBMITTED entry written at creation. Purely additive (appended
    // via Firestore arrayUnion, never rewritten/reordered) so it's safe against concurrent admin
    // edits. Rendered as a timeline on the report detail screen - see StatusTimeline.kt.
    val statusHistory: List<StatusHistoryEntry> = emptyList(),
    // Blank when not (yet) associated with any incident - the common case, and the ONLY state
    // for most reports most of the time. Set by IncidentClusteringWorker (auto-clustering) or an
    // admin's manual merge action. Deliberately just a pointer, not a replacement for anything on
    // Report - the report keeps its own independent status/priority/department exactly as
    // before; the incident is a separate aggregate view layered on top, per the brief's explicit
    // "preserve the original citizen reports" requirement.
    val incidentId: String = "",
)

data class StatusHistoryEntry(
    val status: ReportStatus = ReportStatus.SUBMITTED,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
)
