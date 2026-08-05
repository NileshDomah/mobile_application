package com.king.mob_app_assignment.domain.model

enum class Department {
    ROADS,
    SANITATION,
    ELECTRICAL,
    WATER,
    OTHER;

    companion object {
        fun fromFirestoreValue(value: String?): Department? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

fun Department.displayLabel(): String = when (this) {
    Department.ROADS -> "Roads"
    Department.SANITATION -> "Sanitation"
    Department.ELECTRICAL -> "Electrical"
    Department.WATER -> "Water"
    Department.OTHER -> "Other"
}
