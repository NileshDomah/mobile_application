package com.king.mob_app_assignment.domain.model

data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: UserRole = UserRole.CITIZEN,
)
