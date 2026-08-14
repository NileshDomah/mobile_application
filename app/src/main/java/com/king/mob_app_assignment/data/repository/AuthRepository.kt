package com.king.mob_app_assignment.data.repository

import com.king.mob_app_assignment.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    val currentUser: Flow<User?>

    suspend fun register(email: String, password: String, displayName: String, username: String): Result<User>

    // isUsernameTaken checks the /usernames/{username} reservation doc directly - used for
    // live availability feedback on the register screen, separate from the actual reservation
    // write (which happens transactionally inside register() itself to avoid a race between the
    // check and the real write).
    suspend fun isUsernameTaken(username: String): Boolean

    suspend fun login(email: String, password: String): Result<User>

    // Resolves a username to its account email via the /usernames/{username} reservation doc,
    // so LoginViewModel can accept a username at the login screen and still call login() (above)
    // with a real email underneath - Firebase Auth itself only ever signs in by email/password.
    suspend fun resolveUsernameToEmail(username: String): String?

    fun logout()

    suspend fun fetchCurrentUserProfile(): Result<User?>
}
