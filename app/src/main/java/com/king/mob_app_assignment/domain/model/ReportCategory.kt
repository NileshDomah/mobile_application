package com.king.mob_app_assignment.domain.model

enum class ReportCategory {
    POTHOLE,
    STREETLIGHT,
    GARBAGE,
    WATER_LEAK,
    OTHER;

    companion object {
        fun fromFirestoreValue(value: String?): ReportCategory =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
    }
}
