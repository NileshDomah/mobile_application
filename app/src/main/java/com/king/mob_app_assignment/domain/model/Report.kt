package com.king.mob_app_assignment.domain.model

enum class ReportMediaType { PHOTO, VIDEO }

data class Report(
    val id: String = "",
    // Empty when isAnonymous == true - no owner is ever recorded for an anonymous report, by
    // design (see isAnonymous doc below). Always non-empty for a normal report.
    val userId: String = "",
    val category: ReportCategory = ReportCategory.OTHER,
    // Free-text label the user (or AI classifier) provided when category == CUSTOM, e.g.
    // "Fallen tree blocking road" or "Burst water pipe". Ignored/blank for every other category.
    val customLabel: String = "",
    val photoUrl: String? = null,
    val localPhotoPath: String? = null,
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
    val upvoteCount: Int = 0,
    val upvotedByUids: List<String> = emptyList(),
    // Timestamped log of every status transition this report has gone through, oldest first,
    // starting with the initial SUBMITTED entry written at creation. Purely additive (appended
    // via Firestore arrayUnion, never rewritten/reordered) so it's safe against concurrent admin
    // edits. Rendered as a timeline on the report detail screen - see StatusTimeline.kt.
    val statusHistory: List<StatusHistoryEntry> = emptyList(),
)

data class StatusHistoryEntry(
    val status: ReportStatus = ReportStatus.SUBMITTED,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
)
