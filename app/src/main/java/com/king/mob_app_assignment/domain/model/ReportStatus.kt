package com.king.mob_app_assignment.domain.model

enum class ReportStatus {
    SUBMITTED,
    IN_REVIEW,
    RESOLVED;

    companion object {
        fun fromFirestoreValue(value: String?): ReportStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SUBMITTED
    }
}
