package com.king.mob_app_assignment.domain.model

enum class UserRole {
    CITIZEN,
    ADMIN;

    companion object {
        fun fromFirestoreValue(value: String?): UserRole =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CITIZEN
    }
}
