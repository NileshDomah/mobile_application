package com.king.mob_app_assignment.data.repository

import com.king.mob_app_assignment.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    val currentUser: Flow<User?>

    suspend fun register(email: String, password: String, displayName: String): Result<User>

    suspend fun login(email: String, password: String): Result<User>

    fun logout()

    suspend fun fetchCurrentUserProfile(): Result<User?>
}
