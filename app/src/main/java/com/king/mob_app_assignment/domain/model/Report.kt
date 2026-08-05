package com.king.mob_app_assignment.domain.model

data class Report(
    val id: String = "",
    val userId: String = "",
    val category: ReportCategory = ReportCategory.OTHER,
    // Free-text label the user (or AI classifier) provided when category == CUSTOM, e.g.
    // "Fallen tree blocking road" or "Burst water pipe". Ignored/blank for every other category.
    val customLabel: String = "",
    val photoUrl: String? = null,
    val localPhotoPath: String? = null,
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
)
