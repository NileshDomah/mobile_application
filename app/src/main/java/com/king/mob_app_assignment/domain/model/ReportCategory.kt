package com.king.mob_app_assignment.domain.model

enum class ReportCategory {
    POTHOLE,
    STREETLIGHT,
    GARBAGE,
    WATER_LEAK,
    OTHER,

    // A real, user-selectable category for issues that don't fit the fixed list above (fallen
    // trees, burst pipes, downed power lines, etc). When this is selected, Report.customLabel
    // holds the free-text description the user typed (e.g. "Fallen tree blocking road"). The AI
    // classifier can also propose CUSTOM with its own short label when the photo clearly doesn't
    // match any fixed category - see VisionClassifierService's prompt and Report.customLabel.
    CUSTOM,

    // Placeholder set when a report is submitted without a manually chosen category (the user
    // is allowed to skip picking one now - AI classifies it in the background afterward, see
    // AiReviewCoordinator). Not selectable by the user directly in the category picker.
    UNCATEGORIZED;

    companion object {
        fun fromFirestoreValue(value: String?): ReportCategory =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER

        // UNCATEGORIZED is a submit-time placeholder, not something a user or the AI classifier
        // should ever pick from a list of options. CUSTOM IS selectable - it's the real
        // "describe your own issue" escape hatch.
        val selectableEntries: List<ReportCategory> = entries.filterNot { it == UNCATEGORIZED }
    }
}
