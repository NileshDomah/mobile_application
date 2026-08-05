package com.king.mob_app_assignment.domain.model

enum class ReportPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT;

    companion object {
        fun fromFirestoreValue(value: String?): ReportPriority =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NORMAL
    }
}
